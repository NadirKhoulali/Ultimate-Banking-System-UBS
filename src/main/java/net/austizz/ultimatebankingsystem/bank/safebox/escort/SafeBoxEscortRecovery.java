package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntimeState.Active;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntimeState.Stage;

final class SafeBoxEscortRecovery {
    private final SafeBoxEscortRuntimePorts.Navigation navigation;
    private final SafeBoxEscortRuntimePorts.Effects effects;
    private final SafeBoxEscortRuntimeState state;
    private final SafeBoxEscortCleanup cleanup;

    SafeBoxEscortRecovery(SafeBoxEscortRuntimePorts.Navigation navigation,
                          SafeBoxEscortRuntimePorts.Effects effects,
                          SafeBoxEscortRuntimeState state,
                          SafeBoxEscortCleanup cleanup) {
        this.navigation = navigation;
        this.effects = effects;
        this.state = state;
        this.cleanup = cleanup;
    }

    SafeBoxEscortRuntimePorts.NavigationStart startOutbound(Active active) {
        active.stage = Stage.OUTBOUND;
        active.navigationActive = true;
        active.navigationStartUncertain = false;
        try {
            SafeBoxEscortRuntimePorts.NavigationStart result = navigation.start(
                    active.context.sessionId(), active.context.tellerId(), active.context.playerId(),
                    active.context.premiseBounds(),
                    active.context.outboundRoute());
            if (result != SafeBoxEscortRuntimePorts.NavigationStart.STARTED) {
                active.navigationActive = false;
            }
            return result;
        } catch (RuntimeException ignored) {
            active.navigationStartUncertain = true;
            return SafeBoxEscortRuntimePorts.NavigationStart.TELLER_UNAVAILABLE;
        }
    }

    boolean grantInspection(Active active) {
        active.accessOwned = true;
        try {
            effects.grantAccess(active.context);
        } catch (RuntimeException ignored) {
            return false;
        }
        active.markerOwned = true;
        try {
            effects.showMarker(active.context);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    void requestReturn(Active active, boolean eject) {
        active.ejectPending |= eject;
        if (active.stage == Stage.RETURNING || active.stage == Stage.RETURN_PREPARING
                || active.stage == Stage.FINALIZING) {
            return;
        }
        active.stage = Stage.RETURN_PREPARING;
        cleanup.requestNavigationCleanup(active);
    }

    void requestFinal(Active active, boolean eject) {
        active.ejectPending |= eject;
        active.stage = Stage.FINALIZING;
        cleanup.requestNavigationCleanup(active);
    }

    Signal progress(Active active) {
        return switch (active.stage) {
            case STARTING -> Signal.NONE;
            case OUTBOUND -> pollOutbound(active);
            case AT_VAULT -> {
                cleanup.progressNavigation(active);
                yield Signal.NONE;
            }
            case RETURN_PREPARING -> prepareReturn(active);
            case RETURNING -> pollReturn(active);
            case FINALIZING -> finish(active);
        };
    }

    private Signal pollOutbound(Active active) {
        SafeBoxEscortRuntimePorts.NavigationState current = cleanup.navigationState(active);
        if (current == null || current == SafeBoxEscortRuntimePorts.NavigationState.RUNNING) {
            return Signal.NONE;
        }
        active.navigationActive = false;
        active.navigationStartUncertain = false;
        if (current != SafeBoxEscortRuntimePorts.NavigationState.ARRIVED) {
            notifyNavigationFailure(active, false);
            active.cancelPending = false;
            active.forgetPending = current != SafeBoxEscortRuntimePorts.NavigationState.MISSING;
            requestFinal(active, false);
            return Signal.OUTBOUND_FAILED;
        }
        active.forgetPending = true;
        cleanup.progressNavigation(active);
        active.doorOwned = true;
        try {
            if (!effects.acquireDoorHold(active.context)) {
                active.doorOwned = false;
                active.stage = Stage.AT_VAULT;
                return Signal.OUTBOUND_FAILED_AT_VAULT;
            }
        } catch (RuntimeException ignored) {
            active.stage = Stage.AT_VAULT;
            return Signal.OUTBOUND_FAILED_AT_VAULT;
        }
        active.stage = Stage.AT_VAULT;
        return Signal.ARRIVED_AT_VAULT;
    }

    private Signal prepareReturn(Active active) {
        cleanup.progress(active);
        if (cleanup.pending(active)) {
            return Signal.NONE;
        }
        active.navigationActive = true;
        active.navigationStartUncertain = false;
        try {
            SafeBoxEscortRuntimePorts.NavigationStart result = navigation.start(
                    active.context.sessionId(), active.context.tellerId(), active.context.playerId(),
                    active.context.premiseBounds(),
                    active.context.returnRoute());
            if (result == SafeBoxEscortRuntimePorts.NavigationStart.STARTED) {
                active.stage = Stage.RETURNING;
                return Signal.NONE;
            }
            active.navigationActive = false;
        } catch (RuntimeException ignored) {
            active.navigationStartUncertain = true;
        }
        requestFinal(active, false);
        return Signal.RETURN_FAILED;
    }

    private Signal pollReturn(Active active) {
        SafeBoxEscortRuntimePorts.NavigationState current = cleanup.navigationState(active);
        if (current == null || current == SafeBoxEscortRuntimePorts.NavigationState.RUNNING) {
            return Signal.NONE;
        }
        active.navigationActive = false;
        active.navigationStartUncertain = false;
        active.cancelPending = false;
        active.forgetPending = current != SafeBoxEscortRuntimePorts.NavigationState.MISSING;
        requestFinal(active, false);
        if (current != SafeBoxEscortRuntimePorts.NavigationState.ARRIVED) {
            notifyNavigationFailure(active, true);
        }
        return current == SafeBoxEscortRuntimePorts.NavigationState.ARRIVED
                ? Signal.RETURN_ARRIVED : Signal.RETURN_FAILED;
    }

    private void notifyNavigationFailure(Active active, boolean returning) {
        try {
            effects.navigationFailed(active.context, returning);
        } catch (RuntimeException ignored) {
            // Failure reporting must not block escort cleanup.
        }
    }

    private Signal finish(Active active) {
        cleanup.progress(active);
        if (!cleanup.pending(active) && state.remove(active)) {
            return Signal.REMOVED;
        }
        return Signal.NONE;
    }

    enum Signal {
        NONE,
        ARRIVED_AT_VAULT,
        OUTBOUND_FAILED,
        OUTBOUND_FAILED_AT_VAULT,
        RETURN_ARRIVED,
        RETURN_FAILED,
        REMOVED
    }
}
