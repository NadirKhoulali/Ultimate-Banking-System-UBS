package net.austizz.ultimatebankingsystem.bank.safebox.zone;

import java.util.List;
import java.util.UUID;

public final class BankPremiseProtectionPolicy {
    private BankPremiseProtectionPolicy() {
    }

    public static Decision decide(SafeBoxZoneIndex index,
                                  String dimension,
                                  int x,
                                  int y,
                                  int z,
                                  StaffAccess staffAccess) {
        if (index == null) {
            return Decision.outside();
        }
        List<SafeBoxZoneIndex.Presence> premises = index.premisesAt(dimension, x, y, z);
        if (premises.isEmpty()) {
            return Decision.outside();
        }
        List<SafeBoxZoneIndex.Presence> denied = premises.stream()
                .filter(presence -> staffAccess == null
                        || !staffAccess.isStaff(presence.record().bankId()))
                .toList();
        return new Decision(true, denied.isEmpty(), premises, denied);
    }

    public static boolean protects(SafeBoxZoneIndex index,
                                   String dimension,
                                   int x,
                                   int y,
                                   int z) {
        return decide(index, dimension, x, y, z, bankId -> false).insidePremise();
    }

    @FunctionalInterface
    public interface StaffAccess {
        boolean isStaff(UUID bankId);
    }

    public record Decision(boolean insidePremise,
                           boolean modificationAllowed,
                           List<SafeBoxZoneIndex.Presence> premises,
                           List<SafeBoxZoneIndex.Presence> deniedPremises) {
        public Decision {
            premises = premises == null ? List.of() : List.copyOf(premises);
            deniedPremises = deniedPremises == null ? List.of() : List.copyOf(deniedPremises);
        }

        static Decision outside() {
            return new Decision(false, true, List.of(), List.of());
        }
    }
}
