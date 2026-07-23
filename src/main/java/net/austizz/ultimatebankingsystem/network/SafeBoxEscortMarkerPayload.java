package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SafeBoxEscortMarkerPayload(boolean active,
                                         String dimensionId,
                                         int rowX,
                                         int rowY,
                                         int rowZ,
                                         int doorIndex,
                                         String boxLabel) implements CustomPacketPayload {
    public static final Type<SafeBoxEscortMarkerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    UltimateBankingSystem.MODID, "safe_box_escort_marker"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SafeBoxEscortMarkerPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.BOOL.encode(buf, payload.active());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.rowX());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.rowY());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.rowZ());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.doorIndex());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.boxLabel());
                    },
                    buf -> new SafeBoxEscortMarkerPayload(
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)
                    )
            );

    public SafeBoxEscortMarkerPayload {
        dimensionId = dimensionId == null ? "" : dimensionId;
        boxLabel = boxLabel == null ? "" : boxLabel;
    }

    public static SafeBoxEscortMarkerPayload inactive() {
        return new SafeBoxEscortMarkerPayload(false, "", 0, 0, 0, 0, "");
    }

    @Override
    public Type<SafeBoxEscortMarkerPayload> type() {
        return TYPE;
    }
}
