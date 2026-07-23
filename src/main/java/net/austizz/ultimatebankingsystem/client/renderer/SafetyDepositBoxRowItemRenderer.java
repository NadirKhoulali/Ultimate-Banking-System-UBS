package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.client.model.SafetyDepositBoxRowBlockEntityModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class SafetyDepositBoxRowItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "textures/block/safety_deposit_box_row.png");

    private SafetyDepositBoxRowBlockEntityModel model;

    public SafetyDepositBoxRowItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void renderByItem(ItemStack stack,
                             ItemDisplayContext displayContext,
                             PoseStack poseStack,
                             MultiBufferSource bufferSource,
                             int packedLight,
                             int packedOverlay) {
        SafetyDepositBoxRowBlockEntityModel rowModel = getModel();
        poseStack.pushPose();
        applyItemTransform(displayContext, poseStack);
        rowModel.applyShellOnly();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entitySolid(TEXTURE));
        rowModel.renderToBuffer(poseStack, consumer, packedLight, packedOverlay, 0xFFFFFFFF);
        poseStack.popPose();
    }

    private SafetyDepositBoxRowBlockEntityModel getModel() {
        if (model == null) {
            model = new SafetyDepositBoxRowBlockEntityModel(
                    Minecraft.getInstance().getEntityModels().bakeLayer(SafetyDepositBoxRowBlockEntityModel.LAYER_LOCATION)
            );
        }
        return model;
    }

    private static void applyItemTransform(ItemDisplayContext displayContext, PoseStack poseStack) {
        poseStack.translate(0.5D, 0.5D, 0.5D);
        if (displayContext == ItemDisplayContext.GUI) {
            // Vanilla-block style isometric icon: 30-degree screen pitch, yawed so
            // the DOOR side (entrance) faces the camera. The shell center sits 16px
            // above the model origin, which lands at -13.86px after the flip+pitch —
            // the 0.87 translate recenters.
            poseStack.scale(0.58F, 0.58F, 0.58F);
            poseStack.translate(0.0D, 0.87D, 0.0D);
            poseStack.mulPose(Axis.XP.rotationDegrees(30.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            return;
        } else if (displayContext == ItemDisplayContext.GROUND) {
            poseStack.scale(0.34F, 0.34F, 0.34F);
        } else if (displayContext == ItemDisplayContext.FIXED) {
            poseStack.scale(0.52F, 0.52F, 0.52F);
        } else {
            poseStack.scale(0.44F, 0.44F, 0.44F);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
    }
}
