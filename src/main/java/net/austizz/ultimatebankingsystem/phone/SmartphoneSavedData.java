package net.austizz.ultimatebankingsystem.phone;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SmartphoneSavedData extends SavedData {
    private static final String DATA_NAME = "ultimate_banking_system_phones";
    private static final long DUPLICATE_MESSAGE_WINDOW_MS = 3_000L;
    private final ConcurrentHashMap<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PlayerPrefs> prefs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReportEntry> reports = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, GiftEntry> gifts = new ConcurrentHashMap<>();

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
        PlayerPrefs playerPrefs = prefs.computeIfAbsent(playerId, ignored -> new PlayerPrefs());
        if (normalizePrefs(playerPrefs)) {
            setDirty();
        }
        return playerPrefs;
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
        moveToFront(recipientPrefs.knownContacts, senderId, 120);
        moveToFront(senderPrefs.knownContacts, recipientId, 120);
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

    public void queueNotification(UUID owner, String title, String message, int toneCode, int durationMs) {
        if (owner == null || message == null || message.isBlank()) {
            return;
        }
        PlayerPrefs prefs = prefs(owner);
        QueuedNotification notification = new QueuedNotification(
                UUID.randomUUID(),
                clamp(title == null || title.isBlank() ? "UBS Phone" : title, 40),
                clamp(message, 180),
                toneCode,
                Math.max(1800, Math.min(12000, durationMs)),
                Instant.now().toEpochMilli()
        );
        prefs.notifications.add(notification);
        prefs.notifications.sort(Comparator.comparingLong(QueuedNotification::createdAt));
        while (prefs.notifications.size() > 24) {
            prefs.notifications.remove(0);
        }
        setDirty();
    }

    public List<QueuedNotification> queuedNotifications(UUID owner) {
        if (owner == null) {
            return List.of();
        }
        List<QueuedNotification> notifications = new ArrayList<>(prefs(owner).notifications);
        notifications.sort(Comparator.comparingLong(QueuedNotification::createdAt));
        return notifications;
    }

    public List<QueuedNotification> consumeNotifications(UUID owner) {
        if (owner == null) {
            return List.of();
        }
        PlayerPrefs prefs = prefs(owner);
        List<QueuedNotification> notifications = new ArrayList<>(prefs.notifications);
        notifications.sort(Comparator.comparingLong(QueuedNotification::createdAt));
        if (!prefs.notifications.isEmpty()) {
            prefs.notifications.clear();
            setDirty();
        }
        return notifications;
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

    public GiftEntry createGift(UUID senderId, UUID recipientId, UUID sourceAccountId, BigDecimal amount) {
        if (senderId == null || recipientId == null || sourceAccountId == null
                || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        GiftEntry gift = new GiftEntry(UUID.randomUUID(), senderId, recipientId, sourceAccountId,
                amount.toPlainString(), Instant.now().toEpochMilli(), "PENDING");
        gifts.put(gift.id(), gift);
        setDirty();
        return gift;
    }

    public GiftEntry gift(UUID giftId) {
        return giftId == null ? null : gifts.get(giftId);
    }

    public boolean markGiftStatus(UUID giftId, String nextStatus) {
        if (giftId == null || nextStatus == null || nextStatus.isBlank()) {
            return false;
        }
        GiftEntry gift = gifts.get(giftId);
        if (gift == null || !"PENDING".equalsIgnoreCase(gift.status())) {
            return false;
        }
        gifts.put(giftId, gift.withStatus(nextStatus));
        setDirty();
        return true;
    }

    public List<GiftEntry> expirePendingGifts(long nowMillis, long timeoutMillis) {
        List<GiftEntry> expired = new ArrayList<>();
        long timeout = Math.max(1L, timeoutMillis);
        for (GiftEntry gift : gifts.values()) {
            if (gift == null || !"PENDING".equalsIgnoreCase(gift.status())) {
                continue;
            }
            if (nowMillis - gift.createdAt() <= timeout) {
                continue;
            }
            GiftEntry expiredGift = gift.withStatus("EXPIRED");
            gifts.put(gift.id(), expiredGift);
            expired.add(expiredGift);
        }
        if (!expired.isEmpty()) {
            setDirty();
        }
        return expired;
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

        ListTag giftsTag = new ListTag();
        for (GiftEntry gift : gifts.values()) {
            giftsTag.add(gift.save());
        }
        tag.put("gifts", giftsTag);
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

        ListTag giftsTag = tag.getList("gifts", Tag.TAG_COMPOUND);
        for (int i = 0; i < giftsTag.size(); i++) {
            GiftEntry gift = GiftEntry.load(giftsTag.getCompound(i));
            if (gift != null) {
                data.gifts.put(gift.id(), gift);
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

    private static boolean normalizePrefs(PlayerPrefs prefs) {
        if (prefs == null) {
            return false;
        }
        boolean changed = false;
        changed |= dedupeUuidList(prefs.knownContacts, 120);
        changed |= dedupeUuidList(prefs.favorites, 120);
        changed |= dedupeUuidList(prefs.muted, 120);
        changed |= dedupeUuidList(prefs.blocked, 120);
        changed |= dedupeUuidList(prefs.paymentRecentPlayers, 12);
        changed |= dedupeUuidList(prefs.paymentRecentAccounts, 12);
        return changed;
    }

    private static boolean dedupeUuidList(List<UUID> values, int maxSize) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        Set<UUID> seen = new HashSet<>();
        List<UUID> unique = new ArrayList<>(Math.min(values.size(), Math.max(1, maxSize)));
        boolean changed = false;
        for (UUID value : values) {
            if (value == null || !seen.add(value)) {
                changed = true;
                continue;
            }
            if (unique.size() < maxSize) {
                unique.add(value);
            } else {
                changed = true;
            }
        }
        if (!changed && unique.size() == values.size()) {
            return false;
        }
        values.clear();
        values.addAll(unique);
        return true;
    }

    private static boolean moveToFront(List<UUID> values, UUID value, int maxSize) {
        if (values == null || value == null) {
            return false;
        }
        boolean alreadyFirst = !values.isEmpty() && value.equals(values.get(0));
        int beforeSize = values.size();
        values.removeIf(value::equals);
        int removed = beforeSize - values.size();
        values.add(0, value);
        boolean trimmed = false;
        while (values.size() > maxSize) {
            values.remove(values.size() - 1);
            trimmed = true;
        }
        return !alreadyFirst || removed != 1 || trimmed;
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
        public final List<QueuedNotification> notifications = new ArrayList<>();
        public final ConcurrentHashMap<UUID, Integer> unread = new ConcurrentHashMap<>();

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("known", saveUuidList(knownContacts));
            tag.put("favorites", saveUuidList(favorites));
            tag.put("muted", saveUuidList(muted));
            tag.put("blocked", saveUuidList(blocked));
            tag.put("paymentRecentPlayers", saveUuidList(paymentRecentPlayers));
            tag.put("paymentRecentAccounts", saveUuidList(paymentRecentAccounts));
            ListTag notificationsTag = new ListTag();
            for (QueuedNotification notification : notifications) {
                notificationsTag.add(notification.save());
            }
            tag.put("notifications", notificationsTag);
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
            ListTag notificationsTag = tag.getList("notifications", Tag.TAG_COMPOUND);
            for (int i = 0; i < notificationsTag.size(); i++) {
                QueuedNotification notification = QueuedNotification.load(notificationsTag.getCompound(i));
                if (notification != null) {
                    prefs.notifications.add(notification);
                }
            }
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
                    UUID value = row.getUUID("id");
                    if (!values.contains(value)) {
                        values.add(value);
                    }
                }
            }
            return values;
        }
    }

    public record QueuedNotification(UUID id,
                                     String title,
                                     String message,
                                     int toneCode,
                                     int durationMs,
                                     long createdAt) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id == null ? UUID.randomUUID() : id);
            tag.putString("title", title == null || title.isBlank() ? "UBS Phone" : title);
            tag.putString("message", message == null ? "" : message);
            tag.putInt("toneCode", toneCode);
            tag.putInt("durationMs", durationMs);
            tag.putLong("createdAt", createdAt);
            return tag;
        }

        static QueuedNotification load(CompoundTag tag) {
            if (tag == null || !tag.hasUUID("id")) {
                return null;
            }
            String message = tag.getString("message");
            if (message == null || message.isBlank()) {
                return null;
            }
            return new QueuedNotification(
                    tag.getUUID("id"),
                    tag.getString("title").isBlank() ? "UBS Phone" : tag.getString("title"),
                    message,
                    tag.getInt("toneCode"),
                    Math.max(1800, Math.min(12000, tag.getInt("durationMs"))),
                    tag.getLong("createdAt")
            );
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

    public record GiftEntry(UUID id,
                            UUID senderId,
                            UUID recipientId,
                            UUID sourceAccountId,
                            String amount,
                            long createdAt,
                            String status) {
        public GiftEntry withStatus(String nextStatus) {
            return new GiftEntry(id, senderId, recipientId, sourceAccountId, amount, createdAt,
                    nextStatus == null || nextStatus.isBlank() ? status : nextStatus.trim().toUpperCase(Locale.ROOT));
        }

        public BigDecimal amountDecimal() {
            try {
                return new BigDecimal(amount == null || amount.isBlank() ? "0" : amount);
            } catch (NumberFormatException ex) {
                return BigDecimal.ZERO;
            }
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putUUID("senderId", senderId);
            tag.putUUID("recipientId", recipientId);
            tag.putUUID("sourceAccountId", sourceAccountId);
            tag.putString("amount", amount == null ? "0" : amount);
            tag.putLong("createdAt", createdAt);
            tag.putString("status", status == null || status.isBlank() ? "PENDING" : status);
            return tag;
        }

        static GiftEntry load(CompoundTag tag) {
            if (!tag.hasUUID("id") || !tag.hasUUID("senderId")
                    || !tag.hasUUID("recipientId") || !tag.hasUUID("sourceAccountId")) {
                return null;
            }
            String status = tag.getString("status");
            if (status == null || status.isBlank()) {
                status = "PENDING";
            }
            return new GiftEntry(tag.getUUID("id"), tag.getUUID("senderId"), tag.getUUID("recipientId"),
                    tag.getUUID("sourceAccountId"), tag.getString("amount"), tag.getLong("createdAt"),
                    status.trim().toUpperCase(Locale.ROOT));
        }
    }
}
