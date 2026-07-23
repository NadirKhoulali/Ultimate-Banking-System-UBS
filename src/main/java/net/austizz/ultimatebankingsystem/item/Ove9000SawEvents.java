package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class Ove9000SawEvents {
    private Ove9000SawEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (mainHand.is(ModItems.OVE9000_SAW.get())) {
            if (!offHand.isEmpty()) {
                returnToInventory(player, InteractionHand.OFF_HAND, offHand);
            }
            return;
        }

        if (!offHand.is(ModItems.OVE9000_SAW.get())) {
            return;
        }
        if (mainHand.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, offHand.copy());
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();
            return;
        }
        returnToInventory(player, InteractionHand.OFF_HAND, offHand);
    }

    private static void returnToInventory(ServerPlayer player, InteractionHand hand, ItemStack heldStack) {
        ItemStack returned = heldStack.copy();
        player.setItemInHand(hand, ItemStack.EMPTY);
        player.getInventory().add(returned);
        if (!returned.isEmpty()) {
            player.drop(returned, false);
        }
        player.containerMenu.broadcastChanges();
    }
}
