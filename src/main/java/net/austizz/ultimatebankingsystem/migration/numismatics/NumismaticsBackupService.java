package net.austizz.ultimatebankingsystem.migration.numismatics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NumismaticsBackupService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long MIN_FREE_MARGIN = 64L * 1024L * 1024L;

    private NumismaticsBackupService() {
    }

    public static BackupResult create(MinecraftServer server,
                                      NumismaticsMigrationSavedData journal,
                                      Path sourcePath) throws IOException {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        UUID migrationId = journal.migrationId();
        if (migrationId == null) throw new IOException("Migration has no ID.");
        Path migrationRoot = worldRoot.resolve("ubs-migrations").resolve("numismatics")
                .resolve(migrationId.toString());
        Path backupRoot = migrationRoot.resolve("backup");
        Files.createDirectories(backupRoot);

        LinkedHashSet<Path> sources = new LinkedHashSet<>();
        addIfFile(sources, sourcePath);
        addIfFile(sources, worldRoot.resolve("data").resolve("ultimate_banking_system.dat"));
        addIfFile(sources, worldRoot.resolve("data").resolve("ultimate_banking_system_numismatics_migration.dat"));
        for (String value : journal.preflight().affectedFiles()) {
            if (value == null || value.isBlank()) continue;
            Path parsed = Path.of(value);
            addIfFile(sources, parsed.isAbsolute() ? parsed : worldRoot.resolve(parsed));
        }

        long totalBytes = 0L;
        for (Path source : sources) totalBytes = Math.addExact(totalBytes, Files.size(source));
        FileStore store = Files.getFileStore(migrationRoot);
        long required = Math.addExact(totalBytes, Math.max(MIN_FREE_MARGIN, totalBytes / 5L));
        if (store.getUsableSpace() < required) {
            throw new IOException("Not enough disk space for a verified migration backup. Required "
                    + required + " bytes, available " + store.getUsableSpace() + ".");
        }

        List<ManifestEntry> entries = new ArrayList<>();
        int externalIndex = 0;
        for (Path source : sources) {
            Path absolute = source.toAbsolutePath().normalize();
            Path relative;
            try {
                relative = worldRoot.relativize(absolute);
            } catch (IllegalArgumentException outsideWorld) {
                relative = Path.of("external", Integer.toString(externalIndex++), absolute.getFileName().toString());
            }
            Path destination = backupRoot.resolve(relative).normalize();
            if (!destination.startsWith(backupRoot)) throw new IOException("Unsafe backup path: " + destination);
            Files.createDirectories(destination.getParent());
            Files.copy(absolute, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            entries.add(new ManifestEntry(absolute.toString(), backupRoot.relativize(destination).toString(),
                    Files.size(destination), NumismaticsFileHashes.sha256(destination)));
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("migrationId", migrationId.toString());
        manifest.put("createdAt", Instant.now().toString());
        manifest.put("sourceHash", journal.sourceHash());
        manifest.put("worldRoot", worldRoot.toString());
        manifest.put("files", entries);
        Path manifestPath = migrationRoot.resolve("backup-manifest.json");
        Files.writeString(manifestPath, GSON.toJson(manifest), StandardCharsets.UTF_8);
        return new BackupResult(migrationRoot, backupRoot, manifestPath, entries.size(), totalBytes);
    }

    public static Path requestRollback(MinecraftServer server, UUID migrationId) throws IOException {
        if (server == null || migrationId == null) throw new IOException("Migration ID is required.");
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path migrationRoot = worldRoot.resolve("ubs-migrations").resolve("numismatics").resolve(migrationId.toString());
        Path manifest = migrationRoot.resolve("backup-manifest.json");
        if (!Files.isRegularFile(manifest)) throw new IOException("No verified backup manifest exists for " + migrationId + ".");
        Path marker = worldRoot.resolve("ubs-migrations").resolve("numismatics").resolve("rollback-request.txt");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, migrationId.toString(), StandardCharsets.UTF_8);
        return marker;
    }

    public static boolean restorePendingRollback(MinecraftServer server) throws IOException {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path base = worldRoot.resolve("ubs-migrations").resolve("numismatics");
        Path marker = base.resolve("rollback-request.txt");
        if (!Files.isRegularFile(marker)) return false;
        UUID migrationId;
        try {
            migrationId = UUID.fromString(Files.readString(marker, StandardCharsets.UTF_8).trim());
        } catch (RuntimeException malformed) {
            throw new IOException("Rollback request contains an invalid migration ID.", malformed);
        }
        Path migrationRoot = base.resolve(migrationId.toString()).normalize();
        Path manifestPath = migrationRoot.resolve("backup-manifest.json");
        if (!Files.isRegularFile(manifestPath)) throw new IOException("Rollback backup manifest is missing.");
        BackupManifest manifest = GSON.fromJson(Files.readString(manifestPath, StandardCharsets.UTF_8), BackupManifest.class);
        if (manifest == null || manifest.files == null || manifest.files.isEmpty()) {
            throw new IOException("Rollback backup manifest contains no files.");
        }
        Path backupRoot = migrationRoot.resolve("backup").normalize();
        for (ManifestEntry entry : manifest.files) {
            Path destination = Path.of(entry.source()).toAbsolutePath().normalize();
            if (!destination.startsWith(worldRoot)) {
                throw new IOException("Rollback refused an out-of-world destination: " + destination);
            }
            Path source = backupRoot.resolve(entry.backup()).normalize();
            if (!source.startsWith(backupRoot) || !Files.isRegularFile(source)) {
                throw new IOException("Rollback backup file is missing or unsafe: " + source);
            }
            if (!NumismaticsFileHashes.sha256(source).equalsIgnoreCase(entry.sha256())) {
                throw new IOException("Rollback checksum failed for " + source.getFileName() + ".");
            }
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
        markJournalRolledBack(worldRoot.resolve("data")
                .resolve("ultimate_banking_system_numismatics_migration.dat"));
        Files.delete(marker);
        Files.writeString(migrationRoot.resolve("rollback-complete.txt"),
                "Rollback restored before world load at " + Instant.now() + System.lineSeparator(),
                StandardCharsets.UTF_8);
        return true;
    }

    private static void markJournalRolledBack(Path journalPath) throws IOException {
        if (!Files.isRegularFile(journalPath)) return;
        CompoundTag root = NbtIo.readCompressed(journalPath, NbtAccounter.unlimitedHeap());
        CompoundTag data = root.contains("data", Tag.TAG_COMPOUND) ? root.getCompound("data") : root;
        data.putString("phase", NumismaticsMigrationPhase.ROLLED_BACK.name());
        data.putBoolean("maintenance", false);
        data.putBoolean("source_consumed", false);
        data.putString("failure", "");
        data.putString("status", "Verified backup restored before world load.");
        Path temporary = journalPath.resolveSibling(journalPath.getFileName() + ".rollback.tmp");
        NbtIo.writeCompressed(root, temporary);
        try {
            Files.move(temporary, journalPath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, journalPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void addIfFile(LinkedHashSet<Path> target, Path path) {
        if (path != null && Files.isRegularFile(path)) target.add(path.toAbsolutePath().normalize());
    }

    public record BackupResult(Path migrationRoot, Path backupRoot, Path manifest,
                               int fileCount, long totalBytes) {
    }

    private record BackupManifest(String migrationId, String createdAt, String sourceHash,
                                  String worldRoot, List<ManifestEntry> files) {
    }

    private record ManifestEntry(String source, String backup, long bytes, String sha256) {
    }
}
