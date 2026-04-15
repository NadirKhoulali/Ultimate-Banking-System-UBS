package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client payload carrying the result of a deposit request.
 */
public record DepositResponsePayload(boolean success, String newBalance, String errorMessage) implements CustomPacketPayload {

    public static final Type<DepositResponsePayload> TYPE = new Type<>(
        new ResourceLocation(UltimateBankingSystem.MODID, "deposit_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DepositResponsePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL,        DepositResponsePayload::success,
            ByteBufCodecs.STRING_UTF8, DepositResponsePayload::newBalance,
            ByteBufCodecs.STRING_UTF8, DepositResponsePayload::errorMessage,
            DepositResponsePayload::new
        );

    @Override
    public Type<DepositResponsePayload> type() { return TYPE; }
}
