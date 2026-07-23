package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class SafeBoxEscortRuntimeState {
    private final Map<UUID, Active> byPlayer;
    private final Map<UUID, Active> byTeller;
    private final Map<UUID, Active> bySession;

    SafeBoxEscortRuntimeState() {
        this(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    SafeBoxEscortRuntimeState(Map<UUID, Active> byPlayer,
                              Map<UUID, Active> byTeller,
                              Map<UUID, Active> bySession) {
        this.byPlayer = Objects.requireNonNull(byPlayer, "byPlayer");
        this.byTeller = Objects.requireNonNull(byTeller, "byTeller");
        this.bySession = Objects.requireNonNull(bySession, "bySession");
    }

    Active reserve(SafeBoxEscortRuntimeContext context) {
        if (byPlayer.containsKey(context.playerId())
                || byTeller.containsKey(context.tellerId())
                || bySession.containsKey(context.sessionId())) {
            return null;
        }
        Active active = new Active(context);
        byPlayer.put(context.playerId(), active);
        byTeller.put(context.tellerId(), active);
        bySession.put(context.sessionId(), active);
        return active;
    }

    Active forPlayer(UUID playerId) {
        return byPlayer.get(playerId);
    }

    Active forSession(UUID sessionId) {
        return bySession.get(sessionId);
    }

    boolean busyPlayer(UUID playerId) {
        return playerId != null && byPlayer.containsKey(playerId);
    }

    boolean busyTeller(UUID tellerId) {
        return tellerId != null && byTeller.containsKey(tellerId);
    }

    List<Active> sessions() {
        return List.copyOf(bySession.values());
    }

    void releasePlayer(Active active) {
        if (!active.playerIndexed) {
            return;
        }
        try {
            byPlayer.remove(active.context.playerId(), active);
            active.playerIndexed = false;
        } catch (RuntimeException ignored) {
            // Retained for the next tick.
        }
    }

    boolean remove(Active active) {
        releasePlayer(active);
        if (active.tellerIndexed) {
            try {
                byTeller.remove(active.context.tellerId(), active);
                active.tellerIndexed = false;
            } catch (RuntimeException ignored) {
                // Retained for the next tick.
            }
        }
        if (!active.playerIndexed && !active.tellerIndexed && active.sessionIndexed) {
            try {
                bySession.remove(active.context.sessionId(), active);
                active.sessionIndexed = false;
            } catch (RuntimeException ignored) {
                // Retained for the next tick.
            }
        }
        return !active.playerIndexed && !active.tellerIndexed && !active.sessionIndexed;
    }

    enum Stage {
        STARTING,
        OUTBOUND,
        AT_VAULT,
        RETURN_PREPARING,
        RETURNING,
        FINALIZING
    }

    static final class Active {
        final SafeBoxEscortRuntimeContext context;
        Stage stage = Stage.STARTING;
        boolean playerIndexed = true;
        boolean tellerIndexed = true;
        boolean sessionIndexed = true;
        boolean navigationActive;
        boolean navigationStartUncertain;
        boolean cancelPending;
        boolean forgetPending;
        boolean accessOwned;
        boolean markerOwned;
        boolean doorOwned;
        boolean ejectPending;

        Active(SafeBoxEscortRuntimeContext context) {
            this.context = context;
        }

        boolean returnRequired() {
            return doorOwned || stage == Stage.AT_VAULT
                    || stage == Stage.RETURN_PREPARING || stage == Stage.RETURNING;
        }

        SafeBoxEscortRuntime.RecoveryState recoveryState() {
            return switch (stage) {
                case STARTING -> SafeBoxEscortRuntime.RecoveryState.STARTING;
                case OUTBOUND -> SafeBoxEscortRuntime.RecoveryState.OUTBOUND;
                case AT_VAULT -> SafeBoxEscortRuntime.RecoveryState.AT_VAULT;
                case RETURN_PREPARING -> SafeBoxEscortRuntime.RecoveryState.RETURN_PREPARING;
                case RETURNING -> SafeBoxEscortRuntime.RecoveryState.RETURNING;
                case FINALIZING -> SafeBoxEscortRuntime.RecoveryState.CLEANING;
            };
        }
    }
}
