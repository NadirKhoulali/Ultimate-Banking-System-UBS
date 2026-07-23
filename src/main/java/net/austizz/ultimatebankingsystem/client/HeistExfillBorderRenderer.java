package net.austizz.ultimatebankingsystem.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.austizz.ultimatebankingsystem.heist.HeistExfillZone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/** Cached, client-only world decal renderer for the configured heist extraction perimeter. */
public final class HeistExfillBorderRenderer {
    private static final HeistExfillBorderProfile PROFILE = HeistExfillBorderProfile.TACTICAL;
    private static final RenderType GLOW_RENDER_TYPE = createDecalRenderType(
            "ubs_heist_exfill_glow", RenderType.ADDITIVE_TRANSPARENCY);
    private static final RenderType CORE_RENDER_TYPE = createDecalRenderType(
            "ubs_heist_exfill_core", RenderType.TRANSLUCENT_TRANSPARENCY);
    private static final double GLOW_HEIGHT = 0.012D;
    private static final double CORE_HEIGHT = 0.018D;
    private static final double PULSE_HEIGHT = 0.024D;
    private static final double MAX_STEP_DELTA = 1.25D;

    private static GroundMesh cachedMesh = GroundMesh.empty();
    private static long cachedRevision = Long.MIN_VALUE;
    private static long lastTerrainRefreshTick = Long.MIN_VALUE;

    private HeistExfillBorderRenderer() {}

    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        if (event == null || minecraft == null || minecraft.level == null || minecraft.player == null
                || !HeistExfillBorderClientState.visible()) return;
        HeistExfillBorderClientState.Snapshot snapshot = HeistExfillBorderClientState.snapshot();
        if (!snapshot.dimension().equals(minecraft.level.dimension().location().toString())) return;
        double dx = minecraft.player.getX() - snapshot.boundary().centerX();
        double dz = minecraft.player.getZ() - snapshot.boundary().centerZ();
        if (dx * dx + dz * dz > PROFILE.renderDistance() * PROFILE.renderDistance()) return;

        long gameTick = minecraft.level.getGameTime();
        long revision = HeistExfillBorderClientState.revision();
        if (revision != cachedRevision
                || gameTick - lastTerrainRefreshTick >= PROFILE.terrainRefreshTicks()) {
            cachedMesh = buildMesh(minecraft, snapshot);
            cachedRevision = revision;
            lastTerrainRefreshTick = gameTick;
        }
        if (cachedMesh.segments().isEmpty()) return;

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double animationTime = gameTick + partialTick;
        VisualProfile visual = visual(snapshot.state(), animationTime);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        PoseStack.Pose pose = poseStack.last();

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer glowQuads = buffers.getBuffer(GLOW_RENDER_TYPE);
        for (GroundSegment segment : cachedMesh.segments()) {
            double hazard = ((segment.index() / 2) & 1) == 0 ? 1.0D : 0.68D;
            drawStrip(glowQuads, pose, segment, PROFILE.glowWidth(), GLOW_HEIGHT,
                    visual.red(), visual.green(), visual.blue(),
                    alpha(visual.glowAlpha() * hazard));
            double pulse = pulseStrength(segment.midDistance(), cachedMesh.perimeterLength(), animationTime,
                    snapshot.state());
            if (pulse > 0.015D) {
                drawStrip(glowQuads, pose, segment, PROFILE.glowWidth() * 0.68D, PULSE_HEIGHT,
                        235, 255, 244, alpha(pulse * visual.pulseAlpha()));
            }
        }
        buffers.endBatch(GLOW_RENDER_TYPE);

