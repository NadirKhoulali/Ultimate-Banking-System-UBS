package net.austizz.ultimatebankingsystem.claim;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimToolLifecycle;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Prevents vanilla world mutation while the client uses the virtual claim workspace. */
@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class ClaimModeEvents {
    private ClaimModeEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isActiveServerPlayer(event.getEntity(), event.getLevel().isClientSide())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isActiveServerPlayer(event.getEntity(), event.getLevel().isClientSide())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isActiveServerPlayer(event.getEntity(), event.getLevel().isClientSide())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && ClaimModeService.hasSession(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ClaimModeService.hasSession(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && ClaimModeService.hasSession(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            close(player, SafeClaimToolLifecycle.TerminalReason.LOGOUT,
                    "Claim mode closed on logout.");
        }
    }

    @SubscribeEvent
    public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            close(player, SafeClaimToolLifecycle.TerminalReason.DIMENSION_CHANGE,
                    "Claim mode closed after dimension change.");
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            close(player, SafeClaimToolLifecycle.TerminalReason.DEATH,
                    "Claim mode closed on death.");
        }
    }

    private static boolean isActiveServerPlayer(net.minecraft.world.entity.player.Player player,
                                                boolean clientSide) {
        return !clientSide && player instanceof ServerPlayer serverPlayer
                && ClaimModeService.hasSession(serverPlayer.getUUID());
    }

    private static void close(ServerPlayer player,
                              SafeClaimToolLifecycle.TerminalReason reason,
                              String message) {
        ClaimToolKind kind = ClaimModeService.kind(player.getUUID());
        if (kind == null) {
            return;
        }
        if (kind == ClaimToolKind.SHOP_PLOT
                || kind == ClaimToolKind.SHOP_STOCKROOM
                || kind == ClaimToolKind.DELIVERY_PALLET) {
            ShopService.closeAllClaimToolSessions(player, message);
        } else {
            SafetyDepositBoxService.closeSafeAreaClaimToolSession(player, message, reason);
        }
        ClaimModeService.domainClosed(player);
    }
}
