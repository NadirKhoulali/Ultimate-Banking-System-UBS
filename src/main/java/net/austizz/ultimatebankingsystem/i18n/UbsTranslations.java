package net.austizz.ultimatebankingsystem.i18n;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lightweight UBS localization helper.
 *
 * <p>We intentionally use the original English text as the translation key.
 * When a key is missing in the active locale, Minecraft falls back to
 * rendering the key itself, which keeps behavior stable while allowing
 * language packs to override text.</p>
 */
public final class UbsTranslations {
    private static final int MAX_RESOLVE_DEPTH = 6;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[+-]?\\$?\\d[\\d,]*(?:\\.\\d+)?%?$");
    private static final String[] EMBEDDED_VALUE_TEXT = {
            "Certificate of Deposit Account",
            "Money Market Account",
            "Checking Account",
            "Saving Account",
            "APPROVED",
            "REJECTED",
            "ACCEPTED",
            "DECLINED",
            "CANCELLED",
            "CANCELED",
            "DISABLED",
            "ENABLED",
            "PENDING",
            "EXPIRED",
            "UNKNOWN",
            "ACTIVE",
            "IDLE",
            "YES",
            "NO"
    };
    private static final String[] LEADING_PREFIX_KEYS = {
            "Click to copy Account ID (",
            "Pay using "
    };

    private UbsTranslations() {
    }

