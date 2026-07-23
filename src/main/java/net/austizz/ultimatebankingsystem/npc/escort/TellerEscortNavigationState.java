package net.austizz.ultimatebankingsystem.npc.escort;

import java.util.UUID;

public record TellerEscortNavigationState(UUID sessionId,
                                          UUID tellerId,
                                          Status status,
                                          FailureReason failureReason,
                                          int stepIndex) {
    public enum Status {
        RUNNING,
        ARRIVED,
        FAILED,
        CANCELLED
    }

    public enum FailureReason {
        NONE,
        INVALID_ROUTE,
        TELLER_UNAVAILABLE,
        LEASE_LOST,
        PATH_NOT_FOUND,
        WALK_STALLED,
        WALK_TIMEOUT,
        OUTSIDE_BANK_PREMISE,
        RELAY_POSITION_UNLOADED,
        RELAY_POSITION_OCCUPIED,
        RELAY_PLACEMENT_FAILED,
        RFID_SCANNER_UNAVAILABLE,
        RFID_ACCESS_DENIED,
        CLEANUP_INCOMPLETE,
        INTERNAL_ERROR
    }
}
