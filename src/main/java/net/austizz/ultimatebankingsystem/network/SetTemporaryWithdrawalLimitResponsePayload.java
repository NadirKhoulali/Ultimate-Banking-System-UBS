        package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client payload with the result of applying a temporary custom ATM withdrawal limit.
 */
public record SetTemporaryWithdrawalLimitResponsePayload(
        boolean success,
        String defaultLimit,
        String effectiveLimit,
        String temporaryLimit,
        long temporaryLimitExpiresAtGameTime,
        String errorMessage
) implements CustomPacketPayload {

    public static final Type<SetTemporaryWithdrawalLimitResponsePayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "set_temporary_withdrawal_limit_response"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Long> LONG_CODEC =
            StreamCodec.of((buf, value) -> buf.writeLong(value), RegistryFriendlyByteBuf::readLong);

    public static final StreamCodec<RegistryFriendlyByteBuf, SetTemporaryWithdrawalLimitResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SetTemporaryWithdrawalLimitResponsePayload::success,
                    ByteBufCodecs.STRING_UTF8, SetTemporaryWithdrawalLimitResponsePayload::defaultLimit,
                    ByteBufCodecs.STRING_UTF8, SetTemporaryWithdrawalLimitResponsePayload::effectiveLimit,
                    ByteBufCodecs.STRING_UTF8, SetTemporaryWithdrawalLimitResponsePayload::temporaryLimit,
                    LONG_CODEC, SetTemporaryWithdrawalLimitResponsePayload::temporaryLimitExpiresAtGameTime,
                    ByteBufCodecs.STRING_UTF8, SetTemporaryWithdrawalLimitResponsePayload::errorMessage,
                    SetTemporaryWithdrawalLimitResponsePayload::new
            );

    @Override
    public Type<SetTemporaryWithdrawalLimitResponsePayload> type() {
        return TYPE;
    }
}


