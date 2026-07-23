package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.heist.HeistExfillZone;
import net.austizz.ultimatebankingsystem.network.HeistHudPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class HeistExfillBorderClientState {
    public record Snapshot(String dimension, int referenceY, HeistExfillZone.Boundary boundary,
                           HeistExfillZone.VisualState state) {
        public Snapshot {
            dimension = dimension == null ? "" : dimension.trim();
            boundary = boundary == null ? new HeistExfillZone.Boundary(java.util.List.of()) : boundary;
            state = state == null ? HeistExfillZone.VisualState.HIDDEN : state;
        }

        public boolean valid() {
            return !dimension.isBlank() && boundary.valid() && state != HeistExfillZone.VisualState.HIDDEN;
        }
    }

    private static Snapshot snapshot = hiddenSnapshot();
    private static boolean visible;
    private static long revision;

    private HeistExfillBorderClientState() {}

    public static void apply(HeistHudPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload == null || !payload.active()) {
            snapshot = hiddenSnapshot();
            hideExfillBorder(minecraft.player);
            return;
        }
        HeistExfillZone.Boundary boundary = HeistExfillZone.Boundary.decode(payload.exfillBoundary());
        Snapshot next = new Snapshot(payload.exfillDimension(), payload.exfillReferenceY(), boundary,
                HeistExfillZone.VisualState.byName(payload.exfillState()));
        if (!next.equals(snapshot)) {
            snapshot = next;
            revision++;
        }
        if (next.valid()) showExfillBorder(minecraft.player);
        else hideExfillBorder(minecraft.player);
    }

    public static void showExfillBorder(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player == null || player != minecraft.player || !snapshot.valid()) return;
        visible = true;
    }

    public static void hideExfillBorder(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player != null && player != minecraft.player) return;
        if (visible) revision++;
        visible = false;
    }

    public static void clear() {
        snapshot = hiddenSnapshot();
        visible = false;
        revision++;
    }

    public static boolean visible() {
        return visible && snapshot.valid();
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static long revision() {
        return revision;
    }

    private static Snapshot hiddenSnapshot() {
        return new Snapshot("", 0, new HeistExfillZone.Boundary(java.util.List.of()),
                HeistExfillZone.VisualState.HIDDEN);
    }
}
