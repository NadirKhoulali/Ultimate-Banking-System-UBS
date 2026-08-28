package net.austizz.ultimatebankingsystem.migration.numismatics;

public enum NumismaticsMigrationPhase {
    IDLE,
    SOURCE_READY,
    PREFLIGHT_RUNNING,
    READY,
    BACKING_UP,
    CONVERTING_ACCOUNTS,
    CONVERTING_PLAYERS,
    CONVERTING_WORLD,
    RECONCILING,
    COMPLETE,
    FAILED,
    ROLLED_BACK;

    public boolean running() {
        return this == PREFLIGHT_RUNNING
                || this == BACKING_UP
                || this == CONVERTING_ACCOUNTS
                || this == CONVERTING_PLAYERS
                || this == CONVERTING_WORLD
                || this == RECONCILING;
    }
}
