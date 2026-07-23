package net.austizz.ultimatebankingsystem.bank.owner.route;

import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRoutePosition;

public record OwnerPcVaultRouteWorldBounds(int minBuildHeight,
                                           int maxBuildHeight,
                                           double borderMinX,
                                           double borderMaxX,
                                           double borderMinZ,
                                           double borderMaxZ) {
    private static final int MIN_HORIZONTAL = -30_000_000;
    private static final int MAX_HORIZONTAL_EXCLUSIVE = 30_000_000;

    public OwnerPcVaultRouteWorldBounds {
        if (maxBuildHeight <= minBuildHeight
                || !Double.isFinite(borderMinX) || !Double.isFinite(borderMaxX)
                || !Double.isFinite(borderMinZ) || !Double.isFinite(borderMaxZ)
                || borderMaxX <= borderMinX || borderMaxZ <= borderMinZ) {
            throw new IllegalArgumentException("world bounds are invalid");
        }
    }

    public boolean contains(OwnerPcVaultRoutePosition position) {
        if (position == null) {
            return false;
        }
        int x = position.x();
        int y = position.y();
        int z = position.z();
        return x >= MIN_HORIZONTAL && x < MAX_HORIZONTAL_EXCLUSIVE
                && z >= MIN_HORIZONTAL && z < MAX_HORIZONTAL_EXCLUSIVE
                && y >= minBuildHeight && y < maxBuildHeight
                && x >= borderMinX && x < borderMaxX
                && z >= borderMinZ && z < borderMaxZ;
    }
}
