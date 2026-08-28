package net.austizz.ultimatebankingsystem.migration.numismatics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class NumismaticsSourceService {
    private NumismaticsSourceService() {
    }

    public static Path worldSource(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("numismatics_bank.dat")
                .toAbsolutePath().normalize();
    }

    public static Path prepareWorldSource(MinecraftServer server) throws IOException {
        Path source = worldSource(server);
        if (Files.isRegularFile(source)) return source;
        if (!ModList.get().isLoaded("numismatics")) {
            throw new IOException("This world has no Numismatics bank data. Load Numismatics or import its data file.");
        }
        NumismaticsRuntimeBridge.writeLiveSnapshot(source);
        return source;
    }

    public static Path stageExternalSource(MinecraftServer server, UUID ownerId, Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("The selected Numismatics file does not exist.");
        }
        long size = Files.size(source);
        if (size <= 0L || size > NumismaticsDataReader.MAX_SOURCE_BYTES) {
            throw new IOException("Numismatics source files must be between 1 byte and 32 MiB.");
        }
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path staging = worldRoot.resolve("ubs-migrations").resolve("numismatics").resolve("staging");
        Files.createDirectories(staging);
        String owner = ownerId == null ? "console" : ownerId.toString();
        Path destination = staging.resolve(owner + "-" + UUID.randomUUID() + ".dat");
        Files.copy(source.toAbsolutePath().normalize(), destination, StandardCopyOption.REPLACE_EXISTING);
        NumismaticsDataReader.read(destination);
        return destination;
    }

    public static NumismaticsSourceSnapshot readJournalSource(NumismaticsMigrationSavedData journal)
            throws IOException {
        if (journal.sourcePath().isBlank()) throw new IOException("No migration source is configured.");
        return NumismaticsDataReader.read(Path.of(journal.sourcePath()));
    }

    public static void consumeSourceBalances(NumismaticsMigrationSavedData journal,
                                             NumismaticsSourceSnapshot source) throws IOException {
        if (journal.sourceKind() != NumismaticsMigrationSavedData.SourceKind.WORLD) return;
        if (ModList.get().isLoaded("numismatics")) {
            if (!NumismaticsRuntimeBridge.consumeLiveBalances(
                    source.accounts().stream().map(NumismaticsAccountRecord::sourceAccountId).toList())) {
                throw new IOException("Numismatics is loaded, but UBS could not clear its live account balances safely.");
            }
            return;
        }
        zeroStoredBalances(source.sourcePath());
    }

    private static void zeroStoredBalances(Path source) throws IOException {
        CompoundTag root = NbtIo.readCompressed(source, NbtAccounter.create(
                NumismaticsDataReader.MAX_SOURCE_BYTES * 16L));
        CompoundTag data = root.contains("data", Tag.TAG_COMPOUND) ? root.getCompound("data") : root;
        ListTag accounts = data.getList("Accounts", Tag.TAG_COMPOUND);
        for (int index = 0; index < accounts.size(); index++) accounts.getCompound(index).putInt("balance", 0);
        Path temporary = source.resolveSibling(source.getFileName() + ".ubs.tmp");
        NbtIo.writeCompressed(root, temporary);
        try {
            Files.move(temporary, source, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, source, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
