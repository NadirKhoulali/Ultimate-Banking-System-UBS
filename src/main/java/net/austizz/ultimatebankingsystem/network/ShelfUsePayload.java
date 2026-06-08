package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShelfUsePayload(
        String dimensionId,
        int x,
        int y,
        int z,
        double hitX,
        double hitY,
        double hitZ,
        boolean configureAction,
        boolean mainHand
) implements CustomPacketPayload {

    public static final Type<ShelfUsePayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "shelf_use"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Double> DOUBLE_CODEC =
            StreamCodec.of(RegistryFriendlyByteBuf::writeDouble, RegistryFriendlyByteBuf::readDouble);

    public static final StreamCodec<RegistryFriendlyByteBuf, ShelfUsePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.x());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.y());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.z());
                        DOUBLE_CODEC.encode(buf, payload.hitX());
                        DOUBLE_CODEC.encode(buf, payload.hitY());
                        DOUBLE_CODEC.encode(buf, payload.hitZ());
                        ByteBufCodecs.BOOL.encode(buf, payload.configureAction());
                        ByteBufCodecs.BOOL.encode(buf, payload.mainHand());
                    },
                    buf -> new ShelfUsePayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            DOUBLE_CODEC.decode(buf),
                            DOUBLE_CODEC.decode(buf),
                            DOUBLE_CODEC.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)
                    )
            );

    @Override
    public Type<ShelfUsePayload> type() {
        return TYPE;
    }
}
