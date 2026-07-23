package net.austizz.ultimatebankingsystem.heist;

public enum HeistPhase {
    PLANNING,
    COUNTDOWN,
    CASING,
    ACTIVE,
    ESCAPING,
    SUCCESS,
    FAILED;

    public boolean isRunning() {
        return this == CASING || this == ACTIVE || this == ESCAPING;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }

    public static HeistPhase byName(String value) {
        if (value == null || value.isBlank()) {
            return PLANNING;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return PLANNING;
        }
    }
}
