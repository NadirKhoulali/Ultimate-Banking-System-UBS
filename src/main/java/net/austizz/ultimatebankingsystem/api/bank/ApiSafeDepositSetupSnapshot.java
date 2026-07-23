package net.austizz.ultimatebankingsystem.api.bank;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.UUID;

@ApiStatus.AvailableSince("2.0.0")
public record ApiSafeDepositSetupSnapshot(UUID bankId,
                                          int schemaVersion,
                                          boolean enabled,
                                          int premiseCount,
                                          int safeAreaCount,
                                          int vaultCount,
                                          int readyVaultCount,
                                          List<String> missingRequirements,
                                          List<ApiBankPremiseSnapshot> premises) {
    public ApiSafeDepositSetupSnapshot {
        schemaVersion = Math.max(0, schemaVersion);
        premiseCount = Math.max(0, premiseCount);
        safeAreaCount = Math.max(0, safeAreaCount);
        vaultCount = Math.max(0, vaultCount);
        readyVaultCount = Math.max(0, readyVaultCount);
        missingRequirements = missingRequirements == null ? List.of() : List.copyOf(missingRequirements);
        premises = premises == null ? List.of() : List.copyOf(premises);
    }
}
