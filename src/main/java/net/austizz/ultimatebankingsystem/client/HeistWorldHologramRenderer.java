package net.austizz.ultimatebankingsystem.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.BankVaultDoorBlock;
import net.austizz.ultimatebankingsystem.block.custom.SecureSafeBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.SecureSafeBlockEntity;
import net.austizz.ultimatebankingsystem.heist.HeistDrillGeometry;
import net.austizz.ultimatebankingsystem.heist.HeistExfillZone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Client-only, entity-free world labels for active heist objectives. */
public final class HeistWorldHologramRenderer {
    private static final float LABEL_SCALE = 0.021F;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final double DRILL_RENDER_RANGE_SQ = 72.0D * 72.0D;
    private static final double EXFILL_RENDER_RANGE_SQ = 112.0D * 112.0D;

    private HeistWorldHologramRenderer() {}

    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        if (event == null || minecraft == null || minecraft.level == null || minecraft.player == null
                || minecraft.font == null || minecraft.getEntityRenderDispatcher() == null
                || minecraft.options.hideGui || !HeistClientState.active()) {
            return;
        }

        String dimension = minecraft.level.dimension().location().toString();
        Vec3 camera = event.getCamera().getPosition();
        Vec3 player = minecraft.player.position();
        long clientTick = minecraft.level.getGameTime();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean drewLabel = false;

        for (HeistClientState.HudDrillEntry drill : HeistClientState.hudDrills()) {
            if (drill == null || !dimension.equals(drill.dimension())
                    || !minecraft.level.hasChunkAt(drill.pos())) {
                continue;
            }
            Vec3 anchor = drillAnchor(minecraft.level, drill);
            if (anchor == null || anchor.distanceToSqr(player) > DRILL_RENDER_RANGE_SQ) continue;
            drawLabel(poseStack, buffers, minecraft, camera, anchor, drillLines(drill, clientTick));
            drewLabel = true;
        }

        HeistExfillBorderClientState.Snapshot exfill = HeistExfillBorderClientState.snapshot();
        if (exfill.valid() && dimension.equals(exfill.dimension())) {
            Vec3 anchor = exfillAnchor(minecraft, exfill);
            if (anchor.distanceToSqr(player) <= EXFILL_RENDER_RANGE_SQ) {
                drawLabel(poseStack, buffers, minecraft, camera, anchor,
                        exfillLines(HeistClientState.hud(), clientTick));
                drewLabel = true;
            }
        }

