package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.util.List;

public record SafeVaultReadinessSummary(String vaultId,
                                        boolean ready,
                                        List<SafeReadinessMissingReason> missingReasons) {
    public SafeVaultReadinessSummary {
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
    }
}
