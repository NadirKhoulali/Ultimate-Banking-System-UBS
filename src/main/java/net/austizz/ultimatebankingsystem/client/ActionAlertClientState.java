package net.austizz.ultimatebankingsystem.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayDeque;

@OnlyIn(Dist.CLIENT)
public final class ActionAlertClientState {
    public enum Tone {
        SUCCESS,
        ERROR,
        INFO,
        WARNING
    }

    private static String title = "";
    private static String message = "";
    private static Tone tone = Tone.INFO;
    private static long shownAtMillis;
    private static long expiresAtMillis;
    // Queue rapid-fire alerts so players can still read each result instead of losing messages.
    private static final ArrayDeque<AlertEntry> PENDING_ALERTS = new ArrayDeque<>();
    private static final int MAX_PENDING_ALERTS = 8;
    private static final int MIN_ALERT_DURATION_MS = 600;

    private ActionAlertClientState() {
    }

    public static synchronized void show(String title, String message, Tone tone, int durationMs) {
        String normalizedMessage = message == null ? "" : message.trim();
        if (normalizedMessage.isBlank()) {
            return;
        }
        int clampedDurationMs = Math.max(1400, Math.min(12000, durationMs));
        int adjustedDurationMs = clampDurationForCurrentScreen(clampedDurationMs);
        long now = System.currentTimeMillis();
        advanceQueueLocked(now);

        AlertEntry entry = new AlertEntry(
                title == null ? "" : title.trim(),
                normalizedMessage,
                tone == null ? Tone.INFO : tone,
                adjustedDurationMs
        );

        if (!isCurrentActiveLocked(now)) {
            applyEntryLocked(entry, now);
            return;
        }

        // Collapse same-in-a-row alerts to keep repeated spam from flooding the queue.
        if (isSameAlertLocked(currentEntryLocked(), entry) || isSameAlertLocked(PENDING_ALERTS.peekLast(), entry)) {
            return;
        }

        if (PENDING_ALERTS.size() >= MAX_PENDING_ALERTS) {
            // Keep the newest alerts during heavy action bursts.
            PENDING_ALERTS.removeFirst();
        }
        PENDING_ALERTS.addLast(entry);
        // If backlog grows while an alert is active, shorten the current display to drain faster.
        accelerateCurrentAlertForBacklogLocked(now);
    }

    public static void showSuccess(String title, String message) {
        show(title, message, Tone.SUCCESS, 4200);
    }

    public static void showError(String title, String message) {
        show(title, message, Tone.ERROR, 5200);
    }

    public static void showInfo(String title, String message) {
        show(title, message, Tone.INFO, 4200);
    }

    public static void showWarning(String title, String message) {
        show(title, message, Tone.WARNING, 5000);
    }

    public static synchronized boolean isActive() {
        long now = System.currentTimeMillis();
        advanceQueueLocked(now);
        return isCurrentActiveLocked(now);
    }

    public static synchronized String getTitle() {
        return title == null ? "" : title;
    }

    public static synchronized String getMessage() {
        return message == null ? "" : message;
    }

    public static synchronized Tone getTone() {
        return tone == null ? Tone.INFO : tone;
    }

    public static synchronized long getShownAtMillis() {
        return shownAtMillis;
    }

    public static synchronized long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public static synchronized void clear() {
        PENDING_ALERTS.clear();
        clearCurrentLocked();
    }

    private static boolean isCurrentActiveLocked(long now) {
        return message != null && !message.isBlank() && now < expiresAtMillis;
    }

    private static void advanceQueueLocked(long now) {
        if (isCurrentActiveLocked(now)) {
            return;
        }
        if (!PENDING_ALERTS.isEmpty()) {
            applyEntryLocked(PENDING_ALERTS.removeFirst(), now);
            return;
        }
        clearCurrentLocked();
    }

    private static void applyEntryLocked(AlertEntry entry, long now) {
        if (entry == null || entry.message() == null || entry.message().isBlank()) {
            clearCurrentLocked();
            return;
        }
        int adjustedDurationMs = adjustDurationForBacklog(entry.durationMs(), PENDING_ALERTS.size());
        ActionAlertClientState.title = entry.title();
        ActionAlertClientState.message = entry.message();
        ActionAlertClientState.tone = entry.tone();
        ActionAlertClientState.shownAtMillis = now;
        ActionAlertClientState.expiresAtMillis = now + adjustedDurationMs;
    }

    private static void clearCurrentLocked() {
        title = "";
        message = "";
        tone = Tone.INFO;
        shownAtMillis = 0L;
        expiresAtMillis = 0L;
    }

    // Keep alerts very short while any GUI screen is open so they do not block menu workflows.
    private static int clampDurationForCurrentScreen(int durationMs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen != null) {
            return 1000;
        }
        return durationMs;
    }

    private static void accelerateCurrentAlertForBacklogLocked(long now) {
        if (!isCurrentActiveLocked(now)) {
            return;
        }
        int backlog = PENDING_ALERTS.size();
        if (backlog < 3) {
            return;
        }
        long maxRemainingMs;
        if (backlog >= 6) {
            maxRemainingMs = 450L;
        } else if (backlog >= 4) {
            maxRemainingMs = 700L;
        } else {
            maxRemainingMs = 900L;
        }
        long targetExpiry = now + maxRemainingMs;
        if (expiresAtMillis > targetExpiry) {
            expiresAtMillis = targetExpiry;
        }
    }

    private static int adjustDurationForBacklog(int baseDurationMs, int pendingAfterPop) {
        int duration = baseDurationMs;
        if (pendingAfterPop >= 6) {
            duration = Math.min(duration, 650);
        } else if (pendingAfterPop >= 4) {
            duration = Math.min(duration, 850);
        } else if (pendingAfterPop >= 2) {
            duration = Math.min(duration, 1100);
        }
        return Math.max(MIN_ALERT_DURATION_MS, duration);
    }

    private static AlertEntry currentEntryLocked() {
        if (message == null || message.isBlank()) {
            return null;
        }
        return new AlertEntry(title == null ? "" : title, message, tone == null ? Tone.INFO : tone, 0);
    }

    private static boolean isSameAlertLocked(AlertEntry a, AlertEntry b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.tone() != b.tone()) {
            return false;
        }
        String aTitle = a.title() == null ? "" : a.title().trim();
        String bTitle = b.title() == null ? "" : b.title().trim();
        if (!aTitle.equals(bTitle)) {
            return false;
        }
        String aMessage = a.message() == null ? "" : a.message().trim();
        String bMessage = b.message() == null ? "" : b.message().trim();
        return aMessage.equals(bMessage);
    }

    private record AlertEntry(String title, String message, Tone tone, int durationMs) {
    }
}
