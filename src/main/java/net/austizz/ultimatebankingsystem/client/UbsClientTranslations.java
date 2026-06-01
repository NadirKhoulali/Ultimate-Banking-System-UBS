package net.austizz.ultimatebankingsystem.client;

import net.minecraft.client.resources.language.I18n;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UbsClientTranslations {
    private static final int MAX_CACHE_SIZE = 4096;
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private UbsClientTranslations() {
    }

    public static String resolve(String keyOrText) {
        if (keyOrText == null || keyOrText.isEmpty()) {
            return "";
        }

        String cached = CACHE.get(keyOrText);
        if (cached != null) {
            return cached;
        }

        String resolved = resolveInternal(keyOrText, 0);
        if (CACHE.size() > MAX_CACHE_SIZE) {
            CACHE.clear();
        }
        CACHE.putIfAbsent(keyOrText, resolved);
        return resolved;
    }

    private static String resolveInternal(String text, int depth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (depth > 3) {
            return text;
        }

        if (I18n.exists(text)) {
            return I18n.get(text);
        }

        String byNewline = resolveDelimited(text, "\n", depth + 1);
        if (!byNewline.equals(text)) {
            return byNewline;
        }

        String byPipe = resolveDelimited(text, " | ", depth + 1);
        if (!byPipe.equals(text)) {
            return byPipe;
        }

        String byDash = resolveDelimited(text, " - ", depth + 1);
        if (!byDash.equals(text)) {
            return byDash;
        }

        int idx = text.indexOf(": ");
        if (idx > 0) {
            String prefix = text.substring(0, idx + 2);
            String localizedPrefix = localizePrefix(prefix);
            if (!localizedPrefix.equals(prefix)) {
                return localizedPrefix + text.substring(idx + 2);
            }
        }

        return text;
    }

    private static String resolveDelimited(String text, String delimiter, int depth) {
        if (!text.contains(delimiter)) {
            return text;
        }
        String[] parts = text.split(java.util.regex.Pattern.quote(delimiter), -1);
        if (parts.length <= 1) {
            return text;
        }
        boolean changed = false;
        for (int i = 0; i < parts.length; i++) {
            String resolved = resolveInternal(parts[i], depth);
            if (!resolved.equals(parts[i])) {
                changed = true;
                parts[i] = resolved;
            }
        }
        if (!changed) {
            return text;
        }
        return String.join(delimiter, parts);
    }

    private static String localizePrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }
        if (I18n.exists(prefix)) {
            return I18n.get(prefix);
        }
        if (prefix.endsWith(": ")) {
            String noTrailingSpace = prefix.substring(0, prefix.length() - 1);
            if (I18n.exists(noTrailingSpace)) {
                return I18n.get(noTrailingSpace) + " ";
            }
            String noColon = prefix.substring(0, prefix.length() - 2);
            if (I18n.exists(noColon)) {
                return I18n.get(noColon) + ": ";
            }
        }
        return prefix;
    }
}
