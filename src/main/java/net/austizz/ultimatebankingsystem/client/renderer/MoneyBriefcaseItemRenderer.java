package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * BEWLR for the money briefcase item: renders the CLOSED case via the shared
 * {@link MoneyBriefcaseRenderer#drawCase} used by the block entity renderer.
 */
public class MoneyBriefcaseItemRenderer extends BlockEntityWithoutLevelRenderer {
    // The drawn case spans x 0.5..15.5, y 0..5.5, z 2.5..13.5 model px; its
    // vertical center sits at 2.75px, used to recenter after the transforms.
    private static final double CASE_CENTER_Y = 2.75D / 16.0D;

    public MoneyBriefcaseItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void renderByItem(ItemStack stack,
                             ItemDisplayContext displayContext,
                             PoseStack poseStack,
                             MultiBufferSource bufferSource,
                             int packedLight,
                             int packedOverlay) {
        poseStack.pushPose();
        applyItemTransform(displayContext, poseStack);
        MoneyBriefcaseRenderer.drawCase(poseStack, bufferSource, 0.0F, packedLight, packedOverlay);
        poseStack.popPose();
    }

    // drawCase draws y-up block-space geometry (shulker-BEWLR style), so NO
    // Axis.XP 180 flip is needed in any display context -- flipping would turn
    // the case upside down. Only per-context yaw/pitch/scale below.
    private static void applyItemTransform(ItemDisplayContext displayContext, PoseStack poseStack) {
        poseStack.translate(0.5D, 0.5D, 0.5D);
        if (displayContext == ItemDisplayContext.GUI) {
            // Yaw 200 shows the FRONT (latch/handle, -z) face with a sliver of
            // the east side; pitch 30 tips the lid top into view. Projected
            // width: 15*cos20 + 11*sin20 ~ 17.9px, so 0.8 keeps it ~14.3px in
            // the 16px slot without clipping. Stays centered (no extra
            // translation -- GUI translation clamp).
            poseStack.scale(0.8F, 0.8F, 0.8F);
            poseStack.mulPose(Axis.XP.rotationDegrees(30.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(200.0F));
        } else if (displayContext == ItemDisplayContext.GROUND) {
            // Dropped item: flat on the ground plane; the item entity supplies
            // the spin, so no rotation of our own.
            poseStack.scale(0.5F, 0.5F, 0.5F);
        } else if (displayContext == ItemDisplayContext.FIXED) {
            // Item frame: turn the front (latch, -z) face outward.
            poseStack.scale(0.85F, 0.85F, 0.85F);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        } else {
            // Hands: carried flat with a slight yaw so the latch face angles
            // toward the camera.
            poseStack.scale(0.6F, 0.6F, 0.6F);
            poseStack.mulPose(Axis.YP.rotationDegrees(160.0F));
        }
        // Recenter the block-space case geometry on the transformed origin.
        poseStack.translate(-0.5D, -CASE_CENTER_Y, -0.5D);
    }
}
