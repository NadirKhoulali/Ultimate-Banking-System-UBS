package net.austizz.ultimatebankingsystem.bank.safebox.viewing;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class SafeBoxViewingEvents {
    private SafeBoxViewingEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        SafeBoxViewingCoordinator.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SafeBoxViewingCoordinator.handlePlayerTick(player);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (SafeBoxViewingCoordinator.blocksWorldAction(player)) {
            event.setCanceled(true);
            return;
        }
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof SafetyDepositBoxRowBlockEntity row
                && row.hasAnyViewingTransfer()) {
            event.setCanceled(true);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("A deposit box from this row is in a viewing session."),
                    true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && SafeBoxViewingCoordinator.blocksWorldAction(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && SafeBoxViewingCoordinator.blocksWorldAction(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && SafeBoxViewingCoordinator.blocksWorldAction(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player
                && SafeBoxViewingCoordinator.blocksWorldAction(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !SafeBoxViewingCoordinator.blocksWorldAction(player)) {
            return;
        }
        if (SafeBoxViewingCoordinator.canInteractWithViewingEntity(player, event.getTarget())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !SafeBoxViewingCoordinator.blocksWorldAction(player)) {
            return;
        }
        if (SafeBoxViewingCoordinator.canInteractWithViewingEntity(player, event.getTarget())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && SafeBoxViewingCoordinator.blocksWorldAction(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && SafeBoxViewingCoordinator.blocksWorldAction(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && SafeBoxViewingCoordinator.blocksWorldAction(player)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        boolean protectedTarget = server != null
                && SafeBoxViewingCoordinator.isProtectedActor(server, event.getEntity().getUUID());
        boolean protectedAttacker = server != null && event.getSource().getEntity() != null
                && SafeBoxViewingCoordinator.isProtectedActor(server, event.getSource().getEntity().getUUID());
        if (protectedTarget || protectedAttacker) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            SafeBoxViewingCoordinator.onLogout(player.getServer(), player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            SafeBoxViewingCoordinator.onDeath(player.getServer(), player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            SafeBoxViewingCoordinator.onDimensionChange(player.getServer(), player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SafeBoxViewingCoordinator.applyDeferredReturn(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SafeBoxViewingCoordinator.applyDeferredReturn(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SafeBoxViewingCoordinator.stop(event.getServer());
    }

}
