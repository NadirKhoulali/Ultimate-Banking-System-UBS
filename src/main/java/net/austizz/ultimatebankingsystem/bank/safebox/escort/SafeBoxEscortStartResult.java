package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import java.util.Optional;
import java.util.Objects;

public record SafeBoxEscortStartResult(Status status, SafeBoxEscortSession sessionValue) {
    public enum Status {
        STARTED,
        BUSY
    }

    public SafeBoxEscortStartResult {
        Objects.requireNonNull(status, "status");
        if ((status == Status.STARTED) != (sessionValue != null)) {
            throw new IllegalArgumentException("only a started result has a session");
        }
    }

    static SafeBoxEscortStartResult started(SafeBoxEscortSession session) {
        return new SafeBoxEscortStartResult(Status.STARTED, session);
    }

    static SafeBoxEscortStartResult busy() {
        return new SafeBoxEscortStartResult(Status.BUSY, null);
    }

    public Optional<SafeBoxEscortSession> session() {
        return Optional.ofNullable(sessionValue);
    }
}
