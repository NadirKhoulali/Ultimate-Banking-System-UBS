package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.client.SafeBoxDisplayClientState;
import net.austizz.ultimatebankingsystem.client.model.SafetyDepositBoxTrayModel;
import net.austizz.ultimatebankingsystem.entity.custom.SafetyDepositBoxDisplayProxyEntity;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class SafetyDepositBoxDisplayProxyRenderer
        extends EntityRenderer<SafetyDepositBoxDisplayProxyEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/block/safety_deposit_box_row.png");

    private final SafetyDepositBoxTrayModel trayModel;
    private final ItemRenderer itemRenderer;

    public SafetyDepositBoxDisplayProxyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.trayModel = new SafetyDepositBoxTrayModel(
                context.bakeLayer(SafetyDepositBoxTrayModel.LAYER_LOCATION));
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.42F;
    }

    @Override
    public void render(SafetyDepositBoxDisplayProxyEntity entity,
                       float entityYaw,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight) {
        SafetyDepositBoxRowBlockEntity.ModuleType moduleType = entity.getModuleType();
        poseStack.pushPose();
        poseStack.translate(0.0D, SafetyDepositBoxTrayModel.trayHeight(moduleType) / 32.0D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        trayModel.setModuleType(moduleType);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entitySolid(TEXTURE));
        trayModel.renderToBuffer(poseStack, consumer, packedLight,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        poseStack.popPose();
        renderContents(entity, moduleType, poseStack, bufferSource, packedLight);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SafetyDepositBoxDisplayProxyEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private void renderContents(SafetyDepositBoxDisplayProxyEntity entity,
                                SafetyDepositBoxRowBlockEntity.ModuleType moduleType,
                                PoseStack poseStack,
                                MultiBufferSource bufferSource,
                                int packedLight) {
        List<ItemStack> slots = SafeBoxDisplayClientState.contents(entity.getUUID());
        if (slots.isEmpty()) {
            return;
        }
        int slotLimit = Math.min(moduleType.inventorySlots(), slots.size());
        double trayHeight = SafetyDepositBoxTrayModel.trayHeight(moduleType) / 16.0D;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));
        for (int slot = 0; slot < slotLimit; slot++) {
            ItemStack stack = slots.get(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            SafeBoxDisplayLayout.Position position = SafeBoxDisplayLayout.position(
                    slot, moduleType.inventorySlots(), trayHeight);

            poseStack.pushPose();
            poseStack.translate(position.x(), position.y(), position.z());
            poseStack.mulPose(Axis.YP.rotationDegrees(((slot * 7) % 9) - 4.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(84.0F));
            poseStack.scale(0.17F, 0.17F, 0.17F);
            itemRenderer.renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    poseStack,
                    bufferSource,
                    entity.level(),
                    entity.getId() + slot
            );
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}
