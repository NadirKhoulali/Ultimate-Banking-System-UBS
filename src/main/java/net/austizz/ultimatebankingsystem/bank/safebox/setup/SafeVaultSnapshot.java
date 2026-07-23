package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.util.List;
import java.util.OptionalInt;

public record SafeVaultSnapshot(String id,
                                String safeAreaId,
                                String dimension,
                                SafeVaultSetupStatus status,
                                OptionalInt vaultDoorX,
                                OptionalInt vaultDoorY,
                                OptionalInt vaultDoorZ,
                                OptionalInt doorIndex,
                                List<SafeTellerRouteHook> routeHooks) {
    public SafeVaultSnapshot(String id,
                             String safeAreaId,
                             String dimension,
                             SafeVaultSetupStatus status,
                             OptionalInt vaultDoorX,
                             OptionalInt vaultDoorY,
                             OptionalInt vaultDoorZ,
                             OptionalInt doorIndex) {
        this(id, safeAreaId, dimension, status, vaultDoorX, vaultDoorY, vaultDoorZ, doorIndex, List.of());
    }

    public SafeVaultSnapshot {
        id = id == null ? "" : id;
        safeAreaId = safeAreaId == null ? "" : safeAreaId;
        dimension = SafeBlockBounds.normalizeDimension(dimension);
        status = status == null ? SafeVaultSetupStatus.SETUP_PENDING : status;
        vaultDoorX = vaultDoorX == null ? OptionalInt.empty() : vaultDoorX;
        vaultDoorY = vaultDoorY == null ? OptionalInt.empty() : vaultDoorY;
        vaultDoorZ = vaultDoorZ == null ? OptionalInt.empty() : vaultDoorZ;
        doorIndex = doorIndex == null ? OptionalInt.empty() : doorIndex;
        routeHooks = routeHooks == null ? List.of() : List.copyOf(routeHooks);
    }
}
