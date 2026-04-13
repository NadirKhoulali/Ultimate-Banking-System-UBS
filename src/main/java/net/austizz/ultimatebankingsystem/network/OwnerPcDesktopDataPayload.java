package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record OwnerPcDesktopDataPayload(
        String computerLabel,
        int maxStorageBytes,
        int usedStorageBytes,
        boolean pinSet,
        List<OwnerPcFileEntry> files,
        List<String> hiddenAppIds
) implements CustomPacketPayload {

    public static final Type<OwnerPcDesktopDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "owner_pc_desktop_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcDesktopDataPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.computerLabel());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.maxStorageBytes());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.usedStorageBytes());
                        ByteBufCodecs.BOOL.encode(buf, payload.pinSet());
                        OwnerPcFileEntry.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, payload.files());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).encode(buf, payload.hiddenAppIds());
                    },
                    buf -> new OwnerPcDesktopDataPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            OwnerPcFileEntry.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).decode(buf)
                    )
            );

    @Override
    public Type<OwnerPcDesktopDataPayload> type() {
        return TYPE;
    }
}
