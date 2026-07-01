package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.client.model.BankSafeIronBarGateBlockEntityModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class BankSafeIronBarGateItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "textures/block/bank_safe_iron_bar_gate.png");

    private BankSafeIronBarGateBlockEntityModel model;

    public BankSafeIronBarGateItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void renderByItem(ItemStack stack,
                             ItemDisplayContext displayContext,
                             PoseStack poseStack,
                             MultiBufferSource bufferSource,
                             int packedLight,
                             int packedOverlay) {
        BankSafeIronBarGateBlockEntityModel gateModel = getModel();
        poseStack.pushPose();
        applyItemTransform(displayContext, poseStack);
        gateModel.applyAnimation(0.0F);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entitySolid(TEXTURE));
        gateModel.renderToBuffer(poseStack, consumer, packedLight, packedOverlay, 0xFFFFFFFF);
        poseStack.popPose();
    }

    private BankSafeIronBarGateBlockEntityModel getModel() {
        if (model == null) {
            model = new BankSafeIronBarGateBlockEntityModel(
                    Minecraft.getInstance().getEntityModels().bakeLayer(BankSafeIronBarGateBlockEntityModel.LAYER_LOCATION)
            );
        }
        return model;
    }

    private static void applyItemTransform(ItemDisplayContext displayContext, PoseStack poseStack) {
        poseStack.translate(0.5D, 0.5D, 0.5D);
        if (displayContext == ItemDisplayContext.GUI) {
            poseStack.scale(0.21F, 0.21F, 0.21F);
            poseStack.translate(0.0D, 1.55D, 0.0D);
        } else if (displayContext == ItemDisplayContext.GROUND) {
            poseStack.scale(0.12F, 0.12F, 0.12F);
            poseStack.translate(0.0D, 1.45D, 0.0D);
        } else if (displayContext == ItemDisplayContext.FIXED) {
            poseStack.scale(0.18F, 0.18F, 0.18F);
            poseStack.translate(0.0D, 1.5D, 0.0D);
        } else {
            poseStack.scale(0.16F, 0.16F, 0.16F);
            poseStack.translate(0.0D, 1.5D, 0.0D);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
    }
}
