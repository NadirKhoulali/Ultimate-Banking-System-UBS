package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.client.DallasMaskAnimationClientState;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;

public final class DallasMaskFirstPersonRenderer {
    private DallasMaskFirstPersonRenderer() {
    }

    public static boolean render(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return false;
        }

        DallasMaskAnimationClientState.AnimationSample sample =
                DallasMaskAnimationClientState.sample(minecraft.player.getUUID());
        if (sample == null) {
            return false;
        }
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            renderAnimation(minecraft, event, sample);
        }
        return true;
    }

    private static void renderAnimation(
            Minecraft minecraft,
            RenderHandEvent event,
            DallasMaskAnimationClientState.AnimationSample sample
    ) {
        if (minecraft.player.isInvisible()) {
            return;
        }

        PlayerRenderer playerRenderer = (PlayerRenderer) minecraft.getEntityRenderDispatcher()
                .getRenderer(minecraft.player);
        renderArm(playerRenderer, minecraft.player, event, HumanoidArm.LEFT, sample);
        renderArm(playerRenderer, minecraft.player, event, HumanoidArm.RIGHT, sample);

        float face = sample.faceAmount();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.62F - 0.60F * face, -0.92F + 0.57F * face);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(24.0F * (1.0F - face)));
        float scale = 1.05F + 0.55F * face;
        poseStack.scale(scale, scale, scale);
        minecraft.getItemRenderer().renderStatic(
                new ItemStack(ModItems.DALLAS_MASK.get()),
                ItemDisplayContext.NONE,
                event.getPackedLight(),
                OverlayTexture.NO_OVERLAY,
                poseStack,
                event.getMultiBufferSource(),
                minecraft.level,
                minecraft.player.getId()
        );
        poseStack.popPose();
    }

    private static void renderArm(
            PlayerRenderer renderer,
            AbstractClientPlayer player,
            RenderHandEvent event,
            HumanoidArm arm,
            DallasMaskAnimationClientState.AnimationSample sample
    ) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float reach = sample.reachAmount();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(92.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(48.0F - 8.0F * reach));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * (-43.0F + 7.0F * reach)));
        poseStack.translate(
                side * (0.31F - 0.07F * reach),
                -1.12F + 0.22F * reach,
                0.47F - 0.19F * reach
        );
        if (arm == HumanoidArm.RIGHT) {
            renderer.renderRightHand(poseStack, event.getMultiBufferSource(), event.getPackedLight(), player);
        } else {
            renderer.renderLeftHand(poseStack, event.getMultiBufferSource(), event.getPackedLight(), player);
        }
        poseStack.popPose();
    }
}
