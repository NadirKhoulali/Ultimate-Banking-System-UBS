package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import java.util.Optional;
import java.util.Objects;

public record SafeBoxEscortMutation(Status status,
                                    Action action,
                                    SafeBoxEscortSession sessionValue,
                                    SafeBoxEscortTarget accessTargetValue,
                                    SafeBoxEscortTerminalReason terminalReasonValue,
                                    boolean ejectionRequired,
                                    boolean cleanupRequired) {
    public SafeBoxEscortMutation {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(action, "action");
        if (status == Status.APPLIED && sessionValue == null) {
            throw new IllegalArgumentException("an applied mutation requires a session");
        }
        if (status != Status.APPLIED && action != Action.NONE) {
            throw new IllegalArgumentException("unchanged mutations cannot request actions");
        }
        if ((action == Action.GRANT_TARGET_ACCESS) != (accessTargetValue != null)) {
            throw new IllegalArgumentException("grant action and target must agree");
        }
        if (cleanupRequired != (terminalReasonValue != null)) {
            throw new IllegalArgumentException("cleanup and terminal reason must agree");
        }
        if (ejectionRequired != (terminalReasonValue == SafeBoxEscortTerminalReason.TIMED_OUT)) {
            throw new IllegalArgumentException("only timeout cleanup ejects");
        }
    }
    public enum Status {
        APPLIED,
        NO_CHANGE,
        INVALID_PHASE,
        AUTHORIZATION_DENIED,
        WRONG_TELLER,
        NOT_FOUND,
        STALE_SESSION
    }

    public enum Action {
        NONE,
        GRANT_TARGET_ACCESS,
        INSPECTION_COMPLETE,
        BEGIN_RETURN_ROUTE,
        EJECT_AND_CLEANUP,
        CLEANUP
    }

    static SafeBoxEscortMutation applied(SafeBoxEscortSession session, Action action) {
        SafeBoxEscortTarget granted = action == Action.GRANT_TARGET_ACCESS ? session.target() : null;
        return new SafeBoxEscortMutation(Status.APPLIED, action, session, granted,
                null, false, false);
    }

    static SafeBoxEscortMutation terminal(SafeBoxEscortSession session) {
        boolean eject = session.terminalReasonValue() == SafeBoxEscortTerminalReason.TIMED_OUT;
        return new SafeBoxEscortMutation(Status.APPLIED,
                eject ? Action.EJECT_AND_CLEANUP : Action.CLEANUP,
                session, null, session.terminalReasonValue(), eject, true);
    }

    static SafeBoxEscortMutation unchanged(Status status, SafeBoxEscortSession session) {
        return new SafeBoxEscortMutation(status, Action.NONE, session, null,
                null, false, false);
    }

    public Optional<SafeBoxEscortSession> session() {
        return Optional.ofNullable(sessionValue);
    }

    public Optional<SafeBoxEscortTarget> accessTarget() {
        return Optional.ofNullable(accessTargetValue);
    }

    public Optional<SafeBoxEscortTerminalReason> terminalReason() {
        return Optional.ofNullable(terminalReasonValue);
    }
}
