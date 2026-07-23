package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RfidScannerActionPayload(
        String dimensionId,
        int x,
        int y,
        int z,
        String action,
        String pin,
        String text1,
        String text2,
        int int1,
        int int2,
        int int3,
        int int4,
        boolean bool1
) implements CustomPacketPayload {
    public static final Type<RfidScannerActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "rfid_scanner_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RfidScannerActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.dimensionId());
                        buf.writeVarInt(payload.x());
                        buf.writeVarInt(payload.y());
                        buf.writeVarInt(payload.z());
                        buf.writeUtf(payload.action());
                        buf.writeUtf(payload.pin());
                        buf.writeUtf(payload.text1());
                        buf.writeUtf(payload.text2());
                        buf.writeVarInt(payload.int1());
                        buf.writeVarInt(payload.int2());
                        buf.writeVarInt(payload.int3());
                        buf.writeVarInt(payload.int4());
                        buf.writeBoolean(payload.bool1());
                    },
                    buf -> new RfidScannerActionPayload(
                            buf.readUtf(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readBoolean()
                    )
            );

    public RfidScannerActionPayload {
        dimensionId = safe(dimensionId);
        action = safe(action);
        pin = safe(pin);
        text1 = safe(text1);
        text2 = safe(text2);
    }

    @Override
    public Type<RfidScannerActionPayload> type() {
        return TYPE;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
