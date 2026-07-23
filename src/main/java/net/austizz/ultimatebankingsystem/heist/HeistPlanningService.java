package net.austizz.ultimatebankingsystem.heist;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.item.HeistDuffelData;
import net.austizz.ultimatebankingsystem.network.HeistHudPayload;
import net.austizz.ultimatebankingsystem.network.HeistPlanningActionPayload;
import net.austizz.ultimatebankingsystem.network.HeistPlanningPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HeistPlanningService {
    public static final char FIELD_SEPARATOR = '\u001f';
    public static final char ROW_SEPARATOR = '\u001e';
    private static final Map<MinecraftServer, Set<UUID>> HUD_ACTIVE_CLIENTS = new IdentityHashMap<>();

    private HeistPlanningService() {}

    public static void open(ServerPlayer player) {
        sendSnapshot(player, "");
    }

    public static void handle(ServerPlayer player, HeistPlanningActionPayload payload) {
        if (player == null || payload == null) return;
        HeistSession previous = HeistService.session(player);
        String requestedAction = payload.action().toLowerCase(Locale.ROOT);
        HeistService.Result result;
        try {
            result = switch (requestedAction) {
                case "refresh" -> HeistService.Result.ok("");
                case "invite" -> HeistService.invite(player, payload.arg1());
                case "accept" -> HeistService.accept(player, true);
                case "decline" -> HeistService.accept(player, false);
                case "leave" -> HeistService.leave(player);
                case "ready" -> HeistService.setReady(player, true);
                case "unready" -> HeistService.setReady(player, false);
                case "select" -> HeistService.selectTarget(player, UUID.fromString(payload.arg1()), payload.arg2());
                case "start" -> HeistService.startCountdown(player);
                case "cancel" -> HeistService.cancelCountdown(player);
                case "abandon" -> HeistService.abandon(player);
                default -> HeistService.Result.fail("Unknown heist planning action.");
            };
        } catch (IllegalArgumentException exception) {
            result = HeistService.Result.fail("Invalid heist target or action value.");
        }
        boolean closeRequester = result.success()
                && ("decline".equals(requestedAction) || "leave".equals(requestedAction)
                || "abandon".equals(requestedAction));
        if (closeRequester) closePlanner(player, result.message());
        else sendSnapshot(player, result.message());
        HeistSession session = HeistService.session(player);
        if (session == null) session = previous;
        if (session == null) return;
        for (HeistMember member : session.members().values()) {
            if (member.playerId().equals(player.getUUID())) continue;
            ServerPlayer online = player.getServer().getPlayerList().getPlayer(member.playerId());
            if (online == null) continue;
            if (HeistService.session(online) == null) closePlanner(online, "Heist crew disbanded.");
            else sendSnapshot(online, "");
        }
    }

    public static void sendSnapshot(ServerPlayer player, String status) {
        if (player == null || player.getServer() == null) return;
        HeistSession session = HeistService.session(player);
        if (session == null) session = HeistService.planningSession(player);
        if (session == null) return;
        MinecraftServer server = player.getServer();
        CentralBank centralBank = BankManager.getCentralBank(server);
        StringBuilder crew = new StringBuilder();
        for (HeistMember member : session.members().values()) {
            appendRow(crew,
                    member.playerId().toString(), clean(member.lastKnownName()),
                    Boolean.toString(member.accepted()), Boolean.toString(member.ready()),
                    Boolean.toString(member.active()),
                    Boolean.toString(server.getPlayerList().getPlayer(member.playerId()) != null));
        }
        StringBuilder targets = new StringBuilder();
        if (centralBank != null) {
            for (HeistTarget target : HeistEligibilityService.targets(server, centralBank, HeistSavedData.get(server))) {
                appendRow(targets, target.bankId().toString(), clean(target.premiseId()), clean(target.bankName()),
                        Boolean.toString(target.eligible()), clean(String.join(" ", target.blockers())),
                        Integer.toString(target.physicalLootSources()));
            }
        }
        HeistMember leader = session.member(session.leaderId());
        PacketDistributor.sendToPlayer(player, new HeistPlanningPayload(
                true, session.phase().name(), player.getUUID().equals(session.leaderId()),
                leader == null ? "" : clean(leader.lastKnownName()), clean(session.bankName()),
                clean(session.premiseId()), crew.toString(), targets.toString(), clean(status)));
    }

    public static void syncHud(MinecraftServer server) {
        if (server == null) return;
        Set<UUID> activeClients = HUD_ACTIVE_CLIENTS.computeIfAbsent(server, ignored -> new LinkedHashSet<>());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            HeistSession session = HeistService.activeSession(player);
            if (session == null) {
                if (activeClients.remove(player.getUUID())) {
                    PacketDistributor.sendToPlayer(player, HeistHudPayload.inactive());
                }
                continue;
            }
            activeClients.add(player.getUUID());
            HeistInteractionService.ActionView view = HeistInteractionService.view(player);
            int bagSlots = 0;
            int bagCapacity = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!session.id().equals(HeistDuffelData.sessionId(stack))) continue;
                bagSlots += HeistDuffelData.occupiedSlots(stack, player.registryAccess());
                bagCapacity += HeistDuffelData.SLOT_COUNT;
            }
            List<String> crewRows = new ArrayList<>();
            for (HeistMember member : session.members().values()) {
                ServerPlayer online = server.getPlayerList().getPlayer(member.playerId());
                int health = online == null ? 0 : Math.max(0, Math.round(online.getHealth()));
                int maxHealth = online == null ? 20 : Math.max(1, Math.round(online.getMaxHealth()));
                crewRows.add(joinFields(clean(member.lastKnownName()), Integer.toString(health),
                        Integer.toString(maxHealth), Boolean.toString(member.active()),
                        Boolean.toString(online != null)));
            }
            long remaining = Math.max(0L, session.deadlineTick() - server.getTickCount());
            HeistExfillZone.Boundary exfillBoundary = HeistService.exfillBoundary(session);
            HeistService.ExfillStatus exfillStatus = HeistService.exfillStatus(server, session);
            String exfillDimension = session.exit() == null ? "" : session.exit().dimension();
            int exfillReferenceY = session.exit() == null ? 0 : session.exit().y();
            PacketDistributor.sendToPlayer(player, new HeistHudPayload(true, clean(session.bankName()),
                    session.phase().name(), session.alarmed(), (int) Math.min(Integer.MAX_VALUE, remaining),
                    session.totalLootCents(), bagSlots, bagCapacity,
                    String.join(String.valueOf(ROW_SEPARATOR), crewRows), view.actionable(), view.prompt(),
                    view.elapsedTicks(), view.requiredTicks(), exfillDimension, exfillReferenceY,
                    exfillBoundary.encode(), exfillStatus.visualState().name(), exfillStatus.lootArmed(),
                    exfillStatus.remainingTicks(), exfillStatus.crewInside(), exfillStatus.crewRequired(),
                    encodeDrillData(session, server.getTickCount())));
        }
        activeClients.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private static String encodeDrillData(HeistSession session, long currentTick) {
        if (session == null) return "";
        StringBuilder rows = new StringBuilder();
        for (HeistSession.DrillState drill : session.vaultDrills()) {
            appendDrillRow(rows, "VAULT", drill, currentTick,
                    HeistInteractionService.VAULT_DRILL_DURATION_TICKS);
        }
        for (HeistSession.DrillState drill : session.safeDrills()) {
            appendDrillRow(rows, "SAFE", drill, currentTick,
                    HeistInteractionService.SAFE_DRILL_DURATION_TICKS);
        }
        return rows.toString();
    }

    private static void appendDrillRow(StringBuilder rows, String kind,
                                       HeistSession.DrillState drill, long currentTick,
                                       int totalTicks) {
        if (drill == null || drill.pos() == null || drill.dimension().isBlank()) return;
        String status = drill.completed() ? "COMPLETE"
                : drill.jammedAtTick() > 0L ? "STALLED" : "RUNNING";
        long timerTick = drill.jammedAtTick() > 0L ? drill.jammedAtTick() : currentTick;
        long remaining = drill.completed() ? 0L : Math.max(0L, drill.finishesTick() - timerTick);
        appendRow(rows, kind, clean(drill.dimension()), Long.toString(drill.pos().asLong()), status,
                Long.toString(Math.min(Integer.MAX_VALUE, remaining)), Integer.toString(totalTicks));
    }

    public static void stop(MinecraftServer server) {
        if (server != null) HUD_ACTIVE_CLIENTS.remove(server);
    }

    public static void clearClientState(ServerPlayer player, String status) {
        if (player == null || player.getServer() == null) return;
        Set<UUID> activeClients = HUD_ACTIVE_CLIENTS.get(player.getServer());
        if (activeClients != null) activeClients.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, HeistHudPayload.inactive());
        closePlanner(player, status);
    }

    private static void closePlanner(ServerPlayer player, String status) {
        PacketDistributor.sendToPlayer(player, new HeistPlanningPayload(false, "", false, "", "", "",
                "", "", clean(status)));
    }

    private static void appendRow(StringBuilder target, String... fields) {
        if (!target.isEmpty()) target.append(ROW_SEPARATOR);
        target.append(joinFields(fields));
    }

    private static String joinFields(String... fields) {
        return String.join(String.valueOf(FIELD_SEPARATOR), fields);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace(FIELD_SEPARATOR, ' ').replace(ROW_SEPARATOR, ' ').trim();
    }
}
