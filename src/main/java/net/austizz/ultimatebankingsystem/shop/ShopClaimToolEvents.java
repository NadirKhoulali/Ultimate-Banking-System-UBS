package net.austizz.ultimatebankingsystem.shop;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Shop-only side effects that are intentionally separate from universal claim-mode input. */
@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class ShopClaimToolEvents {
    private ShopClaimToolEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (cancelStockroomLocate(event)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (cancelStockroomLocate(event)) {
            event.setCanceled(true);
        }
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
            ShopService.clearDeliveryPalletLabelStateForPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShopService.refreshDeliveryPalletLabelsForPlayer(player);
        }
    }

    private static boolean cancelStockroomLocate(PlayerInteractEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !player.isShiftKeyDown()
                || !ShopService.hasStockroomLocateSession(player.getUUID())) {
            return false;
        }
        ShopService.cancelStockroomLocate(player, "Stockroom locate canceled.");
        return true;
    }
}
