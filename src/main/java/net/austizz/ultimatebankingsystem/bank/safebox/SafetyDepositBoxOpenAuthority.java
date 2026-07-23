package net.austizz.ultimatebankingsystem.bank.safebox;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SafetyDepositBoxOpenAuthority {
    private final Ports ports;

    public SafetyDepositBoxOpenAuthority(Ports ports) {
        this.ports = Objects.requireNonNull(ports, "ports");
    }

    public Decision authorize(Request request) {
        if (request == null || request.playerId() == null || request.target() == null) {
            return denied(Denial.ASSIGNMENT_MISSING, "This safety deposit box is not assigned.");
        }

        Assignment assignment;
        try {
            Optional<Assignment> resolved = ports.findExactAssignment(request.target());
            assignment = resolved == null ? null : resolved.orElse(null);
        } catch (RuntimeException ignored) {
            assignment = null;
        }
        if (assignment == null
                || !request.target().equals(assignment.target())
                || (request.expectedAccountId() != null
                && !request.expectedAccountId().equals(assignment.accountId()))) {
            return denied(Denial.ASSIGNMENT_MISSING, "This safety deposit box is not assigned.");
        }

        if (!safeAccountAuthorized(request.playerId(), assignment)) {
            return denied(Denial.ACCOUNT_DENIED, "You do not have access to this safety deposit box.");
        }
        if (!safeVaultReady(assignment)) {
            return denied(Denial.VAULT_NOT_READY, "This safety deposit vault is temporarily unavailable.");
        }
        if (assignment.locked()) {
            return denied(Denial.ASSIGNMENT_LOCKED, "This safety deposit box is locked for overdue rent.");
        }

        EscortAccess escortAccess = safeEscortAccess(request.playerId(), assignment);
        return switch (escortAccess) {
            case ALLOWED -> new Decision(true, Denial.NONE, "", assignment);
            case DENIED_ACTIVE_ESCORT -> denied(Denial.WRONG_ESCORT,
                    "Your active teller escort only authorizes its assigned safety deposit box.");
            case NO_ACTIVE_ESCORT -> denied(Denial.NO_ACTIVE_ESCORT,
                    "An active teller escort is required to open this safety deposit box.");
        };
    }

    private boolean safeAccountAuthorized(UUID playerId, Assignment assignment) {
        try {
            return ports.accountAuthorized(playerId, assignment);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean safeVaultReady(Assignment assignment) {
        try {
            return ports.vaultReady(assignment);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private EscortAccess safeEscortAccess(UUID playerId, Assignment assignment) {
        try {
            EscortAccess access = ports.escortAccess(playerId, assignment);
            return access == null ? EscortAccess.NO_ACTIVE_ESCORT : access;
        } catch (RuntimeException ignored) {
            return EscortAccess.NO_ACTIVE_ESCORT;
        }
    }

    private static Decision denied(Denial denial, String message) {
        return new Decision(false, denial, message, null);
    }

    public interface Ports {
        Optional<Assignment> findExactAssignment(Target target);

        boolean accountAuthorized(UUID playerId, Assignment assignment);

        boolean vaultReady(Assignment assignment);

        EscortAccess escortAccess(UUID playerId, Assignment assignment);
    }

    public record Request(UUID playerId, UUID expectedAccountId, Target target) {
    }

    public record Target(String dimension, int x, int y, int z, int doorIndex) {
        public Target {
            dimension = dimension == null ? "" : dimension.trim();
        }
    }

    public record Assignment(UUID bankId,
                             UUID accountId,
                             Target target,
                             String boxNumber,
                             boolean locked) {
        public Assignment {
            Objects.requireNonNull(bankId, "bankId");
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(target, "target");
            boxNumber = boxNumber == null ? "" : boxNumber;
        }
    }

    public enum EscortAccess {
        ALLOWED,
        DENIED_ACTIVE_ESCORT,
        NO_ACTIVE_ESCORT
    }

    public enum Denial {
        NONE,
        ASSIGNMENT_MISSING,
        ACCOUNT_DENIED,
        VAULT_NOT_READY,
        ASSIGNMENT_LOCKED,
        WRONG_ESCORT,
        NO_ACTIVE_ESCORT
    }

    public record Decision(boolean allowed, Denial denial, String message, Assignment assignment) {
        public Decision {
            Objects.requireNonNull(denial, "denial");
            message = message == null ? "" : message;
            if (allowed && (denial != Denial.NONE || assignment == null)) {
                throw new IllegalArgumentException("allowed decision requires an assignment and no denial");
            }
            if (!allowed && (denial == Denial.NONE || assignment != null)) {
                throw new IllegalArgumentException("denied decision requires a denial and no assignment");
            }
        }
    }
}
