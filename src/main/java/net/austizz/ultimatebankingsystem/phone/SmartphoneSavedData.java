package net.austizz.ultimatebankingsystem.phone;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SmartphoneSavedData extends SavedData {
    private static final String DATA_NAME = "ultimate_banking_system_phones";
    private static final long DUPLICATE_MESSAGE_WINDOW_MS = 3_000L;
    private final ConcurrentHashMap<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PlayerPrefs> prefs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReportEntry> reports = new ConcurrentHashMap<>();

    public static SmartphoneSavedData get(MinecraftServer server) {
        if (server == null) {
            return new SmartphoneSavedData();
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return new SmartphoneSavedData();
        }
        return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static SavedData.Factory<SmartphoneSavedData> factory() {
        return new SavedData.Factory<>(SmartphoneSavedData::new, SmartphoneSavedData::load, null);
    }

    public PlayerPrefs prefs(UUID playerId) {
        return prefs.computeIfAbsent(playerId, ignored -> new PlayerPrefs());
    }

    public List<MessageEntry> conversation(UUID left, UUID right) {
        Conversation conversation = conversations.computeIfAbsent(conversationKey(left, right), ignored -> new Conversation(left, right));
        if (pruneDuplicateBursts(conversation)) {
            setDirty();
        }
        return conversation.messages;
    }

    public boolean sendMessage(UUID senderId, String senderName, UUID recipientId, String recipientName, String body) {
        if (senderId == null || recipientId == null || body == null || body.isBlank()) {
            return false;
        }
        PlayerPrefs senderPrefs = prefs(senderId);
        PlayerPrefs recipientPrefs = prefs(recipientId);
        if (senderPrefs.blocked.contains(recipientId) || recipientPrefs.blocked.contains(senderId)) {
            return false;
        }
        Conversation conversation = conversations.computeIfAbsent(conversationKey(senderId, recipientId), ignored -> new Conversation(senderId, recipientId));
        if (pruneDuplicateBursts(conversation)) {
            setDirty();
        }
        String safeBody = clamp(body, 600);
        long now = Instant.now().toEpochMilli();
        if (!conversation.messages.isEmpty()) {
            MessageEntry last = conversation.messages.get(conversation.messages.size() - 1);
            if (sameMessagePayload(last, senderId, recipientId, safeBody)
                    && now - last.createdAt() <= DUPLICATE_MESSAGE_WINDOW_MS) {
                return false;
            }
        }
        conversation.messages.add(new MessageEntry(UUID.randomUUID(), senderId, safeName(senderName), recipientId,
                safeName(recipientName), safeBody, now));
        while (conversation.messages.size() > 200) {
            conversation.messages.remove(0);
        }
        recipientPrefs.knownContacts.add(senderId);
        senderPrefs.knownContacts.add(recipientId);
        recipientPrefs.unread.merge(senderId, 1, Integer::sum);
        setDirty();
        return true;
    }

    public void markRead(UUID owner, UUID other) {
        PlayerPrefs prefs = prefs(owner);
        if (prefs.unread.remove(other) != null) {
            setDirty();
        }
    }

    public void rememberPaymentPlayer(UUID owner, UUID other) {
        if (owner == null || other == null || owner.equals(other)) {
            return;
        }
        PlayerPrefs prefs = prefs(owner);
        moveToFront(prefs.paymentRecentPlayers, other, 12);
        setDirty();
    }

    public void rememberPaymentAccount(UUID owner, UUID accountId) {
        if (owner == null || accountId == null) {
            return;
        }
        PlayerPrefs prefs = prefs(owner);
        moveToFront(prefs.paymentRecentAccounts, accountId, 12);
        setDirty();
    }

    public void favorite(UUID owner, UUID other, boolean favorite) {
        PlayerPrefs prefs = prefs(owner);
        if (favorite) {
            prefs.favorites.add(other);
        } else {
            prefs.favorites.remove(other);
        }
        setDirty();
    }

    public void mute(UUID owner, UUID other, boolean mute) {
        PlayerPrefs prefs = prefs(owner);
        if (mute) {
            prefs.muted.add(other);
        } else {
            prefs.muted.remove(other);
        }
        setDirty();
    }

    public void block(UUID owner, UUID other, boolean block) {
        PlayerPrefs prefs = prefs(owner);
        if (block) {
            prefs.blocked.add(other);
        } else {
            prefs.blocked.remove(other);
        }
        setDirty();
    }

    public ReportEntry report(UUID reporter, String reporterName, UUID reported, String reportedName, String reason) {
        ReportEntry entry = new ReportEntry(UUID.randomUUID(), reporter, safeName(reporterName), reported,
                safeName(reportedName), clamp(reason, 400), Instant.now().toEpochMilli(), false);
        reports.put(entry.id(), entry);
        setDirty();
        return entry;
    }

    public List<ReportEntry> reports(boolean includeResolved) {
        return reports.values().stream()
                .filter(report -> includeResolved || !report.resolved())
                .sorted(Comparator.comparingLong(ReportEntry::createdAt).reversed())
                .toList();
    }

    public boolean resolveReport(UUID reportId) {
        ReportEntry report = reports.get(reportId);
        if (report == null || report.resolved()) {
            return false;
        }
        reports.put(reportId, new ReportEntry(report.id(), report.reporterId(), report.reporterName(),
                report.reportedId(), report.reportedName(), report.reason(), report.createdAt(), true));
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag conversationsTag = new ListTag();
        for (Conversation conversation : conversations.values()) {
            CompoundTag conversationTag = new CompoundTag();
            conversationTag.putString("key", conversation.key());
            conversationTag.putUUID("left", conversation.left);
            conversationTag.putUUID("right", conversation.right);
            ListTag messagesTag = new ListTag();
            for (MessageEntry message : conversation.messages) {
                messagesTag.add(message.save());
            }
            conversationTag.put("messages", messagesTag);
            conversationsTag.add(conversationTag);
        }
        tag.put("conversations", conversationsTag);

        ListTag prefsTag = new ListTag();
        for (var entry : prefs.entrySet()) {
            CompoundTag prefTag = entry.getValue().save();
            prefTag.putUUID("player", entry.getKey());
            prefsTag.add(prefTag);
        }
        tag.put("prefs", prefsTag);

        ListTag reportsTag = new ListTag();
        for (ReportEntry report : reports.values()) {
            reportsTag.add(report.save());
        }
        tag.put("reports", reportsTag);
        return tag;
    }

    public static SmartphoneSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SmartphoneSavedData data = new SmartphoneSavedData();
        ListTag conversationsTag = tag.getList("conversations", Tag.TAG_COMPOUND);
        for (int i = 0; i < conversationsTag.size(); i++) {
            CompoundTag conversationTag = conversationsTag.getCompound(i);
            if (!conversationTag.hasUUID("left") || !conversationTag.hasUUID("right")) {
                continue;
            }
            Conversation conversation = new Conversation(conversationTag.getUUID("left"), conversationTag.getUUID("right"));
            ListTag messagesTag = conversationTag.getList("messages", Tag.TAG_COMPOUND);
            for (int m = 0; m < messagesTag.size(); m++) {
                MessageEntry message = MessageEntry.load(messagesTag.getCompound(m));
                if (message != null) {
                    conversation.messages.add(message);
                }
            }
            data.conversations.put(conversation.key(), conversation);
        }

        ListTag prefsTag = tag.getList("prefs", Tag.TAG_COMPOUND);
        for (int i = 0; i < prefsTag.size(); i++) {
            CompoundTag prefTag = prefsTag.getCompound(i);
            if (prefTag.hasUUID("player")) {
                data.prefs.put(prefTag.getUUID("player"), PlayerPrefs.load(prefTag));
            }
        }

        ListTag reportsTag = tag.getList("reports", Tag.TAG_COMPOUND);
        for (int i = 0; i < reportsTag.size(); i++) {
            ReportEntry report = ReportEntry.load(reportsTag.getCompound(i));
            if (report != null) {
                data.reports.put(report.id(), report);
            }
        }
        return data;
    }

    private static String conversationKey(UUID left, UUID right) {
        if (left == null || right == null) {
            return "";
        }
        String a = left.toString();
        String b = right.toString();
        return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
    }

    private static String safeName(String value) {
        return value == null || value.isBlank() ? "Player" : value.trim();
    }

    private static String clamp(String value, int max) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    private static boolean pruneDuplicateBursts(Conversation conversation) {
        if (conversation == null || conversation.messages.size() < 2) {
            return false;
        }
        conversation.messages.sort(Comparator
                .comparingLong(MessageEntry::createdAt)
                .thenComparing(message -> message.id().toString()));
        List<MessageEntry> deduped = new ArrayList<>(conversation.messages.size());
        for (MessageEntry message : conversation.messages) {
            if (!deduped.isEmpty()) {
                MessageEntry previous = deduped.get(deduped.size() - 1);
                if (sameMessagePayload(previous, message.senderId(), message.recipientId(), message.body())
                        && message.createdAt() - previous.createdAt() <= DUPLICATE_MESSAGE_WINDOW_MS) {
                    continue;
                }
            }
            deduped.add(message);
        }
        if (deduped.size() == conversation.messages.size()) {
            return false;
        }
        conversation.messages.clear();
        conversation.messages.addAll(deduped);
        return true;
    }

    private static boolean sameMessagePayload(MessageEntry message, UUID senderId, UUID recipientId, String body) {
        return message.senderId().equals(senderId)
                && message.recipientId().equals(recipientId)
                && message.body().equals(body);
    }

    private static void moveToFront(List<UUID> values, UUID value, int maxSize) {
        values.remove(value);
        values.add(0, value);
        while (values.size() > maxSize) {
            values.remove(values.size() - 1);
        }
    }

    private record Conversation(UUID left, UUID right, List<MessageEntry> messages) {
        Conversation(UUID left, UUID right) {
            this(left, right, new ArrayList<>());
        }

        String key() {
            return conversationKey(left, right);
        }
    }

    public static final class PlayerPrefs {
        public final List<UUID> knownContacts = new ArrayList<>();
        public final List<UUID> favorites = new ArrayList<>();
        public final List<UUID> muted = new ArrayList<>();
        public final List<UUID> blocked = new ArrayList<>();
        public final List<UUID> paymentRecentPlayers = new ArrayList<>();
        public final List<UUID> paymentRecentAccounts = new ArrayList<>();
        public final ConcurrentHashMap<UUID, Integer> unread = new ConcurrentHashMap<>();

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("known", saveUuidList(knownContacts));
            tag.put("favorites", saveUuidList(favorites));
            tag.put("muted", saveUuidList(muted));
            tag.put("blocked", saveUuidList(blocked));
            tag.put("paymentRecentPlayers", saveUuidList(paymentRecentPlayers));
            tag.put("paymentRecentAccounts", saveUuidList(paymentRecentAccounts));
            ListTag unreadTag = new ListTag();
            for (var entry : unread.entrySet()) {
                CompoundTag row = new CompoundTag();
                row.putUUID("player", entry.getKey());
                row.putInt("count", Math.max(0, entry.getValue()));
                unreadTag.add(row);
            }
            tag.put("unread", unreadTag);
            return tag;
        }

        static PlayerPrefs load(CompoundTag tag) {
            PlayerPrefs prefs = new PlayerPrefs();
            prefs.knownContacts.addAll(loadUuidList(tag.getList("known", Tag.TAG_COMPOUND)));
            prefs.favorites.addAll(loadUuidList(tag.getList("favorites", Tag.TAG_COMPOUND)));
            prefs.muted.addAll(loadUuidList(tag.getList("muted", Tag.TAG_COMPOUND)));
            prefs.blocked.addAll(loadUuidList(tag.getList("blocked", Tag.TAG_COMPOUND)));
            prefs.paymentRecentPlayers.addAll(loadUuidList(tag.getList("paymentRecentPlayers", Tag.TAG_COMPOUND)));
            prefs.paymentRecentAccounts.addAll(loadUuidList(tag.getList("paymentRecentAccounts", Tag.TAG_COMPOUND)));
            ListTag unreadTag = tag.getList("unread", Tag.TAG_COMPOUND);
            for (int i = 0; i < unreadTag.size(); i++) {
                CompoundTag row = unreadTag.getCompound(i);
                if (row.hasUUID("player")) {
                    prefs.unread.put(row.getUUID("player"), Math.max(0, row.getInt("count")));
                }
            }
            return prefs;
        }

        private static ListTag saveUuidList(List<UUID> values) {
            ListTag list = new ListTag();
            for (UUID value : values) {
                CompoundTag row = new CompoundTag();
                row.putUUID("id", value);
                list.add(row);
            }
            return list;
        }

        private static List<UUID> loadUuidList(ListTag list) {
            List<UUID> values = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag row = list.getCompound(i);
                if (row.hasUUID("id")) {
                    values.add(row.getUUID("id"));
                }
            }
            return values;
        }
    }

    public record MessageEntry(UUID id,
                               UUID senderId,
                               String senderName,
                               UUID recipientId,
                               String recipientName,
                               String body,
                               long createdAt) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putUUID("senderId", senderId);
            tag.putString("senderName", senderName);
            tag.putUUID("recipientId", recipientId);
            tag.putString("recipientName", recipientName);
            tag.putString("body", body);
            tag.putLong("createdAt", createdAt);
            return tag;
        }

        static MessageEntry load(CompoundTag tag) {
            if (!tag.hasUUID("id") || !tag.hasUUID("senderId") || !tag.hasUUID("recipientId")) {
                return null;
            }
            return new MessageEntry(tag.getUUID("id"), tag.getUUID("senderId"), tag.getString("senderName"),
                    tag.getUUID("recipientId"), tag.getString("recipientName"), tag.getString("body"),
                    tag.getLong("createdAt"));
        }
    }

    public record ReportEntry(UUID id,
                              UUID reporterId,
                              String reporterName,
                              UUID reportedId,
                              String reportedName,
                              String reason,
                              long createdAt,
                              boolean resolved) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putUUID("reporterId", reporterId);
            tag.putString("reporterName", reporterName);
            tag.putUUID("reportedId", reportedId);
            tag.putString("reportedName", reportedName);
            tag.putString("reason", reason);
            tag.putLong("createdAt", createdAt);
            tag.putBoolean("resolved", resolved);
            return tag;
        }

        static ReportEntry load(CompoundTag tag) {
            if (!tag.hasUUID("id") || !tag.hasUUID("reporterId") || !tag.hasUUID("reportedId")) {
                return null;
            }
            return new ReportEntry(tag.getUUID("id"), tag.getUUID("reporterId"),
                    tag.getString("reporterName"), tag.getUUID("reportedId"),
                    tag.getString("reportedName"), tag.getString("reason"),
                    tag.getLong("createdAt"), tag.getBoolean("resolved"));
        }

        public String statusLabel() {
            return resolved ? "RESOLVED" : "OPEN";
        }
    }
}
