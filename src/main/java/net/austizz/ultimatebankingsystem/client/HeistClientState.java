package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.gui.screens.HeistPlanningScreen;
import net.austizz.ultimatebankingsystem.heist.HeistPlanningService;
import net.austizz.ultimatebankingsystem.network.HeistHudPayload;
import net.austizz.ultimatebankingsystem.network.HeistPlanningPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class HeistClientState {
    public record CrewEntry(UUID id, String name, boolean accepted, boolean ready, boolean active, boolean online) {}
    public record TargetEntry(UUID bankId, String premiseId, String bankName, boolean eligible,
                              String blocker, int lootSources) {}
    public record HudCrewEntry(String name, int health, int maxHealth, boolean active, boolean online) {}
    public enum DrillKind { VAULT, SAFE }
    public enum DrillStatus { RUNNING, STALLED, COMPLETE }
    public record HudDrillEntry(DrillKind kind, String dimension, BlockPos pos, DrillStatus status,
                                int remainingTicks, int totalTicks) {}

    private static HeistPlanningPayload planning;
    private static HeistHudPayload hud = HeistHudPayload.inactive();
    private static List<HudDrillEntry> hudDrills = List.of();
    private static long hudReceivedClientTick;

    private HeistClientState() {}

    public static void apply(HeistPlanningPayload payload) {
        if (payload == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!payload.open()) {
            planning = null;
            if (minecraft.screen instanceof HeistPlanningScreen) minecraft.setScreen(null);
            return;
        }
        planning = payload;
        if (minecraft.screen instanceof HeistPlanningScreen screen) screen.update(payload);
        else minecraft.setScreen(new HeistPlanningScreen(payload));
    }

    public static void apply(HeistHudPayload payload) {
        hud = payload == null ? HeistHudPayload.inactive() : payload;
        HeistExfillBorderClientState.apply(hud);
        Minecraft minecraft = Minecraft.getInstance();
        hudReceivedClientTick = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        hudDrills = hud.active() ? parseDrills(hud.drillData()) : List.of();
        if (hud.active() && minecraft.screen instanceof HeistPlanningScreen) minecraft.setScreen(null);
    }

    public static HeistPlanningPayload planning() { return planning; }
    public static HeistHudPayload hud() { return hud; }
    public static List<HudDrillEntry> hudDrills() { return hudDrills; }
    public static boolean active() { return hud.active(); }
    public static boolean actionable() { return hud.active() && hud.actionable(); }

    public static int estimatedDrillRemainingTicks(HudDrillEntry drill, long clientTick) {
        if (drill == null || drill.status() != DrillStatus.RUNNING) {
            return drill == null ? 0 : Math.max(0, drill.remainingTicks());
        }
        long elapsed = Math.max(0L, clientTick - hudReceivedClientTick);
        return (int) Math.max(0L, drill.remainingTicks() - elapsed);
    }

    public static int estimatedExfillRemainingTicks(long clientTick) {
        int remaining = Math.max(0, hud.exfillRemainingTicks());
        if (!"ACTIVE".equalsIgnoreCase(hud.exfillState())) return remaining;
        long elapsed = Math.max(0L, clientTick - hudReceivedClientTick);
        return (int) Math.max(0L, remaining - elapsed);
    }

    public static List<CrewEntry> planningCrew(HeistPlanningPayload payload) {
        List<CrewEntry> result = new ArrayList<>();
        for (String[] fields : rows(payload == null ? "" : payload.crewData())) {
            if (fields.length < 6) continue;
            try {
                result.add(new CrewEntry(UUID.fromString(fields[0]), fields[1], Boolean.parseBoolean(fields[2]),
                        Boolean.parseBoolean(fields[3]), Boolean.parseBoolean(fields[4]), Boolean.parseBoolean(fields[5])));
            } catch (IllegalArgumentException ignored) {}
        }
        return List.copyOf(result);
    }

    public static List<TargetEntry> targets(HeistPlanningPayload payload) {
        List<TargetEntry> result = new ArrayList<>();
        for (String[] fields : rows(payload == null ? "" : payload.targetData())) {
            if (fields.length < 6) continue;
            try {
                result.add(new TargetEntry(UUID.fromString(fields[0]), fields[1], fields[2],
                        Boolean.parseBoolean(fields[3]), fields[4], Integer.parseInt(fields[5])));
            } catch (IllegalArgumentException ignored) {}
        }
        return List.copyOf(result);
    }

    public static List<HudCrewEntry> hudCrew() {
        List<HudCrewEntry> result = new ArrayList<>();
        for (String[] fields : rows(hud.crewData())) {
            if (fields.length < 5) continue;
            try {
                result.add(new HudCrewEntry(fields[0], Integer.parseInt(fields[1]), Integer.parseInt(fields[2]),
                        Boolean.parseBoolean(fields[3]), Boolean.parseBoolean(fields[4])));
            } catch (NumberFormatException ignored) {}
        }
        return List.copyOf(result);
    }

    private static List<HudDrillEntry> parseDrills(String encoded) {
        List<HudDrillEntry> result = new ArrayList<>();
        for (String[] fields : rows(encoded)) {
            if (fields.length < 6 || result.size() >= 128) continue;
            try {
                DrillKind kind = DrillKind.valueOf(fields[0].trim().toUpperCase(Locale.ROOT));
                DrillStatus status = DrillStatus.valueOf(fields[3].trim().toUpperCase(Locale.ROOT));
                BlockPos pos = BlockPos.of(Long.parseLong(fields[2]));
                int remaining = Math.max(0, Integer.parseInt(fields[4]));
                int total = Math.max(1, Integer.parseInt(fields[5]));
                if (!fields[1].isBlank()) {
                    result.add(new HudDrillEntry(kind, fields[1], pos, status, remaining, total));
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return List.copyOf(result);
    }

    private static List<String[]> rows(String encoded) {
        if (encoded == null || encoded.isEmpty()) return List.of();
        List<String[]> result = new ArrayList<>();
        for (String row : encoded.split(String.valueOf(HeistPlanningService.ROW_SEPARATOR), -1)) {
            result.add(row.split(String.valueOf(HeistPlanningService.FIELD_SEPARATOR), -1));
        }
        return result;
    }
}
