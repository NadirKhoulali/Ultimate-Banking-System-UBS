package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.ShopSellingTableBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.ItemDisplayTransform;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShopSellingTableBlockEntity;
import net.austizz.ultimatebankingsystem.client.ShelfTransformPreviewClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class ShopSellingTableRenderer implements BlockEntityRenderer<ShopSellingTableBlockEntity> {
    private static final float NORMAL_SCALE = 1.02F;
    private static final float LARGE_SCALE = 1.72F;
    private static final float INVISIBLE_SMALL_SCALE = 0.78F;
    private static final float INVISIBLE_MEDIUM_SCALE = 1.02F;
    private static final float INVISIBLE_LARGE_SCALE = 1.34F;
    private static final float SPIN_DEGREES_PER_TICK = 1.1F;

    public ShopSellingTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ShopSellingTableBlockEntity table,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        ItemStack stack = table.getDisplayItem(0);
        if (stack.isEmpty()) {
            return;
        }
        ItemDisplayTransform transform = ShelfTransformPreviewClientState.resolve(
                table.getLevel(),
                table.getBlockPos(),
                0,
                table.getSlotTransform(0)
        );

        BlockState state = table.getBlockState();
        boolean largeTable = state.is(ModBlocks.SHOP_SELLING_TABLE_LARGE.get())
                || state.is(ModBlocks.CREATIVE_SHOP_SELLING_TABLE_LARGE.get());
        boolean invisibleSmall = state.is(ModBlocks.INVISIBLE_DISPLAY_SMALL.get())
                || state.is(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_SMALL.get());
        boolean invisibleMedium = state.is(ModBlocks.INVISIBLE_DISPLAY_MEDIUM.get())
                || state.is(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_MEDIUM.get());
        boolean invisibleLarge = state.is(ModBlocks.INVISIBLE_DISPLAY_LARGE.get())
                || state.is(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_LARGE.get());
        boolean invisibleDisplay = invisibleSmall || invisibleMedium || invisibleLarge;

        float yaw = 180.0F;
        if (state.hasProperty(ShopSellingTableBlock.FACING)) {
            yaw += -state.getValue(ShopSellingTableBlock.FACING).toYRot();
        }
        if (table.isSpinEnabled()) {
            long gameTime = table.getLevel() == null ? 0L : table.getLevel().getGameTime();
            yaw += (gameTime + partialTick) * SPIN_DEGREES_PER_TICK;
        }

        poseStack.pushPose();
        if (largeTable) {
            // 2x2 footprint is {master, east, north, north-east}; center is (1.0, 0.0) from master origin.
            poseStack.translate(1.0D, 1.62D, 0.0D);
        } else if (invisibleDisplay) {
            // Invisible displays have no physical top surface; render item centered in the display block volume.
            poseStack.translate(0.5D, 0.92D, 0.5D);
        } else {
            // Raise item so full blocks are clearly visible above the tabletop.
            poseStack.translate(0.5D, 1.34D, 0.5D);
        }
        poseStack.translate(transform.offsetX(), transform.offsetY(), transform.offsetZ());
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + transform.rotationY()));
        poseStack.mulPose(Axis.XP.rotationDegrees(transform.rotationX()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(transform.rotationZ()));
        float baseScale;
        if (largeTable) {
            baseScale = LARGE_SCALE;
        } else if (invisibleSmall) {
            baseScale = INVISIBLE_SMALL_SCALE;
        } else if (invisibleLarge) {
            baseScale = INVISIBLE_LARGE_SCALE;
        } else if (invisibleMedium) {
            baseScale = INVISIBLE_MEDIUM_SCALE;
        } else {
            baseScale = NORMAL_SCALE;
        }
        poseStack.scale(
                baseScale * transform.scaleX(),
                baseScale * transform.scaleY(),
                baseScale * transform.scaleZ()
        );

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                table.getLevel(),
                0
        );
        poseStack.popPose();
    }
}
