package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.austizz.ultimatebankingsystem.client.DallasMaskAnimationClientState;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class DallasMaskAnimationLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final ItemInHandRenderer itemInHandRenderer;

    public DallasMaskAnimationLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
            ItemInHandRenderer itemInHandRenderer
    ) {
        super(parent);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        DallasMaskAnimationClientState.AnimationSample sample =
                DallasMaskAnimationClientState.sample(player.getUUID());
        if (sample == null || player.isInvisible()) {
            return;
        }

        float awayFromFace = 1.0F - sample.faceAmount();
        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        poseStack.translate(0.0F, 0.56F * awayFromFace, -0.16F * awayFromFace);
        CustomHeadLayer.translateToHead(poseStack, false);
        itemInHandRenderer.renderItem(
                player,
                new ItemStack(ModItems.DALLAS_MASK.get()),
                ItemDisplayContext.HEAD,
                false,
                poseStack,
                bufferSource,
                packedLight
        );
        poseStack.popPose();
    }
}
