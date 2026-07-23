package net.austizz.ultimatebankingsystem.npc.escort;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TemporaryRelayLeaseState {
    private static final String DATA_NAME = "ubs_temporary_relay_leases";
    private static final int MAX_FALLBACK_RECHECK_TICKS = 20;
    private static final ConcurrentMap<GlobalPos, ActiveLease> ACTIVE = new ConcurrentHashMap<>();

    private TemporaryRelayLeaseState() {
    }

    static boolean claim(ServerLevel level, BlockPos relayPos, UUID token, int durationTicks) {
        long duration = Math.max(1L, durationTicks);
        long gameTime = level.getGameTime();
        long expiresAt = gameTime > Long.MAX_VALUE - duration
                ? Long.MAX_VALUE : gameTime + duration;
        LeaseData data = data(level);
        if (!data.putIfAbsent(relayPos, new PersistedLease(token, expiresAt))) {
            return false;
        }
        GlobalPos key = key(level, relayPos);
        try {
            ActiveLease previous = ACTIVE.putIfAbsent(key, new ActiveLease(level, token));
            if (previous == null) {
                return true;
            }
            data.remove(relayPos, token);
            return false;
        } catch (RuntimeException exception) {
            data.remove(relayPos, token);
            throw exception;
        }
    }

    static boolean release(ServerLevel level, BlockPos relayPos, UUID token) {
        boolean removed = data(level).remove(relayPos, token);
        GlobalPos key = key(level, relayPos);
        ActiveLease active = ACTIVE.get(key);
        if (active != null && active.level() == level && active.token().equals(token)) {
            ACTIVE.remove(key, active);
        }
        return removed;
    }

    static int initialFallbackDelay(int durationTicks) {
        return Math.max(1, Math.min(MAX_FALLBACK_RECHECK_TICKS, durationTicks));
    }

    static boolean hasLease(ServerLevel level, BlockPos relayPos) {
        return data(level).get(relayPos) != null;
    }

    static TemporaryRelayTransaction.Ownership ownership(ServerLevel level,
                                                          BlockPos relayPos,
                                                          UUID token) {
        PersistedLease lease = data(level).get(relayPos);
        if (lease == null) {
            GlobalPos key = key(level, relayPos);
            ActiveLease active = ACTIVE.get(key);
            if (active != null && active.level() == level) {
                ACTIVE.remove(key, active);
            }
            return TemporaryRelayTransaction.Ownership.UNOWNED;
        }
        return lease.token().equals(token)
                ? TemporaryRelayTransaction.Ownership.OWNED_BY_TOKEN
                : TemporaryRelayTransaction.Ownership.OWNED_BY_OTHER;
    }

    static void forgetRuntimeOwner(ServerLevel level, BlockPos relayPos) {
        GlobalPos key = key(level, relayPos);
        ActiveLease active = ACTIVE.get(key);
        if (active != null && active.level() == level) {
            ACTIVE.remove(key, active);
        }
    }

    public static int fallbackDelay(ServerLevel level, BlockPos relayPos) {
        LeaseData data = data(level);
        PersistedLease persisted = data.get(relayPos);
        GlobalPos key = key(level, relayPos);
        if (persisted == null) {
            ActiveLease active = ACTIVE.get(key);
            if (active != null && active.level() == level) {
                ACTIVE.remove(key, active);
            }
            return 0;
        }

        ActiveLease active = ACTIVE.get(key);
        long remaining = persisted.expiresAt() - level.getGameTime();
        if (active != null && active.level() == level
                && active.token().equals(persisted.token()) && remaining > 0L) {
            return (int) Math.min(MAX_FALLBACK_RECHECK_TICKS, remaining);
        }

        data.remove(relayPos, persisted.token());
        if (active != null) {
            ACTIVE.remove(key, active);
        }
        return 0;
    }

    private static LeaseData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LeaseData.factory(), DATA_NAME);
    }

    private static GlobalPos key(ServerLevel level, BlockPos relayPos) {
        return GlobalPos.of(level.dimension(), relayPos);
    }

    private record ActiveLease(ServerLevel level, UUID token) {
    }

    private record PersistedLease(UUID token, long expiresAt) {
    }

    private static final class LeaseData extends SavedData {
        private static final String LEASES_TAG = "leases";
        private final Map<Long, PersistedLease> leases = new HashMap<>();

        static SavedData.Factory<LeaseData> factory() {
            return new SavedData.Factory<>(LeaseData::new, LeaseData::load, null);
        }

        static LeaseData load(CompoundTag tag, HolderLookup.Provider registries) {
            LeaseData data = new LeaseData();
            ListTag leases = tag.getList(LEASES_TAG, Tag.TAG_COMPOUND);
            for (int index = 0; index < leases.size(); index++) {
                CompoundTag lease = leases.getCompound(index);
                if (lease.hasUUID("token") && lease.contains("pos", Tag.TAG_LONG)
                        && lease.contains("expiresAt", Tag.TAG_LONG)) {
                    data.leases.put(lease.getLong("pos"), new PersistedLease(
                            lease.getUUID("token"), lease.getLong("expiresAt")));
                }
            }
            return data;
        }

        synchronized PersistedLease get(BlockPos pos) {
            return leases.get(pos.asLong());
        }

        synchronized boolean putIfAbsent(BlockPos pos, PersistedLease lease) {
            if (leases.containsKey(pos.asLong())) {
                return false;
            }
            leases.put(pos.asLong(), lease);
            setDirty();
            return true;
        }

        synchronized boolean remove(BlockPos pos, UUID token) {
            PersistedLease current = leases.get(pos.asLong());
            if (current == null || !current.token().equals(token)) {
                return false;
            }
            leases.remove(pos.asLong());
            setDirty();
            return true;
        }

        @Override
        public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag serialized = new ListTag();
            leases.forEach((pos, lease) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("pos", pos);
                entry.putUUID("token", lease.token());
                entry.putLong("expiresAt", lease.expiresAt());
                serialized.add(entry);
            });
            tag.put(LEASES_TAG, serialized);
            return tag;
        }
    }
}
