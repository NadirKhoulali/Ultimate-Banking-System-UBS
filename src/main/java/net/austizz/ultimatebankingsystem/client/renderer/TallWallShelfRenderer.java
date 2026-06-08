package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.block.custom.TallWallShelfBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.ItemDisplayTransform;
import net.austizz.ultimatebankingsystem.block.entity.custom.TallWallShelfBlockEntity;
import net.austizz.ultimatebankingsystem.client.ShelfTransformPreviewClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class TallWallShelfRenderer implements BlockEntityRenderer<TallWallShelfBlockEntity> {
    private static final float[] SLOT_Y = new float[]{0.74F, 1.22F, 1.66F};

    public TallWallShelfRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TallWallShelfBlockEntity shelf,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        BlockState state = shelf.getBlockState();
        if (!state.hasProperty(TallWallShelfBlock.HALF)
                || state.getValue(TallWallShelfBlock.HALF) != DoubleBlockHalf.LOWER) {
            return;
        }

        float yaw = -state.getValue(TallWallShelfBlock.FACING).toYRot();

        for (int slot = 0; slot < TallWallShelfBlockEntity.SLOT_COUNT; slot++) {
            ItemStack stack = shelf.getDisplayItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemDisplayTransform transform = ShelfTransformPreviewClientState.resolve(
                    shelf.getLevel(),
                    shelf.getBlockPos(),
                    slot,
                    shelf.getSlotTransform(slot)
            );

            poseStack.pushPose();
            poseStack.translate(0.5D, SLOT_Y[slot], 0.5D);
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.translate(0.0D, 0.0D, 0.18D);
            poseStack.translate(transform.offsetX(), transform.offsetY(), transform.offsetZ());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F + transform.rotationY()));
            // Keep item's up-axis as world up (top remains on top), with a subtle upward-facing tilt.
            poseStack.mulPose(Axis.XP.rotationDegrees(12.0F + transform.rotationX()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(transform.rotationZ()));
            poseStack.scale(0.42F * transform.scaleX(), 0.42F * transform.scaleY(), 0.42F * transform.scaleZ());

            Minecraft.getInstance().getItemRenderer().renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    bufferSource,
                    shelf.getLevel(),
                    0
            );

            poseStack.popPose();
        }
    }
}
