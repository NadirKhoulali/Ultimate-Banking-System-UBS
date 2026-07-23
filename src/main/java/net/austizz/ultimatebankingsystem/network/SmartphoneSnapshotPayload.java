package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record SmartphoneSnapshotPayload(boolean open,
                                        boolean animate,
                                        String statusMessage,
                                        List<String> lines) implements CustomPacketPayload {
    public static final Type<SmartphoneSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "smartphone_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SmartphoneSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.BOOL.encode(buf, payload.open());
                        ByteBufCodecs.BOOL.encode(buf, payload.animate());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.statusMessage());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(4096)).encode(buf, payload.lines());
                    },
                    buf -> new SmartphoneSnapshotPayload(
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(4096)).decode(buf)
                    )
            );

    public SmartphoneSnapshotPayload {
        statusMessage = statusMessage == null ? "" : statusMessage;
        lines = lines == null ? List.of() : lines;
    }

    public static SmartphoneSnapshotPayload closed(String message) {
        return new SmartphoneSnapshotPayload(false, false, message == null ? "" : message, List.of());
    }

    @Override
    public Type<SmartphoneSnapshotPayload> type() {
        return TYPE;
    }
}
