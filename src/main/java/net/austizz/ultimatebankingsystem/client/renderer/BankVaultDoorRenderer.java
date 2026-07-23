package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.BankVaultDoorBlock;
import net.austizz.ultimatebankingsystem.block.custom.HeistDrillBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.BankVaultDoorBlockEntity;
import net.austizz.ultimatebankingsystem.client.model.BankVaultDoorBlockEntityModel;
import net.austizz.ultimatebankingsystem.heist.HeistDrillGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class BankVaultDoorRenderer implements BlockEntityRenderer<BankVaultDoorBlockEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "textures/block/bank_vault_door.png");
    private static final float MODEL_WORLD_SCALE = 80.0F / 68.0F;
    private static final double MODEL_GROUND_ANCHOR_Y = 1.625D * MODEL_WORLD_SCALE;

    private final BankVaultDoorBlockEntityModel model;

    public BankVaultDoorRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new BankVaultDoorBlockEntityModel(context.bakeLayer(BankVaultDoorBlockEntityModel.LAYER_LOCATION));
    }

    @Override
    public void render(BankVaultDoorBlockEntity vault,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        if (vault == null) {
            return;
        }
        BlockState state = vault.getBlockState();
        Direction facing = state.hasProperty(BankVaultDoorBlock.FACING)
                ? state.getValue(BankVaultDoorBlock.FACING)
                : Direction.NORTH;

        poseStack.pushPose();
        poseStack.translate(0.5D, MODEL_GROUND_ANCHOR_Y, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.scale(MODEL_WORLD_SCALE, MODEL_WORLD_SCALE, MODEL_WORLD_SCALE);

        model.applyAnimation(vault.getAnimationProgress(partialTick));
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entitySolid(TEXTURE));
        model.renderToBuffer(poseStack, consumer, packedLight, packedOverlay, 0xFFFFFFFF);
        poseStack.popPose();

        if (vault.isHeistDrillAttached()) {
            renderAttachedDrill(facing, poseStack, bufferSource, packedLight, packedOverlay);
        }
    }

    @Override
    public AABB getRenderBoundingBox(BankVaultDoorBlockEntity vault) {
        return vault == null ? AABB.INFINITE : vault.getRenderBoundingBox();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    private static void renderAttachedDrill(Direction facing,
                                            PoseStack poseStack,
                                            MultiBufferSource bufferSource,
                                            int packedLight,
                                            int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(HeistDrillGeometry.rotationFor(facing)));
        // Keep the drill tip in the slab while leaving the motor, display, and
        // four stabilizer rods fully in front of the moving door face.
        poseStack.translate(-0.5D, 1.12D, HeistDrillGeometry.VAULT_DOOR_FACE_OFFSET);
        BlockState drill = ModBlocks.THERMAL_DRILL.get().defaultBlockState()
                .setValue(HeistDrillBlock.FACING, Direction.NORTH)
                .setValue(HeistDrillBlock.MOUNT, HeistDrillBlock.Mount.TARGET);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                drill, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

}
