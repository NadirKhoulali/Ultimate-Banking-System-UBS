package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClaimModeActionPayload(String sessionId,
                                     String action,
                                     boolean hasTarget,
                                     int targetX,
                                     int targetY,
                                     int targetZ) implements CustomPacketPayload {
    public static final Type<ClaimModeActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "claim_mode_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimModeActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUtf(value.sessionId(), 36);
                buf.writeUtf(value.action(), 32);
                buf.writeBoolean(value.hasTarget());
                buf.writeInt(value.targetX());
                buf.writeInt(value.targetY());
                buf.writeInt(value.targetZ());
            },
            buf -> new ClaimModeActionPayload(buf.readUtf(36), buf.readUtf(32), buf.readBoolean(),
                    buf.readInt(), buf.readInt(), buf.readInt())
    );

    public ClaimModeActionPayload {
        sessionId = trim(sessionId, 36);
        action = trim(action, 32);
    }

    public static ClaimModeActionPayload withoutTarget(String sessionId, String action) {
        return new ClaimModeActionPayload(sessionId, action, false, 0, 0, 0);
    }

    private static String trim(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    @Override
    public Type<ClaimModeActionPayload> type() {
        return TYPE;
    }
}
