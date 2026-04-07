package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * Lightweight, client-safe summary of a player's bank account.
 *
 * <p>Sent from server → client inside {@link AccountListPayload} so the ATM
 * screen can display account information without exposing full server-side
 * {@code AccountHolder} objects.</p>
 */
public record AccountSummary(
    UUID accountId,
    String accountType,
    String bankName,
    String balance,
    boolean isPrimary
) {

    /** StreamCodec for UUID — serialises as two longs (mostSigBits, leastSigBits). */
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
        StreamCodec.of(
            (buf, uuid) -> { buf.writeLong(uuid.getMostSignificantBits()); buf.writeLong(uuid.getLeastSignificantBits()); },
            buf -> new UUID(buf.readLong(), buf.readLong())
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, AccountSummary> STREAM_CODEC =
        StreamCodec.composite(
            UUID_CODEC,              AccountSummary::accountId,
            ByteBufCodecs.STRING_UTF8, AccountSummary::accountType,
            ByteBufCodecs.STRING_UTF8, AccountSummary::bankName,
            ByteBufCodecs.STRING_UTF8, AccountSummary::balance,
            ByteBufCodecs.BOOL,        AccountSummary::isPrimary,
            AccountSummary::new
        );
}
