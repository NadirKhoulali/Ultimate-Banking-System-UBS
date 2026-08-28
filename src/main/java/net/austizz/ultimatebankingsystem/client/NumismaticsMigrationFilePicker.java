package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.migration.numismatics.NumismaticsDataReader;
import net.austizz.ultimatebankingsystem.network.NumismaticsMigrationUploadBeginPayload;
import net.austizz.ultimatebankingsystem.network.NumismaticsMigrationUploadChunkPayload;
import net.austizz.ultimatebankingsystem.network.NumismaticsMigrationUploadFinishPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class NumismaticsMigrationFilePicker {
    private static final int CHUNK_BYTES = 256 * 1024;

    private NumismaticsMigrationFilePicker() {
    }

    public static void choose(UUID token) {
        Minecraft minecraft = Minecraft.getInstance();
        CompletableFuture.supplyAsync(NumismaticsMigrationFilePicker::readSelectedFile)
                .whenComplete((selected, error) -> minecraft.execute(() -> {
                    if (error != null) {
                        message("Could not read Numismatics data: " + rootMessage(error), ChatFormatting.RED);
                        return;
                    }
                    if (selected == null) return;
                    upload(token, selected);
                }));
    }

    private static SelectedFile readSelectedFile() {
        String selected;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.dat")).flip();
            selected = TinyFileDialogs.tinyfd_openFileDialog(
                    "Import Create: Numismatics bank data",
                    "numismatics_bank.dat",
                    filters,
                    "Minecraft SavedData (*.dat)",
                    false);
        }
        if (selected == null || selected.isBlank()) return null;
        Path path = Path.of(selected).toAbsolutePath().normalize();
        try {
            long size = Files.size(path);
            if (!Files.isRegularFile(path) || size <= 0L || size > NumismaticsDataReader.MAX_SOURCE_BYTES) {
                throw new IOException("Files must be between 1 byte and 32 MiB.");
            }
            byte[] bytes = Files.readAllBytes(path);
            return new SelectedFile(path.getFileName().toString(), bytes, sha256(bytes));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void upload(UUID token, SelectedFile file) {
        UUID uploadId = UUID.randomUUID();
        int chunks = (file.bytes().length + CHUNK_BYTES - 1) / CHUNK_BYTES;
        PacketDistributor.sendToServer(new NumismaticsMigrationUploadBeginPayload(
                token, uploadId, file.fileName(), file.bytes().length, chunks, file.sha256()));
        for (int index = 0; index < chunks; index++) {
            int start = index * CHUNK_BYTES;
            int end = Math.min(file.bytes().length, start + CHUNK_BYTES);
            PacketDistributor.sendToServer(new NumismaticsMigrationUploadChunkPayload(
                    token, uploadId, index, Arrays.copyOfRange(file.bytes(), start, end)));
        }
        PacketDistributor.sendToServer(new NumismaticsMigrationUploadFinishPayload(token, uploadId));
        message("Numismatics source uploaded for server-side validation.", ChatFormatting.GREEN);
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable.", impossible);
        }
    }

    private static void message(String value, ChatFormatting color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.displayClientMessage(
                Component.literal(value).withStyle(color), false);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null && cursor.getCause() != null) cursor = cursor.getCause();
        return cursor == null || cursor.getMessage() == null ? "Unknown error" : cursor.getMessage();
    }

    private record SelectedFile(String fileName, byte[] bytes, String sha256) {
    }
}
