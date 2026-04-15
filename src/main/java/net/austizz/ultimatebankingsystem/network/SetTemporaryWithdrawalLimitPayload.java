package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client -> server payload to set a temporary custom ATM withdrawal limit.
 */
public record SetTemporaryWithdrawalLimitPayload(UUID accountId, String customLimit, String pin) implements CustomPacketPayload {

    public static final Type<SetTemporaryWithdrawalLimitPayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "set_temporary_withdrawal_limit"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetTemporaryWithdrawalLimitPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUID_CODEC, SetTemporaryWithdrawalLimitPayload::accountId,
                    ByteBufCodecs.STRING_UTF8, SetTemporaryWithdrawalLimitPayload::customLimit,
                    ByteBufCodecs.STRING_UTF8, SetTemporaryWithdrawalLimitPayload::pin,
                    SetTemporaryWithdrawalLimitPayload::new
            );

    @Override
    public Type<SetTemporaryWithdrawalLimitPayload> type() {
        return TYPE;
    }
}

