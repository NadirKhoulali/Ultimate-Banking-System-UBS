package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.block.custom.ShopTerminalBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShopTerminalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class PaymentTerminalRenderer implements BlockEntityRenderer<ShopTerminalBlockEntity> {
    public PaymentTerminalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ShopTerminalBlockEntity blockEntity,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        int segment = state.hasProperty(ShopTerminalBlock.ROTATION)
                ? state.getValue(ShopTerminalBlock.ROTATION)
                : 0;

        // Force classic cardinal orientation (0/90/180/270) for clean texture rendering.
        int snapped90Segment = (Math.round(segment / 4.0F) * 4) & 15;
        float yaw = (snapped90Segment * 22.5F) % 360.0F;
        int result = Mth.clamp(blockEntity.getDisplayResult(), 0, 2);
        BlockState renderState = state.hasProperty(ShopTerminalBlock.RESULT)
                ? state.setValue(ShopTerminalBlock.RESULT, result)
                : state;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        blockRenderer.renderSingleBlock(renderState, poseStack, bufferSource, packedLight, packedOverlay);

        poseStack.popPose();
    }
}
