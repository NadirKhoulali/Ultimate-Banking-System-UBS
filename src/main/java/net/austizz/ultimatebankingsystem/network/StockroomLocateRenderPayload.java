package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StockroomLocateRenderPayload(boolean active,
                                           String dimensionId,
                                           int x,
                                           int y,
                                           int z,
                                           int slot) implements CustomPacketPayload {
    public static final Type<StockroomLocateRenderPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "stockroom_locate_render"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StockroomLocateRenderPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, StockroomLocateRenderPayload::active,
                    ByteBufCodecs.STRING_UTF8, StockroomLocateRenderPayload::dimensionId,
                    ByteBufCodecs.VAR_INT, StockroomLocateRenderPayload::x,
                    ByteBufCodecs.VAR_INT, StockroomLocateRenderPayload::y,
                    ByteBufCodecs.VAR_INT, StockroomLocateRenderPayload::z,
                    ByteBufCodecs.VAR_INT, StockroomLocateRenderPayload::slot,
                    StockroomLocateRenderPayload::new
            );

    public static StockroomLocateRenderPayload inactive() {
        return new StockroomLocateRenderPayload(false, "", 0, 0, 0, 0);
    }

    @Override
    public Type<StockroomLocateRenderPayload> type() {
        return TYPE;
    }
}
