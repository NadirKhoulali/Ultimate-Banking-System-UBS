package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client payload carrying account balance inquiry results.
 */
public record BalanceResponsePayload(
    String accountType,
    String bankName,
    String accountId,
    String balance,
    String createdDate
) implements CustomPacketPayload {

    public static final Type<BalanceResponsePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "balance_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BalanceResponsePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BalanceResponsePayload::accountType,
            ByteBufCodecs.STRING_UTF8, BalanceResponsePayload::bankName,
            ByteBufCodecs.STRING_UTF8, BalanceResponsePayload::accountId,
            ByteBufCodecs.STRING_UTF8, BalanceResponsePayload::balance,
            ByteBufCodecs.STRING_UTF8, BalanceResponsePayload::createdDate,
            BalanceResponsePayload::new
        );

    @Override
    public Type<BalanceResponsePayload> type() { return TYPE; }
}
