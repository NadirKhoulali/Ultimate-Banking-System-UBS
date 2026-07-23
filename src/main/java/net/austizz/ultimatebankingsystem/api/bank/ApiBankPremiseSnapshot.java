package net.austizz.ultimatebankingsystem.api.bank;

import net.austizz.ultimatebankingsystem.api.ApiBlockBounds;
import net.austizz.ultimatebankingsystem.api.ApiBlockPosition;
import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

@ApiStatus.AvailableSince("2.0.0")
public record ApiBankPremiseSnapshot(UUID bankId,
                                     String premiseId,
                                     String mode,
                                     ApiBlockBounds bounds,
                                     ApiBlockPosition exit,
                                     float exitYaw,
                                     int safeAreaCount,
                                     int vaultCount,
                                     int readyVaultCount) {
    public ApiBankPremiseSnapshot {
        premiseId = premiseId == null ? "" : premiseId;
        mode = mode == null ? "" : mode;
        safeAreaCount = Math.max(0, safeAreaCount);
        vaultCount = Math.max(0, vaultCount);
        readyVaultCount = Math.max(0, readyVaultCount);
    }
}
