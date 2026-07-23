package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SecureSafeOpenPayload(
        String dimensionId,
        int x,
        int y,
        int z,
        boolean configured,
        boolean authenticated,
        boolean tall,
        boolean chestUpgradeInstalled,
        boolean open,
        int maxAttempts,
        int attemptsRemaining,
        String message,
        boolean messageSuccess
) implements CustomPacketPayload {
    public static final Type<SecureSafeOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "secure_safe_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SecureSafeOpenPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.x());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.y());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.z());
                        ByteBufCodecs.BOOL.encode(buf, payload.configured());
                        ByteBufCodecs.BOOL.encode(buf, payload.authenticated());
                        ByteBufCodecs.BOOL.encode(buf, payload.tall());
                        ByteBufCodecs.BOOL.encode(buf, payload.chestUpgradeInstalled());
                        ByteBufCodecs.BOOL.encode(buf, payload.open());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.maxAttempts());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.attemptsRemaining());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.message());
                        ByteBufCodecs.BOOL.encode(buf, payload.messageSuccess());
                    },
                    buf -> new SecureSafeOpenPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)
                    )
            );

    public SecureSafeOpenPayload {
        dimensionId = dimensionId == null ? "" : dimensionId;
        message = message == null ? "" : message;
    }

    @Override
    public Type<SecureSafeOpenPayload> type() {
        return TYPE;
    }
}
