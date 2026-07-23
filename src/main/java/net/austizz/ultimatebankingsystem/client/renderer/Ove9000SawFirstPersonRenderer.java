package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.event.RenderHandEvent;

public final class Ove9000SawFirstPersonRenderer {
    private Ove9000SawFirstPersonRenderer() {
    }

    public static boolean render(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.level == null
                || !minecraft.player.getMainHandItem().is(ModItems.OVE9000_SAW.get())) {
            return false;
        }

        if (event.getHand() == InteractionHand.MAIN_HAND) {
            renderHeldSaw(minecraft, event);
        }
        return true;
    }

    private static void renderHeldSaw(Minecraft minecraft, RenderHandEvent event) {
        AbstractClientPlayer player = minecraft.player;
        if (!player.isInvisible()) {
            PlayerRenderer renderer = (PlayerRenderer) minecraft.getEntityRenderDispatcher().getRenderer(player);
            renderArm(renderer, player, event, HumanoidArm.LEFT);
            renderArm(renderer, player, event, HumanoidArm.RIGHT);
        }

        float swing = Mth.sin(Mth.sqrt(event.getSwingProgress()) * Mth.PI);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-0.03F * swing, -0.26F - event.getEquipProgress() * 0.55F, -0.78F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-8.0F - 5.0F * swing));
        poseStack.mulPose(Axis.YP.rotationDegrees(4.0F * swing));
        poseStack.scale(1.35F, 1.35F, 1.35F);
        minecraft.getItemRenderer().renderStatic(
                player.getMainHandItem(),
                ItemDisplayContext.NONE,
                event.getPackedLight(),
                OverlayTexture.NO_OVERLAY,
                poseStack,
                event.getMultiBufferSource(),
                minecraft.level,
                player.getId()
        );
        poseStack.popPose();
    }

    private static void renderArm(
            PlayerRenderer renderer,
            AbstractClientPlayer player,
            RenderHandEvent event,
            HumanoidArm arm
    ) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(92.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(50.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * -38.0F));
        poseStack.translate(side * 0.31F, -1.03F - event.getEquipProgress() * 0.25F, 0.36F);
        if (arm == HumanoidArm.RIGHT) {
            renderer.renderRightHand(poseStack, event.getMultiBufferSource(), event.getPackedLight(), player);
        } else {
            renderer.renderLeftHand(poseStack, event.getMultiBufferSource(), event.getPackedLight(), player);
        }
        poseStack.popPose();
    }
}
