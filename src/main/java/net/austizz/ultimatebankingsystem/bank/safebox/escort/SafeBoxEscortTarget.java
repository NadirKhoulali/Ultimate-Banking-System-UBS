package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record SafeBoxEscortTarget(UUID bankId,
                                  String vaultId,
                                  UUID accountId,
                                  String dimension,
                                  EscortBlockPosition rowPosition,
                                  int doorIndex,
                                  UUID requestedTellerId) {
    public SafeBoxEscortTarget {
        Objects.requireNonNull(bankId, "bankId");
        vaultId = requireText(vaultId, "vaultId");
        Objects.requireNonNull(accountId, "accountId");
        dimension = requireText(dimension, "dimension").toLowerCase(Locale.ROOT);
        Objects.requireNonNull(rowPosition, "rowPosition");
        if (doorIndex < 0) {
            throw new IllegalArgumentException("doorIndex must not be negative");
        }
        Objects.requireNonNull(requestedTellerId, "requestedTellerId");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
