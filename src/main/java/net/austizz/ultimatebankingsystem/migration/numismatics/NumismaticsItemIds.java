package net.austizz.ultimatebankingsystem.migration.numismatics;

import java.util.Set;

public final class NumismaticsItemIds {
    private static final Set<String> DYES = Set.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    );

    private NumismaticsItemIds() {
    }

    public static boolean isBankCard(String itemId) {
        if (itemId == null || !itemId.startsWith("numismatics:") || !itemId.endsWith("_card")
                || itemId.endsWith("_id_card")) {
            return false;
        }
        String color = itemId.substring("numismatics:".length(), itemId.length() - "_card".length());
        return DYES.contains(color);
    }

    public static boolean isIdCard(String itemId) {
        if (itemId == null || !itemId.startsWith("numismatics:") || !itemId.endsWith("_id_card")) {
            return false;
        }
        String color = itemId.substring("numismatics:".length(), itemId.length() - "_id_card".length());
        return DYES.contains(color);
    }

    public static boolean isConvertible(String itemId, boolean includeCards) {
        return NumismaticsCoin.isCoin(itemId) || (includeCards && isBankCard(itemId));
    }
}
