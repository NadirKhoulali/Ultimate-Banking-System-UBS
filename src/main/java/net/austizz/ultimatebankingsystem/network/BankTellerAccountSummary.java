package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;

import java.util.UUID;

public record BankTellerAccountSummary(
        UUID accountId,
        UUID bankId,
        String bankName,
        String accountType,
        String balance,
        String cardIssueFee,
        String cardReplacementFee,
        boolean primary,
        boolean cardEligible,
        boolean hasActiveCard
) {
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, BankTellerAccountSummary> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        UUID_CODEC.encode(buf, payload.accountId());
                        UUID_CODEC.encode(buf, payload.bankId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.bankName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.accountType());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.balance());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.cardIssueFee());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.cardReplacementFee());
                        ByteBufCodecs.BOOL.encode(buf, payload.primary());
                        ByteBufCodecs.BOOL.encode(buf, payload.cardEligible());
                        ByteBufCodecs.BOOL.encode(buf, payload.hasActiveCard());
                    },
                    buf -> new BankTellerAccountSummary(
                            UUID_CODEC.decode(buf),
                            UUID_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)
                    )
            );
}
