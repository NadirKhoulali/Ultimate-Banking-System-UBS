package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;

import java.util.UUID;

public final class SafeBoxEscortRuntimePorts {
    private SafeBoxEscortRuntimePorts() {
    }

    public interface Navigation {
        NavigationStart start(UUID sessionId,
                              UUID tellerId,
                              UUID playerId,
                              SafeBoxArea premiseBounds,
                              SafeTellerRoute route);

        NavigationState state(UUID sessionId);

        void tick();

        boolean cancel(UUID sessionId);

        boolean forget(UUID sessionId);
    }

    public interface Effects {
        boolean freshlyAuthorized(SafeBoxEscortRuntimeContext context);

        boolean acquireDoorHold(SafeBoxEscortRuntimeContext context);

        void releaseDoorHold(SafeBoxEscortRuntimeContext context);

        void grantAccess(SafeBoxEscortRuntimeContext context);

        void revokeAccess(SafeBoxEscortRuntimeContext context);

        void showMarker(SafeBoxEscortRuntimeContext context);

        void clearMarker(SafeBoxEscortRuntimeContext context);

        void eject(SafeBoxEscortRuntimeContext context);

        default void navigationFailed(SafeBoxEscortRuntimeContext context, boolean returning) {
        }
    }

    public enum NavigationStart {
        STARTED,
        BUSY,
        INVALID_ROUTE,
        TELLER_UNAVAILABLE
    }

    public enum NavigationState {
        RUNNING,
        ARRIVED,
        FAILED,
        CANCELLED,
        MISSING
    }
}
