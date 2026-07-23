package net.austizz.ultimatebankingsystem.bank.safebox.route;

import java.util.Locale;

public enum SafeTellerRouteDirection {
    OUTBOUND,
    RETURN;

    static SafeTellerRouteDirection parse(String raw) {
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
