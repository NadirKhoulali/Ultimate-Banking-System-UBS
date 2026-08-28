package net.austizz.ultimatebankingsystem.bank.safebox.zone;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;

import java.util.Optional;

public record SafeBoxZoneEjectionPlan(
        String dimension,
        double x,
        double y,
        double z,
        float yaw,
        boolean cancelMotion
) {
    public static Optional<SafeBoxZoneEjectionPlan> from(SafeBoxZonePolicy.Decision decision) {
        if (decision == null || decision.allowed() || decision.exit().isEmpty()) {
            return Optional.empty();
        }
        SafeExitSnapshot exit = decision.exit().orElseThrow();
        return Optional.of(new SafeBoxZoneEjectionPlan(
                exit.dimension(),
                exit.x() + 0.5D,
                exit.y(),
                exit.z() + 0.5D,
                exit.yaw(),
                true
        ));
    }
}
