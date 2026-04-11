package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record PayRequestInboxResponsePayload(
        List<PayRequestEntry> requests,
        String primaryAccountLabel
) implements CustomPacketPayload {

    public static final Type<PayRequestInboxResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "pay_request_inbox_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PayRequestInboxResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    PayRequestEntry.STREAM_CODEC.apply(ByteBufCodecs.list(128)),
                    PayRequestInboxResponsePayload::requests,
                    ByteBufCodecs.STRING_UTF8,
                    PayRequestInboxResponsePayload::primaryAccountLabel,
                    PayRequestInboxResponsePayload::new
            );

    @Override
    public Type<PayRequestInboxResponsePayload> type() {
        return TYPE;
    }
}
