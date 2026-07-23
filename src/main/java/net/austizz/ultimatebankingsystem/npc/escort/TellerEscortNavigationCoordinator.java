package net.austizz.ultimatebankingsystem.npc.escort;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteValidator;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class TellerEscortNavigationCoordinator {
    private static final int MAX_TERMINAL_RESULTS = 256;

    // Cleanup gets a bounded eager window, then remains active as FAILED/CLEANUP_INCOMPLETE.
    // The session/teller maps are released only after all idempotent cleanup stages succeed.

    private final TellerRouteExecution.Limits limits;
    private final Map<UUID, TellerRouteExecution> activeBySession = new LinkedHashMap<>();
    private final Map<UUID, UUID> sessionByTeller = new HashMap<>();
    private final LinkedHashMap<UUID, TellerEscortNavigationState> terminalBySession = new LinkedHashMap<>();

    TellerEscortNavigationCoordinator() {
        this(TellerRouteExecution.DEFAULT_LIMITS);
    }

    TellerEscortNavigationCoordinator(TellerRouteExecution.Limits limits) {
        this.limits = limits;
    }

    TellerEscortStartResult start(UUID sessionId,
                                  UUID tellerId,
                                  SafeTellerRoute route,
                                  TellerRouteExecution.Actor actor) {
        if (sessionId == null || tellerId == null || route == null || actor == null
                || !SafeTellerRouteValidator.validate(route).valid()) {
            return new TellerEscortStartResult(TellerEscortStartResult.Status.INVALID_ROUTE, null);
        }
        if (activeBySession.containsKey(sessionId) || terminalBySession.containsKey(sessionId)) {
            return new TellerEscortStartResult(TellerEscortStartResult.Status.SESSION_EXISTS,
                    status(sessionId).orElse(null));
        }
        UUID busySession = sessionByTeller.get(tellerId);
        if (busySession != null) {
            return new TellerEscortStartResult(TellerEscortStartResult.Status.BUSY,
                    activeBySession.get(busySession).snapshot());
        }

        TellerRouteExecution execution = new TellerRouteExecution(sessionId, tellerId, route, actor, limits);
        if (!execution.start()) {
            return new TellerEscortStartResult(TellerEscortStartResult.Status.BUSY, null);
        }
        activeBySession.put(sessionId, execution);
        sessionByTeller.put(tellerId, sessionId);
        return new TellerEscortStartResult(TellerEscortStartResult.Status.STARTED, execution.snapshot());
    }

    void tick() {
        for (UUID sessionId : activeBySession.keySet().toArray(UUID[]::new)) {
            TellerRouteExecution execution = activeBySession.get(sessionId);
            try {
                execution.tick();
            } catch (RuntimeException exception) {
                execution.failInternal();
            } finally {
                if (execution.terminal()) {
                    complete(sessionId, execution);
                }
            }
        }
    }

    Optional<TellerEscortNavigationState> status(UUID sessionId) {
        TellerRouteExecution active = activeBySession.get(sessionId);
        return active == null
                ? Optional.ofNullable(terminalBySession.get(sessionId))
                : Optional.of(active.snapshot());
    }

    Optional<TellerEscortNavigationState> statusForTeller(UUID tellerId) {
        UUID sessionId = sessionByTeller.get(tellerId);
        return sessionId == null ? Optional.empty() : status(sessionId);
    }

    boolean cancelSession(UUID sessionId) {
        TellerRouteExecution execution = activeBySession.get(sessionId);
        if (execution == null) {
            return false;
        }
        try {
            execution.cancel();
        } catch (RuntimeException exception) {
            execution.failInternal();
        } finally {
            if (execution.terminal()) {
                complete(sessionId, execution);
            }
        }
        return true;
    }

    boolean cancelTeller(UUID tellerId) {
        UUID sessionId = sessionByTeller.get(tellerId);
        return sessionId != null && cancelSession(sessionId);
    }

    boolean forget(UUID sessionId) {
        return terminalBySession.remove(sessionId) != null;
    }

    int activeCount() {
        return activeBySession.size();
    }

    private void complete(UUID sessionId, TellerRouteExecution execution) {
        activeBySession.remove(sessionId);
        TellerEscortNavigationState state = execution.snapshot();
        sessionByTeller.remove(state.tellerId(), sessionId);
        terminalBySession.put(sessionId, state);
        while (terminalBySession.size() > MAX_TERMINAL_RESULTS) {
            UUID oldest = terminalBySession.keySet().iterator().next();
            terminalBySession.remove(oldest);
        }
    }
}
