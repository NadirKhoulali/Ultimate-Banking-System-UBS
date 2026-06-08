package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.block.custom.ModularWallDisplayBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.ItemDisplayTransform;
import net.austizz.ultimatebankingsystem.block.entity.custom.ModularWallDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.client.ShelfTransformPreviewClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public class ModularWallDisplayRenderer implements BlockEntityRenderer<ModularWallDisplayBlockEntity> {
    // Two physical shelf rows on the new modular wall display.
    private static final float TOP_ROW_Y = 0.74F;
    private static final float BOTTOM_ROW_Y = 0.30F;
    // Shared with the position editor camera target so gizmo center stays aligned to rendered items.
    // Keep items in front of the thin wall body without embedding into the panel.
    // Less negative moves displayed models outward for this transform basis.
    public static final float ROW_FRONT_Z = -0.30F;
    private static final float TWO_LAYOUT_CENTER_X = -0.50F;
    private static final float FOUR_LAYOUT_LEFT_X = -1.00F;
    private static final float FOUR_LAYOUT_RIGHT_X = 0.00F;
    private static final float TWO_LAYOUT_BASE_SCALE = 0.29F;
    private static final float FOUR_LAYOUT_BASE_SCALE = 0.23F;
    // Expensive/custom models (for example weapon mods) tank FPS when many are visible at once.
    // For non-focused/far items we render a lightweight sprite impostor instead.
    private static final double EXPENSIVE_ALWAYS_FULL_DISTANCE_SQR = 2.25D; // <= 1.5 blocks: keep full model.
    private static final double EXPENSIVE_GENERAL_FULL_DISTANCE_SQR = 16.0D; // <= 4 blocks: keep full model while looking at wall.
    private static final double EXPENSIVE_FOCUS_MAX_DISTANCE_SQR = 49.0D;   // focus check up to 7 blocks.
    private static final double EXPENSIVE_FOCUS_DOT = 0.9925D;              // ~7 degrees from crosshair.
    private static final double EXPENSIVE_DIRECT_LOOK_DOT = 0.9975D;        // ~4 degrees from crosshair.
    private static final double EXPENSIVE_DIRECT_LOOK_MAX_DISTANCE_SQR = 64.0D; // <= 8 blocks for direct-look selection.
    private static final double WALL_GENERAL_VIEW_DOT = 0.92D;              // ~23 degrees: "general direction".
    private static final float IMPOSTOR_SCALE_MULTIPLIER = 1.22F;
    // Hard frame budget: when too many expensive custom models are in view, degrade extras to impostors.
    private static final int MAX_EXPENSIVE_FULL_MODELS_PER_FRAME = 8;
    // Cache retains per-slot model metadata/sprite for a short period to avoid expensive lookups every frame.
    private static final long CACHE_TTL_TICKS = 200L;
    private static final long CACHE_REFRESH_TICKS = 20L;

    private static long budgetTick = Long.MIN_VALUE;
    private static int budgetPartialBits = Integer.MIN_VALUE;
    private static int expensiveFullModelsThisFrame = 0;

    private final Map<SlotCacheKey, CachedSlotVisual> slotVisualCache = new HashMap<>();
    private long lastCacheCleanupTick = Long.MIN_VALUE;

    public ModularWallDisplayRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ModularWallDisplayBlockEntity display,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        BlockState state = display.getBlockState();
        if (!state.hasProperty(ModularWallDisplayBlock.FACING)) {
            return;
        }

        float yaw = -state.getValue(ModularWallDisplayBlock.FACING).toYRot();
        Direction facing = state.getValue(ModularWallDisplayBlock.FACING);
        Direction right = facing.getClockWise();
        Minecraft minecraft = Minecraft.getInstance();
        if (display.getLevel() != null) {
            resetFrameBudgetIfNeeded(display.getLevel().getGameTime(), partialTick);
            cleanupCacheIfNeeded(display.getLevel().getGameTime());
        }
        var itemRenderer = minecraft.getItemRenderer();
        Entity cameraEntity = minecraft.getCameraEntity();
        Vec3 cameraPos = cameraEntity == null ? null : cameraEntity.getEyePosition(partialTick);
        Vec3 lookDir = cameraEntity == null ? null : cameraEntity.getLookAngle();
        Vec3 lookDirNorm = lookDir == null || lookDir.lengthSqr() < 1.0E-6D
                ? null
                : lookDir.normalize();
        Vec3 blockCenter = Vec3.atCenterOf(display.getBlockPos());
        boolean viewingWallGenerally = isViewingDisplayGenerally(blockCenter, cameraPos, lookDirNorm);

        int visibleSlots = Math.max(1, display.getSlotCount());
        int focusedExpensiveSlot = determineFocusedExpensiveSlot(
                display,
                visibleSlots,
                facing,
                right,
                cameraPos,
                lookDirNorm,
                itemRenderer,
                blockCenter
        );
        for (int slot = 0; slot < visibleSlots; slot++) {
            ItemStack stack = display.getDisplayItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemDisplayTransform transform = ShelfTransformPreviewClientState.resolve(
                    display.getLevel(),
                    display.getBlockPos(),
                    slot,
                    display.getSlotTransform(slot)
            );

            boolean fourLayout = visibleSlots > 2;
            int row = fourLayout ? (slot / 2) : slot;
            int col = fourLayout ? (slot % 2) : 0;
            float baseX = fourLayout
                    ? (col == 0 ? FOUR_LAYOUT_LEFT_X : FOUR_LAYOUT_RIGHT_X)
                    : TWO_LAYOUT_CENTER_X;
            float baseY = row <= 0 ? TOP_ROW_Y : BOTTOM_ROW_Y;
            float baseScale = fourLayout ? FOUR_LAYOUT_BASE_SCALE : TWO_LAYOUT_BASE_SCALE;
            Vec3 itemPos = computeWorldItemPos(blockCenter, right, facing, baseX, baseY, transform);
            CachedSlotVisual cachedVisual = resolveSlotVisual(display, slot, stack, itemRenderer);
            boolean useImpostor = shouldUseImpostor(
                    cachedVisual.expensiveCustomModel,
                    cachedVisual.hasUsableImpostorSprite,
                    itemPos,
                    cameraPos,
                    lookDirNorm,
                    viewingWallGenerally
            );
            boolean veryClose = cameraPos != null
                    && itemPos != null
                    && itemPos.distanceToSqr(cameraPos) <= EXPENSIVE_ALWAYS_FULL_DISTANCE_SQR;
            // Option 1: for expensive custom models, keep full render only for directly looked-at slot,
            // except very close range where readability is still prioritized.
            if (!veryClose
                    && cachedVisual.expensiveCustomModel
                    && cachedVisual.hasUsableImpostorSprite
                    && focusedExpensiveSlot >= 0
                    && slot != focusedExpensiveSlot) {
                useImpostor = true;
            }
            if (!useImpostor
                    && cachedVisual.expensiveCustomModel
                    && cachedVisual.hasUsableImpostorSprite
                    && !canRenderAnotherExpensiveModelThisFrame()) {
                useImpostor = true;
            }
            float renderScale = useImpostor ? baseScale * IMPOSTOR_SCALE_MULTIPLIER : baseScale;

            poseStack.pushPose();
            // Apply lateral placement in world axes first so slots stay aligned to the 2x1 display footprint.
            poseStack.translate(
                    0.5D + (double) right.getStepX() * baseX,
                    0.5D,
                    0.5D + (double) right.getStepZ() * baseX
            );
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.translate(0.0D, baseY - 0.5F, ROW_FRONT_Z);
            poseStack.translate(transform.offsetX(), transform.offsetY(), transform.offsetZ());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F + transform.rotationY()));
            poseStack.mulPose(Axis.XP.rotationDegrees(transform.rotationX()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(transform.rotationZ()));
            poseStack.scale(renderScale * transform.scaleX(), renderScale * transform.scaleY(), renderScale * transform.scaleZ());

            if (useImpostor) {
                renderItemImpostor(poseStack, bufferSource, packedLight, packedOverlay, cachedVisual.impostorSprite);
            } else {
                if (cachedVisual.expensiveCustomModel) {
                    expensiveFullModelsThisFrame++;
                }
                itemRenderer.renderStatic(
                        stack,
                        ItemDisplayContext.FIXED,
                        packedLight,
                        packedOverlay,
                        poseStack,
                        bufferSource,
                        display.getLevel(),
                        0
                );
            }

            poseStack.popPose();
        }
    }

    private int determineFocusedExpensiveSlot(ModularWallDisplayBlockEntity display,
                                              int visibleSlots,
                                              Direction facing,
                                              Direction right,
                                              Vec3 cameraPos,
                                              Vec3 lookDirNorm,
                                              net.minecraft.client.renderer.entity.ItemRenderer itemRenderer,
                                              Vec3 blockCenter) {
        if (cameraPos == null || lookDirNorm == null || blockCenter == null) {
            return -1;
        }
        int focusedSlot = -1;
        double bestDot = -1.0D;
        for (int slot = 0; slot < visibleSlots; slot++) {
            ItemStack stack = display.getDisplayItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CachedSlotVisual cachedVisual = resolveSlotVisual(display, slot, stack, itemRenderer);
            if (!cachedVisual.expensiveCustomModel || !cachedVisual.hasUsableImpostorSprite) {
                continue;
            }
            ItemDisplayTransform transform = ShelfTransformPreviewClientState.resolve(
                    display.getLevel(),
                    display.getBlockPos(),
                    slot,
                    display.getSlotTransform(slot)
            );
            boolean fourLayout = visibleSlots > 2;
            int row = fourLayout ? (slot / 2) : slot;
            int col = fourLayout ? (slot % 2) : 0;
            float baseX = fourLayout
                    ? (col == 0 ? FOUR_LAYOUT_LEFT_X : FOUR_LAYOUT_RIGHT_X)
                    : TWO_LAYOUT_CENTER_X;
            float baseY = row <= 0 ? TOP_ROW_Y : BOTTOM_ROW_Y;
            Vec3 itemPos = computeWorldItemPos(blockCenter, right, facing, baseX, baseY, transform);
            Vec3 toItem = itemPos.subtract(cameraPos);
            double distanceSqr = toItem.lengthSqr();
            if (distanceSqr < 1.0E-6D || distanceSqr > EXPENSIVE_DIRECT_LOOK_MAX_DISTANCE_SQR) {
                continue;
            }
            double dot = lookDirNorm.dot(toItem.normalize());
            if (dot < EXPENSIVE_DIRECT_LOOK_DOT) {
                continue;
            }
            if (dot > bestDot) {
                bestDot = dot;
                focusedSlot = slot;
            }
        }
        return focusedSlot;
    }

    private CachedSlotVisual resolveSlotVisual(ModularWallDisplayBlockEntity display,
                                               int slot,
                                               ItemStack stack,
                                               net.minecraft.client.renderer.entity.ItemRenderer itemRenderer) {
        if (display.getLevel() == null) {
            BakedModel model = itemRenderer.getModel(stack, null, null, 0);
            TextureAtlasSprite sprite = model == null ? null : model.getParticleIcon();
            return new CachedSlotVisual(
                    isLikelyExpensive(model),
                    hasUsableImpostorSprite(sprite),
                    sprite,
                    computeItemFingerprint(stack),
                    0L,
                    0L
            );
        }

        SlotCacheKey key = new SlotCacheKey(
                display.getLevel().dimension().location().toString(),
                display.getBlockPos().asLong(),
                slot
        );
        int fingerprint = computeItemFingerprint(stack);
        long nowTick = display.getLevel().getGameTime();

        CachedSlotVisual cached = slotVisualCache.get(key);
        if (cached != null
                && cached.itemFingerprint == fingerprint
                && (nowTick - cached.refreshedAtTick) < CACHE_REFRESH_TICKS) {
            cached.lastSeenTick = nowTick;
            return cached;
        }

        BakedModel model = itemRenderer.getModel(stack, display.getLevel(), null, 0);
        TextureAtlasSprite sprite = model == null ? null : model.getParticleIcon();
        CachedSlotVisual refreshed = new CachedSlotVisual(
                isLikelyExpensive(model),
                hasUsableImpostorSprite(sprite),
                sprite,
                fingerprint,
                nowTick,
                nowTick
        );
        slotVisualCache.put(key, refreshed);
        return refreshed;
    }

    private static boolean shouldUseImpostor(boolean expensiveCustomModel,
                                             boolean hasUsableImpostorSprite,
                                             Vec3 itemPos,
                                             Vec3 cameraPos,
                                             Vec3 lookDirNorm,
                                             boolean viewingWallGenerally) {
        if (!expensiveCustomModel || cameraPos == null || itemPos == null) {
            return false;
        }
        // Never impostor if model cannot provide a usable sprite fallback (prevents pink/black missing texture quads).
        if (!hasUsableImpostorSprite) {
            return false;
        }
        Vec3 toItem = itemPos.subtract(cameraPos);
        double distanceSqr = toItem.lengthSqr();
        if (distanceSqr <= EXPENSIVE_ALWAYS_FULL_DISTANCE_SQR) {
            return false;
        }
        // Keep close-range readability intact when player is generally facing the wall.
        if (viewingWallGenerally && distanceSqr <= EXPENSIVE_GENERAL_FULL_DISTANCE_SQR) {
            return false;
        }
        if (distanceSqr > EXPENSIVE_FOCUS_MAX_DISTANCE_SQR || lookDirNorm == null || distanceSqr < 1.0E-6D) {
            return true;
        }
        double focusDot = lookDirNorm.dot(toItem.normalize());
        return focusDot < EXPENSIVE_FOCUS_DOT;
    }

    private static boolean isLikelyExpensive(BakedModel model) {
        // Restrict optimization to true custom-rendered items only.
        return model != null && model.isCustomRenderer();
    }

    private static boolean isViewingDisplayGenerally(Vec3 displayCenter, Vec3 cameraPos, Vec3 lookDirNorm) {
        if (displayCenter == null || cameraPos == null || lookDirNorm == null) {
            return false;
        }
        Vec3 toDisplay = displayCenter.subtract(cameraPos);
        if (toDisplay.lengthSqr() < 1.0E-6D) {
            return true;
        }
        return lookDirNorm.dot(toDisplay.normalize()) >= WALL_GENERAL_VIEW_DOT;
    }

    private static boolean hasUsableImpostorSprite(TextureAtlasSprite sprite) {
        if (sprite == null || sprite.contents() == null || sprite.contents().name() == null) {
            return false;
        }
        return !MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name());
    }

    private static void renderItemImpostor(PoseStack poseStack,
                                           MultiBufferSource bufferSource,
                                           int packedLight,
                                           int packedOverlay,
                                           TextureAtlasSprite sprite) {
        if (!hasUsableImpostorSprite(sprite)) {
            return;
        }
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        float minU = sprite.getU0();
        float maxU = sprite.getU1();
        float minV = sprite.getV0();
        float maxV = sprite.getV1();
        float half = 0.5F;
        // Single front-facing quad: preserves identity of displayed item while avoiding heavy model render cost.
        consumer.vertex(pose, -half, -half, 0.0F).color(255, 255, 255, 255).uv(minU, maxV).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        consumer.vertex(pose, half, -half, 0.0F).color(255, 255, 255, 255).uv(maxU, maxV).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        consumer.vertex(pose, half, half, 0.0F).color(255, 255, 255, 255).uv(maxU, minV).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        consumer.vertex(pose, -half, half, 0.0F).color(255, 255, 255, 255).uv(minU, minV).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
    }

    private static Vec3 rotateByFacing(Vec3 local, Direction facing) {
        if (local == null) {
            return Vec3.ZERO;
        }
        float yaw = facing == null ? 0.0F : -facing.toYRot();
        return local.yRot((float) Math.toRadians(yaw));
    }

    private static Vec3 computeWorldItemPos(Vec3 blockCenter,
                                            Direction right,
                                            Direction facing,
                                            float baseX,
                                            float baseY,
                                            ItemDisplayTransform transform) {
        return blockCenter
                .add(new Vec3((double) right.getStepX() * baseX, 0.0D, (double) right.getStepZ() * baseX))
                .add(rotateByFacing(new Vec3(0.0D, baseY - 0.5D, ROW_FRONT_Z), facing))
                .add(rotateByFacing(new Vec3(transform.offsetX(), transform.offsetY(), transform.offsetZ()), facing));
    }

    private static int computeItemFingerprint(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return Objects.hash(stack.getItem(), stack.getDamageValue(), stack.getTag());
    }

    private static void resetFrameBudgetIfNeeded(long gameTick, float partialTick) {
        int partialBits = Float.floatToIntBits(partialTick);
        if (budgetTick == gameTick && budgetPartialBits == partialBits) {
            return;
        }
        budgetTick = gameTick;
        budgetPartialBits = partialBits;
        expensiveFullModelsThisFrame = 0;
    }

    private static boolean canRenderAnotherExpensiveModelThisFrame() {
        return expensiveFullModelsThisFrame < MAX_EXPENSIVE_FULL_MODELS_PER_FRAME;
    }

    private void cleanupCacheIfNeeded(long gameTick) {
        if (lastCacheCleanupTick != Long.MIN_VALUE && gameTick - lastCacheCleanupTick < 40L) {
            return;
        }
        lastCacheCleanupTick = gameTick;
        Iterator<Map.Entry<SlotCacheKey, CachedSlotVisual>> iterator = slotVisualCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SlotCacheKey, CachedSlotVisual> entry = iterator.next();
            if (gameTick - entry.getValue().lastSeenTick > CACHE_TTL_TICKS) {
                iterator.remove();
            }
        }
    }

    private record SlotCacheKey(String dimensionId, long blockPos, int slot) {
    }

    private static final class CachedSlotVisual {
        private final boolean expensiveCustomModel;
        private final boolean hasUsableImpostorSprite;
        private final TextureAtlasSprite impostorSprite;
        private final int itemFingerprint;
        private final long refreshedAtTick;
        private long lastSeenTick;

        private CachedSlotVisual(boolean expensiveCustomModel,
                                 boolean hasUsableImpostorSprite,
                                 TextureAtlasSprite impostorSprite,
                                 int itemFingerprint,
                                 long refreshedAtTick,
                                 long lastSeenTick) {
            this.expensiveCustomModel = expensiveCustomModel;
            this.hasUsableImpostorSprite = hasUsableImpostorSprite;
            this.impostorSprite = impostorSprite;
            this.itemFingerprint = itemFingerprint;
            this.refreshedAtTick = refreshedAtTick;
            this.lastSeenTick = lastSeenTick;
        }
    }
}
