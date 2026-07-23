package net.austizz.ultimatebankingsystem.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DeliveryAlertClientState {
    private DeliveryAlertClientState() {
    }

    public static void show(String title, String message, boolean success, int durationMs) {
        // Keep backward-compatible entrypoint while routing all alerts through the shared alert style system.
        ActionAlertClientState.show(
                title,
                message,
                success ? ActionAlertClientState.Tone.SUCCESS : ActionAlertClientState.Tone.ERROR,
                durationMs
        );
    }

    public static boolean isActive() {
        return ActionAlertClientState.isActive();
    }

    public static String getTitle() {
        return ActionAlertClientState.getTitle();
    }

    public static String getMessage() {
        return ActionAlertClientState.getMessage();
    }

    public static boolean isSuccess() {
        return ActionAlertClientState.getTone() == ActionAlertClientState.Tone.SUCCESS;
    }

    public static long getShownAtMillis() {
        return ActionAlertClientState.getShownAtMillis();
    }

    public static long getExpiresAtMillis() {
        return ActionAlertClientState.getExpiresAtMillis();
    }

    public static void clear() {
        ActionAlertClientState.clear();
    }
}
