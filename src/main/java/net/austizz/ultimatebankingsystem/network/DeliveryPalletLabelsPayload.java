package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record DeliveryPalletLabelsPayload(
        String dimensionId,
        List<DeliveryPalletLabelSummary> labels
) implements CustomPacketPayload {
    public static final Type<DeliveryPalletLabelsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "delivery_pallet_labels"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeliveryPalletLabelsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, DeliveryPalletLabelsPayload::dimensionId,
                    DeliveryPalletLabelSummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)), DeliveryPalletLabelsPayload::labels,
                    DeliveryPalletLabelsPayload::new
            );

    public DeliveryPalletLabelsPayload {
        dimensionId = trimTo(dimensionId, 96);
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    public static DeliveryPalletLabelsPayload empty(String dimensionId) {
        return new DeliveryPalletLabelsPayload(dimensionId, List.of());
    }

    private static String trimTo(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength));
    }

    @Override
    public Type<DeliveryPalletLabelsPayload> type() {
        return TYPE;
    }
}
