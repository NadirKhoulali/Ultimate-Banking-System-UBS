package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import java.util.Objects;
import java.util.UUID;

final class SafeBoxEscortRuntimeLifecycle {
    private final SafeBoxEscortRuntimeState state;
    private final SafeBoxEscortRegistry registry;
    private final SafeBoxEscortRecovery recovery;

    SafeBoxEscortRuntimeLifecycle(SafeBoxEscortRuntimeState state,
                                  SafeBoxEscortRegistry registry,
                                  SafeBoxEscortRecovery recovery) {
        this.state = Objects.requireNonNull(state, "state");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
    }

    void terminatePlayer(UUID playerId, SafeBoxEscortTerminalReason reason) {
        SafeBoxEscortRuntimeState.Active active = state.forPlayer(playerId);
        if (active == null) {
            return;
        }
        boolean returnRequired = active.returnRequired();
        registry.activeForPlayer(playerId).ifPresent(session -> terminate(session, reason));
        state.releasePlayer(active);
        if (returnRequired) {
            recovery.requestReturn(active, false);
        } else {
            recovery.requestFinal(active, false);
        }
        drive(active);
    }

    void drive(SafeBoxEscortRuntimeState.Active active) {
        for (int step = 0; step < 4 && state.forSession(active.context.sessionId()) == active; step++) {
            SafeBoxEscortRecovery.Signal signal = recovery.progress(active);
            if (signal == SafeBoxEscortRecovery.Signal.NONE
                    || signal == SafeBoxEscortRecovery.Signal.REMOVED) {
                return;
            }
            if (signal == SafeBoxEscortRecovery.Signal.ARRIVED_AT_VAULT) {
                registry.onArrivedAtVault(active.context.playerId(), active.context.sessionId());
                return;
            }
            recover(active, signal);
        }
    }

    void domainFailure(SafeBoxEscortRuntimeState.Active active) {
        registry.activeForPlayer(active.context.playerId()).ifPresent(session ->
                registry.onRouteFailure(session.playerId(), session.sessionId()));
    }

    private void recover(SafeBoxEscortRuntimeState.Active active, SafeBoxEscortRecovery.Signal signal) {
        switch (signal) {
            case OUTBOUND_FAILED_AT_VAULT -> {
                domainFailure(active);
                state.releasePlayer(active);
                recovery.requestReturn(active, false);
            }
            case OUTBOUND_FAILED -> {
                domainFailure(active);
                state.releasePlayer(active);
                recovery.requestFinal(active, false);
            }
            case RETURN_ARRIVED -> {
                registry.activeForPlayer(active.context.playerId()).ifPresent(session ->
                        registry.onReturnRouteComplete(session.playerId(), session.sessionId()));
                state.releasePlayer(active);
            }
            case RETURN_FAILED -> {
                domainFailure(active);
                state.releasePlayer(active);
            }
            case NONE, ARRIVED_AT_VAULT, REMOVED ->
                    throw new IllegalArgumentException("non-recovery signal: " + signal);
        }
    }

    private void terminate(SafeBoxEscortSession session, SafeBoxEscortTerminalReason reason) {
        switch (reason) {
            case LOGOUT -> registry.onLogout(session.playerId(), session.sessionId());
            case DEATH -> registry.onDeath(session.playerId(), session.sessionId());
            case DIMENSION_CHANGE -> registry.onDimensionChange(session.playerId(), session.sessionId());
            default -> throw new IllegalArgumentException("unsupported lifecycle reason");
        }
    }
}
