package net.austizz.ultimatebankingsystem.account;

import java.util.Locale;

public enum AccountPrincipalType {
    PLAYER,
    INSTITUTION;

    public static AccountPrincipalType parse(String value) {
        if (value == null || value.isBlank()) {
            return PLAYER;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return PLAYER;
        }
    }
}
