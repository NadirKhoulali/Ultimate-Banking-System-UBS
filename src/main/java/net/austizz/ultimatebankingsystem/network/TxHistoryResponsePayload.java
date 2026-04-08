package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server → client payload containing transaction history entries.
 */
public record TxHistoryResponsePayload(List<TransactionSummary> entries) implements CustomPacketPayload {

    public static final Type<TxHistoryResponsePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "tx_history_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TxHistoryResponsePayload> STREAM_CODEC =
        StreamCodec.composite(
            TransactionSummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)),
            TxHistoryResponsePayload::entries,
            TxHistoryResponsePayload::new
        );

    @Override
    public Type<TxHistoryResponsePayload> type() { return TYPE; }
}
