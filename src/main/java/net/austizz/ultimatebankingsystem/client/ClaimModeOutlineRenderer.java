package net.austizz.ultimatebankingsystem.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.austizz.ultimatebankingsystem.block.custom.PalletBlock;
import net.austizz.ultimatebankingsystem.claim.ClaimSelectionType;
import net.austizz.ultimatebankingsystem.claim.ClaimToolKind;
import net.austizz.ultimatebankingsystem.network.ClaimModeSnapshotPayload;
import net.austizz.ultimatebankingsystem.network.ClaimOutlineSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Cached tactical ground decals and exact 3D rails for the universal claim workspace. */
public final class ClaimModeOutlineRenderer {
    private static final RenderType GLOW_RENDER_TYPE = createDecalRenderType(
            "ubs_claim_border_glow", RenderType.ADDITIVE_TRANSPARENCY);
    private static final RenderType CORE_RENDER_TYPE = createDecalRenderType(
            "ubs_claim_border_core", RenderType.TRANSLUCENT_TRANSPARENCY);
    private static final double SAMPLE_LENGTH = 0.40D;
    private static final double CORE_WIDTH = 0.072D;
    private static final double GLOW_WIDTH = 0.21D;
    private static final double MAX_STEP_DELTA = 1.25D;
    private static final double RENDER_DISTANCE_SQ = 128.0D * 128.0D;
    private static final int TERRAIN_REFRESH_TICKS = 80;
    private static final int MAX_TERRAIN_SAMPLES = 6144;
    private static final int MAX_SELECTED_TERRAIN_SAMPLES = 2048;
    private static final int MAX_OUTLINE_TERRAIN_SAMPLES = 256;
    private static final int MAX_HOVER_TERRAIN_SAMPLES = 96;
    private static final int MAX_WORLD_LABELS = 18;
    private static final float LABEL_SCALE = 0.018F;

    private static Cache cache = Cache.empty();
    private static long cachedSignature = Long.MIN_VALUE;
    private static long lastTerrainRefreshTick = Long.MIN_VALUE;
    private static BlockPos cachedHoverMaster;
    private static GroundMesh cachedHoverMesh = GroundMesh.empty();

    private ClaimModeOutlineRenderer() {
    }

    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        if (event == null || minecraft == null || minecraft.level == null || minecraft.player == null
                || !ClaimModeClientState.active()) {
            return;
        }
        ClaimModeSnapshotPayload snapshot = ClaimModeClientState.snapshot();
        String dimension = minecraft.level.dimension().location().toString();
        if (!dimension.equals(snapshot.dimensionId())) {
            return;
        }

        long gameTick = minecraft.level.getGameTime();
        long signature = geometrySignature(snapshot, dimension);
        if (signature != cachedSignature
                || gameTick - lastTerrainRefreshTick >= TERRAIN_REFRESH_TICKS) {
            cache = buildCache(minecraft, snapshot, dimension);
            cachedSignature = signature;
            lastTerrainRefreshTick = gameTick;
        }

        HoverTarget hover = resolvePalletHover(minecraft, snapshot);
        if (!Objects.equals(cachedHoverMaster, hover == null ? null : hover.master())) {
            cachedHoverMaster = hover == null ? null : hover.master();
            cachedHoverMesh = hover == null
                    ? GroundMesh.empty()
                    : buildRectangle(minecraft, hover.bounds(), hover.master().getY(),
                    MAX_HOVER_TERRAIN_SAMPLES);
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double animationTime = gameTick + partialTick;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        PoseStack.Pose pose = poseStack.last();

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        renderGroundDecals(buffers, pose, snapshot, animationTime);
        renderRails(buffers, poseStack, snapshot, hover, animationTime);
        poseStack.popPose();

        renderLabels(event.getPoseStack(), buffers, minecraft, camera, snapshot);
        buffers.endBatch();
    }

    public static void clearCache() {
        cache = Cache.empty();
        cachedSignature = Long.MIN_VALUE;
        lastTerrainRefreshTick = Long.MIN_VALUE;
        cachedHoverMaster = null;
        cachedHoverMesh = GroundMesh.empty();
    }

