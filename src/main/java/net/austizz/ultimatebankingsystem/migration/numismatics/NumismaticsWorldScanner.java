package net.austizz.ultimatebankingsystem.migration.numismatics;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/** Read-only indexer for player files and Anvil region/entity files. */
public final class NumismaticsWorldScanner {
    private static final int REGION_HEADER_BYTES = 8_192;
    private static final int SECTOR_BYTES = 4_096;
    private static final int MAX_CHUNK_COMPRESSED_BYTES = 64 * 1024 * 1024;
    private static final Set<String> EXTERNAL_STORAGE_MODS = Set.of("ae2", "refinedstorage", "refinedstorage2");

    private NumismaticsWorldScanner() {
    }

    public static NumismaticsPreflightResult scan(MinecraftServer server,
                                                    NumismaticsMigrationOptions options) throws IOException {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        MutableScan total = new MutableScan();
        scanPlayerFiles(worldRoot, total);

        for (ServerLevel level : server.getAllLevels()) {
            Path dimensionRoot = DimensionType.getStorageFolder(level.dimension(), worldRoot);
            scanRegionDirectory(level.dimension().location().toString(), dimensionRoot.resolve("region"), total);
            scanRegionDirectory(level.dimension().location().toString(), dimensionRoot.resolve("entities"), total);
        }

        Path dataDirectory = worldRoot.resolve("data");
        if (Files.isDirectory(dataDirectory)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dataDirectory, "*.dat")) {
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    if (name.equals("numismatics_bank.dat")
                            || name.startsWith("ultimate_banking_system")) {
                        continue;
                    }
                    ScanCounts counts = scanCompressedNbt(file);
                    if (counts.hasWork()) {
                        total.add(counts);
                        total.affectedFiles.add(relative(worldRoot, file));
                        total.blockers.add("Unsupported Numismatics assets remain in SavedData file "
                                + relative(worldRoot, file) + ".");
                    }
                }
            }
        }

        if (options.scope() == NumismaticsMigrationOptions.Scope.FULL_ECONOMY) {
            for (String modId : EXTERNAL_STORAGE_MODS) {
                if (ModList.get().isLoaded(modId)) {
                    total.blockers.add("External storage mod '" + modId
                            + "' is installed. Move all Numismatics currency into ordinary inventories before migration.");
                }
            }
        }
        if (total.idCards > 0) {
            total.warnings.add(total.idCards + " Numismatics ID card(s) will not be converted.");
        }
        if (total.machineBlocks > 0) {
            total.warnings.add(total.machineBlocks
                    + " Numismatics machine block(s) will have their ordinary contents moved to recovery storage.");
        }

        List<NumismaticsPreflightResult.ChunkRef> chunks = total.chunks.stream()
                .sorted(Comparator.comparing(NumismaticsPreflightResult.ChunkRef::dimension)
                        .thenComparingInt(NumismaticsPreflightResult.ChunkRef::x)
                        .thenComparingInt(NumismaticsPreflightResult.ChunkRef::z))
                .toList();
        return new NumismaticsPreflightResult(
                total.coinItems,
                total.physicalSpurs,
                total.boundCards,
                total.blankCards,
                total.idCards,
                chunks,
                total.playerFiles.stream().sorted().toList(),
                total.affectedFiles.stream().sorted().toList(),
                total.warnings.stream().distinct().toList(),
                total.blockers.stream().distinct().toList()
        );
    }

    public static ScanCounts inspect(Tag root) {
        MutableCounts counts = new MutableCounts();
        inspectTag(root, counts);
        return counts.freeze();
    }

    private static void scanPlayerFiles(Path worldRoot, MutableScan total) throws IOException {
        Path playerData = worldRoot.resolve("playerdata");
        if (!Files.isDirectory(playerData)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(playerData, "*.dat")) {
            for (Path file : files) {
                ScanCounts counts = scanCompressedNbt(file);
                if (!counts.hasWork()) continue;
                total.add(counts);
                String relative = relative(worldRoot, file);
                total.playerFiles.add(relative);
                total.affectedFiles.add(relative);
            }
        }
    }

    private static ScanCounts scanCompressedNbt(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) == 0L) return ScanCounts.empty();
        CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.create(Math.max(64L * 1024L * 1024L, Files.size(file) * 64L)));
        return inspect(tag);
    }

    private static void scanRegionDirectory(String dimension, Path directory, MutableScan total) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "r.*.*.mca")) {
            for (Path region : files) {
                scanRegionFile(dimension, region, total);
            }
        }
    }

    private static void scanRegionFile(String dimension, Path region, MutableScan total) throws IOException {
        String[] parts = region.getFileName().toString().split("\\.");
        if (parts.length < 4) return;
        int regionX;
        int regionZ;
        try {
            regionX = Integer.parseInt(parts[1]);
            regionZ = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {
            return;
        }

        try (FileChannel channel = FileChannel.open(region, StandardOpenOption.READ)) {
            if (channel.size() < REGION_HEADER_BYTES) return;
            ByteBuffer header = ByteBuffer.allocate(REGION_HEADER_BYTES);
            readFully(channel, header, 0L);
            header.flip();
            for (int index = 0; index < 1_024; index++) {
                int offset = header.getInt(index * 4);
                if (offset == 0) continue;
                int sector = offset >>> 8;
                int sectors = offset & 0xFF;
                if (sector < 2 || sectors <= 0) continue;
                int localX = index & 31;
                int localZ = index >>> 5;
                int chunkX = regionX * 32 + localX;
                int chunkZ = regionZ * 32 + localZ;
                CompoundTag chunk = readChunk(channel, region.getParent(), sector, sectors, chunkX, chunkZ);
                if (chunk == null) continue;
                ScanCounts counts = inspect(chunk);
                if (!counts.hasWork()) continue;
                total.add(counts);
                total.chunks.add(new NumismaticsPreflightResult.ChunkRef(dimension, chunkX, chunkZ));
                total.affectedFiles.add(region.toAbsolutePath().normalize().toString());
                Path externalChunk = region.getParent().resolve("c." + chunkX + "." + chunkZ + ".mcc");
                if (Files.isRegularFile(externalChunk)) {
                    total.affectedFiles.add(externalChunk.toAbsolutePath().normalize().toString());
                }
            }
        }
    }

    private static CompoundTag readChunk(FileChannel channel, Path regionDirectory, int sector, int sectors,
                                         int chunkX, int chunkZ) throws IOException {
        long offset = (long) sector * SECTOR_BYTES;
        ByteBuffer prefix = ByteBuffer.allocate(5);
        readFully(channel, prefix, offset);
        prefix.flip();
        if (prefix.remaining() < 5) return null;
        int length = prefix.getInt();
        int compression = prefix.get() & 0xFF;
        boolean external = (compression & 0x80) != 0;
        compression &= 0x7F;
        if (length <= 1 || length > Math.min(MAX_CHUNK_COMPRESSED_BYTES, sectors * SECTOR_BYTES)) return null;

        InputStream raw;
        if (external) {
            Path externalPath = regionDirectory.resolve("c." + chunkX + "." + chunkZ + ".mcc");
            if (!Files.isRegularFile(externalPath)) return null;
            raw = Files.newInputStream(externalPath);
        } else {
            ByteBuffer bytes = ByteBuffer.allocate(length - 1);
            readFully(channel, bytes, offset + 5L);
            raw = new ByteArrayInputStream(bytes.array());
        }

        try (InputStream decompressed = switch (compression) {
            case 1 -> new GZIPInputStream(new BufferedInputStream(raw));
            case 2 -> new InflaterInputStream(new BufferedInputStream(raw));
            case 3 -> new BufferedInputStream(raw);
            default -> null;
        }) {
            if (decompressed == null) return null;
            return NbtIo.read(new DataInputStream(decompressed), NbtAccounter.create(256L * 1024L * 1024L));
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer target, long position) throws IOException {
        while (target.hasRemaining()) {
            int read = channel.read(target, position + target.position());
            if (read < 0) break;
            if (read == 0) throw new IOException("Could not make progress while reading region file.");
        }
    }

    private static void inspectTag(Tag tag, MutableCounts counts) {
        if (tag instanceof CompoundTag compound) {
            String id = compound.contains("id", Tag.TAG_STRING) ? compound.getString("id") : "";
            NumismaticsCoin coin = NumismaticsCoin.fromItemId(id);
            if (coin != null) {
                int count = readCount(compound);
                counts.coinItems += count;
                counts.physicalSpurs = Math.addExact(counts.physicalSpurs,
                        Math.multiplyExact((long) coin.spurs(), count));
            } else if (NumismaticsItemIds.isBankCard(id)) {
                int count = readCount(compound);
                if (hasCardAccountComponent(compound)) counts.boundCards += count;
                else counts.blankCards += count;
            } else if (NumismaticsItemIds.isIdCard(id)) {
                counts.idCards += readCount(compound);
            }
            if (id.startsWith("numismatics:")
                    && compound.contains("x", Tag.TAG_INT)
                    && compound.contains("y", Tag.TAG_INT)
                    && compound.contains("z", Tag.TAG_INT)) {
                counts.machineBlocks++;
            }
            for (String key : compound.getAllKeys()) inspectTag(compound.get(key), counts);
        } else if (tag instanceof CollectionTag<?> collection) {
            for (Tag child : collection) inspectTag(child, counts);
        }
    }

    private static int readCount(CompoundTag tag) {
        Tag count = tag.get("count");
        if (count instanceof NumericTag numeric) return Math.max(1, numeric.getAsInt());
        count = tag.get("Count");
        if (count instanceof NumericTag numeric) return Math.max(1, numeric.getAsInt());
        return 1;
    }

    private static boolean hasCardAccountComponent(CompoundTag stack) {
        if (!stack.contains("components", Tag.TAG_COMPOUND)) return false;
        CompoundTag components = stack.getCompound("components");
        return components.contains("numismatics:card_account_id");
    }

    private static String relative(Path root, Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        try {
            return root.relativize(absolute).toString().replace('\\', '/');
        } catch (IllegalArgumentException ignored) {
            return absolute.toString();
        }
    }

    public record ScanCounts(long coinItems, long physicalSpurs, long boundCards, long blankCards,
                             long idCards, long machineBlocks) {
        public static ScanCounts empty() { return new ScanCounts(0, 0, 0, 0, 0, 0); }
        public boolean hasNumismaticsAssets() { return coinItems > 0 || boundCards > 0 || blankCards > 0 || idCards > 0; }
        public boolean hasWork() { return hasNumismaticsAssets() || machineBlocks > 0; }
    }

    private static final class MutableCounts {
        long coinItems;
        long physicalSpurs;
        long boundCards;
        long blankCards;
        long idCards;
        long machineBlocks;
        ScanCounts freeze() { return new ScanCounts(coinItems, physicalSpurs, boundCards, blankCards, idCards, machineBlocks); }
    }

    private static final class MutableScan {
        long coinItems;
        long physicalSpurs;
        long boundCards;
        long blankCards;
        long idCards;
        long machineBlocks;
        final LinkedHashSet<NumismaticsPreflightResult.ChunkRef> chunks = new LinkedHashSet<>();
        final LinkedHashSet<String> playerFiles = new LinkedHashSet<>();
        final LinkedHashSet<String> affectedFiles = new LinkedHashSet<>();
        final List<String> warnings = new ArrayList<>();
        final List<String> blockers = new ArrayList<>();
        void add(ScanCounts counts) {
            coinItems += counts.coinItems(); physicalSpurs += counts.physicalSpurs();
            boundCards += counts.boundCards(); blankCards += counts.blankCards(); idCards += counts.idCards();
            machineBlocks += counts.machineBlocks();
        }
    }
}
