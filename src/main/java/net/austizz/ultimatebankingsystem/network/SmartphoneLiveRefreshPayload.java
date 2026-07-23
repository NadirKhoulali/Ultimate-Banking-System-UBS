package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record SmartphoneLiveRefreshPayload(String statusMessage,
                                           List<String> lines) implements CustomPacketPayload {
    public static final Type<SmartphoneLiveRefreshPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "smartphone_live_refresh"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SmartphoneLiveRefreshPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.statusMessage());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(4096)).encode(buf, payload.lines());
                    },
                    buf -> new SmartphoneLiveRefreshPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(4096)).decode(buf)
                    )
            );

    public SmartphoneLiveRefreshPayload {
        statusMessage = statusMessage == null ? "" : statusMessage;
        lines = lines == null ? List.of() : lines;
    }

    public static SmartphoneLiveRefreshPayload fromSnapshot(SmartphoneSnapshotPayload snapshot, String statusMessage) {
        return new SmartphoneLiveRefreshPayload(statusMessage, snapshot == null ? List.of() : snapshot.lines());
    }

    @Override
    public Type<SmartphoneLiveRefreshPayload> type() {
        return TYPE;
    }
}
