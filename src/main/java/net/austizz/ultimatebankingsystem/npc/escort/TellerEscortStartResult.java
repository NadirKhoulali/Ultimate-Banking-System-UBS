package net.austizz.ultimatebankingsystem.npc.escort;

public record TellerEscortStartResult(Status status, TellerEscortNavigationState state) {
    public enum Status {
        STARTED,
        BUSY,
        SESSION_EXISTS,
        INVALID_ROUTE,
        TELLER_UNAVAILABLE
    }
}
