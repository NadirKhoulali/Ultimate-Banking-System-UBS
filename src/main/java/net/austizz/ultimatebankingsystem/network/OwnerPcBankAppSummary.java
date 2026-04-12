package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record OwnerPcBankAppSummary(
        UUID bankId,
        String bankName,
        String color,
        String status,
        boolean owner,
        String roleLabel
) {
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcBankAppSummary> STREAM_CODEC =
            StreamCodec.composite(
                    UUID_CODEC, OwnerPcBankAppSummary::bankId,
                    ByteBufCodecs.STRING_UTF8, OwnerPcBankAppSummary::bankName,
                    ByteBufCodecs.STRING_UTF8, OwnerPcBankAppSummary::color,
                    ByteBufCodecs.STRING_UTF8, OwnerPcBankAppSummary::status,
                    ByteBufCodecs.BOOL, OwnerPcBankAppSummary::owner,
                    ByteBufCodecs.STRING_UTF8, OwnerPcBankAppSummary::roleLabel,
                    OwnerPcBankAppSummary::new
            );
}
