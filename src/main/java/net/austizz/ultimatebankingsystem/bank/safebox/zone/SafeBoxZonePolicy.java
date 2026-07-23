package net.austizz.ultimatebankingsystem.bank.safebox.zone;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortSession;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SafeBoxZonePolicy {
    private SafeBoxZonePolicy() {
    }

    public static Decision decide(SafeBoxZoneIndex index,
                                  String dimension,
                                  int x, int y, int z,
                                  StaffAccess staffAccess,
                                  Optional<SafeBoxEscortSession> escort) {
        List<SafeBoxZoneIndex.Presence> present = index.at(dimension, x, y, z);
        if (present.isEmpty()) {
            return Decision.allowedDecision();
        }
        Optional<SafeBoxZoneIndex.EscortScope> scope = escort.flatMap(index::scopeFor);
        List<SafeBoxZoneIndex.Presence> denied = new ArrayList<>();
        for (SafeBoxZoneIndex.Presence presence : present) {
            if (!allowed(presence, staffAccess, scope)) {
                denied.add(presence);
            }
        }
        if (denied.isEmpty()) {
            return Decision.allowedDecision();
        }
        return new Decision(false, index.safeExitFor(denied), List.copyOf(denied));
    }

    private static boolean allowed(SafeBoxZoneIndex.Presence presence,
                                   StaffAccess staffAccess,
                                   Optional<SafeBoxZoneIndex.EscortScope> scope) {
        SafeBoxZoneRecord record = presence.record();
        if (staffAccess.canAccess(record.bankId())) {
            return true;
        }
        if (!presence.safeAreas().isEmpty()) {
            if (presence.safeAreas().size() != 1 || scope.isEmpty()) {
                return false;
            }
            SafeBoxZoneIndex.EscortScope exact = scope.get();
            return exact.matchesPremise(record)
                    && exact.safeAreaId().equals(presence.safeAreas().getFirst().id());
        }
        if (presence.insidePremise() && record.mode() == SafePremiseMode.STAFF_ONLY) {
            return scope.filter(value -> value.matchesPremise(record)).isPresent();
        }
        return true;
    }

    @FunctionalInterface
    public interface StaffAccess {
        boolean canAccess(UUID bankId);
    }

    public record Decision(boolean allowed,
                           Optional<SafeExitSnapshot> exit,
                           List<SafeBoxZoneIndex.Presence> denied) {
        public Decision {
            exit = exit == null ? Optional.empty() : exit;
            denied = denied == null ? List.of() : List.copyOf(denied);
        }

        static Decision allowedDecision() {
            return new Decision(true, Optional.empty(), List.of());
        }
    }
}