        if (drewLabel) buffers.endBatch();
    }

    private static Vec3 drillAnchor(ClientLevel level, HeistClientState.HudDrillEntry drill) {
        BlockPos pos = drill.pos();
        BlockState state = level.getBlockState(pos);
        if (drill.kind() == HeistClientState.DrillKind.VAULT) {
            if (!state.is(ModBlocks.BANK_VAULT_DOOR.get())) return null;
            Direction facing = state.hasProperty(BankVaultDoorBlock.FACING)
                    ? state.getValue(BankVaultDoorBlock.FACING) : Direction.NORTH;
            Vec3 tip = HeistDrillGeometry.vaultVisibleTip(pos, facing);
            Vec3 outward = new Vec3(tip.x - (pos.getX() + 0.5D), 0.0D,
                    tip.z - (pos.getZ() + 0.5D));
            if (outward.lengthSqr() > 1.0E-6D) outward = outward.normalize().scale(0.38D);
            return tip.add(outward).add(0.0D, 0.92D, 0.0D);
        }

        if (!SecureSafeBlock.isSafeBlock(state)) return null;
        Direction facing = state.hasProperty(SecureSafeBlock.FACING)
                ? state.getValue(SecureSafeBlock.FACING) : Direction.NORTH;
        boolean tall = state.is(ModBlocks.STANDING_SAFE.get())
                || level.getBlockEntity(pos) instanceof SecureSafeBlockEntity safe && safe.isTallSafe();
        return new Vec3(pos.getX() + 0.5D + facing.getStepX() * 1.02D,
                pos.getY() + (tall ? 2.18D : 1.48D),
                pos.getZ() + 0.5D + facing.getStepZ() * 1.02D);
    }

    private static Vec3 exfillAnchor(Minecraft minecraft,
                                     HeistExfillBorderClientState.Snapshot exfill) {
        double x = exfill.boundary().centerX();
        double z = exfill.boundary().centerZ();
        Vec3 from = new Vec3(x, exfill.referenceY() + 12.0D, z);
        Vec3 to = new Vec3(x, exfill.referenceY() - 8.0D, z);
        BlockHitResult hit = minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, minecraft.player));
        double groundY = hit.getType() == HitResult.Type.MISS ? exfill.referenceY() : hit.getLocation().y;
        return new Vec3(x, groundY + 1.65D, z);
    }

    private static List<LabelLine> drillLines(HeistClientState.HudDrillEntry drill, long clientTick) {
        List<LabelLine> lines = new ArrayList<>(3);
        String title = drill.kind() == HeistClientState.DrillKind.VAULT
                ? "THERMAL DRILL" : "HEIST DRILL";
        lines.add(new LabelLine(title, 0xFF73D9FF));
        int remaining = HeistClientState.estimatedDrillRemainingTicks(drill, clientTick);
        switch (drill.status()) {
            case COMPLETE -> {
                lines.add(new LabelLine("DRILLING COMPLETE", 0xFF58E4A7));
                lines.add(new LabelLine("Hold " + PickpocketKeyMappings.getBoundKeyName()
                        + " to recover", 0xFFE7F6FF));
            }
            case STALLED -> {
                lines.add(new LabelLine("STALLED - " + formatClock(remaining) + " remaining", 0xFFFF8B63));
                lines.add(new LabelLine("Hold " + PickpocketKeyMappings.getBoundKeyName()
                        + " to restart", 0xFFFFD36A));
            }
            case RUNNING -> {
                int progress = Math.max(0, Math.min(100,
                        (int) Math.round((1.0D - remaining / (double) drill.totalTicks()) * 100.0D)));
                lines.add(new LabelLine("DRILLING - " + formatClock(remaining), 0xFF58E4A7));
                lines.add(new LabelLine("Progress: " + progress + "%", 0xFFE7F6FF));
            }
        }
        return List.copyOf(lines);
    }

    private static List<LabelLine> exfillLines(net.austizz.ultimatebankingsystem.network.HeistHudPayload hud,
                                                long clientTick) {
        HeistExfillZone.VisualState state = HeistExfillZone.VisualState.byName(hud.exfillState());
        List<LabelLine> lines = new ArrayList<>(3);
        int titleColor = state == HeistExfillZone.VisualState.ACTIVE ? 0xFF58E4A7
                : state == HeistExfillZone.VisualState.CONTESTED ? 0xFFFF8B63 : 0xFFFFD36A;
        lines.add(new LabelLine("EXTRACTION ZONE", titleColor));
        if (!hud.exfillLootArmed()) {
            lines.add(new LabelLine("INACTIVE - SECURE LOOT", 0xFFFFD36A));
            lines.add(new LabelLine(crewLine(hud), 0xFFE7F6FF));
        } else if (state == HeistExfillZone.VisualState.ACTIVE) {
            int remaining = HeistClientState.estimatedExfillRemainingTicks(clientTick);
            lines.add(new LabelLine("EXTRACTING - " + formatTenths(remaining), 0xFF58E4A7));
            lines.add(new LabelLine(crewLine(hud) + " - remain inside", 0xFFE7F6FF));
        } else {
            lines.add(new LabelLine("WAITING FOR CREW", 0xFFFF8B63));
            lines.add(new LabelLine(crewLine(hud) + " - all crew must enter", 0xFFE7F6FF));
        }
        return List.copyOf(lines);
    }

    private static String crewLine(net.austizz.ultimatebankingsystem.network.HeistHudPayload hud) {
        return "Crew inside: " + Math.max(0, hud.exfillCrewInside()) + "/"
                + Math.max(0, hud.exfillCrewRequired());
    }

    private static void drawLabel(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                  Minecraft minecraft, Vec3 camera, Vec3 anchor,
                                  List<LabelLine> lines) {
        if (lines == null || lines.isEmpty()) return;
        int background = (int) (minecraft.options.getBackgroundOpacity(0.36F) * 255.0F) << 24;
        poseStack.pushPose();
        poseStack.translate(anchor.x - camera.x, anchor.y - camera.y, anchor.z - camera.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        Matrix4f matrix = poseStack.last().pose();
        Font font = minecraft.font;
        for (int index = 0; index < lines.size(); index++) {
            LabelLine line = lines.get(index);
            float x = -font.width(line.text()) / 2.0F;
            font.drawInBatch(line.text(), x, index * 10.0F, line.color(), false, matrix, buffers,
                    Font.DisplayMode.NORMAL, background, FULL_BRIGHT);
        }
        poseStack.popPose();
    }

    private static String formatClock(int ticks) {
        int seconds = Math.max(0, (ticks + 19) / 20);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static String formatTenths(int ticks) {
        int tenths = Math.max(0, (ticks + 1) / 2);
        return String.format(Locale.ROOT, "%d.%ds", tenths / 10, tenths % 10);
    }

    private record LabelLine(String text, int color) {}
}
