package net.austizz.ultimatebankingsystem.npc.escort;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePosition;

final class TellerRouteWalkDestination {
    private TellerRouteWalkDestination() {
    }

    static Destination onSurface(SafeTellerRoutePosition target, double surfaceHeight) {
        if (target == null || !Double.isFinite(surfaceHeight) || surfaceHeight < 0.0D) {
            return null;
        }
        return new Destination(
                target.x() + 0.5D,
                target.y() + Math.min(1.0D, surfaceHeight),
                target.z() + 0.5D);
    }

    record Destination(double x, double y, double z) {
    }
}
