package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.custom.BankSafeIronBarGateBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.BankSafeIronBarGateBlockEntity;
import net.austizz.ultimatebankingsystem.client.model.BankSafeIronBarGateBlockEntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class BankSafeIronBarGateRenderer implements BlockEntityRenderer<BankSafeIronBarGateBlockEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "textures/block/bank_safe_iron_bar_gate.png");
    private static final float MODEL_WORLD_SCALE = 80.0F / 68.0F;
    private static final double MODEL_GROUND_ANCHOR_Y = (50.0D / 16.0D) * MODEL_WORLD_SCALE;

    private final BankSafeIronBarGateBlockEntityModel model;

    public BankSafeIronBarGateRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new BankSafeIronBarGateBlockEntityModel(
                context.bakeLayer(BankSafeIronBarGateBlockEntityModel.LAYER_LOCATION)
        );
    }

    @Override
    public void render(BankSafeIronBarGateBlockEntity gate,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        if (gate == null) {
            return;
        }
        BlockState state = gate.getBlockState();
        Direction facing = state.hasProperty(BankSafeIronBarGateBlock.FACING)
                ? state.getValue(BankSafeIronBarGateBlock.FACING)
                : Direction.NORTH;

        poseStack.pushPose();
        poseStack.translate(0.5D, MODEL_GROUND_ANCHOR_Y, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.scale(MODEL_WORLD_SCALE, MODEL_WORLD_SCALE, MODEL_WORLD_SCALE);

        model.applyAnimation(gate.getAnimationProgress(partialTick));
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entitySolid(TEXTURE));
        model.renderToBuffer(poseStack, consumer, packedLight, packedOverlay, 0xFFFFFFFF);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(BankSafeIronBarGateBlockEntity gate) {
        return gate == null ? AABB.INFINITE : gate.getRenderBoundingBox();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
