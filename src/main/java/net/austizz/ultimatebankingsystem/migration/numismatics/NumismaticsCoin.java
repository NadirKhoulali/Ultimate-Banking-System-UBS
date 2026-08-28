package net.austizz.ultimatebankingsystem.migration.numismatics;

import java.util.Locale;
import java.util.Map;

/** Create: Numismatics denominations expressed in its base Spur unit. */
public enum NumismaticsCoin {
    SPUR("numismatics:spur", 1),
    BEVEL("numismatics:bevel", 8),
    SPROCKET("numismatics:sprocket", 16),
    COG("numismatics:cog", 64),
    CROWN("numismatics:crown", 512),
    SUN("numismatics:sun", 4096);

    private static final Map<String, NumismaticsCoin> BY_ITEM_ID = Map.ofEntries(
            Map.entry(SPUR.itemId, SPUR),
            Map.entry(BEVEL.itemId, BEVEL),
            Map.entry(SPROCKET.itemId, SPROCKET),
            Map.entry(COG.itemId, COG),
            Map.entry(CROWN.itemId, CROWN),
            Map.entry(SUN.itemId, SUN)
    );

    private final String itemId;
    private final int spurs;

    NumismaticsCoin(String itemId, int spurs) {
        this.itemId = itemId;
        this.spurs = spurs;
    }

    public String itemId() {
        return itemId;
    }

    public int spurs() {
        return spurs;
    }

    public long valueCents(int centsPerSpur, int count) {
        if (centsPerSpur <= 0 || count <= 0) {
            return 0L;
        }
        return Math.multiplyExact(Math.multiplyExact((long) spurs, centsPerSpur), count);
    }

    public static NumismaticsCoin fromItemId(String itemId) {
        if (itemId == null) {
            return null;
        }
        return BY_ITEM_ID.get(itemId.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isCoin(String itemId) {
        return fromItemId(itemId) != null;
    }
}
