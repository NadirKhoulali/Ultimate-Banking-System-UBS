package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.custom.SafetyDepositBoxRowBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity.ModuleType;
import net.austizz.ultimatebankingsystem.client.model.SafetyDepositBoxRowBlockEntityModel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

public class SafetyDepositBoxRowRenderer implements BlockEntityRenderer<SafetyDepositBoxRowBlockEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "textures/block/safety_deposit_box_row.png");
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);
    private static final float DOOR_SWING_DEGREES = -92.0F;
    private static final float ROW_TOP = -14.7F;
    private static final float ROW_PITCH = 3.55F;
    private static final float SMALL_DOOR_HEIGHT = 3.04F;
    private static final float DOOR_HINGE_X = 7.22F;
    private static final float DOOR_HINGE_Z = -7.45F;
    private static final float LABEL_LOCAL_X = -7.22F;
    private static final float LABEL_LOCAL_Z = -1.74F;
    private static final float LABEL_MAX_WIDTH = 0.55F;
    private static final float LABEL_MAX_SCALE = 0.0105F;
    private static final float LABEL_MIN_SCALE = 0.006F;
    private static final int LABEL_LIGHT = 0x00F000F0;

    private final SafetyDepositBoxRowBlockEntityModel model;
    private final Font font;

    public SafetyDepositBoxRowRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new SafetyDepositBoxRowBlockEntityModel(
                context.bakeLayer(SafetyDepositBoxRowBlockEntityModel.LAYER_LOCATION)
        );
        this.font = context.getFont();
    }

    @Override
    public void render(SafetyDepositBoxRowBlockEntity row,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        if (row == null) {
            return;
        }
        BlockState state = row.getBlockState();
        Direction facing = state.hasProperty(SafetyDepositBoxRowBlock.FACING)
                ? state.getValue(SafetyDepositBoxRowBlock.FACING)
                : Direction.NORTH;

        poseStack.pushPose();
        poseStack.translate(0.5D, 1.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));

        float[] progress = new float[SafetyDepositBoxRowBlockEntity.DOOR_COUNT];
        for (int i = 0; i < progress.length; i++) {
            progress[i] = row.getDoorProgress(i, partialTick);
        }
        model.applyState(row.getModuleTypesSnapshot(), progress);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entitySolid(TEXTURE));
        model.renderToBuffer(poseStack, consumer, packedLight, packedOverlay, 0xFFFFFFFF);
        renderBoxNumberLabels(row, progress, poseStack, bufferSource);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(SafetyDepositBoxRowBlockEntity row) {
        return row == null ? AABB.INFINITE : row.getRenderBoundingBox();
    }

    private void renderBoxNumberLabels(SafetyDepositBoxRowBlockEntity row,
                                       float[] progress,
                                       PoseStack poseStack,
                                       MultiBufferSource bufferSource) {
        if (font == null || row == null) {
            return;
        }
        ModuleType[] modules = row.getModuleTypesSnapshot();
        poseStack.pushPose();
        poseStack.translate(0.0F, 24.0F / 16.0F, 0.0F);
        for (int start = 0; start < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; start++) {
            ModuleType type = start >= modules.length || modules[start] == null ? ModuleType.EMPTY : modules[start];
            if (!type.assignable() || !row.isAssignableBoxStart(start) || !row.isAssigned(start)) {
                continue;
            }
            renderBoxNumberLabel(row.getBoxNumber(start), type, start, progress[start], poseStack, bufferSource);
        }
        poseStack.popPose();
    }

    private void renderBoxNumberLabel(String boxNumber,
                                      ModuleType type,
                                      int start,
                                      float progress,
                                      PoseStack poseStack,
                                      MultiBufferSource bufferSource) {
        String label = fitLabel(boxNumber);
        if (label.isBlank()) {
            return;
        }
        float height = moduleHeight(type);
        float yCenter = rowTop(start) + height * 0.5F;
        float swing = smooth(Mth.clamp((progress - 0.10F) / 0.90F, 0.0F, 1.0F));
        float scale = labelScale(label);
        float textX = -font.width(label) * 0.5F;
        float textY = -font.lineHeight * 0.5F;

        poseStack.pushPose();
        poseStack.translate(DOOR_HINGE_X / 16.0F, yCenter / 16.0F, DOOR_HINGE_Z / 16.0F);
        poseStack.mulPose(Axis.YP.rotation(DOOR_SWING_DEGREES * swing * DEG_TO_RAD));
        poseStack.translate(LABEL_LOCAL_X / 16.0F, 0.0F, LABEL_LOCAL_Z / 16.0F);
        poseStack.scale(scale, scale, scale);
        Matrix4f matrix = poseStack.last().pose();
        font.drawInBatch(label, textX, textY, 0xFFEAF6FF, false, matrix, bufferSource,
                Font.DisplayMode.NORMAL, 0x66000000, LABEL_LIGHT);
        poseStack.popPose();
    }

    private float labelScale(String label) {
        int width = Math.max(1, font.width(label));
        return Mth.clamp(LABEL_MAX_WIDTH / width, LABEL_MIN_SCALE, LABEL_MAX_SCALE);
    }

    private static String fitLabel(String boxNumber) {
        if (boxNumber == null) {
            return "";
        }
        String clean = boxNumber.trim();
        if (clean.isEmpty() || clean.equalsIgnoreCase("Unassigned")) {
            return "";
        }
        return clean;
    }

    private static float rowTop(int start) {
        return ROW_TOP + start * ROW_PITCH;
    }

    private static float moduleHeight(ModuleType type) {
        return SMALL_DOOR_HEIGHT + (Math.max(1, type.rowSpan()) - 1) * ROW_PITCH;
    }

    private static float smooth(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
