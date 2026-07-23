package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AccessVerifierActionPayload(
        String dimensionId,
        int x,
        int y,
        int z,
        String action,
        String pin,
        String newPin,
        int successSignal,
        int failSignal,
        int maxAttempts
) implements CustomPacketPayload {
    public static final Type<AccessVerifierActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "access_verifier_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AccessVerifierActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.x());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.y());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.z());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.action());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.pin());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.newPin());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.successSignal());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.failSignal());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.maxAttempts());
                    },
                    buf -> new AccessVerifierActionPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf)
                    )
            );

    public AccessVerifierActionPayload {
        dimensionId = dimensionId == null ? "" : dimensionId;
        action = action == null ? "" : action;
        pin = pin == null ? "" : pin;
        newPin = newPin == null ? "" : newPin;
    }

    @Override
    public Type<AccessVerifierActionPayload> type() {
        return TYPE;
    }
}
