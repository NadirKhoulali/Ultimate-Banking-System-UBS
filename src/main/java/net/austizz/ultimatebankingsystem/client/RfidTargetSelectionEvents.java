package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID, value = Dist.CLIENT)
public final class RfidTargetSelectionEvents {
    private RfidTargetSelectionEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()
                || event.getHand() != InteractionHand.MAIN_HAND
                || !RfidTargetSelectionClientState.isActive()) {
            return;
        }
        PacketDistributor.sendToServer(RfidTargetSelectionClientState.buildPayload(
                event.getLevel().dimension().location().toString(),
                event.getPos(),
                event.getFace()
        ));
        RfidTargetSelectionClientState.finishSelection();
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
