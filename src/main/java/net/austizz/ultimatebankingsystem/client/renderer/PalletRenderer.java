package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.PalletBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.PalletBlockEntity;
import net.austizz.ultimatebankingsystem.client.DeliveryPalletLabelsClientState;
import net.austizz.ultimatebankingsystem.client.UbsClientTranslations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class PalletRenderer implements BlockEntityRenderer<PalletBlockEntity> {
    private static final float DELIVERY_LABEL_SCALE = 0.025F;
    private static final float DELIVERY_LABEL_MIN_Y_OFFSET = 3.35F;
    private static final float PALLET_BOX_BASE_Y = 0.5625F;
    private static final float PALLET_BOX_HEIGHT = 0.75F;
    private static final float DELIVERY_LABEL_TOP_PADDING = 0.50F;
    private static final int FULL_BRIGHT = 0x00F000F0;

    public PalletRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PalletBlockEntity pallet,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        BlockState state = pallet.getBlockState();
        boolean centerPart = state.hasProperty(PalletBlock.PART_X)
                && state.hasProperty(PalletBlock.PART_Z)
                && state.getValue(PalletBlock.PART_X) == 1
                && state.getValue(PalletBlock.PART_Z) == 1;

        if (centerPart) {
            for (int column = 0; column < PalletBlockEntity.COLUMNS; column++) {
                int colX = column % 3;
                int colZ = column / 3;
                float xOffset = (float) (colX - 1);
                float zOffset = (float) (colZ - 1);

                for (int layer = 0; layer < PalletBlockEntity.LAYERS; layer++) {
                    ItemStack stack = pallet.getBox(column, layer);
                    if (stack == null || stack.isEmpty()) {
                        continue;
                    }

                    // Render full-size cardboard-box block models on pallet cells.
                    double y = 0.5625D + (0.75D * layer);
                    poseStack.pushPose();
                    poseStack.translate(xOffset, y, zOffset);
                    Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                            ModBlocks.CARDBOARD_BOX.get().defaultBlockState(),
                            poseStack,
                            bufferSource,
                            packedLight,
                            packedOverlay,
                            net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
                            null
                    );
                    poseStack.popPose();
                }
            }
        }
    }

    private void drawDeliveryLabel(PalletBlockEntity pallet,
                                   PoseStack poseStack,
                                   MultiBufferSource bufferSource) {
        Minecraft minecraft = Minecraft.getInstance();
        DeliveryLabel label = resolveDeliveryLabel(minecraft, pallet);
        if (label == null) {
            return;
        }
        if (minecraft == null || minecraft.font == null || minecraft.getEntityRenderDispatcher() == null) {
            return;
        }
        Font font = minecraft.font;
        String shopName = label.shopName();
        if (shopName == null || shopName.isBlank()) {
            shopName = "Shop";
        }
        BlockPos pos = label.pos();

        List<String> lines = new ArrayList<>(3);
        lines.add(UbsClientTranslations.resolve("Delivery Pallet"));
        lines.add(shopName.trim());
        lines.add("(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");

        poseStack.pushPose();
        poseStack.translate(0.5D, computeDeliveryLabelYOffset(pallet), 0.5D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-DELIVERY_LABEL_SCALE, -DELIVERY_LABEL_SCALE, DELIVERY_LABEL_SCALE);
        Matrix4f matrix = poseStack.last().pose();

        int bgAlpha = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        float y = 0.0F;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color = i == 0 ? 0xFF74D78A : (i == 1 ? 0xFFEAF6FF : 0xFFCFE0F2);
            float x = -font.width(line) / 2.0F;
            drawHologramLine(font, line, x, y, color, matrix, bufferSource, bgAlpha);
            y += 10.0F;
        }
        poseStack.popPose();
    }

    private DeliveryLabel resolveDeliveryLabel(Minecraft minecraft, PalletBlockEntity pallet) {
        if (pallet == null) {
            return null;
        }
        BlockPos pos = pallet.getBlockPos();
        if (minecraft != null && minecraft.level != null) {
            String dimensionId = minecraft.level.dimension().location().toString();
            DeliveryPalletLabelsClientState.Label synced = DeliveryPalletLabelsClientState.getLabel(dimensionId, pos);
            if (synced != null) {
                // The level-render pass owns synced delivery labels; avoid drawing the same text twice.
                return null;
            }
        }
        if (pallet.hasDeliveryLabel()) {
            return new DeliveryLabel(pallet.getDeliveryLabelShopName(), pos);
        }
        return null;
    }

    private float computeDeliveryLabelYOffset(PalletBlockEntity pallet) {
        int highestLayer = -1;
        if (pallet != null) {
            for (int column = 0; column < PalletBlockEntity.COLUMNS; column++) {
                for (int layer = 0; layer < PalletBlockEntity.LAYERS; layer++) {
                    ItemStack stack = pallet.getBox(column, layer);
                    if (stack != null && !stack.isEmpty()) {
                        highestLayer = Math.max(highestLayer, layer);
                    }
                }
            }
        }
        if (highestLayer < 0) {
            return DELIVERY_LABEL_MIN_Y_OFFSET;
        }
        float aboveBoxes = PALLET_BOX_BASE_Y + (PALLET_BOX_HEIGHT * (highestLayer + 1)) + DELIVERY_LABEL_TOP_PADDING;
        return Math.max(DELIVERY_LABEL_MIN_Y_OFFSET, aboveBoxes);
    }

    private void drawHologramLine(Font font,
                                  String line,
                                  float x,
                                  float y,
                                  int color,
                                  Matrix4f matrix,
                                  MultiBufferSource bufferSource,
                                  int bgAlpha) {
        font.drawInBatch(line, x, y, color, false, matrix, bufferSource,
                Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
    }

    @Override
    public AABB getRenderBoundingBox(PalletBlockEntity pallet) {
        return pallet == null ? AABB.INFINITE : pallet.getRenderBoundingBox();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    private record DeliveryLabel(String shopName, BlockPos pos) {
    }
}
