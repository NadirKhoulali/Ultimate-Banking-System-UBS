package net.austizz.ultimatebankingsystem.shop;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.network.DeliveryAlertPayload;
import net.austizz.ultimatebankingsystem.network.ServerActionAlert;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class ShopClaimToolEvents {
    private ShopClaimToolEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getHand() == InteractionHand.MAIN_HAND
                && player.isShiftKeyDown()
                && ShopService.hasStockroomLocateSession(player.getUUID())) {
            ShopService.cancelStockroomLocate(player, "Stockroom locate canceled.");
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (ShopService.hasPalletClaimToolSession(player.getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (event.getHand() != InteractionHand.MAIN_HAND) {
                return;
            }
            ItemStack stack = event.getItemStack();
            if (!ShopService.isPalletClaimToolStack(stack)) {
                sendPalletMessage(player, "Use the pallet claim tool items in your hotbar.", DeliveryAlertPayload.AlertTone.WARNING);
                return;
            }
            String marker = ShopService.palletClaimToolMarker(stack);
            switch (marker) {
                case "pallet_cancel" -> {
                    ShopService.ShopActionResult result = ShopService.finishPalletClaimToolSession(player, "Pallet claim tool canceled.");
                    sendPalletResult(player, event, result);
                }
                case "pallet_save" -> {
                    var server = player.getServer();
                    if (server == null) {
                        return;
                    }
                    CentralBank centralBank = BankManager.getCentralBank(server);
                    if (centralBank == null) {
                        sendPalletMessage(player, "Bank data is unavailable.", DeliveryAlertPayload.AlertTone.ERROR);
                        event.setCancellationResult(InteractionResult.FAIL);
                        return;
                    }
                    ShopService.ShopActionResult result = ShopService.savePalletClaimToolSession(server, centralBank, player);
                    sendPalletResult(player, event, result);
                }
                case "pallet_add" -> {
                    var server = player.getServer();
                    if (server == null) {
                        return;
                    }
                    CentralBank centralBank = BankManager.getCentralBank(server);
                    if (centralBank == null) {
                        sendPalletMessage(player, "Bank data is unavailable.", DeliveryAlertPayload.AlertTone.ERROR);
                        event.setCancellationResult(InteractionResult.FAIL);
                        return;
                    }
                    ShopService.setPalletClaimToolMode(player, true);
                    ShopService.ShopActionResult result = ShopService.stagePalletClaimSelection(centralBank, player, event.getPos());
                    sendPalletResult(player, event, result);
                }
                case "pallet_remove" -> {
                    var server = player.getServer();
                    if (server == null) {
                        return;
                    }
                    CentralBank centralBank = BankManager.getCentralBank(server);
                    if (centralBank == null) {
                        sendPalletMessage(player, "Bank data is unavailable.", DeliveryAlertPayload.AlertTone.ERROR);
                        event.setCancellationResult(InteractionResult.FAIL);
                        return;
                    }
                    ShopService.setPalletClaimToolMode(player, false);
                    ShopService.ShopActionResult result = ShopService.stagePalletClaimSelection(centralBank, player, event.getPos());
                    sendPalletResult(player, event, result);
                }
                case "pallet_lock" -> {
                    sendPalletMessage(player, "This slot is locked while pallet claim mode is active.", DeliveryAlertPayload.AlertTone.WARNING);
                    event.setCancellationResult(InteractionResult.FAIL);
                }
                default -> {
                    sendPalletMessage(player, "Use Add/Remove to target pallets, Paper to save, Barrier to cancel.", DeliveryAlertPayload.AlertTone.INFO);
                    event.setCancellationResult(InteractionResult.FAIL);
                }
            }
            return;
        }
        if (!ShopService.hasClaimToolSession(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!ShopService.isClaimToolStack(stack)) {
            player.sendSystemMessage(Component.literal("§eUse the claim tool items in your hotbar."));
            return;
        }

        String marker = ShopService.claimToolMarker(stack);
        switch (marker) {
            case "finish" -> {
                ShopService.ShopActionResult result = ShopService.finishClaimToolSession(player, "Claim tool closed.");
                sendResult(player, event, result);
            }
            case "add" -> {
                ShopService.ShopActionResult result = ShopService.setClaimToolMode(player, true);
                sendResult(player, event, result);
            }
            case "remove" -> {
                ShopService.ShopActionResult result = ShopService.setClaimToolMode(player, false);
                sendResult(player, event, result);
            }
            case "overlay" -> {
                ShopService.ShopActionResult result = ShopService.toggleClaimOverlay(player);
                sendResult(player, event, result);
            }
            case "clear" -> {
                ShopService.ShopActionResult result = ShopService.clearClaimToolSelection(player);
                sendResult(player, event, result);
            }
            case "apply" -> {
                var server = player.getServer();
                if (server == null) {
                    return;
                }
                CentralBank centralBank = BankManager.getCentralBank(server);
                if (centralBank == null) {
                    player.sendSystemMessage(Component.literal("§cBank data is unavailable."));
                    event.setCancellationResult(InteractionResult.FAIL);
                    return;
                }
                ShopService.ShopActionResult result = ShopService.applyClaimToolSelection(server, centralBank, player);
                sendResult(player, event, result);
            }
            case "wand" -> {
                ShopService.ShopActionResult result = ShopService.setClaimToolSecondCorner(player, event.getPos());
                sendResult(player, event, result);
            }
            case "lock" -> {
                player.sendSystemMessage(Component.literal("§eThis slot is locked while claim selection mode is active."));
                event.setCancellationResult(InteractionResult.FAIL);
            }
            default -> {
                player.sendSystemMessage(Component.literal("§eUse stick for corners, paper to apply, sponge to clear."));
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
        if (event.getHand() == InteractionHand.MAIN_HAND
                && player.isShiftKeyDown()
                && ShopService.hasStockroomLocateSession(player.getUUID())) {
            ShopService.cancelStockroomLocate(player, "Stockroom locate canceled.");
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (ShopService.hasPalletClaimToolSession(player.getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (event.getHand() != InteractionHand.MAIN_HAND) {
                return;
            }
            ItemStack stack = event.getItemStack();
            if (!ShopService.isPalletClaimToolStack(stack)) {
                sendPalletMessage(player, "Use the pallet claim tool items in your hotbar.", DeliveryAlertPayload.AlertTone.WARNING);
                return;
            }
            String marker = ShopService.palletClaimToolMarker(stack);
            switch (marker) {
                case "pallet_cancel" -> {
                    ShopService.ShopActionResult result = ShopService.finishPalletClaimToolSession(player, "Pallet claim tool canceled.");
                    sendPalletResult(player, event, result);
                }
                case "pallet_save" -> {
                    var server = player.getServer();
                    if (server == null) {
                        return;
                    }
                    CentralBank centralBank = BankManager.getCentralBank(server);
                    if (centralBank == null) {
                        sendPalletMessage(player, "Bank data is unavailable.", DeliveryAlertPayload.AlertTone.ERROR);
                        event.setCancellationResult(InteractionResult.FAIL);
                        return;
                    }
                    ShopService.ShopActionResult result = ShopService.savePalletClaimToolSession(server, centralBank, player);
                    sendPalletResult(player, event, result);
                }
                case "pallet_add" -> {
                    ShopService.ShopActionResult result = ShopService.setPalletClaimToolMode(player, true);
                    sendPalletResult(player, event, result);
                }
                case "pallet_remove" -> {
                    ShopService.ShopActionResult result = ShopService.setPalletClaimToolMode(player, false);
                    sendPalletResult(player, event, result);
                }
                case "pallet_lock" -> {
                    sendPalletMessage(player, "This slot is locked while pallet claim mode is active.", DeliveryAlertPayload.AlertTone.WARNING);
                    event.setCancellationResult(InteractionResult.FAIL);
                }
                default -> {
                    sendPalletMessage(player, "Click a pallet block with Add/Remove tool, Paper to save, Barrier to cancel.", DeliveryAlertPayload.AlertTone.INFO);
                    event.setCancellationResult(InteractionResult.FAIL);
                }
            }
            return;
        }
        if (!ShopService.hasClaimToolSession(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!ShopService.isClaimToolStack(stack)) {
            player.sendSystemMessage(Component.literal("§eUse the claim tool items in your hotbar."));
            return;
        }
        String marker = ShopService.claimToolMarker(stack);
        switch (marker) {
            case "finish" -> {
                ShopService.ShopActionResult result = ShopService.finishClaimToolSession(player, "Claim tool closed.");
                sendResult(player, event, result);
            }
            case "add" -> {
                ShopService.ShopActionResult result = ShopService.setClaimToolMode(player, true);
                sendResult(player, event, result);
            }
            case "remove" -> {
                ShopService.ShopActionResult result = ShopService.setClaimToolMode(player, false);
                sendResult(player, event, result);
            }
            case "overlay" -> {
                ShopService.ShopActionResult result = ShopService.toggleClaimOverlay(player);
                sendResult(player, event, result);
            }
            case "clear" -> {
                ShopService.ShopActionResult result = ShopService.clearClaimToolSelection(player);
                sendResult(player, event, result);
            }
            case "apply" -> {
                var server = player.getServer();
                if (server == null) {
                    return;
                }
                CentralBank centralBank = BankManager.getCentralBank(server);
                if (centralBank == null) {
                    player.sendSystemMessage(Component.literal("§cBank data is unavailable."));
                    event.setCancellationResult(InteractionResult.FAIL);
                    return;
                }
                ShopService.ShopActionResult result = ShopService.applyClaimToolSelection(server, centralBank, player);
                sendResult(player, event, result);
            }
            case "wand" -> {
                player.sendSystemMessage(Component.literal("§eLeft-click block = Pos1, Right-click block = Pos2, Paper = Apply."));
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            case "lock" -> {
                player.sendSystemMessage(Component.literal("§eThis slot is locked while claim selection mode is active."));
                event.setCancellationResult(InteractionResult.FAIL);
            }
            default -> event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!ShopService.hasAnyClaimToolSession(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (ShopService.hasPalletClaimToolSession(player.getUUID())) {
            event.setCanceled(true);
            if (event.getHand() != InteractionHand.MAIN_HAND) {
                return;
            }
            ItemStack stack = player.getMainHandItem();
            if (!ShopService.isPalletClaimToolStack(stack)) {
                sendPalletMessage(player, "Use the pallet claim tool items in your hotbar.", DeliveryAlertPayload.AlertTone.WARNING);
                return;
            }
            String marker = ShopService.palletClaimToolMarker(stack);
            switch (marker) {
                case "pallet_add" -> {
                    var server = player.getServer();
                    if (server == null) {
                        return;
                    }
                    CentralBank centralBank = BankManager.getCentralBank(server);
                    if (centralBank == null) {
                        sendPalletMessage(player, "Bank data is unavailable.", DeliveryAlertPayload.AlertTone.ERROR);
                        return;
                    }
                    ShopService.setPalletClaimToolMode(player, true);
                    ShopService.ShopActionResult result = ShopService.stagePalletClaimSelection(centralBank, player, event.getPos());
                    sendPalletResult(player, result);
                }
                case "pallet_remove" -> {
                    var server = player.getServer();
                    if (server == null) {
                        return;
                    }
                    CentralBank centralBank = BankManager.getCentralBank(server);
                    if (centralBank == null) {
                        sendPalletMessage(player, "Bank data is unavailable.", DeliveryAlertPayload.AlertTone.ERROR);
                        return;
                    }
                    ShopService.setPalletClaimToolMode(player, false);
                    ShopService.ShopActionResult result = ShopService.stagePalletClaimSelection(centralBank, player, event.getPos());
                    sendPalletResult(player, result);
                }
                case "pallet_save" -> {
                    var server = player.getServer();
                    if (server == null) {
                        return;
                    }
                    CentralBank centralBank = BankManager.getCentralBank(server);
                    if (centralBank == null) {
                        sendPalletMessage(player, "Bank data is unavailable.", DeliveryAlertPayload.AlertTone.ERROR);
                        return;
                    }
                    ShopService.ShopActionResult result = ShopService.savePalletClaimToolSession(server, centralBank, player);
                    sendPalletResult(player, result);
                }
                case "pallet_cancel" -> {
                    ShopService.ShopActionResult result = ShopService.finishPalletClaimToolSession(player, "Pallet claim tool canceled.");
                    sendPalletResult(player, result);
                }
                case "pallet_lock" -> sendPalletMessage(player, "This slot is locked while pallet claim mode is active.", DeliveryAlertPayload.AlertTone.WARNING);
                default -> sendPalletMessage(player, "Use Add/Remove to click pallets. Paper saves, Barrier cancels.", DeliveryAlertPayload.AlertTone.INFO);
            }
            return;
        }
        if (!ShopService.hasClaimToolSession(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!ShopService.isClaimToolStack(stack)) {
            player.sendSystemMessage(Component.literal("§eUse the claim tool items in your hotbar."));
            return;
        }
        String marker = ShopService.claimToolMarker(stack);
        if ("wand".equals(marker)) {
            ShopService.ShopActionResult result = ShopService.setClaimToolFirstCorner(player, event.getPos());
            player.sendSystemMessage(Component.literal((result.success() ? "§a" : "§c") + result.message()));
            return;
        }
        if ("lock".equals(marker)) {
            player.sendSystemMessage(Component.literal("§eThis slot is locked while claim selection mode is active."));
            return;
        }
        player.sendSystemMessage(Component.literal("§eUse stick for corners and paper to apply selection."));
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ShopService.hasAnyClaimToolSession(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("§eBlock placement is disabled while using claim tool mode."));
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!ShopService.hasAnyClaimToolSession(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("§eYou cannot drop claim tools while selection mode is active."));
    }

    private static void sendResult(ServerPlayer player, PlayerInteractEvent event, ShopService.ShopActionResult result) {
        if (player == null || event == null || result == null) {
            return;
        }
        player.sendSystemMessage(Component.literal((result.success() ? "§a" : "§c") + result.message()));
        event.setCancellationResult(result.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL);
    }

    private static void sendPalletResult(ServerPlayer player, PlayerInteractEvent event, ShopService.ShopActionResult result) {
        if (event != null && result != null) {
            event.setCancellationResult(result.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL);
        }
        sendPalletResult(player, result);
    }

    private static void sendPalletResult(ServerPlayer player, ShopService.ShopActionResult result) {
        if (result == null) {
            return;
        }
        sendPalletMessage(
                player,
                result.message(),
                result.success() ? DeliveryAlertPayload.AlertTone.SUCCESS : DeliveryAlertPayload.AlertTone.ERROR
        );
    }

    private static void sendPalletMessage(ServerPlayer player, String message, DeliveryAlertPayload.AlertTone tone) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        DeliveryAlertPayload.AlertTone normalizedTone = tone == null ? DeliveryAlertPayload.AlertTone.INFO : tone;
        String prefix = switch (normalizedTone) {
            case SUCCESS -> "§a";
            case ERROR -> "§c";
            case WARNING -> "§e";
            case INFO -> "§b";
        };
        player.sendSystemMessage(Component.literal(prefix + message));
        int durationMs = switch (normalizedTone) {
            case SUCCESS, INFO -> 3600;
            case WARNING -> 4300;
            case ERROR -> 5200;
        };
        ServerActionAlert.sendLegacy(player, "Delivery Pallets", message, normalizedTone, durationMs);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShopService.refreshDeliveryPalletLabelsForPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShopService.closeAllClaimToolSessions(player, "Selection tools closed on logout.");
            ShopService.clearDeliveryPalletLabelStateForPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ShopService.hasAnyClaimToolSession(player.getUUID())) {
            ShopService.closeAllClaimToolSessions(player, "Selection tools closed after dimension change.");
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            ShopService.refreshDeliveryPalletLabelsForPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShopService.closeAllClaimToolSessions(player, "Selection tools closed on death and hotbar restored.");
        }
    }
}
