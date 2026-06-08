package net.austizz.ultimatebankingsystem.shelf;

import net.austizz.ultimatebankingsystem.util.MoneyText;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ShelfPrice {
    private static final long MAX_CENTS = Long.MAX_VALUE;

    private ShelfPrice() {
    }

    public static long parseInputToCents(String raw, long fallbackCents) {
        if (raw == null || raw.isBlank()) {
            return Math.max(0L, fallbackCents);
        }
        try {
            BigDecimal parsed = new BigDecimal(raw.replace(",", "").trim());
            if (parsed.compareTo(BigDecimal.ZERO) < 0) {
                return -1L;
            }
            BigDecimal scaled = parsed.setScale(2, RoundingMode.DOWN).movePointRight(2);
            if (scaled.compareTo(BigDecimal.valueOf(MAX_CENTS)) > 0) {
                return MAX_CENTS;
            }
            return scaled.longValueExact();
        } catch (Exception ignored) {
            return Math.max(0L, fallbackCents);
        }
    }

    public static String displayInputFromCents(long cents) {
        BigDecimal dollars = toDollars(cents);
        return dollars.stripTrailingZeros().toPlainString();
    }

    public static String abbreviateFromCents(long cents) {
        return MoneyText.abbreviate(toDollars(cents));
    }

    public static BigDecimal toDollars(long cents) {
        long safe = Math.max(0L, cents);
        return BigDecimal.valueOf(safe).movePointLeft(2);
    }
}