    private static Cache buildCache(Minecraft minecraft,
                                    ClaimModeSnapshotPayload snapshot,
                                    String dimension) {
        GroundMesh selected = GroundMesh.empty();
        AABB selectedBounds = selectedBounds(snapshot);
        if (selectedBounds != null) {
            selected = buildRectangle(minecraft, selectedBounds,
                    (int) Math.floor(selectedBounds.minY), MAX_SELECTED_TERRAIN_SAMPLES);
        }

        List<OutlineCandidate> candidates = new ArrayList<>();
        for (ClaimOutlineSummary outline : snapshot.outlines()) {
            if (outline == null || !dimension.equals(outline.dimensionId())
                    || !snapshot.outlinesVisible() && !isPendingPallet(outline.type())) {
                continue;
            }
            AABB bounds = outlineBounds(outline);
            if (!withinRenderDistance(minecraft, bounds)) {
                continue;
            }
            candidates.add(new OutlineCandidate(outline, bounds, colorFor(outline.type())));
        }
        int terrainBudget = Math.max(0, MAX_TERRAIN_SAMPLES - selected.samplesUsed());
        List<CachedOutline> nearby = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            OutlineCandidate candidate = candidates.get(index);
            int candidatesLeft = candidates.size() - index;
            int quota = terrainBudget < 4 ? 0 : Math.min(MAX_OUTLINE_TERRAIN_SAMPLES,
                    Math.max(4, terrainBudget / candidatesLeft));
            GroundMesh mesh = quota < 4
                    ? GroundMesh.empty()
                    : buildRectangle(minecraft, candidate.bounds(), candidate.summary().minY(), quota);
            terrainBudget = Math.max(0, terrainBudget - mesh.samplesUsed());
            nearby.add(new CachedOutline(candidate.summary(), candidate.bounds(), mesh, candidate.color()));
        }
        return new Cache(selected, selectedBounds, List.copyOf(nearby));
    }

    private static void renderGroundDecals(MultiBufferSource.BufferSource buffers,
                                           PoseStack.Pose pose,
                                           ClaimModeSnapshotPayload snapshot,
                                           double time) {
        double breath = 0.62D + 0.16D * Math.sin(time * 0.11D);
        Color selectedColor = snapshot.addMode() ? Color.ADD : Color.REMOVE;
        VertexConsumer glow = buffers.getBuffer(GLOW_RENDER_TYPE);

        drawMesh(glow, pose, cache.selected(), GLOW_WIDTH, 0.012D,
                selectedColor, 0.22D * breath, time, true);
        drawMesh(glow, pose, cachedHoverMesh, GLOW_WIDTH, 0.013D,
                selectedColor, 0.22D * breath, time, true);
        for (CachedOutline outline : cache.nearby()) {
            boolean pending = isPendingPallet(outline.summary().type());
            drawMesh(glow, pose, outline.mesh(), GLOW_WIDTH * 0.74D, 0.010D,
                    outline.color(), (pending ? 0.22D : 0.075D) * breath, time, pending);
        }
        buffers.endBatch(GLOW_RENDER_TYPE);

        VertexConsumer core = buffers.getBuffer(CORE_RENDER_TYPE);
        drawMesh(core, pose, cache.selected(), CORE_WIDTH, 0.021D,
                selectedColor, 0.94D, time, true);
        drawMesh(core, pose, cachedHoverMesh, CORE_WIDTH, 0.022D,
                selectedColor, 0.94D, time, true);
        for (CachedOutline outline : cache.nearby()) {
            boolean pending = isPendingPallet(outline.summary().type());
            drawMesh(core, pose, outline.mesh(), CORE_WIDTH * 0.78D, 0.019D,
                    outline.color(), pending ? 0.94D : 0.46D, time, pending);
        }
        buffers.endBatch(CORE_RENDER_TYPE);
    }

    private static void renderRails(MultiBufferSource.BufferSource buffers,
                                    PoseStack poseStack,
                                    ClaimModeSnapshotPayload snapshot,
                                    HoverTarget hover,
                                    double time) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Color selectedColor = snapshot.addMode() ? Color.ADD : Color.REMOVE;
        float pulse = (float) (0.82D + 0.18D * (0.5D + 0.5D * Math.sin(time * 0.12D)));

        if (cache.selectedBounds() != null) {
            drawBox(poseStack, lines, cache.selectedBounds(), selectedColor, pulse);
        }
        if (hover != null) {
            drawBox(poseStack, lines, hover.bounds(), selectedColor, pulse);
        }
        for (CachedOutline outline : cache.nearby()) {
            drawBox(poseStack, lines, outline.bounds(), outline.color(),
                    isPendingPallet(outline.summary().type()) ? pulse : 0.52F);
        }
        if (snapshot.hasAnchor()) {
            drawAnchor(poseStack, lines, snapshot, selectedColor);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void drawBox(PoseStack poseStack,
                                VertexConsumer lines,
                                AABB box,
                                Color color,
                                float alpha) {
        LevelRenderer.renderLineBox(poseStack, lines,
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                color.redF(), color.greenF(), color.blueF(), alpha);
    }

    private static void drawAnchor(PoseStack poseStack,
                                   VertexConsumer lines,
                                   ClaimModeSnapshotPayload snapshot,
                                   Color color) {
        double x = snapshot.anchorX();
        double y = snapshot.anchorY();
        double z = snapshot.anchorZ();
        drawBox(poseStack, lines, new AABB(x - 0.24D, y, z - 0.24D,
                x + 0.24D, y + 1.85D, z + 0.24D), color, 0.92F);

        double radians = Math.toRadians(snapshot.anchorYaw());
        Vec3 direction = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians)).normalize();
        Vec3 start = new Vec3(x, y + 1.25D, z);
        Vec3 tip = start.add(direction.scale(1.15D));
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x).scale(0.18D);
        Vec3 base = tip.subtract(direction.scale(0.30D));
        PoseStack.Pose pose = poseStack.last();
        lineVertex(lines, pose, start, tip, color, 255);
        lineVertex(lines, pose, base.add(side), tip, color, 255);
        lineVertex(lines, pose, base.subtract(side), tip, color, 255);
    }

    private static void renderLabels(PoseStack poseStack,
                                     MultiBufferSource.BufferSource buffers,
                                     Minecraft minecraft,
                                     Vec3 camera,
                                     ClaimModeSnapshotPayload snapshot) {
        if (minecraft.font == null || minecraft.getEntityRenderDispatcher() == null
                || !snapshot.outlinesVisible()) {
            return;
        }
        int shown = 0;
        for (CachedOutline outline : cache.nearby()) {
            if (isPendingPallet(outline.summary().type())
                    || shown >= MAX_WORLD_LABELS
                    || !withinLabelDistance(minecraft, outline.bounds())) {
                continue;
            }
            String owner = outline.summary().ownerName().isBlank()
                    ? "Unassigned" : outline.summary().ownerName();
            String label = displayType(outline.summary().type()) + "  |  " + owner;
            drawWorldLabel(poseStack, buffers, minecraft, camera,
                    outline.bounds().getCenter().x,
                    outline.bounds().maxY + 0.38D,
                    outline.bounds().getCenter().z,
                    label, outline.color());
            shown++;
        }
    }

    private static void drawWorldLabel(PoseStack poseStack,
                                       MultiBufferSource.BufferSource buffers,
                                       Minecraft minecraft,
                                       Vec3 camera,
                                       double x,
                                       double y,
                                       double z,
                                       String text,
                                       Color color) {
        poseStack.pushPose();
        poseStack.translate(x - camera.x, y - camera.y, z - camera.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        Matrix4f matrix = poseStack.last().pose();
        float textX = -minecraft.font.width(text) / 2.0F;
        int background = (int) (minecraft.options.getBackgroundOpacity(0.34F) * 255.0F) << 24;
        minecraft.font.drawInBatch(text, textX, 0.0F, color.argb(), false,
                matrix, buffers, Font.DisplayMode.NORMAL, background, 0xF000F0);
        poseStack.popPose();
    }

    private static HoverTarget resolvePalletHover(Minecraft minecraft,
                                                   ClaimModeSnapshotPayload snapshot) {
        if (ClaimToolKind.byName(snapshot.kind()).selectionType() != ClaimSelectionType.BLOCK_TARGET
                || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || minecraft.level == null) {
            return null;
        }
        BlockPos hitPos = hit.getBlockPos();
        BlockState state = minecraft.level.getBlockState(hitPos);
        if (!(state.getBlock() instanceof PalletBlock)) {
            return null;
        }
        BlockPos master = PalletBlock.getMasterPos(state, hitPos);
        return new HoverTarget(master, new AABB(
                master.getX() - 1.0D, master.getY(), master.getZ() - 1.0D,
                master.getX() + 2.0D, master.getY() + 1.02D, master.getZ() + 2.0D));
    }

    private static GroundMesh buildRectangle(Minecraft minecraft,
                                             AABB bounds,
                                             int referenceY,
                                             int maxSamples) {
        if (maxSamples < 4) {
            return GroundMesh.empty();
        }
        double[][] points = {
                {bounds.minX, bounds.minZ},
                {bounds.maxX, bounds.minZ},
                {bounds.maxX, bounds.maxZ},
                {bounds.minX, bounds.maxZ}
        };
        List<GroundSegment> segments = new ArrayList<>();
        double perimeter = 2.0D * ((bounds.maxX - bounds.minX) + (bounds.maxZ - bounds.minZ));
        if (perimeter < 1.0E-5D) {
            return GroundMesh.empty();
        }
        double sampleLength = Math.max(SAMPLE_LENGTH, perimeter / Math.max(4, maxSamples - 4));
        double distance = 0.0D;
        int index = 0;
        int samplesUsed = 0;
        for (int edge = 0; edge < points.length; edge++) {
            double[] start = points[edge];
            double[] end = points[(edge + 1) % points.length];
            double edgeX = end[0] - start[0];
            double edgeZ = end[1] - start[1];
            double edgeLength = Math.hypot(edgeX, edgeZ);
            if (edgeLength < 1.0E-5D) {
                continue;
            }
            int edgesAfter = points.length - edge - 1;
            int available = maxSamples - samplesUsed - edgesAfter;
            if (available <= 0) {
                break;
            }
            int samples = Math.min(available,
                    Math.max(1, (int) Math.ceil(edgeLength / sampleLength)));
            for (int sample = 0; sample < samples; sample++) {
                double t0 = sample / (double) samples;
                double t1 = (sample + 1) / (double) samples;
                double x0 = start[0] + edgeX * t0;
                double z0 = start[1] + edgeZ * t0;
                double x1 = start[0] + edgeX * t1;
                double z1 = start[1] + edgeZ * t1;
                double y0 = groundY(minecraft, x0, z0, referenceY);
                double y1 = groundY(minecraft, x1, z1, referenceY);
                double length = Math.hypot(x1 - x0, z1 - z0);
                if (Math.abs(y1 - y0) <= MAX_STEP_DELTA) {
                    segments.add(new GroundSegment(index++, x0, y0, z0, x1, y1, z1,
                            distance, distance + length));
                }
                samplesUsed++;
                distance += length;
            }
        }
        return new GroundMesh(List.copyOf(segments), distance, samplesUsed);
    }

    private static double groundY(Minecraft minecraft, double x, double z, int referenceY) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return referenceY;
        }
        Vec3 from = new Vec3(x, referenceY + 4.0D, z);
        Vec3 to = new Vec3(x, referenceY - 5.0D, z);
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, minecraft.player));
        return hit.getType() == HitResult.Type.MISS ? referenceY : hit.getLocation().y;
    }

    private static void drawMesh(VertexConsumer consumer,
                                 PoseStack.Pose pose,
                                 GroundMesh mesh,
                                 double width,
                                 double height,
                                 Color color,
                                 double baseAlpha,
                                 double time,
                                 boolean animated) {
        if (mesh == null || mesh.segments().isEmpty()) {
            return;
        }
        for (GroundSegment segment : mesh.segments()) {
            double hazard = ((segment.index() / 2) & 1) == 0 ? 1.0D : 0.72D;
            double pulse = animated ? pulseStrength(segment.midDistance(), mesh.perimeter(), time) : 0.0D;
            double alpha = Math.min(1.0D, baseAlpha * hazard + pulse * 0.28D);
            drawStrip(consumer, pose, segment, width, height, color, alpha);
        }
    }

    private static void drawStrip(VertexConsumer consumer,
                                  PoseStack.Pose pose,
                                  GroundSegment segment,
                                  double width,
                                  double height,
                                  Color color,
                                  double alpha) {
        double dx = segment.x1() - segment.x0();
        double dz = segment.z1() - segment.z0();
        double length = Math.hypot(dx, dz);
        int alphaByte = alpha(alpha);
        if (length < 1.0E-6D || alphaByte <= 0) {
            return;
        }
        double offsetX = -dz / length * width * 0.5D;
        double offsetZ = dx / length * width * 0.5D;
        vertex(consumer, pose, segment.x0() + offsetX, segment.y0() + height,
                segment.z0() + offsetZ, color, alphaByte);
        vertex(consumer, pose, segment.x0() - offsetX, segment.y0() + height,
                segment.z0() - offsetZ, color, alphaByte);
        vertex(consumer, pose, segment.x1() - offsetX, segment.y1() + height,
                segment.z1() - offsetZ, color, alphaByte);
        vertex(consumer, pose, segment.x1() + offsetX, segment.y1() + height,
                segment.z1() + offsetZ, color, alphaByte);
    }

    private static void vertex(VertexConsumer consumer,
                               PoseStack.Pose pose,
                               double x,
                               double y,
                               double z,
                               Color color,
                               int alpha) {
        consumer.addVertex(pose.pose(), (float) x, (float) y, (float) z)
                .setColor(color.red(), color.green(), color.blue(), alpha);
    }

    private static void lineVertex(VertexConsumer consumer,
                                   PoseStack.Pose pose,
                                   Vec3 from,
                                   Vec3 to,
                                   Color color,
                                   int alpha) {
        Vec3 normal = to.subtract(from).normalize();
        consumer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(color.red(), color.green(), color.blue(), alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(color.red(), color.green(), color.blue(), alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static double pulseStrength(double distance, double perimeter, double time) {
        if (perimeter <= 0.0D) {
            return 0.0D;
        }
        double head = (time * 0.16D) % perimeter;
        double delta = Math.abs(distance - head);
        delta = Math.min(delta, perimeter - delta);
        if (delta >= 1.8D) {
            return 0.0D;
        }
        double normalized = 1.0D - delta / 1.8D;
        return normalized * normalized;
    }

    private static AABB selectedBounds(ClaimModeSnapshotPayload snapshot) {
        if (!snapshot.hasPos1() || !snapshot.hasPos2()) {
            return null;
        }
        int minX = Math.min(snapshot.pos1X(), snapshot.pos2X());
        int minY = Math.min(snapshot.pos1Y(), snapshot.pos2Y());
        int minZ = Math.min(snapshot.pos1Z(), snapshot.pos2Z());
        int maxX = Math.max(snapshot.pos1X(), snapshot.pos2X());
        int maxY = Math.max(snapshot.pos1Y(), snapshot.pos2Y());
        int maxZ = Math.max(snapshot.pos1Z(), snapshot.pos2Z());
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    private static AABB outlineBounds(ClaimOutlineSummary outline) {
        return new AABB(outline.minX(), outline.minY(), outline.minZ(),
                outline.maxX() + 1.0D, outline.maxY() + 1.0D, outline.maxZ() + 1.0D);
    }

    private static boolean withinRenderDistance(Minecraft minecraft, AABB bounds) {
        return minecraft.player != null
                && minecraft.player.position().distanceToSqr(bounds.getCenter()) <= RENDER_DISTANCE_SQ;
    }

    private static boolean withinLabelDistance(Minecraft minecraft, AABB bounds) {
        return minecraft.player != null
                && minecraft.player.position().distanceToSqr(bounds.getCenter()) <= 64.0D * 64.0D;
    }

    private static long geometrySignature(ClaimModeSnapshotPayload snapshot, String dimension) {
        return Objects.hash(dimension, snapshot.kind(), snapshot.addMode(), snapshot.outlinesVisible(),
                snapshot.hasPos1(), snapshot.pos1X(), snapshot.pos1Y(), snapshot.pos1Z(),
                snapshot.hasPos2(), snapshot.pos2X(), snapshot.pos2Y(), snapshot.pos2Z(),
                snapshot.outlines());
    }

    private static Color colorFor(String type) {
        String normalized = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (normalized.equals("PENDING_PALLET_ADD")) {
            return Color.ADD;
        }
        if (normalized.equals("PENDING_PALLET_REMOVE")) {
            return Color.REMOVE;
        }
        if (normalized.contains("SHOP")) {
            return new Color(63, 198, 244);
        }
        if (normalized.contains("STOCK")) {
            return new Color(87, 148, 255);
        }
        if (normalized.contains("PALLET")) {
            return new Color(244, 185, 66);
        }
        if (normalized.contains("SAFE")) {
            return new Color(255, 106, 137);
        }
        if (normalized.contains("VIEW")) {
            return new Color(193, 130, 255);
        }
        if (normalized.contains("PREMISE") || normalized.contains("BANK")) {
            return new Color(76, 218, 166);
        }
        return new Color(149, 180, 211);
    }

    private static boolean isPendingPallet(String type) {
        return type != null && type.toUpperCase(Locale.ROOT).startsWith("PENDING_PALLET_");
    }

    private static String displayType(String type) {
        if (type == null || type.isBlank()) {
            return "UBS Claim";
        }
        String[] words = type.toLowerCase(Locale.ROOT).split("[_\\s]+");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    private static int alpha(double value) {
        return (int) Math.round(Math.max(0.0D, Math.min(1.0D, value)) * 255.0D);
    }

    private static RenderType createDecalRenderType(
            String name,
            net.minecraft.client.renderer.RenderStateShard.TransparencyStateShard transparency) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 8192, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderType.POSITION_COLOR_SHADER)
                        .setTransparencyState(transparency)
                        .setCullState(RenderType.NO_CULL)
                        .setDepthTestState(RenderType.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderType.COLOR_WRITE)
                        .setLayeringState(RenderType.VIEW_OFFSET_Z_LAYERING)
                        .createCompositeState(false));
    }

    private record GroundSegment(int index,
                                 double x0, double y0, double z0,
                                 double x1, double y1, double z1,
                                 double startDistance, double endDistance) {
        private double midDistance() {
            return (startDistance + endDistance) * 0.5D;
        }
    }

    private record GroundMesh(List<GroundSegment> segments, double perimeter, int samplesUsed) {
        private static GroundMesh empty() {
            return new GroundMesh(List.of(), 0.0D, 0);
        }
    }

    private record OutlineCandidate(ClaimOutlineSummary summary,
                                    AABB bounds,
                                    Color color) {
    }

    private record CachedOutline(ClaimOutlineSummary summary,
                                 AABB bounds,
                                 GroundMesh mesh,
                                 Color color) {
    }

    private record Cache(GroundMesh selected,
                         AABB selectedBounds,
                         List<CachedOutline> nearby) {
        private static Cache empty() {
            return new Cache(GroundMesh.empty(), null, List.of());
        }
    }

    private record HoverTarget(BlockPos master, AABB bounds) {
    }

    private record Color(int red, int green, int blue) {
        private static final Color ADD = new Color(62, 245, 154);
        private static final Color REMOVE = new Color(255, 83, 106);

        private float redF() {
            return red / 255.0F;
        }

        private float greenF() {
            return green / 255.0F;
        }

        private float blueF() {
            return blue / 255.0F;
        }

        private int argb() {
            return 0xFF000000 | red << 16 | green << 8 | blue;
        }
    }
}
