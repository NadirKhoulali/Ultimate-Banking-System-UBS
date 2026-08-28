package net.austizz.ultimatebankingsystem.migration.numismatics;

import java.util.List;
import java.util.UUID;

public record NumismaticsAccountRecord(
        UUID sourceAccountId,
        AccountKind kind,
        int balanceSpurs,
        String label,
        List<UUID> trustedPlayers
) {
    public NumismaticsAccountRecord {
        kind = kind == null ? AccountKind.PLAYER : kind;
        label = label == null ? "" : label.trim();
        trustedPlayers = trustedPlayers == null ? List.of() : List.copyOf(trustedPlayers);
    }

    public enum AccountKind {
        PLAYER,
        BLAZE_BANKER
    }
}
