package net.austizz.ultimatebankingsystem.bank.safebox.route;

import java.util.Locale;

public enum SafeTellerRouteFace {
    DOWN,
    UP,
    NORTH,
    SOUTH,
    WEST,
    EAST;

    static SafeTellerRouteFace parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
