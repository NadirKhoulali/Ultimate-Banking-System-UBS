package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShelfActionResponsePayload(
        boolean success,
        String message
) implements CustomPacketPayload {

    public static final Type<ShelfActionResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "shelf_action_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShelfActionResponsePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.BOOL.encode(buf, payload.success());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.message());
                    },
                    buf -> new ShelfActionResponsePayload(
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)
                    )
            );

    @Override
    public Type<ShelfActionResponsePayload> type() {
        return TYPE;
    }
}
