package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.util.List;

public record SafeAreaSnapshot(String id,
                               String premiseId,
                               SafeBlockBounds bounds,
                               List<SafeVaultSnapshot> vaults) {
    public SafeAreaSnapshot {
        id = id == null ? "" : id;
        premiseId = premiseId == null ? "" : premiseId;
        vaults = vaults == null ? List.of() : List.copyOf(vaults);
    }
}
