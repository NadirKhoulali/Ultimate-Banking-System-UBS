package net.austizz.ultimatebankingsystem.client;

import net.minecraft.client.resources.language.I18n;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UbsClientTranslations {
    private static final int MAX_CACHE_SIZE = 4096;
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final String[] LEADING_PREFIX_KEYS = {
            "Copied offer id ",
            "Copied shop ID ",
            "Copied employee ID ",
            "Opened canvas ",
            "Opened note ",
            "Submitting accept for offer ",
            "Selected order ",
            "Selected catalog item ",
            "Selected cart item ",
            "Selected settlement account ",
            "Selected player ",
            "Selected order item ",
            "Selected shelf #",
            "Locating ",
            "Restocking ",
            "Removing ",
            "Firing employee ",
            "Updated stock targets for ",
            "Amount prefilled from pocket cash: $",
            "Amount ",
            "Unit ",
            "Line ",
            "Item ID ",
            "Bal ",
            "Shop ID ",
            "Order ",
            "Total ",
            "Boxes ",
            "Mode ",
            "Created ",
            "ETA ",
            "Attempts ",
            "Last issue ",
            "Reward ",
            "Status ",
            "Courier ",
            "Timeout ",
            "Time left ",
            "Drop ",
            "Filter: ",
            "Count ",
            "Slot ",
            "In stock ",
            "Max stack ",
            "Delivery ",
            "ID ",
            "Pos ",
            "Employee ID: ",
            "Cashier Entity: ",
            "Terminal: ",
            "Index: #",
            "UUID: ",
            "Loc: ",
            "Granted ",
            "Value: ",
            "Catalog item: ",
            "Category: ",
            "Unit price: ",
            "Quantity: ",
            "Line total: ",
            "Bank: ",
            "Type: ",
            "Current mode: ",
            "Assigned delivery pallets: ",
            "Cart ",
            "Account ",
            "Shop ",
            "Pallet ",
            "[Primary] ",
            "Showing ",
            "Loading ",
            "Refresh ",
            "Brand color updated to ",
            "Premise ",
            "Ready | ",
            "Setup required | ",
            "Next: ",
            "Vault ",
            "Request ",
            "Assign a free ",
            "No free ",
            "Ordered steps: ",
            "Target "
    };
    private static volatile String languageCode = "";

    private UbsClientTranslations() {
    }

    public static String resolve(String keyOrText) {
        if (keyOrText == null || keyOrText.isEmpty()) {
            return "";
        }
        syncLanguageCache();

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

    public static String format(String key, Object... args) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        syncLanguageCache();
        if (I18n.exists(key)) {
            return I18n.get(key, args);
        }

        StringBuilder fallback = new StringBuilder(key);
        if (args != null) {
            for (Object arg : args) {
                fallback.append(' ').append(arg);
            }
        }
        return fallback.toString();
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

        String byBullet = resolveDelimited(text, " • ", depth + 1);
        if (!byBullet.equals(text)) {
            return byBullet;
        }

        String byAt = resolveDelimited(text, " @ ", depth + 1);
        if (!byAt.equals(text)) {
            return byAt;
        }

        String bySlash = resolveDelimited(text, " / ", depth + 1);
        if (!bySlash.equals(text)) {
            return bySlash;
        }

        String bySpacedColumns = resolveDelimited(text, "   ", depth + 1);
        if (!bySpacedColumns.equals(text)) {
            return bySpacedColumns;
        }

        String byOf = resolveDelimited(text, " of ", depth + 1);
        if (!byOf.equals(text)) {
            return byOf;
        }

        int idx = text.indexOf(": ");
        if (idx > 0) {
            String prefix = text.substring(0, idx + 2);
            String localizedPrefix = localizePrefix(prefix);
            if (!localizedPrefix.equals(prefix)) {
                return localizedPrefix + resolveInternal(text.substring(idx + 2), depth + 1);
            }
        }

        int parenIdx = text.indexOf(" (");
        if (parenIdx > 0) {
            String prefix = text.substring(0, parenIdx);
            if (I18n.exists(prefix)) {
                return I18n.get(prefix) + text.substring(parenIdx);
            }
        }

        String byLeadingPrefix = resolveLeadingPrefix(text, depth + 1);
        if (!byLeadingPrefix.equals(text)) {
            return byLeadingPrefix;
        }

        return text;
    }

    private static void syncLanguageCache() {
        String selectedLanguage = I18n.get("language.code");
        if (!selectedLanguage.equals(languageCode)) {
            languageCode = selectedLanguage;
            CACHE.clear();
        }
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
        String localizedDelimiter = I18n.exists(delimiter) ? I18n.get(delimiter) : delimiter;
        return String.join(localizedDelimiter, parts);
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

    private static String resolveLeadingPrefix(String text, int depth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        for (String prefix : LEADING_PREFIX_KEYS) {
            if (text.startsWith(prefix) && text.length() > prefix.length() && I18n.exists(prefix)) {
                return I18n.get(prefix) + resolveInternal(text.substring(prefix.length()), depth);
            }
        }
        return text;
    }
}
