package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SafeBoxEscortRuntime {
    private final SafeBoxEscortRuntimeState state = new SafeBoxEscortRuntimeState();
    private final SafeBoxEscortCleanup cleanup;
    private final SafeBoxEscortRecovery recovery;
    private final SafeBoxEscortRegistry registry;
    private final SafeBoxEscortRuntimeAuthorization authorization;
    private final SafeBoxEscortRuntimeLifecycle lifecycle;
    private boolean stopped;

    public SafeBoxEscortRuntime(SafeBoxEscortRuntimePorts.Navigation navigation,
                                SafeBoxEscortRuntimePorts.Effects effects) {
        this.cleanup = new SafeBoxEscortCleanup(
                Objects.requireNonNull(navigation, "navigation"),
                Objects.requireNonNull(effects, "effects"));
        this.recovery = new SafeBoxEscortRecovery(
                navigation, effects, state, cleanup);
        this.authorization = new SafeBoxEscortRuntimeAuthorization(state, effects);
        this.registry = new SafeBoxEscortRegistry(authorization.policy());
        this.lifecycle = new SafeBoxEscortRuntimeLifecycle(state, registry, recovery);
    }

    public synchronized StartResult start(SafeBoxEscortRuntimeContext context) {
        if (stopped || context == null) {
            return new StartResult(StartStatus.INVALID, null);
        }
        if (state.busyPlayer(context.playerId())) {
            return new StartResult(StartStatus.PLAYER_BUSY, null);
        }
        if (state.busyTeller(context.tellerId())) {
            return new StartResult(StartStatus.TELLER_BUSY, null);
        }
        SafeBoxEscortRuntimeState.Active active = state.reserve(context);
        if (active == null) {
            return new StartResult(StartStatus.TELLER_BUSY, null);
        }
        try {
            if (registry.start(context.sessionId(), context.playerId(),
                    context.target(), context.safeBounds()).status()
                    != SafeBoxEscortStartResult.Status.STARTED) {
                recovery.requestFinal(active, false);
                lifecycle.drive(active);
                return new StartResult(StartStatus.TELLER_BUSY, null);
            }
        } catch (RuntimeException ignored) {
            lifecycle.domainFailure(active);
            recovery.requestFinal(active, false);
            lifecycle.drive(active);
            return new StartResult(StartStatus.INVALID, null);
        }
        SafeBoxEscortRuntimePorts.NavigationStart result = recovery.startOutbound(active);
        if (result != SafeBoxEscortRuntimePorts.NavigationStart.STARTED) {
            lifecycle.domainFailure(active);
            state.releasePlayer(active);
            recovery.requestFinal(active, false);
            lifecycle.drive(active);
            return new StartResult(mapStart(result), null);
        }
        return new StartResult(StartStatus.STARTED, context.sessionId());
    }

    public synchronized InteractionStatus handleTellerInteraction(UUID playerId, UUID tellerId,
                                                                   long serverTick) {
        SafeBoxEscortRuntimeState.Active active = state.forPlayer(playerId);
        if (active == null) {
            return InteractionStatus.NOT_FOUND;
        }
        SafeBoxEscortMutation mutation = registry.onTellerInteraction(
                playerId, active.context.sessionId(), tellerId, serverTick);
        if (mutation.status() == SafeBoxEscortMutation.Status.AUTHORIZATION_DENIED) {
            return InteractionStatus.AUTHORIZATION_DENIED;
        }
        if (mutation.status() == SafeBoxEscortMutation.Status.WRONG_TELLER) {
            return InteractionStatus.WRONG_TELLER;
        }
        if (mutation.action() == SafeBoxEscortMutation.Action.GRANT_TARGET_ACCESS) {
            if (!recovery.grantInspection(active)) {
                lifecycle.domainFailure(active);
                state.releasePlayer(active);
                recovery.requestReturn(active, false);
                lifecycle.drive(active);
                return InteractionStatus.NOT_FOUND;
            }
            return InteractionStatus.ACCESS_GRANTED;
        }
        if (mutation.action() == SafeBoxEscortMutation.Action.INSPECTION_COMPLETE) {
            return InteractionStatus.WAITING_FOR_EXIT;
        }
        return mutation.status() == SafeBoxEscortMutation.Status.INVALID_PHASE
                ? InteractionStatus.INVALID_PHASE : InteractionStatus.NOT_FOUND;
    }

    public synchronized void tick(long serverTick) {
        if (stopped) {
            state.sessions().forEach(lifecycle::drive);
            return;
        }
        cleanup.tickNavigation();
        state.sessions().forEach(lifecycle::drive);
        for (SafeBoxEscortMutation mutation : registry.tick(serverTick)) {
            mutation.session().map(SafeBoxEscortSession::sessionId).map(state::forSession)
                    .ifPresent(active -> {
                        state.releasePlayer(active);
                        recovery.requestReturn(active, true);
                        lifecycle.drive(active);
                    });
        }
    }

    public synchronized void onPlayerPosition(UUID playerId, String dimension,
                                               EscortBlockPosition position) {
        SafeBoxEscortRuntimeState.Active active = state.forPlayer(playerId);
        if (active == null) {
            return;
        }
        SafeBoxEscortMutation mutation = registry.onPlayerPosition(
                playerId, active.context.sessionId(), dimension, position);
        if (mutation.action() == SafeBoxEscortMutation.Action.BEGIN_RETURN_ROUTE) {
            recovery.requestReturn(active, false);
            lifecycle.drive(active);
        }
    }

    public synchronized Optional<SafeBoxEscortSession> activeForPlayer(UUID playerId) {
        return registry.activeForPlayer(playerId);
    }

    public synchronized Optional<SafeBoxEscortSession> activeForTeller(UUID tellerId) {
        return registry.activeForTeller(tellerId);
    }

    public synchronized Set<String> activeVaultIds() {
        Set<String> vaultIds = new LinkedHashSet<>();
        for (SafeBoxEscortRuntimeState.Active active : state.sessions()) {
            if (active != null && active.context != null && active.context.target() != null) {
                vaultIds.add(active.context.target().vaultId());
            }
        }
        return Set.copyOf(vaultIds);
    }

    public synchronized int cancelVaults(Set<String> vaultIds) {
        if (vaultIds == null || vaultIds.isEmpty()) {
            return 0;
        }
        int cancelled = 0;
        for (SafeBoxEscortRuntimeState.Active active : List.copyOf(state.sessions())) {
            if (active == null || active.context == null || active.context.target() == null
                    || !vaultIds.contains(active.context.target().vaultId())) {
                continue;
            }
            registry.activeForPlayer(active.context.playerId()).ifPresent(session ->
                    registry.cancel(session.playerId(), session.sessionId()));
            state.releasePlayer(active);
            recovery.requestFinal(active, true);
            lifecycle.drive(active);
            cancelled++;
        }
        return cancelled;
    }

    public synchronized boolean busyTeller(UUID tellerId) {
        return state.busyTeller(tellerId);
    }

    public synchronized boolean busyPlayer(UUID playerId) {
        return state.busyPlayer(playerId);
    }

    public synchronized Optional<RecoveryState> recoveryState(UUID sessionId) {
        return Optional.ofNullable(state.forSession(sessionId))
                .map(SafeBoxEscortRuntimeState.Active::recoveryState);
    }

    public synchronized AccessDecision inspectAccess(SafeBoxEscortAccessRequest request,
                                                     long currentServerTick) {
        return authorization.inspect(registry, request, currentServerTick);
    }

    public synchronized void onLogout(UUID playerId) {
        lifecycle.terminatePlayer(playerId, SafeBoxEscortTerminalReason.LOGOUT);
    }

    public synchronized void onDeath(UUID playerId) {
        lifecycle.terminatePlayer(playerId, SafeBoxEscortTerminalReason.DEATH);
    }

    public synchronized void onDimensionChange(UUID playerId) {
        lifecycle.terminatePlayer(playerId, SafeBoxEscortTerminalReason.DIMENSION_CHANGE);
    }

    public synchronized void stop() {
        if (!stopped) {
            stopped = true;
            registry.onServerStop();
            for (SafeBoxEscortRuntimeState.Active active : state.sessions()) {
                state.releasePlayer(active);
                recovery.requestFinal(active, false);
            }
        }
        state.sessions().forEach(lifecycle::drive);
    }

    private static StartStatus mapStart(SafeBoxEscortRuntimePorts.NavigationStart status) {
        return switch (status) {
            case STARTED -> StartStatus.STARTED;
            case BUSY -> StartStatus.TELLER_BUSY;
            case INVALID_ROUTE -> StartStatus.INVALID_ROUTE;
            case TELLER_UNAVAILABLE -> StartStatus.TELLER_UNAVAILABLE;
        };
    }

    public enum StartStatus { STARTED, PLAYER_BUSY, TELLER_BUSY, INVALID_ROUTE, TELLER_UNAVAILABLE, INVALID }
    public enum InteractionStatus { ACCESS_GRANTED, WAITING_FOR_EXIT, AUTHORIZATION_DENIED, WRONG_TELLER, INVALID_PHASE, NOT_FOUND }
    public enum AccessDecision { ALLOWED, DENIED_ACTIVE_ESCORT, NO_ACTIVE_ESCORT }
    public enum RecoveryState { STARTING, OUTBOUND, AT_VAULT, RETURN_PREPARING, RETURNING, CLEANING }

    public record StartResult(StartStatus status, UUID sessionId) {
        public StartResult {
            Objects.requireNonNull(status, "status");
            if ((status == StartStatus.STARTED) != (sessionId != null)) {
                throw new IllegalArgumentException("only a started result has a session id");
            }
        }
    }
}
