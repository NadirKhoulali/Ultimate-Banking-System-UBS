package net.austizz.ultimatebankingsystem.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MoneyText {
    private static final Pattern DOLLAR_TOKEN = Pattern.compile("\\$([+-]?(?:\\d{1,3}(?:,\\d{3})*|\\d+)(?:\\.\\d+)?)");
    private static final String[] SCALE_SUFFIXES = {
            "", "K", "M", "B", "T",
            "Qa", "Qi", "Sx", "Sp", "Oc", "No",
            "Dc", "Ud", "Dd", "Td", "Qad", "Qid", "Sxd", "Spd", "Ocd", "Nod"
    };
    private static final BigDecimal[] SCALE_DIVISORS = buildScaleDivisors();

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
        int scaleIndex = 0;
        for (int i = SCALE_DIVISORS.length - 1; i >= 1; i--) {
            if (abs.compareTo(SCALE_DIVISORS[i]) >= 0) {
                scaleIndex = i;
                break;
            }
        }

        // Truncate to 2 decimals so we never round up displayed money.
        BigDecimal shortened = amount.divide(SCALE_DIVISORS[scaleIndex], 2, RoundingMode.DOWN);
        return shortened.stripTrailingZeros().toPlainString() + SCALE_SUFFIXES[scaleIndex];
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

    private static BigDecimal[] buildScaleDivisors() {
        BigDecimal[] divisors = new BigDecimal[SCALE_SUFFIXES.length];
        divisors[0] = BigDecimal.ONE;
        BigDecimal current = BigDecimal.ONE;
        for (int i = 1; i < SCALE_SUFFIXES.length; i++) {
            current = current.multiply(BigDecimal.valueOf(1_000L));
            divisors[i] = current;
        }
        return divisors;
    }
}
