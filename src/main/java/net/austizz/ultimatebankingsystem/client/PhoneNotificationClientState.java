package net.austizz.ultimatebankingsystem.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayDeque;

@OnlyIn(Dist.CLIENT)
public final class PhoneNotificationClientState {
    private static final int MAX_PENDING = 8;
    private static final int SLIDE_IN_MS = 320;
    private static final int SLIDE_OUT_MS = 850;
    private static final ArrayDeque<Entry> PENDING = new ArrayDeque<>();

    private static String title = "";
    private static String message = "";
    private static ActionAlertClientState.Tone tone = ActionAlertClientState.Tone.INFO;
    private static long shownAtMillis;
    private static long expiresAtMillis;

    private PhoneNotificationClientState() {
    }

    public static synchronized void show(String nextTitle,
                                         String nextMessage,
                                         ActionAlertClientState.Tone nextTone,
                                         int durationMs) {
        String safeMessage = nextMessage == null ? "" : nextMessage.trim();
        if (safeMessage.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        advanceLocked(now);
        Entry entry = new Entry(
                nextTitle == null || nextTitle.isBlank() ? "UBS Phone" : nextTitle.trim(),
                safeMessage,
                nextTone == null ? ActionAlertClientState.Tone.INFO : nextTone,
                Math.max(2600, Math.min(12000, durationMs))
        );
        if (!isCurrentActiveLocked(now)) {
            applyLocked(entry, now);
            return;
        }
        if (sameEntry(currentEntryLocked(), entry) || sameEntry(PENDING.peekLast(), entry)) {
            return;
        }
        if (PENDING.size() >= MAX_PENDING) {
            PENDING.removeFirst();
        }
        PENDING.addLast(entry);
    }

    public static synchronized boolean isActive() {
        long now = System.currentTimeMillis();
        advanceLocked(now);
        return isCurrentActiveLocked(now);
    }

    public static synchronized String title() {
        return title == null ? "" : title;
    }

    public static synchronized String message() {
        return message == null ? "" : message;
    }

    public static synchronized ActionAlertClientState.Tone tone() {
        return tone == null ? ActionAlertClientState.Tone.INFO : tone;
    }

    public static synchronized float progress() {
        long now = System.currentTimeMillis();
        advanceLocked(now);
        if (!isCurrentActiveLocked(now)) {
            return 0.0F;
        }
        long elapsed = Math.max(0L, now - shownAtMillis);
        long remaining = Math.max(0L, expiresAtMillis - now);
        if (elapsed < SLIDE_IN_MS) {
            return easeOut(elapsed / (float) SLIDE_IN_MS);
        }
        if (remaining < SLIDE_OUT_MS) {
            return 1.0F - easeIn(1.0F - (remaining / (float) SLIDE_OUT_MS));
        }
        return 1.0F;
    }

    public static synchronized float alpha() {
        return Math.max(0.0F, Math.min(1.0F, progress()));
    }

    private static boolean isCurrentActiveLocked(long now) {
        return message != null && !message.isBlank() && now < expiresAtMillis;
    }

    private static void advanceLocked(long now) {
        if (isCurrentActiveLocked(now)) {
            return;
        }
        if (!PENDING.isEmpty()) {
            applyLocked(PENDING.removeFirst(), now);
            return;
        }
        clearLocked();
    }

    private static void applyLocked(Entry entry, long now) {
        if (entry == null || entry.message().isBlank()) {
            clearLocked();
            return;
        }
        title = entry.title();
        message = entry.message();
        tone = entry.tone();
        shownAtMillis = now;
        expiresAtMillis = now + entry.durationMs();
    }

    private static void clearLocked() {
        title = "";
        message = "";
        tone = ActionAlertClientState.Tone.INFO;
        shownAtMillis = 0L;
        expiresAtMillis = 0L;
    }

    private static Entry currentEntryLocked() {
        if (message == null || message.isBlank()) {
            return null;
        }
        return new Entry(title == null ? "" : title, message, tone == null ? ActionAlertClientState.Tone.INFO : tone, 0);
    }

    private static boolean sameEntry(Entry left, Entry right) {
        if (left == null || right == null) {
            return false;
        }
        return left.tone() == right.tone()
                && left.title().equals(right.title())
                && left.message().equals(right.message());
    }

    private static float easeOut(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    private static float easeIn(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        return t * t * t;
    }

    private record Entry(String title, String message, ActionAlertClientState.Tone tone, int durationMs) {
    }
}
