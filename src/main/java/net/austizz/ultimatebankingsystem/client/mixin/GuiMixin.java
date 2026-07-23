package net.austizz.ultimatebankingsystem.client.mixin;

import net.austizz.ultimatebankingsystem.client.ClaimModeClientState;
import net.austizz.ultimatebankingsystem.client.ClaimModeHudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ultimatebankingsystem$renderClaimModeHud(
            GuiGraphics graphics,
            DeltaTracker deltaTracker,
            CallbackInfo callbackInfo) {
        if (ClaimModeClientState.active() && ClaimModeHudRenderer.render(graphics)) {
            callbackInfo.cancel();
        }
    }
}
