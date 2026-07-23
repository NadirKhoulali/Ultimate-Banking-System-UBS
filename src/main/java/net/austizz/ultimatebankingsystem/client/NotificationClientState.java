package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.api.ApiNotificationPlacement;
import net.austizz.ultimatebankingsystem.api.ApiNotificationPriority;
import net.austizz.ultimatebankingsystem.api.ApiNotificationRequest;
import net.austizz.ultimatebankingsystem.api.ApiNotificationType;
import net.austizz.ultimatebankingsystem.network.UiNotificationPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Client-owned state for the versioned notification stack. */
@OnlyIn(Dist.CLIENT)
public final class NotificationClientState {
    public static final int ENTER_ANIMATION_MS = 260;
    public static final int EXIT_ANIMATION_MS = 220;
    private static final int MAX_ENTRIES = 24;
    private static final long DUPLICATE_WINDOW_MS = 1500L;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private NotificationClientState() {
    }

    public static synchronized void apply(UiNotificationPayload payload) {
        apply(payload, System.currentTimeMillis());
    }

    static synchronized void apply(UiNotificationPayload payload, long now) {
        if (payload == null) {
            return;
        }
        cleanExpired(now);
        switch (payload.operation()) {
            case SHOW -> show(payload, now);
            case DISMISS -> dismiss(payload.id(), now);
            case CLEAR_CHANNEL -> dismissChannel(payload.channel(), now);
            case CLEAR_ALL -> dismissAll(now);
        }
    }

    public static synchronized String showLocal(ApiNotificationRequest request) {
        if (request == null || request.message().isBlank()) {
            return "";
        }
        String id = request.id().isBlank() ? UUID.randomUUID().toString() : request.id();
        apply(UiNotificationPayload.show(id, request), System.currentTimeMillis());
        return id;
    }

    public static synchronized List<View> snapshot() {
        return snapshot(System.currentTimeMillis());
    }

    static synchronized List<View> snapshot(long now) {
        cleanExpired(now);
        return ENTRIES.stream()
                .sorted(Comparator
                        .comparingInt((Entry entry) -> entry.priority.id()).reversed()
                        .thenComparing(Comparator.comparingLong((Entry entry) -> entry.shownAt).reversed()))
                .map(Entry::view)
                .toList();
    }

    public static synchronized void markSoundPlayed(String id, long revision) {
        Entry entry = findById(id);
        if (entry != null && entry.revision == revision) {
            entry.soundPending = false;
        }
    }

    public static synchronized void clear() {
        ENTRIES.clear();
    }

    private static void show(UiNotificationPayload payload, long now) {
        if (payload.message().isBlank()) {
            return;
        }
        String id = payload.id().isBlank() ? UUID.randomUUID().toString() : payload.id();
        Entry existing = findById(id);
        if (existing != null && payload.replaceExisting()) {
            existing.update(payload, now);
            return;
        }

        Entry duplicate = findRecentDuplicate(payload, now);
        if (duplicate != null) {
            duplicate.repeatCount = Math.min(99, duplicate.repeatCount + 1);
            duplicate.expiresAt = payload.sticky() ? Long.MAX_VALUE : now + payload.durationMs();
            duplicate.dismissedAt = 0L;
            duplicate.revision++;
            return;
        }

        if (existing != null) {
            id = id + ":" + UUID.randomUUID().toString().substring(0, 8);
        }
        ENTRIES.add(new Entry(id, payload, now));
        enforceCapacity();
    }

    private static Entry findRecentDuplicate(UiNotificationPayload payload, long now) {
        for (int index = ENTRIES.size() - 1; index >= 0; index--) {
            Entry entry = ENTRIES.get(index);
            if (entry.dismissedAt > 0L || now - entry.shownAt > DUPLICATE_WINDOW_MS) {
                continue;
            }
            if (entry.type == payload.notificationType()
                    && entry.channel.equalsIgnoreCase(payload.channel())
                    && entry.title.equals(payload.title())
                    && entry.message.equals(payload.message())
                    && entry.detail.equals(payload.detail())) {
                return entry;
            }
        }
        return null;
    }

    private static void dismiss(String id, long now) {
        if (id == null || id.isBlank()) {
            return;
        }
        Entry entry = findById(id);
        if (entry != null && entry.dismissedAt == 0L) {
            entry.dismissedAt = now;
        }
    }

