package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Imports local OGG files into a generated, client-only resource pack. */
@EventBusSubscriber(modid = UltimateBankingSystem.MODID, value = Dist.CLIENT)
public final class SafeAlarmOggImporter {
    private static final String PACK_DIRECTORY = "ubs-imported-alarm-sounds";
    private static final String PACK_ID = "file/" + PACK_DIRECTORY;
    private static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;
    private static boolean legacyRepairChecked;

    private SafeAlarmOggImporter() {
    }

    public record ImportResult(boolean success, boolean cancelled, String soundEventId, String message) {
    }

    public static void chooseAndImport(Consumer<ImportResult> callback) {
        Minecraft minecraft = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> chooseAndImport(minecraft))
                .whenComplete((result, error) -> minecraft.execute(
                        () -> finishImport(minecraft, result, error, callback)));
    }

    private static ImportResult chooseAndImport(Minecraft minecraft) {
        String selected;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.ogg")).flip();
            selected = TinyFileDialogs.tinyfd_openFileDialog(
                    "Import UBS bank alarm sound",
                    "",
                    filters,
                    "Ogg Vorbis audio (*.ogg)",
                    false);
        }
        if (selected == null || selected.isBlank()) {
            return new ImportResult(false, true, "", "Import cancelled.");
        }

        Path source = Path.of(selected).toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(source) || !source.getFileName().toString().toLowerCase().endsWith(".ogg")) {
                return new ImportResult(false, false, "", "Select a valid .ogg file.");
            }
            long size = Files.size(source);
            if (size <= 4L || size > MAX_FILE_BYTES) {
                return new ImportResult(false, false, "", "OGG files must be between 5 bytes and 16 MiB.");
            }
            byte[] bytes = Files.readAllBytes(source);
            if (bytes[0] != 'O' || bytes[1] != 'g' || bytes[2] != 'g' || bytes[3] != 'S') {
                return new ImportResult(false, false, "", "The selected file is not an OGG container.");
            }

            String hash = sha256(bytes).substring(0, 16);
            Path packRoot = minecraft.gameDirectory.toPath().resolve("resourcepacks").resolve(PACK_DIRECTORY);
            Path namespaceRoot = packRoot.resolve("assets").resolve(UltimateBankingSystem.MODID);
            Path soundsRoot = namespaceRoot.resolve("sounds");
            Path importedRoot = soundsRoot.resolve("imported");
            Files.createDirectories(importedRoot);
            Files.write(importedRoot.resolve(hash + ".ogg"), bytes);
            writePackMetadata(packRoot);
            writeSoundsJson(namespaceRoot.resolve("sounds.json"), importedRoot);
            Files.deleteIfExists(soundsRoot.resolve("sounds.json"));

            String eventId = UltimateBankingSystem.MODID + ":imported/" + hash;
            return new ImportResult(true, false, eventId,
                    "Imported and loaded locally as " + eventId + ". Other clients need the same resource pack.");
        } catch (IOException | NoSuchAlgorithmException exception) {
            return new ImportResult(false, false, "", "Could not import OGG: " + rootMessage(exception));
        }
    }

    private static void writePackMetadata(Path packRoot) throws IOException {
        Files.createDirectories(packRoot);
        int packFormat = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        String json = "{\n  \"pack\": {\n    \"pack_format\": " + packFormat
                + ",\n    \"description\": \"UBS imported bank alarm sounds\"\n  }\n}\n";
        Files.writeString(packRoot.resolve("pack.mcmeta"), json, StandardCharsets.UTF_8);
    }

    private static void writeSoundsJson(Path soundsJson, Path importedRoot) throws IOException {
        List<String> names;
        try (var files = Files.list(importedRoot)) {
            names = files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase().endsWith(".ogg"))
                    .map(name -> name.substring(0, name.length() - 4))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
        StringBuilder json = new StringBuilder("{\n");
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index);
            json.append("  \"imported/").append(name).append("\": {\"sounds\": [{\"name\": \"")
                    .append(UltimateBankingSystem.MODID).append(":imported/").append(name)
                    .append("\", \"stream\": false}]}");
            if (index + 1 < names.size()) json.append(',');
            json.append('\n');
        }
        json.append("}\n");
        Files.createDirectories(soundsJson.getParent());
        Files.writeString(soundsJson, json, StandardCharsets.UTF_8);
    }

    private static void finishImport(Minecraft minecraft,
                                     ImportResult result,
                                     Throwable error,
                                     Consumer<ImportResult> callback) {
        if (error != null || result == null || !result.success()) {
            ImportResult resolved = error == null && result != null
                    ? result
                    : new ImportResult(false, false, "", "Could not import OGG: " + rootMessage(error));
            if (callback != null) callback.accept(resolved);
            return;
        }
        try {
            enableAndReload(minecraft).whenComplete((ignored, reloadError) -> minecraft.execute(() -> {
                ImportResult resolved = reloadError == null
                        ? result
                        : new ImportResult(false, false, result.soundEventId(),
                        "Imported OGG, but resource reload failed: " + rootMessage(reloadError));
                if (callback != null) callback.accept(resolved);
            }));
        } catch (RuntimeException reloadError) {
            if (callback != null) {
                callback.accept(new ImportResult(false, false, result.soundEventId(),
                        "Imported OGG, but resource reload failed: " + rootMessage(reloadError)));
            }
        }
    }

    private static CompletableFuture<Void> enableAndReload(Minecraft minecraft) {
        minecraft.getResourcePackRepository().reload();
        if (!minecraft.options.resourcePacks.contains(PACK_ID)) {
            minecraft.options.resourcePacks.add(PACK_ID);
        }
        minecraft.options.save();
        minecraft.getResourcePackRepository().setSelected(minecraft.options.resourcePacks);
        return minecraft.reloadResourcePacks();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (legacyRepairChecked) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) return;
        legacyRepairChecked = true;
        Path packRoot = minecraft.gameDirectory.toPath().resolve("resourcepacks").resolve(PACK_DIRECTORY);
        Path namespaceRoot = packRoot.resolve("assets").resolve(UltimateBankingSystem.MODID);
        Path soundsRoot = namespaceRoot.resolve("sounds");
        Path importedRoot = soundsRoot.resolve("imported");
        Path legacySoundsJson = soundsRoot.resolve("sounds.json");
        Path soundsJson = namespaceRoot.resolve("sounds.json");
        if (!Files.isDirectory(importedRoot)
                || (!Files.isRegularFile(legacySoundsJson) && Files.isRegularFile(soundsJson))) {
            return;
        }
        try {
            writePackMetadata(packRoot);
            writeSoundsJson(soundsJson, importedRoot);
            Files.deleteIfExists(legacySoundsJson);
            enableAndReload(minecraft).exceptionally(error -> {
                UltimateBankingSystem.LOGGER.error("Could not reload the repaired UBS alarm sound pack", error);
                return null;
            });
        } catch (IOException error) {
            UltimateBankingSystem.LOGGER.error("Could not repair the UBS alarm sound pack", error);
        }
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String rootMessage(Throwable error) {
        if (error == null) return "Unknown error";
        Throwable cursor = error;
        while (cursor.getCause() != null) cursor = cursor.getCause();
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message;
    }
}
