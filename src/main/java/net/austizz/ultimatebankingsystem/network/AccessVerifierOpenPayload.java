package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AccessVerifierOpenPayload(
        String dimensionId,
        int x,
        int y,
        int z,
        boolean configured,
        boolean authenticated,
        boolean successCircuitActive,
        boolean failCircuitActive,
        int successSignal,
        int failSignal,
        int maxAttempts,
        int attemptsRemaining,
        String message,
        boolean messageSuccess
) implements CustomPacketPayload {
    public static final Type<AccessVerifierOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "access_verifier_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AccessVerifierOpenPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.x());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.y());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.z());
                        ByteBufCodecs.BOOL.encode(buf, payload.configured());
                        ByteBufCodecs.BOOL.encode(buf, payload.authenticated());
                        ByteBufCodecs.BOOL.encode(buf, payload.successCircuitActive());
                        ByteBufCodecs.BOOL.encode(buf, payload.failCircuitActive());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.successSignal());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.failSignal());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.maxAttempts());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.attemptsRemaining());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.message());
                        ByteBufCodecs.BOOL.encode(buf, payload.messageSuccess());
                    },
                    buf -> new AccessVerifierOpenPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)
                    )
            );

    public AccessVerifierOpenPayload {
        dimensionId = dimensionId == null ? "" : dimensionId;
        message = message == null ? "" : message;
    }

    @Override
    public Type<AccessVerifierOpenPayload> type() {
        return TYPE;
    }
}
