package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.network.DeliveryPalletLabelSummary;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DeliveryPalletLabelsClientState {
    private static String dimensionId = "";
    private static List<Label> labels = List.of();

    private DeliveryPalletLabelsClientState() {
    }

    public static void set(String nextDimensionId, List<DeliveryPalletLabelSummary> nextLabels) {
        if (nextLabels == null || nextLabels.isEmpty()) {
            setLabels(nextDimensionId, List.of());
            return;
        }
        List<Label> copy = new ArrayList<>(nextLabels.size());
        for (DeliveryPalletLabelSummary label : nextLabels) {
            if (label == null) {
                continue;
            }
            copy.add(new Label(label.x(), label.y(), label.z(), label.shopName()));
        }
        setLabels(nextDimensionId, copy);
    }

    static void setLabels(String nextDimensionId, List<Label> nextLabels) {
        dimensionId = normalizeDimensionId(nextDimensionId);
        if (nextLabels == null || nextLabels.isEmpty()) {
            labels = List.of();
            return;
        }
        List<Label> copy = new ArrayList<>(nextLabels.size());
        for (Label label : nextLabels) {
            if (label == null) {
                continue;
            }
            copy.add(new Label(label.x(), label.y(), label.z(), sanitizeText(label.shopName())));
        }
        labels = List.copyOf(copy);
    }

    public static void clear() {
        dimensionId = "";
        labels = List.of();
    }

    public static List<Label> getLabels(String currentDimensionId) {
        if (!normalizeDimensionId(currentDimensionId).equals(dimensionId)) {
            return List.of();
        }
        return labels;
    }

    public static Label getLabel(String currentDimensionId, BlockPos pos) {
        if (pos == null || !normalizeDimensionId(currentDimensionId).equals(dimensionId)) {
            return null;
        }
        return getLabel(currentDimensionId, pos.getX(), pos.getY(), pos.getZ());
    }

    static Label getLabel(String currentDimensionId, int x, int y, int z) {
        if (!normalizeDimensionId(currentDimensionId).equals(dimensionId)) {
            return null;
        }
        for (Label label : labels) {
            if (label == null) {
                continue;
            }
            if (label.x() == x && label.y() == y && label.z() == z) {
                return label;
            }
        }
        return null;
    }

    private static String normalizeDimensionId(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("minecraft:")
                ? normalized.substring("minecraft:".length())
                : normalized;
    }

    private static String sanitizeText(String text) {
        return text == null ? "" : text.trim();
    }

    public record Label(int x, int y, int z, String shopName) {
    }
}
