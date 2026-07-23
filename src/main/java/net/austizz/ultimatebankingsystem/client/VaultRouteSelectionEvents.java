package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteFace;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRoutePosition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID, value = Dist.CLIENT)
public final class VaultRouteSelectionEvents {
    private VaultRouteSelectionEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()
                || event.getHand() != InteractionHand.MAIN_HAND
                || VaultRouteEditorClientState.pickerMode()
                == VaultRouteEditorClientState.PickerMode.NONE) {
            return;
        }

        BlockPos position = event.getPos();
        SafeTellerRouteFace face = event.getFace() == null
                ? SafeTellerRouteFace.UP
                : SafeTellerRouteFace.valueOf(event.getFace().name());
        VaultRoutePickerHandshake.CaptureIntent capture = VaultRoutePickerHandshake.capture(
                event.getLevel().dimension().location().toString(),
                new OwnerPcVaultRoutePosition(position.getX(), position.getY(), position.getZ()),
                face);
        if (capture.reopenPayload() != null) {
            PacketDistributor.sendToServer(capture.reopenPayload());
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
