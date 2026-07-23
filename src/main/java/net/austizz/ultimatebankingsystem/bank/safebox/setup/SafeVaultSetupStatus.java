package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.util.Locale;

public enum SafeVaultSetupStatus {
    SETUP_PENDING,
    ROUTES_PENDING,
    READY;

    public static SafeVaultSetupStatus parse(String raw) {
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