    private static void dismissChannel(String channel, long now) {
        if (channel == null || channel.isBlank()) {
            return;
        }
        for (Entry entry : ENTRIES) {
            if (entry.dismissedAt == 0L && entry.channel.equalsIgnoreCase(channel)) {
                entry.dismissedAt = now;
            }
        }
    }

    private static void dismissAll(long now) {
        for (Entry entry : ENTRIES) {
            if (entry.dismissedAt == 0L) {
                entry.dismissedAt = now;
            }
        }
    }

    private static void cleanExpired(long now) {
        for (Entry entry : ENTRIES) {
            if (!entry.sticky && entry.dismissedAt == 0L && now >= entry.expiresAt) {
                entry.dismissedAt = now;
            }
        }
        ENTRIES.removeIf(entry -> entry.dismissedAt > 0L && now - entry.dismissedAt >= EXIT_ANIMATION_MS);
    }

    private static Entry findById(String id) {
        for (Entry entry : ENTRIES) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }
        return null;
    }

    private static void enforceCapacity() {
        while (ENTRIES.size() > MAX_ENTRIES) {
            Entry removable = ENTRIES.stream()
                    .filter(entry -> !entry.sticky)
                    .min(Comparator
                            .comparingInt((Entry entry) -> entry.priority.id())
                            .thenComparingLong(entry -> entry.shownAt))
                    .orElse(ENTRIES.get(0));
            ENTRIES.remove(removable);
        }
    }

    public record View(
            String id,
            String channel,
            String source,
            String title,
            String message,
            String detail,
            ApiNotificationType type,
            ApiNotificationPriority priority,
            ApiNotificationPlacement placement,
            int durationMs,
            float progress,
            boolean sticky,
            int repeatCount,
            long shownAt,
            long expiresAt,
            long dismissedAt,
            boolean soundPending,
            long revision
    ) {
        public boolean hasProgress() {
            return progress >= 0.0F || progress == ApiNotificationRequest.INDETERMINATE_PROGRESS;
        }

        public boolean isIndeterminate() {
            return progress == ApiNotificationRequest.INDETERMINATE_PROGRESS;
        }

        public float visibility(long now) {
            float enter = Math.min(1.0F, Math.max(0.0F,
                    (now - shownAt) / (float) ENTER_ANIMATION_MS));
            if (dismissedAt <= 0L) {
                return enter;
            }
            float exit = Math.min(1.0F, Math.max(0.0F,
                    (now - dismissedAt) / (float) EXIT_ANIMATION_MS));
            return Math.min(enter, 1.0F - exit);
        }
    }

    private static final class Entry {
        private final String id;
        private String channel;
        private String source;
        private String title;
        private String message;
        private String detail;
        private ApiNotificationType type;
        private ApiNotificationPriority priority;
        private ApiNotificationPlacement placement;
        private int durationMs;
        private float progress;
        private boolean sticky;
        private int repeatCount = 1;
        private long shownAt;
        private long expiresAt;
        private long dismissedAt;
        private boolean soundPending;
        private long revision = 1L;

        private Entry(String id, UiNotificationPayload payload, long now) {
            this.id = id;
            assign(payload);
            this.shownAt = now;
            this.expiresAt = sticky ? Long.MAX_VALUE : now + durationMs;
            this.soundPending = payload.playSound();
        }

        private void update(UiNotificationPayload payload, long now) {
            ApiNotificationType oldType = type;
            ApiNotificationPriority oldPriority = priority;
            assign(payload);
            expiresAt = sticky ? Long.MAX_VALUE : now + durationMs;
            dismissedAt = 0L;
            revision++;
            if (payload.playSound() && (oldType != type || oldPriority != priority)) {
                soundPending = true;
            }
        }

        private void assign(UiNotificationPayload payload) {
            channel = payload.channel().toLowerCase(Locale.ROOT);
            source = payload.source();
            title = payload.title();
            message = payload.message();
            detail = payload.detail();
            type = payload.notificationType();
            priority = payload.priority();
            placement = payload.placement();
            durationMs = payload.durationMs();
            progress = payload.progress();
            sticky = payload.sticky();
        }

        private View view() {
            return new View(
                    id, channel, source, title, message, detail, type, priority, placement,
                    durationMs, progress, sticky, repeatCount, shownAt, expiresAt,
                    dismissedAt, soundPending, revision
            );
        }
    }
}
