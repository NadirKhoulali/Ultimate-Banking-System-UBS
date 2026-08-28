package net.austizz.ultimatebankingsystem.migration.numismatics;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class NumismaticsMigrationEvents {
    private static final Component MAINTENANCE_MESSAGE = Component.literal(
            "UBS economy migration maintenance is active. Reconnect after the server restarts.");

    private NumismaticsMigrationEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            if (NumismaticsBackupService.restorePendingRollback(event.getServer())) {
                UltimateBankingSystem.LOGGER.warn("[UBS] Restored a verified Numismatics migration backup.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("UBS refused to load the world because migration rollback failed.", exception);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStarting(ServerStartingEvent event) {
        NumismaticsMigrationSavedData journal = NumismaticsMigrationSavedData.get(event.getServer());
        if (journal.phase() == NumismaticsMigrationPhase.COMPLETE
                && journal.maintenanceLocked()
                && !ModList.get().isLoaded("numismatics")) {
            journal.setMaintenanceLocked(false);
            journal.setPhase(NumismaticsMigrationPhase.COMPLETE,
                    "Numismatics is no longer installed. UBS migration maintenance has ended.");
            journal.audit("FINALIZED", "Server restarted without Create: Numismatics.");
        } else if (journal.maintenanceLocked()) {
            NumismaticsMigrationService.ensureMaintenanceFreeze(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) NumismaticsMigrationService.tick(server);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (NumismaticsMigrationService.maintenanceLocked(player.getServer())
                && !player.getServer().getPlayerList().isOp(player.getGameProfile())) {
            player.connection.disconnect(MAINTENANCE_MESSAGE);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (locked(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && locked(player)) {
            event.setCanceled(true);
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onToss(ItemTossEvent event) {
        if (locked(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        NumismaticsMigrationService.onServerStopping(event.getServer());
    }

    private static boolean locked(net.minecraft.world.entity.player.Player player) {
        return player instanceof ServerPlayer serverPlayer
                && NumismaticsMigrationService.maintenanceLocked(serverPlayer.getServer());
    }
}
