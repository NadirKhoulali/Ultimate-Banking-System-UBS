package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntimeState.Active;

final class SafeBoxEscortCleanup {
    private final SafeBoxEscortRuntimePorts.Navigation navigation;
    private final SafeBoxEscortRuntimePorts.Effects effects;

    SafeBoxEscortCleanup(SafeBoxEscortRuntimePorts.Navigation navigation,
                         SafeBoxEscortRuntimePorts.Effects effects) {
        this.navigation = navigation;
        this.effects = effects;
    }

    void tickNavigation() {
        try {
            navigation.tick();
        } catch (RuntimeException ignored) {
            // Per-session cleanup still progresses.
        }
    }

    SafeBoxEscortRuntimePorts.NavigationState navigationState(Active active) {
        try {
            return navigation.state(active.context.sessionId());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    void requestNavigationCleanup(Active active) {
        if (active.navigationActive) {
            active.cancelPending = true;
            active.forgetPending = true;
        }
    }

    void progress(Active active) {
        cleanupEffects(active);
        cleanupEjection(active);
        cleanupNavigation(active);
    }

    void progressNavigation(Active active) {
        cleanupNavigation(active);
    }

    boolean pending(Active active) {
        return active.accessOwned || active.markerOwned || active.doorOwned || active.ejectPending
                || active.cancelPending || active.forgetPending;
    }

    private void cleanupNavigation(Active active) {
        if (active.cancelPending) {
            try {
                if (navigation.cancel(active.context.sessionId())) {
                    active.cancelPending = false;
                    active.navigationActive = false;
                    active.navigationStartUncertain = false;
                }
            } catch (RuntimeException ignored) {
                // Reconciliation and forget still run independently.
            }
            reconcileCancellation(active);
        }
        if (active.forgetPending) {
            try {
                boolean accepted = navigation.forget(active.context.sessionId());
                if (accepted) {
                    active.cancelPending = false;
                    active.forgetPending = false;
                    active.navigationActive = false;
                    active.navigationStartUncertain = false;
                }
            } catch (RuntimeException ignored) {
                // Retry on a later tick.
            }
        }
    }

    private void reconcileCancellation(Active active) {
        if (!active.cancelPending) {
            return;
        }
        SafeBoxEscortRuntimePorts.NavigationState current = navigationState(active);
        if (current == SafeBoxEscortRuntimePorts.NavigationState.MISSING
                && active.navigationStartUncertain) {
            active.cancelPending = false;
            active.forgetPending = false;
            active.navigationActive = false;
            active.navigationStartUncertain = false;
            return;
        }
        if (current == SafeBoxEscortRuntimePorts.NavigationState.ARRIVED
                || current == SafeBoxEscortRuntimePorts.NavigationState.FAILED
                || current == SafeBoxEscortRuntimePorts.NavigationState.CANCELLED) {
            active.cancelPending = false;
            active.navigationActive = false;
            active.navigationStartUncertain = false;
        }
    }

    private void cleanupEffects(Active active) {
        if (active.accessOwned) {
            try {
                effects.revokeAccess(active.context);
                active.accessOwned = false;
            } catch (RuntimeException ignored) {
                // Retry independently.
            }
        }
        if (active.markerOwned) {
            try {
                effects.clearMarker(active.context);
                active.markerOwned = false;
            } catch (RuntimeException ignored) {
                // Retry independently.
            }
        }
        if (active.doorOwned) {
            try {
                effects.releaseDoorHold(active.context);
                active.doorOwned = false;
            } catch (RuntimeException ignored) {
                // Retry independently.
            }
        }
    }

    private void cleanupEjection(Active active) {
        if (!active.ejectPending) {
            return;
        }
        try {
            effects.eject(active.context);
            active.ejectPending = false;
        } catch (RuntimeException ignored) {
            // Retry on a later tick.
        }
    }
}
