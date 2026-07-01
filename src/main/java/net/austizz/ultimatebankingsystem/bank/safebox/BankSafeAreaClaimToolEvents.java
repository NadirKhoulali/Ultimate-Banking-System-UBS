package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class BankSafeAreaClaimToolEvents {
    private BankSafeAreaClaimToolEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!SafetyDepositBoxService.hasSafeAreaClaimToolSession(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!SafetyDepositBoxService.isSafeAreaClaimToolStack(stack)) {
            sendMessage(player, false, "Use the safe-area claim tool items in your hotbar.");
            return;
        }
        String marker = SafetyDepositBoxService.safeAreaClaimToolMarker(stack);
        switch (marker) {
            case "finish" -> sendResult(player, SafetyDepositBoxService.finishSafeAreaClaimToolSession(player, "Safe-area claim tool closed."));
            case "add" -> sendResult(player, SafetyDepositBoxService.setSafeAreaClaimToolMode(player, true));
            case "remove" -> sendResult(player, SafetyDepositBoxService.setSafeAreaClaimToolMode(player, false));
            case "overlay" -> sendResult(player, SafetyDepositBoxService.toggleSafeAreaClaimOverlay(player));
            case "clear" -> sendResult(player, SafetyDepositBoxService.clearSafeAreaClaimToolSelection(player));
            case "apply" -> applySelection(player);
            case "wand" -> sendResult(player, SafetyDepositBoxService.setSafeAreaClaimToolSecondCorner(player, event.getPos()));
            case "lock" -> sendMessage(player, false, "This slot is locked while safe-area claim mode is active.");
            default -> {
                sendMessage(player, false, "Use stick for corners, paper to apply, sponge to clear.");
                event.setCancellationResult(InteractionResult.FAIL);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!SafetyDepositBoxService.hasSafeAreaClaimToolSession(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!SafetyDepositBoxService.isSafeAreaClaimToolStack(stack)) {
            sendMessage(player, false, "Use the safe-area claim tool items in your hotbar.");
            return;
        }
        String marker = SafetyDepositBoxService.safeAreaClaimToolMarker(stack);
        switch (marker) {
            case "finish" -> sendResult(player, SafetyDepositBoxService.finishSafeAreaClaimToolSession(player, "Safe-area claim tool closed."));
            case "add" -> sendResult(player, SafetyDepositBoxService.setSafeAreaClaimToolMode(player, true));
            case "remove" -> sendResult(player, SafetyDepositBoxService.setSafeAreaClaimToolMode(player, false));
            case "overlay" -> sendResult(player, SafetyDepositBoxService.toggleSafeAreaClaimOverlay(player));
            case "clear" -> sendResult(player, SafetyDepositBoxService.clearSafeAreaClaimToolSelection(player));
            case "apply" -> applySelection(player);
            case "wand" -> sendMessage(player, true, "Left-click block = Pos1, Right-click block = Pos2, Paper = Apply.");
            case "lock" -> sendMessage(player, false, "This slot is locked while safe-area claim mode is active.");
            default -> event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!SafetyDepositBoxService.hasSafeAreaClaimToolSession(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!SafetyDepositBoxService.isSafeAreaClaimToolStack(stack)) {
            sendMessage(player, false, "Use the safe-area claim tool items in your hotbar.");
            return;
        }
        String marker = SafetyDepositBoxService.safeAreaClaimToolMarker(stack);
        if ("wand".equals(marker)) {
            sendResult(player, SafetyDepositBoxService.setSafeAreaClaimToolFirstCorner(player, event.getPos()));
            return;
        }
        if ("lock".equals(marker)) {
            sendMessage(player, false, "This slot is locked while safe-area claim mode is active.");
            return;
        }
        sendMessage(player, false, "Use stick for corners and paper to apply selection.");
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!SafetyDepositBoxService.hasSafeAreaClaimToolSession(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!SafetyDepositBoxService.hasSafeAreaClaimToolSession(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
        sendMessage(player, false, "Block placement is disabled while using safe-area claim mode.");
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!SafetyDepositBoxService.hasSafeAreaClaimToolSession(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
        sendMessage(player, false, "You cannot drop safe-area claim tools while selection mode is active.");
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SafetyDepositBoxService.closeSafeAreaClaimToolSession(player, "Safe-area claim tool closed on logout.");
        }
    }

    @SubscribeEvent
    public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SafetyDepositBoxService.closeSafeAreaClaimToolSession(player, "Safe-area claim tool closed after dimension change.");
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SafetyDepositBoxService.closeSafeAreaClaimToolSession(player, "Safe-area claim tool closed on death and hotbar restored.");
        }
    }

    private static void applySelection(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            sendMessage(player, false, "Server context is unavailable.");
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            sendMessage(player, false, "Bank data is unavailable.");
            return;
        }
        sendResult(player, SafetyDepositBoxService.applySafeAreaClaimToolSelection(server, centralBank, player));
    }

    private static void sendResult(ServerPlayer player, SafetyDepositBoxService.ActionResult result) {
        if (result == null) {
            return;
        }
        sendMessage(player, result.success(), result.message());
    }

    private static void sendMessage(ServerPlayer player, boolean success, String message) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        player.sendSystemMessage(UbsTranslations.literal((success ? "§a" : "§e") + message));
    }
}
