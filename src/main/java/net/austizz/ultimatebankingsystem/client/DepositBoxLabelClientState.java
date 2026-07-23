package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.network.DepositBoxLabelRequestPayload;
import net.austizz.ultimatebankingsystem.network.DepositBoxLabelsPayload;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DepositBoxLabelClientState {
    private static final long REFRESH_MILLIS = 2_000L;
    private static final long EXPIRE_MILLIS = 5_000L;
    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final Map<String, Long> REQUESTED_AT = new HashMap<>();

    private DepositBoxLabelClientState() {}

    public static synchronized void apply(DepositBoxLabelsPayload payload) {
        if (payload == null) return;
        String key = key(payload.dimension(), new BlockPos(payload.x(), payload.y(), payload.z()));
        ENTRIES.put(key, new Entry(payload.visible(), payload.labels(), System.currentTimeMillis()));
    }

    public static synchronized List<String> labels(String dimension, BlockPos pos) {
        if (pos == null) return List.of();
        long now = System.currentTimeMillis();
        String key = key(dimension, pos);
        long requested = REQUESTED_AT.getOrDefault(key, 0L);
        if (now - requested >= REFRESH_MILLIS) {
            REQUESTED_AT.put(key, now);
            PacketDistributor.sendToServer(new DepositBoxLabelRequestPayload(pos.getX(), pos.getY(), pos.getZ()));
        }
        Entry entry = ENTRIES.get(key);
        return entry != null && entry.visible && now - entry.receivedAt <= EXPIRE_MILLIS
                ? entry.labels : List.of();
    }

    public static synchronized void clear() {
        ENTRIES.clear();
        REQUESTED_AT.clear();
    }

    private static String key(String dimension, BlockPos pos) {
        return (dimension == null ? "" : dimension) + "|" + pos.asLong();
    }

    private record Entry(boolean visible, List<String> labels, long receivedAt) {}
}
