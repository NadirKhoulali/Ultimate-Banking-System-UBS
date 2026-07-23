package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SafeBoxEscortSession(UUID sessionId,
                                   UUID playerId,
                                   SafeBoxEscortTarget target,
                                   SafeBoxArea safeArea,
                                   SafeBoxEscortPhase phase,
                                   long inspectionGrantedAtTick,
                                   SafeBoxEscortTerminalReason terminalReasonValue) {
    static final long NOT_GRANTED = -1L;

    public SafeBoxEscortSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(safeArea, "safeArea");
        Objects.requireNonNull(phase, "phase");
        if (inspectionGrantedAtTick < NOT_GRANTED) {
            throw new IllegalArgumentException("inspection tick is invalid");
        }
        boolean terminal = phase == SafeBoxEscortPhase.COMPLETE || phase == SafeBoxEscortPhase.TIMED_OUT;
        if (terminal != (terminalReasonValue != null)) {
            throw new IllegalArgumentException("terminal phase and reason must agree");
        }
        if ((phase == SafeBoxEscortPhase.TIMED_OUT)
                != (terminalReasonValue == SafeBoxEscortTerminalReason.TIMED_OUT)) {
            throw new IllegalArgumentException("timeout phase and reason must agree");
        }
    }

    static SafeBoxEscortSession started(UUID sessionId, UUID playerId,
                                        SafeBoxEscortTarget target, SafeBoxArea safeArea) {
        return new SafeBoxEscortSession(sessionId, playerId, target, safeArea,
                SafeBoxEscortPhase.OUTBOUND, NOT_GRANTED, null);
    }

    SafeBoxEscortSession moveTo(SafeBoxEscortPhase nextPhase) {
        return new SafeBoxEscortSession(sessionId, playerId, target, safeArea,
                nextPhase, inspectionGrantedAtTick, terminalReasonValue);
    }

    SafeBoxEscortSession grantInspection(long serverTick) {
        if (serverTick < 0) {
            throw new IllegalArgumentException("serverTick must not be negative");
        }
        return new SafeBoxEscortSession(sessionId, playerId, target, safeArea,
                SafeBoxEscortPhase.INSPECTING, serverTick, null);
    }

    SafeBoxEscortSession terminate(SafeBoxEscortTerminalReason reason) {
        SafeBoxEscortPhase terminalPhase = reason == SafeBoxEscortTerminalReason.TIMED_OUT
                ? SafeBoxEscortPhase.TIMED_OUT : SafeBoxEscortPhase.COMPLETE;
        return new SafeBoxEscortSession(sessionId, playerId, target, safeArea,
                terminalPhase, inspectionGrantedAtTick, Objects.requireNonNull(reason, "reason"));
    }

    public Optional<SafeBoxEscortTerminalReason> terminalReason() {
        return Optional.ofNullable(terminalReasonValue);
    }
}
