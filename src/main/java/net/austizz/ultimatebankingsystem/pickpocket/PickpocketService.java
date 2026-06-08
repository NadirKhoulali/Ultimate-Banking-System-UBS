package net.austizz.ultimatebankingsystem.pickpocket;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.compat.neoforge.network.PacketDistributor;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.network.DeliveryAlertPayload;
import net.austizz.ultimatebankingsystem.network.PickpocketStatePayload;
import net.austizz.ultimatebankingsystem.network.ServerActionAlert;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class PickpocketService {
    private static final double MAX_INTERACTION_RANGE_BLOCKS = 1.0D;

    private static final ConcurrentHashMap<UUID, Attempt> ACTIVE_ATTEMPTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> COOLDOWN_UNTIL_TICK = new ConcurrentHashMap<>();

    private static final class Attempt {
        private final UUID thiefId;
        private final UUID targetId;
        private final long startedTick;
        private final int durationTicks;
        private int lastSyncedElapsedTicks = -1;

        private Attempt(UUID thiefId, UUID targetId, long startedTick, int durationTicks) {
            this.thiefId = thiefId;
            this.targetId = targetId;
            this.startedTick = startedTick;
            this.durationTicks = durationTicks;
        }
    }

    private record CashSlot(boolean offhand, int slot, ItemStack snapshot) {
    }

    private PickpocketService() {
    }

    public static void onServerStarting() {
        ACTIVE_ATTEMPTS.clear();
        COOLDOWN_UNTIL_TICK.clear();
    }

    public static void onServerStopping() {
        ACTIVE_ATTEMPTS.clear();
        COOLDOWN_UNTIL_TICK.clear();
    }

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }

        long nowTick = resolveServerTick(server);
        pruneCooldowns(nowTick);
        if (ACTIVE_ATTEMPTS.isEmpty()) {
            return;
        }

        CentralBank centralBank = BankManager.getCentralBank(server);
        for (Map.Entry<UUID, Attempt> entry : ACTIVE_ATTEMPTS.entrySet()) {
            Attempt attempt = entry.getValue();
            if (attempt == null) {
                continue;
            }

            ServerPlayer thief = server.getPlayerList().getPlayer(attempt.thiefId);
            if (thief == null) {
                ACTIVE_ATTEMPTS.remove(attempt.thiefId);
                continue;
            }

            if (!Config.PICKPOCKET_ENABLED.get()) {
                cancelAttempt(thief, nowTick,
                        "Pickpocketing is disabled by server configuration.",
                        true);
                continue;
            }

            ServerPlayer target = server.getPlayerList().getPlayer(attempt.targetId);
            String validationFailure = validateAttempt(thief, target, centralBank);
            if (validationFailure != null) {
                cancelAttempt(thief, nowTick, validationFailure, true);
                continue;
            }

            int elapsedTicks = (int) Math.max(0L, nowTick - attempt.startedTick);
            if (elapsedTicks != attempt.lastSyncedElapsedTicks) {
                attempt.lastSyncedElapsedTicks = elapsedTicks;
                syncActiveState(thief, safeName(target), elapsedTicks, attempt.durationTicks);
            }

            if (elapsedTicks < attempt.durationTicks) {
                continue;
            }

            resolveCompletion(thief, target, attempt, centralBank, nowTick);
        }
    }

    public static void handleStartRequest(ServerPlayer thief, UUID targetId) {
        if (thief == null || targetId == null) {
            return;
        }

        MinecraftServer server = thief.getServer();
        if (server == null) {
            return;
        }

        long nowTick = resolveServerTick(server);
        if (!Config.PICKPOCKET_ENABLED.get()) {
            ServerActionAlert.send(thief,
                    "Pickpocket",
                    "Pickpocketing is currently disabled by server configuration.",
                    DeliveryAlertPayload.AlertTone.ERROR,
                    4200);
            syncIdleState(thief, nowTick);
            return;
        }

        Attempt existing = ACTIVE_ATTEMPTS.get(thief.getUUID());
        if (existing != null) {
            return;
        }

        long cooldownRemaining = getCooldownRemainingTicks(thief.getUUID(), nowTick);
        if (cooldownRemaining > 0L) {
            ServerActionAlert.send(thief,
                    "Pickpocket",
                    "Cooldown active: wait " + formatSeconds(cooldownRemaining) + " seconds.",
                    DeliveryAlertPayload.AlertTone.WARNING,
                    3800);
            syncIdleState(thief, nowTick);
            return;
        }

        CentralBank centralBank = BankManager.getCentralBank(server);
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        String validationFailure = validateAttempt(thief, target, centralBank);
        if (validationFailure != null) {
            ServerActionAlert.send(thief,
                    "Pickpocket",
                    validationFailure,
                    DeliveryAlertPayload.AlertTone.WARNING,
                    3600);
            syncIdleState(thief, nowTick);
            return;
        }

        int durationTicks = Math.max(1, Config.PICKPOCKET_DURATION_TICKS.get());
        Attempt attempt = new Attempt(thief.getUUID(), targetId, nowTick, durationTicks);
        ACTIVE_ATTEMPTS.put(thief.getUUID(), attempt);
        syncActiveState(thief, safeName(target), 0, durationTicks);
    }

    public static void handleCancelRequest(ServerPlayer thief) {
        if (thief == null) {
            return;
        }
        MinecraftServer server = thief.getServer();
        if (server == null) {
            return;
        }
        long nowTick = resolveServerTick(server);
        cancelAttempt(thief, nowTick, "", false);
    }

    public static boolean isPlayerOptedOut(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) {
            return false;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return false;
        }
        return centralBank.isPickpocketOptOut(playerId);
    }

    public static boolean setPlayerOptOut(MinecraftServer server, UUID playerId, boolean optedOut) {
        if (server == null || playerId == null) {
            return false;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return false;
        }
        centralBank.setPickpocketOptOut(playerId, optedOut);
        // If this player is involved in active theft, cancel immediately so state is consistent.
        long nowTick = resolveServerTick(server);
        for (Attempt attempt : ACTIVE_ATTEMPTS.values()) {
            if (attempt == null) {
                continue;
            }
            if (!playerId.equals(attempt.thiefId) && !playerId.equals(attempt.targetId)) {
                continue;
            }
            ServerPlayer thief = server.getPlayerList().getPlayer(attempt.thiefId);
            if (thief != null) {
                cancelAttempt(thief,
                        nowTick,
                        "Pickpocket attempt cancelled because one of the players disabled pickpocketing.",
                        true);
            } else {
                ACTIVE_ATTEMPTS.remove(attempt.thiefId);
            }
        }
        return true;
    }

    public static void sendLoginStatusMessage(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        boolean optedOut = isPlayerOptedOut(player.getServer(), player.getUUID());
        if (optedOut) {
            player.sendSystemMessage(Component.literal(
                    "Pickpocket status: Disabled (you are immune and cannot pickpocket others)."
            ).withStyle(ChatFormatting.RED));
        } else {
            player.sendSystemMessage(Component.literal(
                    "Pickpocket status: Enabled (you can pickpocket and be pickpocketed)."
            ).withStyle(ChatFormatting.GREEN));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        sendLoginStatusMessage(player);
        syncIdleState(player, resolveServerTick(player.getServer()));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ACTIVE_ATTEMPTS.remove(player.getUUID());
    }

    private static void resolveCompletion(ServerPlayer thief,
                                          ServerPlayer target,
                                          Attempt attempt,
                                          CentralBank centralBank,
                                          long nowTick) {
        ACTIVE_ATTEMPTS.remove(thief.getUUID());

        CashSlot slot = findFirstCashStack(target);
        if (slot == null) {
            applyCooldown(thief.getUUID(), nowTick);
            syncIdleState(thief, nowTick);
            ServerActionAlert.send(thief,
                    "Pickpocket Failed",
                    safeName(target) + " has no cash stack to steal.",
                    DeliveryAlertPayload.AlertTone.ERROR,
                    4200);
            return;
        }

        ItemStack stolenStack = removeCashStack(target, slot);
        if (stolenStack.isEmpty()) {
            applyCooldown(thief.getUUID(), nowTick);
            syncIdleState(thief, nowTick);
            ServerActionAlert.send(thief,
                    "Pickpocket Failed",
                    "Could not steal cash stack due to inventory desync.",
                    DeliveryAlertPayload.AlertTone.ERROR,
                    4200);
            return;
        }

        ItemStack transferStack = stolenStack.copy();
        thief.getInventory().add(transferStack);
        if (!transferStack.isEmpty()) {
            thief.drop(transferStack, false);
        }
        thief.getInventory().setChanged();
        thief.containerMenu.broadcastChanges();

        String stolenSummary = formatStackSummary(stolenStack);
        String thiefName = safeName(thief);
        String targetName = safeName(target);

        if (centralBank != null) {
            centralBank.recordPickpocket(
                    thief.getUUID(),
                    target.getUUID(),
                    targetName,
                    System.currentTimeMillis(),
                    stolenSummary
            );
        }

        applyCooldown(thief.getUUID(), nowTick);
        syncIdleState(thief, nowTick);

        ServerActionAlert.send(thief,
                "Pickpocket Success",
                "You stole " + stolenSummary + " from " + targetName + ".",
                DeliveryAlertPayload.AlertTone.SUCCESS,
                4200);

        ServerActionAlert.send(target,
                "You Were Pickpocketed",
                thiefName + " stole " + stolenSummary + " from you.",
                DeliveryAlertPayload.AlertTone.WARNING,
                4600);
    }

    private static void cancelAttempt(ServerPlayer thief,
                                      long nowTick,
                                      String reason,
                                      boolean notifyThief) {
        if (thief == null) {
            return;
        }
        Attempt removed = ACTIVE_ATTEMPTS.remove(thief.getUUID());
        if (removed == null) {
            syncIdleState(thief, nowTick);
            return;
        }
        applyCooldown(thief.getUUID(), nowTick);
        syncIdleState(thief, nowTick);
        if (notifyThief && reason != null && !reason.isBlank()) {
            ServerActionAlert.send(thief,
                    "Pickpocket Cancelled",
                    reason,
                    DeliveryAlertPayload.AlertTone.WARNING,
                    3600);
        }
    }

    private static String validateAttempt(ServerPlayer thief, ServerPlayer target, CentralBank centralBank) {
        if (thief == null || target == null) {
            return "Target player is not available.";
        }
        if (centralBank == null) {
            return "Bank data is unavailable.";
        }
        if (thief.getUUID().equals(target.getUUID())) {
            return "You cannot pickpocket yourself.";
        }
        if (!thief.isAlive() || !target.isAlive()) {
            return "Pickpocketing requires both players to be alive.";
        }
        if (thief.level() != target.level()) {
            return "Target is in another dimension.";
        }
        if (!isAllowedGameMode(thief) || !isAllowedGameMode(target)) {
            return "Both players must be in Survival or Adventure mode.";
        }
        if (centralBank.isPickpocketOptOut(thief.getUUID())) {
            return "Pickpocketing is disabled for you. Use /account pickpocket toggle to re-enable.";
        }
        if (centralBank.isPickpocketOptOut(target.getUUID())) {
            return "That player has pickpocketing disabled.";
        }
        if (!isWithinRange(thief, target)) {
            return "Target is too far away (must be within 1 block).";
        }
        return null;
    }

    private static boolean isAllowedGameMode(ServerPlayer player) {
        if (player == null || player.gameMode == null) {
            return false;
        }
        GameType mode = player.gameMode.getGameModeForPlayer();
        return mode == GameType.SURVIVAL || mode == GameType.ADVENTURE;
    }

    private static boolean isWithinRange(ServerPlayer thief, ServerPlayer target) {
        if (thief == null || target == null) {
            return false;
        }
        double distanceSq = squaredBoxDistance(thief.getBoundingBox(), target.getBoundingBox());
        return distanceSq <= (MAX_INTERACTION_RANGE_BLOCKS * MAX_INTERACTION_RANGE_BLOCKS);
    }

    // Computes shortest squared distance between two AABBs so reach checks respect player hitboxes.
    private static double squaredBoxDistance(AABB first, AABB second) {
        double dx = axisDistance(first.minX, first.maxX, second.minX, second.maxX);
        double dy = axisDistance(first.minY, first.maxY, second.minY, second.maxY);
        double dz = axisDistance(first.minZ, first.maxZ, second.minZ, second.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double minA, double maxA, double minB, double maxB) {
        if (maxA < minB) {
            return minB - maxA;
        }
        if (maxB < minA) {
            return minA - maxB;
        }
        return 0.0D;
    }

    private static CashSlot findFirstCashStack(ServerPlayer target) {
        if (target == null) {
            return null;
        }
        Inventory inventory = target.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack stack = inventory.items.get(i);
            if (isStealableCashStack(stack)) {
                return new CashSlot(false, i, stack.copy());
            }
        }
        for (int i = 0; i < inventory.offhand.size(); i++) {
            ItemStack stack = inventory.offhand.get(i);
            if (isStealableCashStack(stack)) {
                return new CashSlot(true, i, stack.copy());
            }
        }
        return null;
    }

    private static ItemStack removeCashStack(ServerPlayer target, CashSlot slot) {
        if (target == null || slot == null) {
            return ItemStack.EMPTY;
        }
        Inventory inventory = target.getInventory();
        ItemStack existing = slot.offhand
                ? inventory.offhand.get(slot.slot)
                : inventory.items.get(slot.slot);
        if (!isStealableCashStack(existing)) {
            return ItemStack.EMPTY;
        }
        ItemStack stolen = existing.copy();
        if (slot.offhand) {
            inventory.offhand.set(slot.slot, ItemStack.EMPTY);
        } else {
            inventory.items.set(slot.slot, ItemStack.EMPTY);
        }
        inventory.setChanged();
        target.containerMenu.broadcastChanges();
        return stolen;
    }

    private static boolean isStealableCashStack(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getCount() > 0
                && DollarBills.isCashTenderItem(stack.getItem());
    }

    private static String formatStackSummary(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "cash";
        }
        String name = stack.getHoverName() == null
                ? "cash"
                : stack.getHoverName().getString();
        return name + " x" + Math.max(1, stack.getCount());
    }

    private static void syncActiveState(ServerPlayer thief,
                                        String targetName,
                                        int elapsedTicks,
                                        int durationTicks) {
        if (thief == null) {
            return;
        }
        PacketDistributor.sendToPlayer(thief, new PickpocketStatePayload(
                true,
                targetName,
                Math.max(0, elapsedTicks),
                Math.max(1, durationTicks),
                0
        ));
    }

    private static void syncIdleState(ServerPlayer thief, long nowTick) {
        if (thief == null) {
            return;
        }
        int cooldownTicks = (int) Math.min(Integer.MAX_VALUE, getCooldownRemainingTicks(thief.getUUID(), nowTick));
        PacketDistributor.sendToPlayer(thief, new PickpocketStatePayload(
                false,
                "",
                0,
                0,
                Math.max(0, cooldownTicks)
        ));
    }

    private static void applyCooldown(UUID thiefId, long nowTick) {
        if (thiefId == null) {
            return;
        }
        int cooldownTicks = Math.max(1, Config.PICKPOCKET_COOLDOWN_TICKS.get());
        COOLDOWN_UNTIL_TICK.put(thiefId, nowTick + cooldownTicks);
    }

    private static long getCooldownRemainingTicks(UUID thiefId, long nowTick) {
        if (thiefId == null) {
            return 0L;
        }
        Long untilTick = COOLDOWN_UNTIL_TICK.get(thiefId);
        if (untilTick == null) {
            return 0L;
        }
        return Math.max(0L, untilTick - nowTick);
    }

    private static void pruneCooldowns(long nowTick) {
        COOLDOWN_UNTIL_TICK.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= nowTick);
    }

    private static long resolveServerTick(MinecraftServer server) {
        if (server == null) {
            return 0L;
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        return overworld == null ? 0L : overworld.getGameTime();
    }

    private static String safeName(ServerPlayer player) {
        if (player == null || player.getName() == null) {
            return "Unknown";
        }
        String raw = player.getName().getString();
        return raw == null || raw.isBlank() ? "Unknown" : raw.trim();
    }

    private static String formatSeconds(long ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", Math.max(0L, ticks) / 20.0D);
    }
}
