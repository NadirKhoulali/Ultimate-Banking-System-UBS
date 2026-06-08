package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record ShelfOpenPayload(
        String dimensionId,
        int rootX,
        int rootY,
        int rootZ,
        int selectedShelfIndex,
        List<ShelfUnitSummary> shelves
) implements CustomPacketPayload {

    public static final Type<ShelfOpenPayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "shelf_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShelfOpenPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.rootX());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.rootY());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.rootZ());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.selectedShelfIndex());
                        ShelfUnitSummary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).encode(buf, payload.shelves());
                    },
                    buf -> new ShelfOpenPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ShelfUnitSummary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf)
                    )
            );

    @Override
    public Type<ShelfOpenPayload> type() {
        return TYPE;
    }
}
