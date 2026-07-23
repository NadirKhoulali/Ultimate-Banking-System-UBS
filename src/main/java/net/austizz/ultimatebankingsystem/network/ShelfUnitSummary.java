package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record ShelfUnitSummary(
        String posKey,
        String ownerName,
        boolean canManage,
        boolean creativeShelf,
        boolean shopMode,
        boolean spinCapable,
        boolean spinEnabled,
        String displayType,
        List<ShelfSlotSummary> slots
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ShelfUnitSummary> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.posKey());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.ownerName());
                        ByteBufCodecs.BOOL.encode(buf, payload.canManage());
                        ByteBufCodecs.BOOL.encode(buf, payload.creativeShelf());
                        ByteBufCodecs.BOOL.encode(buf, payload.shopMode());
                        ByteBufCodecs.BOOL.encode(buf, payload.spinCapable());
                        ByteBufCodecs.BOOL.encode(buf, payload.spinEnabled());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.displayType());
                        ShelfSlotSummary.STREAM_CODEC.apply(ByteBufCodecs.list(16)).encode(buf, payload.slots());
                    },
                    buf -> new ShelfUnitSummary(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ShelfSlotSummary.STREAM_CODEC.apply(ByteBufCodecs.list(16)).decode(buf)
                    )
            );
}
