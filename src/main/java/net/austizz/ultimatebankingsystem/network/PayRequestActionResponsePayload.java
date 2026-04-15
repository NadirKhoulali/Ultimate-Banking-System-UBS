package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PayRequestActionResponsePayload(
        boolean success,
        String message
) implements CustomPacketPayload {

    public static final Type<PayRequestActionResponsePayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "pay_request_action_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PayRequestActionResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, PayRequestActionResponsePayload::success,
                    ByteBufCodecs.STRING_UTF8, PayRequestActionResponsePayload::message,
                    PayRequestActionResponsePayload::new
            );

    @Override
    public Type<PayRequestActionResponsePayload> type() {
        return TYPE;
    }
}
