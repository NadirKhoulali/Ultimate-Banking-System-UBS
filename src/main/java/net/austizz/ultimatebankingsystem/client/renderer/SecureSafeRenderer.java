package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.HeistDrillBlock;
import net.austizz.ultimatebankingsystem.block.custom.MoneyStackBlock;
import net.austizz.ultimatebankingsystem.block.custom.SecureSafeBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.SecureSafeBlockEntity;
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

public class SecureSafeRenderer implements BlockEntityRenderer<SecureSafeBlockEntity> {
    private static final ResourceLocation DOOR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "textures/block/safe_door_dark.png");
    private static final ResourceLocation COMPARTMENT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "textures/block/safe_metal.png");
    private static final ResourceLocation GOLD_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/gold_block.png");
    private static final float DOOR_OPEN_DEGREES = -108.0F;
    private static final float DOOR_HINGE_X = 14.35F;
    private static final float DOOR_FORWARD_OFFSET = 0.0F;
    private static final float DOOR_HINGE_Z = 1.0F + DOOR_FORWARD_OFFSET;
    // Animation phase split: wheel spins during progress 0..WHEEL_PHASE_END, door swings after.
    // Open plays wheel-then-door; close (progress running 1 -> 0) plays door-then-reverse-wheel.
    private static final float WHEEL_PHASE_END = 0.45F;
    private static final float WHEEL_SPIN_DEGREES = 540.0F;
    // Gold 5-spoke star wheel geometry (door-local pixels).
    private static final float WHEEL_CENTER_X = 8.0F;
    private static final float WHEEL_STANDOFF_Z = -1.35F + DOOR_FORWARD_OFFSET;

    public SecureSafeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SecureSafeBlockEntity safe,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        if (safe == null) {
            return;
        }

        BlockState state = safe.getBlockState();
        Direction facing = state.hasProperty(SecureSafeBlock.FACING) ? state.getValue(SecureSafeBlock.FACING) : Direction.NORTH;
        float progress = safe.getAnimationProgress(partialTick);

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationFor(facing)));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        renderDoor(safe.isTallSafe(), progress, poseStack, bufferSource, packedLight, packedOverlay);
        if (progress > 0.42F || safe.isTargetOpen()) {
            if (safe.hasChestUpgrade()) {
                renderChestCompartment(safe.isTallSafe(), progress, poseStack, bufferSource, packedLight, packedOverlay);
            }
            renderShelfStacks(safe, progress, poseStack, bufferSource, packedLight, packedOverlay);
        }
        if (safe.isHeistDrillAttached()) {
            renderAttachedDrill(safe.isTallSafe(), poseStack, bufferSource, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(SecureSafeBlockEntity safe) {
        if (safe == null) {
            return AABB.INFINITE;
        }
        int height = safe.isTallSafe() ? 2 : 1;
        return new AABB(
                safe.getBlockPos().getX() - 1.0D,
                safe.getBlockPos().getY(),
                safe.getBlockPos().getZ() - 1.0D,
                safe.getBlockPos().getX() + 2.0D,
                safe.getBlockPos().getY() + height + 0.35D,
                safe.getBlockPos().getZ() + 2.0D
        );
    }

    private static void renderDoor(boolean tall,
                                   float progress,
                                   PoseStack poseStack,
                                   MultiBufferSource bufferSource,
                                   int packedLight,
                                   int packedOverlay) {
        float doorPhase = Mth.clamp((progress - WHEEL_PHASE_END) / (1.0F - WHEEL_PHASE_END), 0.0F, 1.0F);
        float wheelPhase = Mth.clamp(progress / WHEEL_PHASE_END, 0.0F, 1.0F);
        float wheelSpin = WHEEL_SPIN_DEGREES * smooth(wheelPhase);
        float height = tall ? 32.0F : 16.0F;
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(DOOR_TEXTURE));

        poseStack.pushPose();
        poseStack.translate(DOOR_HINGE_X / 16.0D, 0.0D, DOOR_HINGE_Z / 16.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(DOOR_OPEN_DEGREES * smooth(doorPhase)));
        poseStack.translate(-DOOR_HINGE_X / 16.0D, 0.0D, -DOOR_HINGE_Z / 16.0D);

        box(poseStack, consumer, 2.0F, 1.5F, 0.15F + DOOR_FORWARD_OFFSET, 14.0F, height - 1.45F, 2.35F + DOOR_FORWARD_OFFSET, packedLight, packedOverlay, 0xFFDADADA);
        box(poseStack, consumer, 3.0F, 2.6F, -0.12F + DOOR_FORWARD_OFFSET, 13.0F, height - 2.7F, 0.42F + DOOR_FORWARD_OFFSET, packedLight, packedOverlay, 0xFFF2F2F2);
        box(poseStack, consumer, 13.45F, 1.5F, -0.18F + DOOR_FORWARD_OFFSET, 14.35F, height - 1.45F, 2.55F + DOOR_FORWARD_OFFSET, packedLight, packedOverlay, 0xFFDADADA);

        float dialY = tall ? 20.5F : 9.25F;

        VertexConsumer goldConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(GOLD_TEXTURE));
        // Gold combination dial, centered on the door above the wheel (the old
        // PIN-pad plate was removed per design feedback).
        box(poseStack, goldConsumer, 7.3F, dialY + 0.35F, -0.7F + DOOR_FORWARD_OFFSET, 8.7F, dialY + 1.75F, 0.05F + DOOR_FORWARD_OFFSET, packedLight, packedOverlay, 0xFFFFFFFF);
        box(poseStack, goldConsumer, 7.8F, dialY + 0.85F, -0.88F + DOOR_FORWARD_OFFSET, 8.2F, dialY + 1.25F, -0.6F + DOOR_FORWARD_OFFSET, packedLight, packedOverlay, 0xFFF0F0F0);

        // Brass hinge knuckles on the hinge edge.
        float hingeLowY = height * 0.18F;
        float hingeHighY = height * 0.72F;
        box(poseStack, goldConsumer, 13.85F, hingeLowY, -0.45F + DOOR_FORWARD_OFFSET, 15.05F, hingeLowY + 2.4F, 0.75F + DOOR_FORWARD_OFFSET, packedLight, packedOverlay, 0xFFFFFFFF);
        box(poseStack, goldConsumer, 13.85F, hingeHighY, -0.45F + DOOR_FORWARD_OFFSET, 15.05F, hingeHighY + 2.4F, 0.75F + DOOR_FORWARD_OFFSET, packedLight, packedOverlay, 0xFFFFFFFF);

        // Compact wheel sits slightly lower (0.4625 vs 0.5) and renders smaller
        // so the spinning knobs keep clear margin from the dial above.
        renderWheel(poseStack, goldConsumer, tall ? height * 0.42F : height * 0.4625F, wheelSpin,
                tall ? 0.72F : 0.5F, packedLight, packedOverlay);

        poseStack.popPose();
    }

    private static void renderAttachedDrill(boolean tall,
                                            PoseStack poseStack,
                                            MultiBufferSource bufferSource,
                                            int packedLight,
                                            int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.0D, tall ? 0.70D : 0.18D, -0.96D);
        BlockState drill = ModBlocks.HEIST_DRILL.get().defaultBlockState()
                .setValue(HeistDrillBlock.FACING, Direction.SOUTH)
                .setValue(HeistDrillBlock.MOUNT, HeistDrillBlock.Mount.TARGET);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                drill, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static void renderWheel(PoseStack poseStack,
                                    VertexConsumer goldConsumer,
                                    float centerY,
                                    float wheelSpin,
                                    float scale,
                                    int packedLight,
                                    int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(WHEEL_CENTER_X / 16.0D, centerY / 16.0D, WHEEL_STANDOFF_Z / 16.0D);
        // Door-local Z is the door face normal, so spinning around Z keeps the wheel
        // flat against the door through the FACING and hinge transforms. The -90
        // base offset points one spoke straight UP at rest (classic 5-point star).
        poseStack.mulPose(Axis.ZP.rotationDegrees(wheelSpin - 90.0F));

        // Axle reaching back from the wheel plane into the door slab.
        float ax = 0.45F * scale;
        box(poseStack, goldConsumer, -ax, -ax, -0.4F * scale, ax, ax, 1.95F, packedLight, packedOverlay, 0xFFE9E9E9);
        // Hub with a small raised front cap.
        float hub = 1.0F * scale;
        float hubZ = 0.5F * scale;
        box(poseStack, goldConsumer, -hub, -hub, -hubZ, hub, hub, hubZ, packedLight, packedOverlay, 0xFFFFFFFF);
        box(poseStack, goldConsumer, -0.5F * scale, -0.5F * scale, -hubZ - 0.3F * scale, 0.5F * scale, 0.5F * scale, -hubZ, packedLight, packedOverlay, 0xFFF6F6F6);

        // Five identical spokes, each perfectly centered on its own radial axis so
        // the silhouette reads as a symmetric 5-pointed star.
        float spokeInner = 0.8F * scale;
        float spokeOuter = 4.6F * scale;
        float spokeHalf = 0.32F * scale;
        float knobHalf = 0.7F * scale;
        float knobCenter = spokeOuter + 0.35F * scale;
        for (int i = 0; i < 5; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(72.0F * i));
            box(poseStack, goldConsumer, spokeInner, -spokeHalf, -spokeHalf, spokeOuter, spokeHalf, spokeHalf, packedLight, packedOverlay, 0xFFF6F6F6);
            box(poseStack, goldConsumer, knobCenter - knobHalf, -knobHalf, -knobHalf, knobCenter + knobHalf, knobHalf, knobHalf, packedLight, packedOverlay, 0xFFFFFFFF);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static void renderChestCompartment(boolean tall,
                                               float progress,
                                               PoseStack poseStack,
                                               MultiBufferSource bufferSource,
                                               int packedLight,
                                               int packedOverlay) {
        float alphaScale = Mth.clamp((progress - 0.35F) / 0.4F, 0.0F, 1.0F);
        if (alphaScale <= 0.0F) {
            return;
        }
        float lowestShelf = (float) SecureSafeBlockEntity.shelfBasePixels(tall, 0);
        float top = Math.max(5.8F, lowestShelf - 1.15F);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(COMPARTMENT_TEXTURE));

        box(poseStack, consumer, 2.75F, 1.75F, 2.45F, 13.25F, 2.25F, 12.9F, packedLight, packedOverlay, 0xFFD0D7DE);
        box(poseStack, consumer, 2.55F, 2.2F, 2.35F, 3.05F, top, 12.75F, packedLight, packedOverlay, 0xFF9CA8B4);
        box(poseStack, consumer, 12.95F, 2.2F, 2.35F, 13.45F, top, 12.75F, packedLight, packedOverlay, 0xFF9CA8B4);
        box(poseStack, consumer, 2.75F, 2.2F, 12.35F, 13.25F, top, 12.9F, packedLight, packedOverlay, 0xFF8794A2);
        box(poseStack, consumer, 3.7F, 2.35F, 1.95F, 12.3F, top - 0.25F, 2.45F, packedLight, packedOverlay, 0xFFB9C2CB);
        box(poseStack, consumer, 6.65F, 3.05F, 1.55F, 9.35F, 3.55F, 2.05F, packedLight, packedOverlay, 0xFFEEF3F6);
    }

    private static void renderShelfStacks(SecureSafeBlockEntity safe,
                                          float progress,
                                          PoseStack poseStack,
                                          MultiBufferSource bufferSource,
                                          int packedLight,
                                          int packedOverlay) {
        float alphaScale = Mth.clamp((progress - 0.35F) / 0.4F, 0.0F, 1.0F);
        int count = safe.displaySlotCount();
        for (int slot = 0; slot < count; slot++) {
            ItemStack stack = safe.getDisplayStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            double[] position = slotPosition(safe.isTallSafe(), slot);
            renderShelfStack(stack, position[0], position[1], position[2], alphaScale,
                    poseStack, bufferSource, packedLight, packedOverlay);
        }
    }

    private static void renderShelfStack(ItemStack stack,
                                         double x,
                                         double y,
                                         double z,
                                         float alphaScale,
                                         PoseStack poseStack,
                                         MultiBufferSource bufferSource,
                                         int packedLight,
                                         int packedOverlay) {
        if (isMetalBar(stack)) {
            renderMetalBarStack(stack, x, y, z, alphaScale, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }
        if (isMoneyStack(stack)) {
            renderMoneyStackShelf(stack, x, y, z, alphaScale, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }
        renderFlatItemStack(stack, x, y, z, alphaScale, poseStack, bufferSource, packedLight, packedOverlay);
    }

    private static boolean isMoneyStack(ItemStack stack) {
        return stack != null && MoneyStackBlock.BillDenomination.fromStackItem(stack.getItem()) != null;
    }

    private static void renderMoneyStackShelf(ItemStack stack,
                                              double x,
                                              double y,
                                              double z,
                                              float alphaScale,
                                              PoseStack poseStack,
                                              MultiBufferSource bufferSource,
                                              int packedLight,
                                              int packedOverlay) {
        ItemStack renderStack = singleRenderStack(stack);
        // Bundles are 2px tall in model space; at the shelf scale each visual layer
        // steps by that height so the strapped stacks sit flush like on pallets.
        // The ITEM model geometry is slot-centered (bundle at y 7..9, not 0..2),
        // so shift back down by that 7px offset at render scale.
        int layers = Mth.clamp(stack.getCount(), 1, 8);
        float scale = 0.31F * Mth.clamp(alphaScale, 0.15F, 1.0F);
        double layerStep = (2.0D / 16.0D) * scale;
        double centerOffset = (7.0D / 16.0D) * scale;
        for (int layer = 0; layer < layers; layer++) {
            poseStack.pushPose();
            poseStack.translate(
                    x + ((layer & 1) == 0 ? 0.0D : 0.005D),
                    y + 0.055D + layer * layerStep - centerOffset,
                    z + ((layer % 3) - 1) * 0.004D
            );
            poseStack.scale(scale, scale, scale);
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

    private static void renderMetalBarStack(ItemStack stack,
                                            double x,
                                            double y,
                                            double z,
                                            float alphaScale,
                                            PoseStack poseStack,
                                            MultiBufferSource bufferSource,
                                            int packedLight,
                                            int packedOverlay) {
        ItemStack renderStack = singleRenderStack(stack);
        int layers = visualLayerCount(stack);
        for (int layer = 0; layer < layers; layer++) {
            poseStack.pushPose();
            poseStack.translate(
                    x + ((layer & 1) == 0 ? 0.0D : 0.006D),
                    y + 0.065D + layer * 0.011D,
                    z + ((layer % 3) - 1) * 0.004D
            );
            float scale = 0.31F * Mth.clamp(alphaScale, 0.15F, 1.0F);
            poseStack.scale(scale, scale, scale);
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

    private static void renderFlatItemStack(ItemStack stack,
                                            double x,
                                            double y,
                                            double z,
                                            float alphaScale,
                                            PoseStack poseStack,
                                            MultiBufferSource bufferSource,
                                            int packedLight,
                                            int packedOverlay) {
        ItemStack renderStack = singleRenderStack(stack);
        int layers = visualLayerCount(stack);
        for (int layer = 0; layer < layers; layer++) {
            poseStack.pushPose();
            poseStack.translate(
                    x + ((layer % 3) - 1) * 0.004D,
                    y - 0.010D + layer * 0.004D,
                    z + ((layer & 1) == 0 ? 0.0D : 0.004D)
            );
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(270.0F));
            float scale = 0.30F * Mth.clamp(alphaScale, 0.15F, 1.0F);
            poseStack.scale(scale, scale, scale);
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

    private static ItemStack singleRenderStack(ItemStack stack) {
        ItemStack renderStack = stack.copy();
        renderStack.setCount(1);
        return renderStack;
    }

    private static int visualLayerCount(ItemStack stack) {
        return Mth.clamp(stack == null ? 1 : stack.getCount(), 1, SecureSafeBlockEntity.SHELF_SLOT_LIMIT);
    }

    private static boolean isMetalBar(ItemStack stack) {
        return stack != null
                && (stack.is(ModBlocks.GOLD_BAR.get().asItem()) || stack.is(ModBlocks.SILVER_BAR.get().asItem()));
    }

    private static double[] slotPosition(boolean tall, int slot) {
        int shelf = slot / SecureSafeBlockEntity.SLOTS_PER_SHELF;
        int shelfSlot = slot % SecureSafeBlockEntity.SLOTS_PER_SHELF;
        int row = shelfSlot / 3;
        int col = shelfSlot % 3;
        double x = (4.15D + col * 3.85D) / 16.0D;
        double y = (SecureSafeBlockEntity.shelfBasePixels(tall, shelf) + 2.1D) / 16.0D;
        double z = (4.95D + row * 5.25D) / 16.0D;
        return new double[]{x, y, z};
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
        // ordering mirrored textures horizontally — door lettering read backwards.
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
