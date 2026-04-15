package net.austizz.ultimatebankingsystem.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MoneyText {
    private static final Pattern DOLLAR_TOKEN = Pattern.compile("\\$([+-]?(?:\\d{1,3}(?:,\\d{3})*|\\d+)(?:\\.\\d+)?)");

    private MoneyText() {}

    public static String abbreviate(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return "";
        }

        BigDecimal parsed = parseFlexibleDecimal(value);
        if (parsed == null) {
            return value;
        }
        return abbreviate(parsed);
    }

    public static String abbreviate(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        BigDecimal abs = amount.abs();
        String suffix = "";
        BigDecimal divisor = BigDecimal.ONE;

        if (abs.compareTo(BigDecimal.valueOf(1_000_000_000_000L)) >= 0) {
            suffix = "T";
            divisor = BigDecimal.valueOf(1_000_000_000_000L);
        } else if (abs.compareTo(BigDecimal.valueOf(1_000_000_000L)) >= 0) {
            suffix = "B";
            divisor = BigDecimal.valueOf(1_000_000_000L);
        } else if (abs.compareTo(BigDecimal.valueOf(1_000_000L)) >= 0) {
            suffix = "M";
            divisor = BigDecimal.valueOf(1_000_000L);
        } else if (abs.compareTo(BigDecimal.valueOf(1_000L)) >= 0) {
            suffix = "K";
            divisor = BigDecimal.valueOf(1_000L);
        }

        // Truncate to 2 decimals so we never round up displayed money.
        BigDecimal shortened = amount.divide(divisor, 2, RoundingMode.DOWN);
        return shortened.stripTrailingZeros().toPlainString() + suffix;
    }

    public static String abbreviateWithDollar(String raw) {
        return "$" + abbreviate(raw);
    }

    public static String abbreviateWithDollar(BigDecimal amount) {
        return "$" + abbreviate(amount);
    }

    public static String abbreviateCurrencyTokens(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }

        Matcher matcher = DOLLAR_TOKEN.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = "$" + abbreviate(token);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static BigDecimal parseFlexibleDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.replace(",", "").trim();
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals("+") || cleaned.equals(".")) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
