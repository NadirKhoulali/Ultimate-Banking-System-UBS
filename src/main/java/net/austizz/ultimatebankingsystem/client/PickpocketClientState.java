package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.network.PickpocketStatePayload;

public final class PickpocketClientState {
    private static boolean active;
    private static String targetName = "";
    private static int elapsedTicks;
    private static int durationTicks;
    private static int cooldownRemainingTicks;

    private PickpocketClientState() {
    }

    public static void apply(PickpocketStatePayload payload) {
        if (payload == null) {
            clear();
            return;
        }
        active = payload.active();
        targetName = payload.targetName() == null ? "" : payload.targetName().trim();
        elapsedTicks = Math.max(0, payload.elapsedTicks());
        durationTicks = Math.max(0, payload.durationTicks());
        cooldownRemainingTicks = Math.max(0, payload.cooldownRemainingTicks());
        if (!active) {
            elapsedTicks = 0;
            durationTicks = 0;
            targetName = "";
        }
    }

    public static void tickClient() {
        if (cooldownRemainingTicks > 0) {
            cooldownRemainingTicks--;
        }
    }

    public static void clear() {
        active = false;
        targetName = "";
        elapsedTicks = 0;
        durationTicks = 0;
        cooldownRemainingTicks = 0;
    }

    public static boolean isActive() {
        return active;
    }

    public static String getTargetName() {
        return targetName;
    }

    public static int getElapsedTicks() {
        return elapsedTicks;
    }

    public static int getDurationTicks() {
        return durationTicks;
    }

    public static int getCooldownRemainingTicks() {
        return cooldownRemainingTicks;
    }
}
