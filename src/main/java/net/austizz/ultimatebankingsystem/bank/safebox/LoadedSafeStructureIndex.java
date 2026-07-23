package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public final class LoadedSafeStructureIndex {
    private static final Map<Object, ServerIndex> SERVERS = new WeakHashMap<>();

    private LoadedSafeStructureIndex() {
    }

    public enum Kind {
        ROW,
        VAULT_DOOR_MASTER
    }

    public record Position(String dimension, int x, int y, int z) {
        public Position {
            dimension = SafeBlockBounds.normalizeDimension(dimension);
        }
    }

    public record Entry(Kind kind, String dimension, int x, int y, int z) {
        public Entry {
            dimension = SafeBlockBounds.normalizeDimension(dimension);
        }

        Position position() {
            return new Position(dimension, x, y, z);
        }

        BlockPos blockPos() {
            return new BlockPos(x, y, z);
        }
    }

    public static void register(Level level, BlockPos pos, Kind kind) {
        if (level instanceof ServerLevel serverLevel) {
            register(serverLevel.getServer(), serverLevel.dimension().location().toString(), pos, kind);
        }
    }

    public static synchronized void register(Object serverKey, String dimension, BlockPos pos, Kind kind) {
        if (pos == null) {
            return;
        }
        register(serverKey, dimension, pos.getX(), pos.getY(), pos.getZ(), kind);
    }

    public static synchronized void register(Object serverKey, String dimension, int x, int y, int z, Kind kind) {
        if (serverKey == null || kind == null) {
            return;
        }
        ServerIndex index = SERVERS.computeIfAbsent(serverKey, ignored -> new ServerIndex());
        Entry entry = new Entry(kind, dimension, x, y, z);
        index.entries.put(new StructureKey(entry.kind(), entry.dimension(), entry.x(), entry.y(), entry.z()), entry);
    }

    public static void unregister(Level level, BlockPos pos, Kind kind) {
        if (level instanceof ServerLevel serverLevel) {
            unregister(serverLevel.getServer(), serverLevel.dimension().location().toString(), pos, kind);
        }
    }

    public static synchronized void unregister(Object serverKey, String dimension, BlockPos pos, Kind kind) {
        if (pos == null) {
            return;
        }
        unregister(serverKey, dimension, pos.getX(), pos.getY(), pos.getZ(), kind);
    }

    public static synchronized void unregister(Object serverKey, String dimension, int x, int y, int z, Kind kind) {
        if (serverKey == null || kind == null) {
            return;
        }
        ServerIndex index = SERVERS.get(serverKey);
        if (index == null) {
            return;
        }
        index.entries.remove(new StructureKey(kind, SafeBlockBounds.normalizeDimension(dimension), x, y, z));
        if (index.entries.isEmpty()) {
            SERVERS.remove(serverKey);
        }
    }

    public static synchronized List<Entry> findInBounds(Object serverKey,
                                                        Kind kind,
                                                        Collection<SafeBlockBounds> bounds,
                                                        Predicate<Entry> validator) {
        return findInBounds(serverKey, kind, bounds, validator, null);
    }

    public static synchronized List<Entry> findInBounds(Object serverKey,
                                                        Kind kind,
                                                        Collection<SafeBlockBounds> bounds,
                                                        Predicate<Entry> validator,
                                                        Predicate<Entry> matcher) {
        if (serverKey == null || kind == null || bounds == null || bounds.isEmpty()) {
            return List.of();
        }
        ServerIndex index = SERVERS.get(serverKey);
        if (index == null || index.entries.isEmpty()) {
            return List.of();
        }
        List<Entry> matches = new ArrayList<>();
        List<StructureKey> stale = new ArrayList<>();
        for (Map.Entry<StructureKey, Entry> indexed : index.entries.entrySet()) {
            Entry entry = indexed.getValue();
            if (entry.kind() != kind || !insideAny(bounds, entry)) {
                continue;
            }
            if (validator != null && !validator.test(entry)) {
                stale.add(indexed.getKey());
                continue;
            }
            if (matcher == null || matcher.test(entry)) {
                matches.add(entry);
            }
        }
        for (StructureKey key : stale) {
            index.entries.remove(key);
        }
        if (index.entries.isEmpty()) {
            SERVERS.remove(serverKey);
        }
        return List.copyOf(matches);
    }

    public static synchronized void clear(MinecraftServer server) {
        if (server != null) {
            SERVERS.remove(server);
        }
    }

    public static synchronized void clearAll() {
        SERVERS.clear();
    }

    private static boolean insideAny(Collection<SafeBlockBounds> bounds, Entry entry) {
        for (SafeBlockBounds bound : bounds) {
            if (bound != null && bound.contains(entry.dimension(),
                    entry.x(), entry.y(), entry.z())) {
                return true;
            }
        }
        return false;
    }

    private record StructureKey(Kind kind, String dimension, int x, int y, int z) {
        private StructureKey {
            dimension = SafeBlockBounds.normalizeDimension(dimension);
        }
    }

    private static final class ServerIndex {
        private final LinkedHashMap<StructureKey, Entry> entries = new LinkedHashMap<>();
    }
}
