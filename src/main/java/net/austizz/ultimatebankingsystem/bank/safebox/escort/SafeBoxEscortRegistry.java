package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SafeBoxEscortRegistry {
    public static final long INSPECTION_TIMEOUT_TICKS = 6_000L;

    private final Map<UUID, SafeBoxEscortSession> byPlayer = new LinkedHashMap<>();
    private final Map<UUID, SafeBoxEscortSession> byTeller = new LinkedHashMap<>();
    private final Map<UUID, SafeBoxEscortSession> bySession = new LinkedHashMap<>();
    private final SafeBoxEscortAuthorizationPolicy authorizationPolicy;
    private long latestServerTick = -1L;

    public SafeBoxEscortRegistry() {
        this(SafeBoxEscortAuthorizationPolicy.denyAll());
    }

    public SafeBoxEscortRegistry(SafeBoxEscortAuthorizationPolicy authorizationPolicy) {
        this.authorizationPolicy = Objects.requireNonNull(authorizationPolicy,
                "authorizationPolicy");
    }

    public synchronized SafeBoxEscortStartResult start(UUID sessionId, UUID playerId,
                                                       SafeBoxEscortTarget target,
                                                       SafeBoxArea safeArea) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(safeArea, "safeArea");
        if (byPlayer.containsKey(playerId)
                || byTeller.containsKey(target.requestedTellerId())
                || bySession.containsKey(sessionId)) {
            return SafeBoxEscortStartResult.busy();
        }
        SafeBoxEscortSession session = SafeBoxEscortSession.started(
                sessionId, playerId, target, safeArea);
        index(session);
        return SafeBoxEscortStartResult.started(session);
    }

    public synchronized Optional<SafeBoxEscortSession> activeForPlayer(UUID playerId) {
        return Optional.ofNullable(byPlayer.get(playerId));
    }

    public synchronized Optional<SafeBoxEscortSession> activeForTeller(UUID tellerId) {
        return Optional.ofNullable(byTeller.get(tellerId));
    }

    synchronized Optional<SafeBoxEscortSession> activeInspectionForPlayerAt(UUID playerId,
                                                                             long serverTick) {
        EscortSessionInputs.requireValue(playerId, "playerId");
        validateClock(serverTick);
        latestServerTick = serverTick;
        SafeBoxEscortSession session = byPlayer.get(playerId);
        if (session == null || (session.phase() != SafeBoxEscortPhase.INSPECTING
                && session.phase() != SafeBoxEscortPhase.WAITING_FOR_EXIT)) {
            return Optional.empty();
        }
        long grantedAt = session.inspectionGrantedAtTick();
        return grantedAt >= 0 && serverTick - grantedAt < INSPECTION_TIMEOUT_TICKS
                ? Optional.of(session)
                : Optional.empty();
    }

    public synchronized int activeCount() {
        return bySession.size();
    }

    public synchronized SafeBoxEscortMutation onArrivedAtVault(UUID playerId, UUID sessionId) {
        return transition(playerId, sessionId, SafeBoxEscortPhase.OUTBOUND,
                SafeBoxEscortPhase.AT_VAULT, SafeBoxEscortMutation.Action.NONE);
    }

    public synchronized SafeBoxEscortMutation onTellerInteraction(UUID playerId, UUID sessionId,
                                                                   UUID tellerId, long serverTick) {
        EscortSessionInputs.requireValue(tellerId, "tellerId");
        validateClock(serverTick);
        SessionLookup lookup = lookup(playerId, sessionId);
        if (lookup.failure() != null) {
            return lookup.failure();
        }
        SafeBoxEscortSession current = lookup.session();
        if (!current.target().requestedTellerId().equals(tellerId)) {
            return SafeBoxEscortMutation.unchanged(
                    SafeBoxEscortMutation.Status.WRONG_TELLER, current);
        }
        if (current.phase() == SafeBoxEscortPhase.AT_VAULT) {
            SafeBoxEscortAuthorizationPolicy.Decision decision = authorizationPolicy.authorize(
                    current.playerId(), current.target());
            if (decision != SafeBoxEscortAuthorizationPolicy.Decision.GRANTED) {
                return SafeBoxEscortMutation.unchanged(
                        SafeBoxEscortMutation.Status.AUTHORIZATION_DENIED, current);
            }
            SafeBoxEscortSession next = current.grantInspection(serverTick);
            latestServerTick = serverTick;
            replace(next);
            return SafeBoxEscortMutation.applied(
                    next, SafeBoxEscortMutation.Action.GRANT_TARGET_ACCESS);
        }
        if (current.phase() == SafeBoxEscortPhase.INSPECTING) {
            SafeBoxEscortSession next = current.moveTo(SafeBoxEscortPhase.WAITING_FOR_EXIT);
            latestServerTick = serverTick;
            replace(next);
            return SafeBoxEscortMutation.applied(
                    next, SafeBoxEscortMutation.Action.INSPECTION_COMPLETE);
        }
        return SafeBoxEscortMutation.unchanged(
                SafeBoxEscortMutation.Status.INVALID_PHASE, current);
    }

    public synchronized SafeBoxEscortMutation onPlayerPosition(UUID playerId, UUID sessionId,
                                                                 String dimension,
                                                                 EscortBlockPosition position) {
        EscortSessionInputs.requireText(dimension, "dimension");
        EscortSessionInputs.requireValue(position, "position");
        SessionLookup lookup = lookup(playerId, sessionId);
        if (lookup.failure() != null) {
            return lookup.failure();
        }
        SafeBoxEscortSession current = lookup.session();
        if (current.phase() != SafeBoxEscortPhase.WAITING_FOR_EXIT) {
            return SafeBoxEscortMutation.unchanged(
                    SafeBoxEscortMutation.Status.INVALID_PHASE, current);
        }
        if (current.safeArea().contains(dimension, position)) {
            return SafeBoxEscortMutation.unchanged(SafeBoxEscortMutation.Status.NO_CHANGE, current);
        }
        SafeBoxEscortSession next = current.moveTo(SafeBoxEscortPhase.RETURNING);
        replace(next);
        return SafeBoxEscortMutation.applied(
                next, SafeBoxEscortMutation.Action.BEGIN_RETURN_ROUTE);
    }

    public synchronized SafeBoxEscortMutation onReturnRouteComplete(UUID playerId, UUID sessionId) {
        SessionLookup lookup = lookup(playerId, sessionId);
        if (lookup.failure() != null) {
            return lookup.failure();
        }
        if (lookup.session().phase() != SafeBoxEscortPhase.RETURNING) {
            return SafeBoxEscortMutation.unchanged(
                    SafeBoxEscortMutation.Status.INVALID_PHASE, lookup.session());
        }
        return terminate(lookup.session(), SafeBoxEscortTerminalReason.COMPLETED);
    }

    public synchronized List<SafeBoxEscortMutation> tick(long serverTick) {
        validateClock(serverTick);
        latestServerTick = serverTick;
        List<SafeBoxEscortMutation> timedOut = new ArrayList<>();
        for (SafeBoxEscortSession session : List.copyOf(bySession.values())) {
            long grantedAt = session.inspectionGrantedAtTick();
            boolean eligible = session.phase() == SafeBoxEscortPhase.INSPECTING
                    || session.phase() == SafeBoxEscortPhase.WAITING_FOR_EXIT;
            if (eligible && grantedAt >= 0
                    && serverTick - grantedAt >= INSPECTION_TIMEOUT_TICKS) {
                timedOut.add(terminate(session, SafeBoxEscortTerminalReason.TIMED_OUT));
            }
        }
        return List.copyOf(timedOut);
    }

    public synchronized SafeBoxEscortMutation onLogout(UUID playerId, UUID sessionId) {
        return terminate(playerId, sessionId, SafeBoxEscortTerminalReason.LOGOUT);
    }

    public synchronized SafeBoxEscortMutation onDeath(UUID playerId, UUID sessionId) {
        return terminate(playerId, sessionId, SafeBoxEscortTerminalReason.DEATH);
    }

    public synchronized SafeBoxEscortMutation onDimensionChange(UUID playerId, UUID sessionId) {
        return terminate(playerId, sessionId, SafeBoxEscortTerminalReason.DIMENSION_CHANGE);
    }

    public synchronized SafeBoxEscortMutation onRouteFailure(UUID playerId, UUID sessionId) {
        return terminate(playerId, sessionId, SafeBoxEscortTerminalReason.ROUTE_FAILURE);
    }

    public synchronized SafeBoxEscortMutation cancel(UUID playerId, UUID sessionId) {
        return terminate(playerId, sessionId, SafeBoxEscortTerminalReason.CANCELLED);
    }

    public synchronized List<SafeBoxEscortMutation> onServerStop() {
        List<SafeBoxEscortMutation> stopped = new ArrayList<>();
        for (SafeBoxEscortSession session : List.copyOf(bySession.values())) {
            stopped.add(terminate(session, SafeBoxEscortTerminalReason.SERVER_STOP));
        }
        return List.copyOf(stopped);
    }

    private SafeBoxEscortMutation transition(UUID playerId, UUID sessionId,
                                              SafeBoxEscortPhase expected,
                                              SafeBoxEscortPhase nextPhase,
                                              SafeBoxEscortMutation.Action action) {
        SessionLookup lookup = lookup(playerId, sessionId);
        if (lookup.failure() != null) {
            return lookup.failure();
        }
        if (lookup.session().phase() != expected) {
            return SafeBoxEscortMutation.unchanged(
                    SafeBoxEscortMutation.Status.INVALID_PHASE, lookup.session());
        }
        SafeBoxEscortSession next = lookup.session().moveTo(nextPhase);
        replace(next);
        return SafeBoxEscortMutation.applied(next, action);
    }

    private SafeBoxEscortMutation terminate(UUID playerId, UUID sessionId,
                                             SafeBoxEscortTerminalReason reason) {
        SessionLookup lookup = lookup(playerId, sessionId);
        return lookup.failure() != null ? lookup.failure() : terminate(lookup.session(), reason);
    }

    private SafeBoxEscortMutation terminate(SafeBoxEscortSession current,
                                             SafeBoxEscortTerminalReason reason) {
        SafeBoxEscortSession terminal = current.terminate(reason);
        release(current);
        return SafeBoxEscortMutation.terminal(terminal);
    }

    private SessionLookup lookup(UUID playerId, UUID sessionId) {
        EscortSessionInputs.requireValue(playerId, "playerId");
        EscortSessionInputs.requireValue(sessionId, "sessionId");
        SafeBoxEscortSession current = byPlayer.get(playerId);
        if (current == null) {
            return new SessionLookup(null, SafeBoxEscortMutation.unchanged(
                    SafeBoxEscortMutation.Status.NOT_FOUND, null));
        }
        if (!current.sessionId().equals(sessionId)) {
            return new SessionLookup(null, SafeBoxEscortMutation.unchanged(
                    SafeBoxEscortMutation.Status.STALE_SESSION, current));
        }
        return new SessionLookup(current, null);
    }

    private void index(SafeBoxEscortSession session) {
        byPlayer.put(session.playerId(), session);
        byTeller.put(session.target().requestedTellerId(), session);
        bySession.put(session.sessionId(), session);
    }

    private void replace(SafeBoxEscortSession session) {
        index(session);
    }

    private void release(SafeBoxEscortSession session) {
        byPlayer.remove(session.playerId(), session);
        byTeller.remove(session.target().requestedTellerId(), session);
        bySession.remove(session.sessionId(), session);
    }

    private void validateClock(long serverTick) {
        if (serverTick < 0 || serverTick < latestServerTick) {
            throw new IllegalArgumentException("serverTick must be non-negative and monotonic");
        }
    }

    private record SessionLookup(SafeBoxEscortSession session, SafeBoxEscortMutation failure) {
    }
}