        VertexConsumer coreQuads = buffers.getBuffer(CORE_RENDER_TYPE);
        for (GroundSegment segment : cachedMesh.segments()) {
            double hazard = ((segment.index() / 2) & 1) == 0 ? 1.0D : 0.68D;
            drawStrip(coreQuads, pose, segment, PROFILE.coreWidth(), CORE_HEIGHT,
                    visual.red(), visual.green(), visual.blue(),
                    alpha(visual.coreAlpha() * hazard));
        }
        buffers.endBatch(CORE_RENDER_TYPE);
        poseStack.popPose();
    }

    public static void clearCache() {
        cachedMesh = GroundMesh.empty();
        cachedRevision = Long.MIN_VALUE;
        lastTerrainRefreshTick = Long.MIN_VALUE;
    }

    private static GroundMesh buildMesh(Minecraft minecraft,
                                        HeistExfillBorderClientState.Snapshot snapshot) {
        HeistExfillZone.Boundary boundary = snapshot.boundary();
        if (!boundary.valid()) return GroundMesh.empty();
        List<GroundSegment> segments = new ArrayList<>();
        double distance = 0.0D;
        int index = 0;
        List<HeistExfillZone.Point> points = boundary.points();
        for (int edge = 0; edge < points.size(); edge++) {
            HeistExfillZone.Point start = points.get(edge);
            HeistExfillZone.Point end = points.get((edge + 1) % points.size());
            double edgeX = end.x() - start.x();
            double edgeZ = end.z() - start.z();
            double edgeLength = Math.hypot(edgeX, edgeZ);
            if (edgeLength < 1.0E-5D) continue;
            int samples = Math.max(1, (int) Math.ceil(edgeLength / PROFILE.sampleLength()));
            for (int sample = 0; sample < samples; sample++) {
                double t0 = sample / (double) samples;
                double t1 = (sample + 1) / (double) samples;
                double x0 = start.x() + edgeX * t0;
                double z0 = start.z() + edgeZ * t0;
                double x1 = start.x() + edgeX * t1;
                double z1 = start.z() + edgeZ * t1;
                double y0 = groundY(minecraft, x0, z0, snapshot.referenceY());
                double y1 = groundY(minecraft, x1, z1, snapshot.referenceY());
                double length = Math.hypot(x1 - x0, z1 - z0);
                if (Math.abs(y1 - y0) <= MAX_STEP_DELTA) {
                    segments.add(new GroundSegment(index++, x0, y0, z0, x1, y1, z1,
                            distance, distance + length));
                }
                distance += length;
            }
        }
        return new GroundMesh(List.copyOf(segments), Math.max(distance, boundary.perimeterLength()));
    }

    private static double groundY(Minecraft minecraft, double x, double z, int referenceY) {
        ClientLevel level = minecraft.level;
        if (level == null) return referenceY + CORE_HEIGHT;
        Vec3 from = new Vec3(x, referenceY + PROFILE.floorSearchAbove(), z);
        Vec3 to = new Vec3(x, referenceY - PROFILE.floorSearchBelow(), z);
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, minecraft.player));
        if (hit.getType() != HitResult.Type.MISS) return hit.getLocation().y;
        return referenceY;
    }

    private static VisualProfile visual(HeistExfillZone.VisualState state, double time) {
        double breath = 0.5D + 0.5D * Math.sin(time * 0.105D);
        int rgb;
        double core;
        double glow;
        double pulse;
        if (state == HeistExfillZone.VisualState.ACTIVE) {
            rgb = PROFILE.activeRgb();
            core = 0.78D + breath * 0.17D;
            glow = 0.13D + breath * 0.11D;
            pulse = 0.88D;
        } else if (state == HeistExfillZone.VisualState.CONTESTED) {
            boolean flash = ((long) Math.floor(time / 5.0D) & 1L) == 0L;
            rgb = flash ? PROFILE.contestedRgb() : PROFILE.contestedFlashRgb();
            core = flash ? 0.92D : 0.68D;
            glow = flash ? 0.28D : 0.13D;
            pulse = 0.78D;
        } else {
            rgb = PROFILE.idleRgb();
            core = 0.34D + breath * 0.18D;
            glow = 0.055D + breath * 0.055D;
            pulse = 0.32D;
        }
        return new VisualProfile((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF,
                core, glow, pulse);
    }

    private static double pulseStrength(double distance, double perimeter, double time,
                                        HeistExfillZone.VisualState state) {
        if (perimeter <= 0.0D) return 0.0D;
        double speed = state == HeistExfillZone.VisualState.CONTESTED ? 0.26D
                : state == HeistExfillZone.VisualState.ACTIVE ? 0.18D : 0.075D;
        double head = (time * speed) % perimeter;
        double delta = Math.abs(distance - head);
        delta = Math.min(delta, perimeter - delta);
        double width = state == HeistExfillZone.VisualState.ACTIVE ? 2.15D : 1.25D;
        if (delta >= width) return 0.0D;
        double normalized = 1.0D - delta / width;
        return normalized * normalized;
    }

    private static void drawStrip(VertexConsumer consumer, PoseStack.Pose pose, GroundSegment segment,
                                  double width, double height, int red, int green, int blue, int alpha) {
        double dx = segment.x1() - segment.x0();
        double dz = segment.z1() - segment.z0();
        double length = Math.hypot(dx, dz);
        if (length < 1.0E-6D || alpha <= 0) return;
        double offsetX = -dz / length * width * 0.5D;
        double offsetZ = dx / length * width * 0.5D;
        vertex(consumer, pose, segment.x0() + offsetX, segment.y0() + height,
                segment.z0() + offsetZ, red, green, blue, alpha);
        vertex(consumer, pose, segment.x0() - offsetX, segment.y0() + height,
                segment.z0() - offsetZ, red, green, blue, alpha);
        vertex(consumer, pose, segment.x1() - offsetX, segment.y1() + height,
                segment.z1() - offsetZ, red, green, blue, alpha);
        vertex(consumer, pose, segment.x1() + offsetX, segment.y1() + height,
                segment.z1() + offsetZ, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, double x, double y, double z,
                               int red, int green, int blue, int alpha) {
        consumer.addVertex(pose.pose(), (float) x, (float) y, (float) z)
                .setColor(red, green, blue, alpha);
    }

    private static int alpha(double value) {
        return (int) Math.round(Math.max(0.0D, Math.min(1.0D, value)) * 255.0D);
    }

    private static RenderType createDecalRenderType(
            String name,
            net.minecraft.client.renderer.RenderStateShard.TransparencyStateShard transparency) {
        return RenderType.create(
                name,
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,
                4096,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderType.POSITION_COLOR_SHADER)
                        .setTransparencyState(transparency)
                        .setCullState(RenderType.NO_CULL)
                        .setDepthTestState(RenderType.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderType.COLOR_WRITE)
                        .setLayeringState(RenderType.VIEW_OFFSET_Z_LAYERING)
                        .createCompositeState(false)
        );
    }

    private record GroundSegment(int index, double x0, double y0, double z0,
                                 double x1, double y1, double z1,
                                 double startDistance, double endDistance) {
        double midDistance() {
            return (startDistance + endDistance) * 0.5D;
        }
    }

    private record GroundMesh(List<GroundSegment> segments, double perimeterLength) {
        static GroundMesh empty() {
            return new GroundMesh(List.of(), 0.0D);
        }
    }

    private record VisualProfile(int red, int green, int blue,
                                 double coreAlpha, double glowAlpha, double pulseAlpha) {}
}
