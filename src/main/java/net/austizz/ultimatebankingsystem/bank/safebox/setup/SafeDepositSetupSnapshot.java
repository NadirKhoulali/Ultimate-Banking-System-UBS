package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.util.List;

public record SafeDepositSetupSnapshot(int version, List<SafePremiseSnapshot> premises) {
    public SafeDepositSetupSnapshot {
        premises = premises == null ? List.of() : List.copyOf(premises);
    }
}
