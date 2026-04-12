package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PayRequestCreateResponsePayload(
        boolean success,
        String message
) implements CustomPacketPayload {

    public static final Type<PayRequestCreateResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "pay_request_create_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PayRequestCreateResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, PayRequestCreateResponsePayload::success,
                    ByteBufCodecs.STRING_UTF8, PayRequestCreateResponsePayload::message,
                    PayRequestCreateResponsePayload::new
            );

    @Override
    public Type<PayRequestCreateResponsePayload> type() {
        return TYPE;
    }
}
