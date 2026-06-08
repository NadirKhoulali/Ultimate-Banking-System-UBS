package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;

public record DeliveryPalletLabelSummary(
        int x,
        int y,
        int z,
        String shopName
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, DeliveryPalletLabelSummary> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DeliveryPalletLabelSummary::x,
                    ByteBufCodecs.VAR_INT, DeliveryPalletLabelSummary::y,
                    ByteBufCodecs.VAR_INT, DeliveryPalletLabelSummary::z,
                    ByteBufCodecs.STRING_UTF8, DeliveryPalletLabelSummary::shopName,
                    DeliveryPalletLabelSummary::new
            );

    public DeliveryPalletLabelSummary {
        shopName = trimTo(shopName, 96);
    }

    private static String trimTo(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength));
    }
}
