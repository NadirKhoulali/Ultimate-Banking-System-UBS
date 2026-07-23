package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.custom.MoneyBriefcaseBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.MoneyBriefcaseBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Fully procedural briefcase renderer: black pebbled leather shell with gold
 * latches, a lid hinged along the back top edge, and the stored money-stack
 * bundles laid out in a fixed 5x2x2 grid while the case is open.
 */
public class MoneyBriefcaseRenderer implements BlockEntityRenderer<MoneyBriefcaseBlockEntity> {
    private static final ResourceLocation LEATHER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "textures/block/briefcase_leather.png");
    private static final ResourceLocation GOLD_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "textures/block/safe_gold.png");

    // Lid opens up-and-back about the hinge on the back top edge (y=3.5, z=13.5).
    // Positive XP rotation lifts the front edge (local z below the hinge) upward.
    private static final float LID_OPEN_DEGREES = 105.0F;
    private static final float LID_HINGE_Y = 3.5F;
    private static final float LID_HINGE_Z = 13.5F;

    // Fixed interior money grid (model px): 5 across (x) x 2 deep (z) x 2 layers (y).
    // Case interior: x 1.3..14.7, z 3.3..12.7, floor top y=0.8, rim top y=3.5.
    private static final double[] GRID_X_CENTERS = {2.9D, 5.45D, 8.0D, 10.55D, 13.1D};
    private static final double[] GRID_Z_CENTERS = {5.6D, 10.4D};
    // Bundle CENTER heights: floor 0.8 + half the 1.0px rendered height, then
    // one rendered-height step per layer (centers 1.3 and 2.3, tops 1.8/2.8).
    private static final double GRID_BASE_Y = 1.3D;
    private static final double GRID_LAYER_STEP_Y = 1.0D;
    // Bundle model geometry: 12px long (model x), 2px tall (y), 5px wide (z).
    // Each bundle is turned 90 degrees about YP, so MODEL x maps to WORLD z and
    // MODEL z maps to WORLD x: the model-x scale controls world-z depth and the
    // model-z scale controls world-x width. Final world-space footprint per
    // bundle: 5 * 0.51 = 2.55px wide (x), 12 * (4.55/12) = 4.55px deep (z),
    // 2 * 0.5 = 1.0px tall (y). Grid extents: x 1.625..14.375 (inside 1.3..14.7),
    // z rows 3.325..7.875 and 8.125..12.675 (inside 3.3..12.7), y 0.8..2.8
    // (below the 3.5 rim).
    private static final float BUNDLE_SCALE_MODEL_X = 4.55F / 12.0F;
    private static final float BUNDLE_SCALE_MODEL_Y = 0.5F;
    private static final float BUNDLE_SCALE_MODEL_Z = 2.55F / 5.0F;

    public MoneyBriefcaseRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MoneyBriefcaseBlockEntity briefcase,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        if (briefcase == null) {
            return;
        }

        BlockState state = briefcase.getBlockState();
        Direction facing = state.hasProperty(MoneyBriefcaseBlock.FACING)
                ? state.getValue(MoneyBriefcaseBlock.FACING)
                : Direction.NORTH;
        float progress = briefcase.getAnimationProgress(partialTick);

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationFor(facing)));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        drawCase(poseStack, bufferSource, progress, packedLight, packedOverlay);
        if (progress > 0.4F) {
            renderStoredBundles(briefcase, poseStack, bufferSource, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(MoneyBriefcaseBlockEntity briefcase) {
        if (briefcase == null) {
            return AABB.INFINITE;
        }
        // Expanded one block up so the open lid never clips out of view.
        return new AABB(
                briefcase.getBlockPos().getX() - 1.0D,
                briefcase.getBlockPos().getY(),
                briefcase.getBlockPos().getZ() - 1.0D,
                briefcase.getBlockPos().getX() + 2.0D,
                briefcase.getBlockPos().getY() + 2.0D,
                briefcase.getBlockPos().getZ() + 2.0D
        );
    }

    /**
     * Draws the whole case (base shell, handle, latches, lid) in model space
     * [0..1]. Shared with {@link MoneyBriefcaseItemRenderer}, which calls it
     * with progress 0 for the closed in-inventory look.
     */
    public static void drawCase(PoseStack poseStack,
                                MultiBufferSource bufferSource,
                                float progress,
                                int packedLight,
                                int packedOverlay) {
        // All geometry of one texture is drawn before the next buffer is
        // fetched: non-fixed render types share the BufferSource's single
        // fallback builder, so requesting the gold buffer ENDS the leather
        // batch -- writing to a stale consumer crashes with "Not building!"
        // (same phase-ordered discipline as SecureSafeRenderer).
        float lidAngle = LID_OPEN_DEGREES * smooth(progress);

        VertexConsumer leather = bufferSource.getBuffer(RenderType.entityCutoutNoCull(LEATHER_TEXTURE));

        // Base shell: floor + 4 walls (0.8px thick, interior floor at y=0.8).
        box(poseStack, leather, 0.5F, 0.0F, 2.5F, 15.5F, 0.8F, 13.5F, packedLight, packedOverlay, 0xFFF0F0F0);
        box(poseStack, leather, 0.5F, 0.8F, 2.5F, 15.5F, 3.5F, 3.3F, packedLight, packedOverlay, 0xFFEAEAEA);
        box(poseStack, leather, 0.5F, 0.8F, 12.7F, 15.5F, 3.5F, 13.5F, packedLight, packedOverlay, 0xFFEAEAEA);
        box(poseStack, leather, 0.5F, 0.8F, 3.3F, 1.3F, 3.5F, 12.7F, packedLight, packedOverlay, 0xFFE0E0E0);
        box(poseStack, leather, 14.7F, 0.8F, 3.3F, 15.5F, 3.5F, 12.7F, packedLight, packedOverlay, 0xFFE0E0E0);
        // Interior floor liner, darker so the open case reads as lined felt.
        box(poseStack, leather, 1.3F, 0.8F, 3.3F, 14.7F, 0.95F, 12.7F, packedLight, packedOverlay, 0xFF5A5A5A);

        // Handle on the front face: two posts plus the grip bar.
        box(poseStack, leather, 6.3F, 1.2F, 1.6F, 7.1F, 2.4F, 2.5F, packedLight, packedOverlay, 0xFFC9C9C9);
        box(poseStack, leather, 8.9F, 1.2F, 1.6F, 9.7F, 2.4F, 2.5F, packedLight, packedOverlay, 0xFFC9C9C9);
        box(poseStack, leather, 6.3F, 2.2F, 1.5F, 9.7F, 3.0F, 2.3F, packedLight, packedOverlay, 0xFFD6D6D6);

        // Lid leather in its hinge pose: rotate about the back top edge.
        poseStack.pushPose();
        poseStack.translate(0.0D, LID_HINGE_Y / 16.0D, LID_HINGE_Z / 16.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(lidAngle));
        poseStack.translate(0.0D, -LID_HINGE_Y / 16.0D, -LID_HINGE_Z / 16.0D);

        box(poseStack, leather, 0.5F, 3.5F, 2.5F, 15.5F, 5.5F, 13.5F, packedLight, packedOverlay, 0xFFF6F6F6);
        // Slightly raised top panel for the pebbled-leather silhouette.
        box(poseStack, leather, 1.5F, 5.5F, 3.5F, 14.5F, 5.75F, 12.5F, packedLight, packedOverlay, 0xFFEDEDED);

        poseStack.popPose();

        // Gold phase: leather consumer must not be used past this point.
        VertexConsumer gold = bufferSource.getBuffer(RenderType.entityCutoutNoCull(GOLD_TEXTURE));

        // Gold latches on the front of the base (static; the lid overlaps them
        // with matching keeper strips while closed).
        box(poseStack, gold, 3.0F, 2.9F, 2.05F, 4.6F, 4.1F, 2.6F, packedLight, packedOverlay, 0xFFFFFFFF);
        box(poseStack, gold, 11.4F, 2.9F, 2.05F, 13.0F, 4.1F, 2.6F, packedLight, packedOverlay, 0xFFFFFFFF);

        // Gold keeper strips aligned with the base latches, in the lid pose.
        poseStack.pushPose();
        poseStack.translate(0.0D, LID_HINGE_Y / 16.0D, LID_HINGE_Z / 16.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(lidAngle));
        poseStack.translate(0.0D, -LID_HINGE_Y / 16.0D, -LID_HINGE_Z / 16.0D);

        box(poseStack, gold, 3.2F, 3.55F, 2.15F, 4.4F, 4.35F, 2.55F, packedLight, packedOverlay, 0xFFF2F2F2);
        box(poseStack, gold, 11.6F, 3.55F, 2.15F, 12.8F, 4.35F, 2.55F, packedLight, packedOverlay, 0xFFF2F2F2);

        poseStack.popPose();
    }

    private static void renderStoredBundles(MoneyBriefcaseBlockEntity briefcase,
                                            PoseStack poseStack,
                                            MultiBufferSource bufferSource,
                                            int packedLight,
                                            int packedOverlay) {
        // ItemRenderer.renderStatic CENTERS item-model geometry on the pose
        // origin (vanilla applies translate(-0.5,-0.5,-0.5) after the display
        // transform), so each bundle is placed by its CENTER directly -- no
        // extra model-offset compensation.
        for (int slot = 0; slot < MoneyBriefcaseBlockEntity.SLOT_COUNT; slot++) {
            ItemStack stored = briefcase.getStoredStack(slot);
            if (stored == null || stored.isEmpty()) {
                continue;
            }
            // Slot order is layer-major, then z, then x.
            int layer = slot / MoneyBriefcaseBlockEntity.SLOTS_PER_LAYER;
            int layerSlot = slot % MoneyBriefcaseBlockEntity.SLOTS_PER_LAYER;
            int zRow = layerSlot / MoneyBriefcaseBlockEntity.SLOTS_PER_ROW;
            int xCol = layerSlot % MoneyBriefcaseBlockEntity.SLOTS_PER_ROW;

            double x = GRID_X_CENTERS[xCol] / 16.0D;
            double y = (GRID_BASE_Y + layer * GRID_LAYER_STEP_Y) / 16.0D;
            double z = GRID_Z_CENTERS[zRow] / 16.0D;

            ItemStack renderStack = stored.copy();
            renderStack.setCount(1);

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            // Turn the bundle so its 5px width runs along world x and its 12px
            // length along world z (5 columns across, 2 rows deep).
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            poseStack.scale(BUNDLE_SCALE_MODEL_X, BUNDLE_SCALE_MODEL_Y, BUNDLE_SCALE_MODEL_Z);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    renderStack,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    bufferSource,
                    Minecraft.getInstance().level,
                    0
            );
            poseStack.popPose();
        }
    }

    private static void box(PoseStack poseStack,
                            VertexConsumer consumer,
                            float x1,
                            float y1,
                            float z1,
                            float x2,
                            float y2,
                            float z2,
                            int packedLight,
                            int packedOverlay,
                            int color) {
        float minX = Math.min(x1, x2) / 16.0F;
        float minY = Math.min(y1, y2) / 16.0F;
        float minZ = Math.min(z1, z2) / 16.0F;
        float maxX = Math.max(x1, x2) / 16.0F;
        float maxY = Math.max(y1, y2) / 16.0F;
        float maxZ = Math.max(z1, z2) / 16.0F;

        // Vertical faces order vertices so u=0 lands on the VIEWER'S LEFT for each
        // face (render type is no-cull, so winding is free to change). The previous
        // ordering mirrored textures horizontally -- door lettering read backwards.
        quad(poseStack, consumer, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, 0.0F, 0.0F, -1.0F, packedLight, packedOverlay, color);
        quad(poseStack, consumer, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, 0.0F, 0.0F, 1.0F, packedLight, packedOverlay, color);
        quad(poseStack, consumer, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, -1.0F, 0.0F, 0.0F, packedLight, packedOverlay, color);
        quad(poseStack, consumer, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, 1.0F, 0.0F, 0.0F, packedLight, packedOverlay, color);
        quad(poseStack, consumer, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, 0.0F, 1.0F, 0.0F, packedLight, packedOverlay, color);
        quad(poseStack, consumer, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, 0.0F, -1.0F, 0.0F, packedLight, packedOverlay, color);
    }

    private static void quad(PoseStack poseStack,
                             VertexConsumer consumer,
                             float x1,
                             float y1,
                             float z1,
                             float x2,
                             float y2,
                             float z2,
                             float x3,
                             float y3,
                             float z3,
                             float x4,
                             float y4,
                             float z4,
                             float normalX,
                             float normalY,
                             float normalZ,
                             int packedLight,
                             int packedOverlay,
                             int color) {
        var pose = poseStack.last();
        vertex(consumer, pose, x1, y1, z1, 0.0F, 1.0F, normalX, normalY, normalZ, packedLight, packedOverlay, color);
        vertex(consumer, pose, x2, y2, z2, 1.0F, 1.0F, normalX, normalY, normalZ, packedLight, packedOverlay, color);
        vertex(consumer, pose, x3, y3, z3, 1.0F, 0.0F, normalX, normalY, normalZ, packedLight, packedOverlay, color);
        vertex(consumer, pose, x4, y4, z4, 0.0F, 0.0F, normalX, normalY, normalZ, packedLight, packedOverlay, color);
    }

    private static void vertex(VertexConsumer consumer,
                               PoseStack.Pose pose,
                               float x,
                               float y,
                               float z,
                               float u,
                               float v,
                               float normalX,
                               float normalY,
                               float normalZ,
                               int packedLight,
                               int packedOverlay,
                               int color) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        consumer.addVertex(pose, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static float rotationFor(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0F;
            case EAST -> -90.0F;
            case WEST -> 90.0F;
            case NORTH, UP, DOWN -> 0.0F;
        };
    }

    private static float smooth(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