    public static MutableComponent literal(String text) {
        return resolve(text == null ? "" : text, 0);
    }

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(key == null ? "" : key, args);
    }

    private static MutableComponent resolve(String text, int depth) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        if (depth > MAX_RESOLVE_DEPTH || isRuntimeValue(text)) {
            return Component.literal(text);
        }

        MutableComponent value = translateKnownValue(text);
        if (value != null) {
            return value;
        }

        MutableComponent byNewline = splitByDelimiter(text, "\n", depth);
        if (byNewline != null) {
            return byNewline;
        }

        MutableComponent byLabel = splitLabelValue(text, depth);
        if (byLabel != null) {
            return byLabel;
        }

        MutableComponent byColumns = splitByDelimiter(text, "  ", depth);
        if (byColumns != null) {
            return byColumns;
        }

        MutableComponent byPipe = splitByDelimiter(text, " | ", depth);
        if (byPipe != null) {
            return byPipe;
        }

        MutableComponent byDash = splitByDelimiter(text, " - ", depth);
        if (byDash != null) {
            return byDash;
        }

        MutableComponent byAt = splitByDelimiter(text, " @ ", depth);
        if (byAt != null) {
            return byAt;
        }

        MutableComponent bySlash = splitByDelimiter(text, " / ", depth);
        if (bySlash != null) {
            return bySlash;
        }

        MutableComponent byEmbeddedValue = splitEmbeddedValue(text, depth);
        if (byEmbeddedValue != null) {
            return byEmbeddedValue;
        }

        MutableComponent byLeadingPrefix = splitLeadingPrefix(text, depth);
        if (byLeadingPrefix != null) {
            return byLeadingPrefix;
        }

        MutableComponent byStyled = splitStyledMessage(text, depth);
        if (byStyled != null) {
            return byStyled;
        }

        return Component.translatable(text);
    }

    private static MutableComponent splitByDelimiter(String text, String delimiter, int depth) {
        int first = text.indexOf(delimiter);
        if (first < 0) {
            return null;
        }
        MutableComponent result = Component.empty();
        int start = 0;
        while (first >= 0) {
            if (first > start) {
                result.append(resolve(text.substring(start, first), depth + 1));
            }
            result.append(Component.translatable(delimiter));
            start = first + delimiter.length();
            first = text.indexOf(delimiter, start);
        }
        if (start < text.length()) {
            result.append(resolve(text.substring(start), depth + 1));
        }
        return result;
    }

    private static MutableComponent splitLabelValue(String text, int depth) {
        int colon = text.indexOf(": ");
        if (colon <= 0 || colon >= text.length() - 2) {
            return null;
        }

        int prefixEnd = colon + 2;
        while (prefixEnd + 1 < text.length() && text.charAt(prefixEnd) == '§') {
            prefixEnd += 2;
        }
        if (prefixEnd < text.length()) {
            char next = text.charAt(prefixEnd);
            if (next == '$' || next == '€' || next == '£') {
                prefixEnd++;
            }
        }
        if (prefixEnd >= text.length()) {
            return null;
        }

        String prefix = text.substring(0, prefixEnd);
        String value = text.substring(prefixEnd);
        return Component.translatable(prefix).append(resolve(value, depth + 1));
    }

    private static MutableComponent splitLeadingPrefix(String text, int depth) {
        for (String prefix : LEADING_PREFIX_KEYS) {
            if (!text.startsWith(prefix) || text.length() <= prefix.length()) {
                continue;
            }

            String rest = text.substring(prefix.length());
            MutableComponent result = Component.translatable(prefix);
            if (rest.endsWith(")") && rest.length() > 1 && isRuntimeValue(rest.substring(0, rest.length() - 1))) {
                result.append(Component.literal(rest.substring(0, rest.length() - 1)));
                result.append(Component.translatable(")"));
            } else {
                result.append(resolve(rest, depth + 1));
            }
            return result;
        }
        return null;
    }

    private static MutableComponent splitEmbeddedValue(String text, int depth) {
        for (String valueText : EMBEDDED_VALUE_TEXT) {
            int index = text.indexOf(valueText);
            if (index < 0 || !isEmbeddedValueBoundary(text, index, valueText.length())) {
                continue;
            }

            MutableComponent value = translateKnownValue(valueText);
            if (value == null) {
                continue;
            }

            MutableComponent result = Component.empty();
            if (index > 0) {
                result.append(resolve(text.substring(0, index), depth + 1));
            }
            result.append(value);
            int end = index + valueText.length();
            if (end < text.length()) {
                result.append(resolve(text.substring(end), depth + 1));
            }
            return result;
        }
        return null;
    }

    private static boolean isEmbeddedValueBoundary(String text, int index, int length) {
        int before = index - 1;
        int after = index + length;
        boolean left = before < 0 || !isValueWordChar(text.charAt(before));
        boolean right = after >= text.length() || !isValueWordChar(text.charAt(after));
        return left && right;
    }

    private static boolean isValueWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static MutableComponent splitStyledMessage(String text, int depth) {
        List<StyledSegment> segments = styledSegments(text);
        if (segments.size() < 3 || !hasSentenceSegment(segments)) {
            return null;
        }

        MutableComponent result = Component.empty();
        for (int i = 0; i < segments.size(); i++) {
            StyledSegment segment = segments.get(i);
            if (segment.text().isEmpty()) {
                continue;
            }
            if (isDynamicStyledSegment(segments, i)) {
                result.append(Component.literal(segment.text()));
            } else {
                MutableComponent value = translateKnownValue(segment.text());
                if (value == null) {
                    value = translateStyledStaticSegment(segment.text());
                }
                result.append(value == null ? Component.translatable(segment.text()) : value);
            }
        }
        return result;
    }

    private static List<StyledSegment> styledSegments(String text) {
        List<StyledSegment> segments = new ArrayList<>();
        int start = 0;
        int index = nextStyleCode(text, 1);
        while (index >= 0) {
            if (index > start) {
                segments.add(new StyledSegment(text.substring(start, index)));
            }
            start = index;
            index = nextStyleCode(text, index + 2);
        }
        if (start < text.length()) {
            segments.add(new StyledSegment(text.substring(start)));
        }
        return segments;
    }

    private static int nextStyleCode(String text, int from) {
        for (int i = Math.max(0, from); i + 1 < text.length(); i++) {
            if (text.charAt(i) == '§' && ChatFormatting.getByCode(text.charAt(i + 1)) != null) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasSentenceSegment(List<StyledSegment> segments) {
        for (StyledSegment segment : segments) {
            String core = stripFormatCodes(segment.text()).trim();
            if (core.contains(" ") || core.endsWith(":") || core.endsWith(".") || core.endsWith("!")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDynamicStyledSegment(List<StyledSegment> segments, int index) {
        StyledSegment segment = segments.get(index);
        String core = stripFormatCodes(segment.text()).trim();
        if (core.isEmpty()) {
            return false;
        }
        if (translateKnownValue(segment.text()) != null) {
            return false;
        }
        if (core.startsWith("[") || core.endsWith("]")) {
            return false;
        }
        if (isRuntimeValue(core)) {
            return true;
        }
        if (index > 0 && index < segments.size() - 1) {
            String previous = stripFormatCodes(segments.get(index - 1).text());
            String next = stripFormatCodes(segments.get(index + 1).text());
            return looksLikeSentenceFragment(previous) || looksLikeSentenceFragment(next);
        }
        return false;
    }

    private static boolean looksLikeSentenceFragment(String text) {
        String trimmed = text.trim();
        return trimmed.contains(" ") || trimmed.endsWith(":") || trimmed.endsWith(".") || trimmed.endsWith("!");
    }

    private static MutableComponent translateStyledStaticSegment(String text) {
        String leading = leadingFormatCodes(text);
        if (leading.isEmpty() || leading.length() >= text.length()) {
            return null;
        }

        String content = text.substring(leading.length());
        int start = 0;
        int end = content.length();
        while (start < end && Character.isWhitespace(content.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(content.charAt(end - 1))) {
            end--;
        }
        if (start >= end) {
            return null;
        }

        MutableComponent result = Component.empty();
        if (start > 0) {
            result.append(Component.literal(content.substring(0, start)));
        }

        MutableComponent translated = Component.translatable(content.substring(start, end));
        ChatFormatting[] formats = formatsFromCodes(leading);
        if (formats.length > 0) {
            translated.withStyle(formats);
        }
        result.append(translated);

        if (end < content.length()) {
            result.append(Component.literal(content.substring(end)));
        }
        return result;
    }

    private static MutableComponent translateKnownValue(String text) {
        String leading = leadingFormatCodes(text);
        String trailing = trailingPunctuation(text.substring(leading.length()));
        String core = text.substring(leading.length(), text.length() - trailing.length()).trim();
        if (core.isEmpty()) {
            return null;
        }

        String key = switch (core.toUpperCase(Locale.ROOT)) {
            case "TRUE", "YES" -> "ubs.value.yes";
            case "FALSE", "NO" -> "ubs.value.no";
            case "ACTIVE" -> "ubs.value.active";
            case "INACTIVE" -> "ubs.value.inactive";
            case "IDLE" -> "ubs.value.disconnected";
            case "DISABLED" -> "ubs.value.disabled";
            case "ENABLED" -> "ubs.value.enabled";
            case "PENDING" -> "ubs.value.pending";
            case "APPROVED" -> "ubs.value.approved";
            case "DENIED" -> "ubs.value.denied";
            case "REJECTED" -> "ubs.value.rejected";
            case "ACCEPTED" -> "ubs.value.accepted";
            case "DECLINED" -> "ubs.value.declined";
            case "EXPIRED" -> "ubs.value.expired";
            case "CANCELLED", "CANCELED" -> "ubs.value.cancelled";
            case "UNKNOWN" -> "ubs.value.unknown";
            case "UNAVAILABLE" -> "ubs.value.unavailable";
            case "NONE" -> "ubs.value.none";
            case "SUSPENDED" -> "ubs.value.suspended";
            case "RESTRICTED" -> "ubs.value.restricted";
            case "WARNING" -> "ubs.value.warning";
            case "CHECKING ACCOUNT" -> "ubs.value.account_type.checking";
            case "SAVING ACCOUNT", "SAVINGS ACCOUNT" -> "ubs.value.account_type.savings";
            case "MONEY MARKET ACCOUNT" -> "ubs.value.account_type.money_market";
            case "CERTIFICATE OF DEPOSIT ACCOUNT" -> "ubs.value.account_type.certificate";
            case "ACCOUNT" -> "ubs.value.account";
            default -> null;
        };

        if (key == null) {
            return null;
        }

        MutableComponent result = Component.translatable(key);
        ChatFormatting[] formats = formatsFromCodes(leading);
        if (formats.length > 0) {
            result.withStyle(formats);
        }
        if (!trailing.isEmpty()) {
            result.append(Component.translatable(trailing));
        }
        return result;
    }

    private static boolean isRuntimeValue(String text) {
        String core = stripFormatCodes(text).trim();
        if (core.isEmpty()) {
            return false;
        }
        if (UUID_PATTERN.matcher(core).matches() || NUMBER_PATTERN.matcher(core).matches()) {
            return true;
        }
        return core.indexOf('\\') >= 0 || core.indexOf('/') >= 0 && !core.startsWith("/");
    }

    private static String stripFormatCodes(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '§' && i + 1 < text.length()
                    && ChatFormatting.getByCode(text.charAt(i + 1)) != null) {
                i++;
                continue;
            }
            builder.append(text.charAt(i));
        }
        return builder.toString();
    }

    private static String leadingFormatCodes(String text) {
        int end = 0;
        while (end + 1 < text.length() && text.charAt(end) == '§'
                && ChatFormatting.getByCode(text.charAt(end + 1)) != null) {
            end += 2;
        }
        return text.substring(0, end);
    }

    private static String trailingPunctuation(String text) {
        int start = text.length();
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (c == '.' || c == '!' || c == '?' || c == ',' || c == ';') {
                start--;
            } else {
                break;
            }
        }
        return text.substring(start);
    }

    private static ChatFormatting[] formatsFromCodes(String codes) {
        List<ChatFormatting> formats = new ArrayList<>();
        for (int i = 0; i + 1 < codes.length(); i += 2) {
            ChatFormatting format = ChatFormatting.getByCode(codes.charAt(i + 1));
            if (format != null) {
                formats.add(format);
            }
        }
        return formats.toArray(ChatFormatting[]::new);
    }

    private record StyledSegment(String text) {
    }
}
