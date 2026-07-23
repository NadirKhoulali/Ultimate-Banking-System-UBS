package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.block.custom.GlassCounterDisplayBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.GlassCounterDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ItemDisplayTransform;
import net.austizz.ultimatebankingsystem.client.ShelfTransformPreviewClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class GlassCounterDisplayRenderer implements BlockEntityRenderer<GlassCounterDisplayBlockEntity> {
    // Centers of the 4 glass panels in model-space (5.5, 10.5, 15.5, 20.5) / 16.
    private static final float[] SHELF_Y = new float[]{0.38375F, 0.69625F, 1.00875F, 1.32125F};
    private static final float SLOT_X = 0.0F;

    public GlassCounterDisplayRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(GlassCounterDisplayBlockEntity counter,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        BlockState state = counter.getBlockState();
        float yaw = -state.getValue(GlassCounterDisplayBlock.FACING).toYRot();

        for (int row = 0; row < GlassCounterDisplayBlockEntity.SHELF_ROWS; row++) {
            int slot = row;
            ItemStack stack = counter.getDisplayItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemDisplayTransform transform = ShelfTransformPreviewClientState.resolve(
                    counter.getLevel(),
                    counter.getBlockPos(),
                    slot,
                    counter.getSlotTransform(slot)
            );

            poseStack.pushPose();
            poseStack.translate(0.5D, SHELF_Y[row], 0.5D);
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.translate(SLOT_X, 0.0D, 0.0D);
            poseStack.translate(transform.offsetX(), transform.offsetY(), transform.offsetZ());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F + transform.rotationY()));
            poseStack.mulPose(Axis.XP.rotationDegrees(transform.rotationX()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(transform.rotationZ()));
            poseStack.scale(0.34F * transform.scaleX(), 0.34F * transform.scaleY(), 0.34F * transform.scaleZ());

            Minecraft.getInstance().getItemRenderer().renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    bufferSource,
                    counter.getLevel(),
                    0
            );

            poseStack.popPose();
        }
    }
}
