package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client payload carrying the result of a transfer request.
 */
public record TransferResponsePayload(boolean success, String newBalance, String errorMessage) implements CustomPacketPayload {

    public static final Type<TransferResponsePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "transfer_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransferResponsePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL,        TransferResponsePayload::success,
            ByteBufCodecs.STRING_UTF8, TransferResponsePayload::newBalance,
            ByteBufCodecs.STRING_UTF8, TransferResponsePayload::errorMessage,
            TransferResponsePayload::new
        );

    @Override
    public Type<TransferResponsePayload> type() { return TYPE; }
}
