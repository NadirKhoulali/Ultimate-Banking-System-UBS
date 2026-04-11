package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record PayRequestEntry(
        UUID requestId,
        String requesterName,
        String amount,
        String createdAt
) {
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, PayRequestEntry> STREAM_CODEC =
            StreamCodec.composite(
                    UUID_CODEC, PayRequestEntry::requestId,
                    ByteBufCodecs.STRING_UTF8, PayRequestEntry::requesterName,
                    ByteBufCodecs.STRING_UTF8, PayRequestEntry::amount,
                    ByteBufCodecs.STRING_UTF8, PayRequestEntry::createdAt,
                    PayRequestEntry::new
            );
}
