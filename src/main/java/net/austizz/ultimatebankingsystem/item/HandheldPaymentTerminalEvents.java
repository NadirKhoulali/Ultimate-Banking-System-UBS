package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class HandheldPaymentTerminalEvents {
    private HandheldPaymentTerminalEvents() {
    }

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer payer)) {
            return;
        }
        if (!(event.getTarget() instanceof net.minecraft.server.level.ServerPlayer merchant)) {
            return;
        }

        ItemStack merchantTerminal = HandheldPaymentTerminalItem.findHeldTerminal(merchant);
        if (merchantTerminal.isEmpty()) {
            return;
        }

        HandheldPaymentTerminalItem.processCharge(merchant, payer, merchantTerminal);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
