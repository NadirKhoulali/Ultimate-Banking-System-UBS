package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.network.DallasMaskAnimationPayload;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DallasMaskAnimationClientState {
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final Map<UUID, Animation> ACTIVE = new HashMap<>();

    private DallasMaskAnimationClientState() {
    }

    public static void apply(DallasMaskAnimationPayload payload) {
        if (payload == null || payload.playerId() == null || payload.durationTicks() <= 0) {
            return;
        }
        ACTIVE.put(payload.playerId(), new Animation(
                payload.puttingOn(),
                System.nanoTime(),
                payload.durationTicks()
        ));
    }

    public static AnimationSample sample(UUID playerId) {
        Animation animation = ACTIVE.get(playerId);
        if (animation == null) {
            return null;
        }

        long durationNanos = Math.max(1L, animation.durationTicks()) * NANOS_PER_TICK;
        float progress = Mth.clamp(
                (float) (System.nanoTime() - animation.startedAtNanos()) / (float) durationNanos,
                0.0F,
                1.0F
        );
        if (progress >= 1.0F) {
            ACTIVE.remove(playerId);
            return null;
        }

        float faceTravel = smoothStep(0.08F, 0.82F, progress);
        float faceAmount = animation.puttingOn() ? faceTravel : 1.0F - faceTravel;
        float reachAmount = smoothStep(0.0F, 0.25F, progress)
                * (1.0F - smoothStep(0.78F, 1.0F, progress));
        return new AnimationSample(animation.puttingOn(), progress, faceAmount, reachAmount);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private record Animation(boolean puttingOn, long startedAtNanos, int durationTicks) {
    }

    public record AnimationSample(boolean puttingOn, float progress, float faceAmount, float reachAmount) {
    }
}
