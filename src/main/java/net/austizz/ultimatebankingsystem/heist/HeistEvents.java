package net.austizz.ultimatebankingsystem.heist;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class HeistEvents {
    private HeistEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        HeistService.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HeistService.onPlayerLogin(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HeistService.onPlayerLogout(player);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HeistService.onPlayerDeath(player);
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        var stack = event.getItemEntity().getItem();
        UUID sessionId = net.austizz.ultimatebankingsystem.item.HeistDuffelData.sessionId(stack);
        if (sessionId == null) return;
        HeistSession session = HeistService.activeSession(player);
        if (session == null || !sessionId.equals(session.id())) {
            event.setCanPickup(TriState.FALSE);
            return;
        }
        int carried = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (sessionId.equals(net.austizz.ultimatebankingsystem.item.HeistDuffelData.sessionId(
                    player.getInventory().getItem(slot)))) carried++;
        }
        if (carried >= 2) {
            event.setCanPickup(TriState.FALSE);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "You can carry at most two heist duffels."), true);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        HeistService.stop(event.getServer());
    }
}
