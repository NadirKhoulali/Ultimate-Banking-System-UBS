package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteValidator;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record SafeBoxEscortRuntimeContext(UUID sessionId,
                                          UUID playerId,
                                          SafeBoxEscortTarget target,
                                          SafeBoxArea premiseBounds,
                                          SafeBoxArea safeBounds,
                                          Exit premiseExit,
                                          EscortBlockPosition vaultDoorMaster,
                                          SafeTellerRoute outboundRoute,
                                          SafeTellerRoute returnRoute,
                                          String label) {
    public SafeBoxEscortRuntimeContext {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(premiseBounds, "premiseBounds");
        Objects.requireNonNull(safeBounds, "safeBounds");
        Objects.requireNonNull(premiseExit, "premiseExit");
        Objects.requireNonNull(vaultDoorMaster, "vaultDoorMaster");
        requireRoute(outboundRoute, target, SafeTellerRouteDirection.OUTBOUND);
        requireRoute(returnRoute, target, SafeTellerRouteDirection.RETURN);
        if (!safeBounds.contains(target.dimension(), target.rowPosition())) {
            throw new IllegalArgumentException("target row must be inside safe bounds");
        }
        if (!premiseBounds.contains(target.dimension(), vaultDoorMaster)) {
            throw new IllegalArgumentException("vault door master must be inside premise bounds");
        }
        if (!premiseExit.dimension().equals(premiseBounds.dimension())
                || premiseBounds.contains(premiseExit.dimension(), premiseExit.position())) {
            throw new IllegalArgumentException("premise exit must be outside its premise");
        }
        label = label == null || label.isBlank() ? "Safety deposit box" : label.trim();
    }

    public UUID tellerId() {
        return target.requestedTellerId();
    }

    private static void requireRoute(SafeTellerRoute route,
                                     SafeBoxEscortTarget target,
                                     SafeTellerRouteDirection direction) {
        if (!SafeTellerRouteValidator.validate(route).valid()
                || route.direction() != direction
                || !target.bankId().toString().equalsIgnoreCase(route.bankId())
                || !target.vaultId().equals(route.vaultId())
                || !target.requestedTellerId().toString().equalsIgnoreCase(route.tellerId())
                || !target.dimension().equals(route.dimension().trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(direction + " route does not match exact escort target");
        }
    }

    public record Exit(String dimension, int x, int y, int z, float yaw) {
        public Exit {
            if (dimension == null || dimension.isBlank() || !Float.isFinite(yaw)) {
                throw new IllegalArgumentException("exit must have a dimension and finite yaw");
            }
            dimension = dimension.trim().toLowerCase(Locale.ROOT);
        }

        public EscortBlockPosition position() {
            return new EscortBlockPosition(x, y, z);
        }
    }
}
