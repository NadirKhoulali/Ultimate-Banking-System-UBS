package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client payload carrying the result of a withdrawal request.
 */
public record WithdrawResponsePayload(boolean success, String newBalance, String errorMessage) implements CustomPacketPayload {

    public static final Type<WithdrawResponsePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "withdraw_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WithdrawResponsePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL,        WithdrawResponsePayload::success,
            ByteBufCodecs.STRING_UTF8, WithdrawResponsePayload::newBalance,
            ByteBufCodecs.STRING_UTF8, WithdrawResponsePayload::errorMessage,
            WithdrawResponsePayload::new
        );

    @Override
    public Type<WithdrawResponsePayload> type() { return TYPE; }
}
