package net.austizz.ultimatebankingsystem.bank.owner.staffing;

import java.util.UUID;

public record BankTellerSummary(UUID entityId,
                                String displayName,
                                int variant,
                                String dimension,
                                double x,
                                double y,
                                double z,
                                boolean active,
                                boolean bound) {
    public BankTellerSummary {
        displayName = displayName == null || displayName.isBlank() ? variantLabel(variant) : displayName.trim();
        dimension = dimension == null ? "" : dimension.trim();
    }

    private static String variantLabel(int variant) {
        return variant == 1 ? "FEMALE" : "MALE";
    }
}
