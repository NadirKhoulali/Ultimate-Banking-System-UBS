package net.austizz.ultimatebankingsystem.claim;

public enum ClaimAction {
    SET_POS1,
    SET_POS2,
    SET_ADD_MODE,
    SET_REMOVE_MODE,
    STAGE_TARGET,
    CAPTURE_POSITION,
    APPLY,
    CLEAR,
    TOGGLE_OUTLINES,
    SAVE_AND_EXIT,
    FINISH_AND_EXIT,
    DISCARD_AND_EXIT,
    REQUEST_SYNC;

    public static ClaimAction byName(String raw) {
        if (raw == null || raw.isBlank()) {
            return REQUEST_SYNC;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return REQUEST_SYNC;
        }
    }
}
