package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShoppingBasketBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ShoppingBasketRenderer implements BlockEntityRenderer<ShoppingBasketBlockEntity> {
    public ShoppingBasketRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ShoppingBasketBlockEntity basket,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        List<ItemStack> stacks = basket.getRenderStacks(12);
        if (stacks.isEmpty()) {
            return;
        }

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            int layer = i / 8;
            int local = i % 8;
            int col = local % 4;
            int row = local / 4;

            double xOffset = -0.20D + (col * 0.13D);
            double zOffset = -0.13D + (row * 0.26D);
            double yOffset = 0.17D + (layer * 0.15D);
            float yaw = 12.0F + (i * 17.0F);

            poseStack.pushPose();
            poseStack.translate(0.5D + xOffset, yOffset, 0.5D + zOffset);
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(82.0F));
            poseStack.scale(0.24F, 0.24F, 0.24F);

            Minecraft.getInstance().getItemRenderer().renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    bufferSource,
                    basket.getLevel(),
                    0
            );

            poseStack.popPose();
        }
    }
}
