package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistent, bounded audit trail for safe and deposit-box operations. */
public final class SafeAccessLogService {
    public static final String CATEGORY_BOX_ACCESS = "BOX_ACCESS";
    public static final String CATEGORY_ASSIGNMENT = "ASSIGNMENT";
    public static final String CATEGORY_SECURITY = "SECURITY";
    public static final String CATEGORY_STORAGE = "STORAGE";
    public static final String CATEGORY_SYSTEM = "SYSTEM";
    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_DENIED = "DENIED";
    public static final String OUTCOME_INFO = "INFO";

    private static final String LOG_TAG = "safeAccessAudit";
    private static final int MAX_ENTRIES = 256;

    private SafeAccessLogService() {
    }

    public record Entry(String eventId,
                        long occurredAtMillis,
                        String category,
                        String outcome,
                        String action,
                        UUID actorId,
                        String actorName,
                        String subject,
                        String detail,
                        String dimension,
                        int x,
                        int y,
                        int z) {
    }

    public static void record(CentralBank centralBank,
                              UUID bankId,
                              ServerPlayer actor,
                              String category,
                              String outcome,
                              String action,
                              String subject,
                              String detail,
                              String dimension,
                              BlockPos pos) {
        append(centralBank, bankId, new Entry(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                clean(category, 32),
                clean(outcome, 24),
                clean(action, 64),
                actor == null ? null : actor.getUUID(),
                actor == null ? "System" : actor.getGameProfile().getName(),
                clean(subject, 128),
                clean(detail, 320),
                clean(dimension, 128),
                pos == null ? 0 : pos.getX(),
                pos == null ? 0 : pos.getY(),
                pos == null ? 0 : pos.getZ()
        ));
    }

    public static void recordSystem(CentralBank centralBank,
                                    UUID bankId,
                                    String category,
                                    String outcome,
                                    String action,
                                    String subject,
                                    String detail,
                                    String dimension,
                                    BlockPos pos) {
        record(centralBank, bankId, null, category, outcome, action,
                subject, detail, dimension, pos);
    }

    public static List<Entry> snapshot(CompoundTag metadata) {
        if (metadata == null || !metadata.contains(LOG_TAG, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag stored = metadata.getList(LOG_TAG, Tag.TAG_COMPOUND);
        List<Entry> result = new ArrayList<>(Math.min(stored.size(), MAX_ENTRIES));
        for (int index = stored.size() - 1; index >= 0 && result.size() < MAX_ENTRIES; index--) {
            CompoundTag tag = stored.getCompound(index);
            result.add(read(tag));
        }
        return List.copyOf(result);
    }

    private static void append(CentralBank centralBank, UUID bankId, Entry entry) {
        if (centralBank == null || bankId == null || entry == null) {
            return;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        ListTag logs = metadata.contains(LOG_TAG, Tag.TAG_LIST)
                ? metadata.getList(LOG_TAG, Tag.TAG_COMPOUND)
                : new ListTag();
        logs.add(write(entry));
        while (logs.size() > MAX_ENTRIES) {
            logs.remove(0);
        }
        metadata.put(LOG_TAG, logs);
        centralBank.putBankMetadata(bankId, metadata);
    }

    private static CompoundTag write(Entry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", clean(entry.eventId(), 64));
        tag.putLong("time", Math.max(0L, entry.occurredAtMillis()));
        tag.putString("category", clean(entry.category(), 32));
        tag.putString("outcome", clean(entry.outcome(), 24));
        tag.putString("action", clean(entry.action(), 64));
        if (entry.actorId() != null) tag.putUUID("actor", entry.actorId());
        tag.putString("actorName", clean(entry.actorName(), 64));
        tag.putString("subject", clean(entry.subject(), 128));
        tag.putString("detail", clean(entry.detail(), 320));
        tag.putString("dimension", clean(entry.dimension(), 128));
        tag.putInt("x", entry.x());
        tag.putInt("y", entry.y());
        tag.putInt("z", entry.z());
        return tag;
    }

    private static Entry read(CompoundTag tag) {
        UUID actorId = tag.hasUUID("actor") ? tag.getUUID("actor") : null;
        return new Entry(
                clean(tag.getString("id"), 64),
                Math.max(0L, tag.getLong("time")),
                clean(tag.getString("category"), 32),
                clean(tag.getString("outcome"), 24),
                clean(tag.getString("action"), 64),
                actorId,
                clean(tag.getString("actorName"), 64),
                clean(tag.getString("subject"), 128),
                clean(tag.getString("detail"), 320),
                clean(tag.getString("dimension"), 128),
                tag.getInt("x"),
                tag.getInt("y"),
                tag.getInt("z")
        );
    }

    private static String clean(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
