package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShelfActionPayload(
        String dimensionId,
        int rootX,
        int rootY,
        int rootZ,
        String shelfPosKey,
        String action,
        int slotIndex,
        String priceInput,
        int inventorySlot
) implements CustomPacketPayload {

    public static final Type<ShelfActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "shelf_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShelfActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.rootX());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.rootY());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.rootZ());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.shelfPosKey());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.action());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.slotIndex());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.priceInput());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.inventorySlot());
                    },
                    buf -> new ShelfActionPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf)
                    )
            );

    @Override
    public Type<ShelfActionPayload> type() {
        return TYPE;
    }
}
