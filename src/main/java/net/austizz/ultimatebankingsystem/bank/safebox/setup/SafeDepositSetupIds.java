package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class SafeDepositSetupIds {
    private SafeDepositSetupIds() {
    }

    static String premiseId(UUID bankId, SafeBlockBounds bounds) {
        return stableId("premise", bankId, bounds.dimension(), bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    static String safeAreaId(UUID bankId, String premiseId, SafeBlockBounds bounds) {
        return stableId("safe-area", bankId, premiseId, bounds.dimension(), bounds.minX(), bounds.minY(),
                bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    static String vaultId(UUID bankId, String safeAreaId) {
        return stableId("vault", bankId, safeAreaId);
    }

    public static boolean isMigrationOwnedPremise(String persistedId, UUID bankId, SafeBlockBounds bounds) {
        return bankId != null && bounds != null && premiseId(bankId, bounds).equals(persistedId);
    }

    private static String stableId(Object... values) {
        StringBuilder builder = new StringBuilder("ubs-safe-deposit-setup-v1");
        for (Object value : values) {
            builder.append('|').append(value == null ? "" : value);
        }
        return UUID.nameUUIDFromBytes(builder.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }
}
