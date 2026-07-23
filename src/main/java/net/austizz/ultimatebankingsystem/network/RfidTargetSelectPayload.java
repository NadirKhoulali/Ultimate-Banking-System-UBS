package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RfidTargetSelectPayload(
        String scannerDimensionId,
        int scannerX,
        int scannerY,
        int scannerZ,
        String targetDimensionId,
        int targetX,
        int targetY,
        int targetZ,
        String face,
        String targetType,
        String pin
) implements CustomPacketPayload {
    public static final Type<RfidTargetSelectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "rfid_target_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RfidTargetSelectPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.scannerDimensionId());
                        buf.writeVarInt(payload.scannerX());
                        buf.writeVarInt(payload.scannerY());
                        buf.writeVarInt(payload.scannerZ());
                        buf.writeUtf(payload.targetDimensionId());
                        buf.writeVarInt(payload.targetX());
                        buf.writeVarInt(payload.targetY());
                        buf.writeVarInt(payload.targetZ());
                        buf.writeUtf(payload.face());
                        buf.writeUtf(payload.targetType());
                        buf.writeUtf(payload.pin());
                    },
                    buf -> new RfidTargetSelectPayload(
                            buf.readUtf(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readUtf(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf()
                    )
            );

    public RfidTargetSelectPayload {
        scannerDimensionId = safe(scannerDimensionId);
        targetDimensionId = safe(targetDimensionId);
        face = safe(face);
        targetType = safe(targetType);
        pin = safe(pin);
    }

    @Override
    public Type<RfidTargetSelectPayload> type() {
        return TYPE;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
