package net.austizz.ultimatebankingsystem.heist;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;

/** Pure distance policy shared by active-heist lifecycle code and tests. */
final class HeistAbandonPolicy {
    private HeistAbandonPolicy() {
    }

    static boolean beyondPremise(SafeBlockBounds bounds,
                                 String dimension,
                                 double x,
                                 double z,
                                 double maximumDistance) {
        if (bounds == null || dimension == null
                || !bounds.dimension().equals(SafeBlockBounds.normalizeDimension(dimension))) {
            return true;
        }
        double safeDistance = Math.max(0.0D, maximumDistance);
        double dx = axisDistance(x, bounds.minX(), bounds.maxX() + 1.0D);
        double dz = axisDistance(z, bounds.minZ(), bounds.maxZ() + 1.0D);
        return dx * dx + dz * dz > safeDistance * safeDistance;
    }

    private static double axisDistance(double value, double min, double max) {
        if (value < min) return min - value;
        if (value > max) return value - max;
        return 0.0D;
    }
}
