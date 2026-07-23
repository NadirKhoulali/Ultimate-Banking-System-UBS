package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.util.Locale;

public enum SafePremiseMode {
    PUBLIC,
    STAFF_ONLY;

    public static SafePremiseMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
