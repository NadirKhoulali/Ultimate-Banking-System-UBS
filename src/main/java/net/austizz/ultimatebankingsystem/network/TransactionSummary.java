package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Lightweight transaction entry sent to the client for history rendering.
 */
public record TransactionSummary(
    String date,
    String description,
    String amount,
    boolean isIncoming,
    String counterpartyId
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, TransactionSummary> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TransactionSummary::date,
            ByteBufCodecs.STRING_UTF8, TransactionSummary::description,
            ByteBufCodecs.STRING_UTF8, TransactionSummary::amount,
            ByteBufCodecs.BOOL,        TransactionSummary::isIncoming,
            ByteBufCodecs.STRING_UTF8, TransactionSummary::counterpartyId,
            TransactionSummary::new
        );
}
