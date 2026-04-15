package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client payload carrying the result of a withdrawal request.
 */
public record WithdrawResponsePayload(
        boolean success,
        String newBalance,
        String errorMessage,
        String dailyLimit,
        String dailyWithdrawn,
        String dailyRemaining,
        long dailyResetEpochMillis
) implements CustomPacketPayload {

    public static final Type<WithdrawResponsePayload> TYPE = new Type<>(
        new ResourceLocation(UltimateBankingSystem.MODID, "withdraw_response"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Long> LONG_CODEC =
            StreamCodec.of((buf, value) -> buf.writeLong(value), RegistryFriendlyByteBuf::readLong);

    public static final StreamCodec<RegistryFriendlyByteBuf, WithdrawResponsePayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.BOOL.encode(buf, payload.success());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.newBalance());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.errorMessage());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.dailyLimit());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.dailyWithdrawn());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.dailyRemaining());
                LONG_CODEC.encode(buf, payload.dailyResetEpochMillis());
            },
            buf -> new WithdrawResponsePayload(
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                LONG_CODEC.decode(buf)
            )
        );

    @Override
    public Type<WithdrawResponsePayload> type() { return TYPE; }
}
