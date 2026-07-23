package net.austizz.ultimatebankingsystem.hud;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Server/client-safe set of supported balance HUD anchors. */
public enum HudPosition {
    TOP_RIGHT("top-right", true, false, false),
    TOP_LEFT("top-left", false, false, false),
    MIDDLE_RIGHT("middle-right", true, true, false),
    MIDDLE_LEFT("middle-left", false, true, false),
    BOTTOM_RIGHT("bottom-right", true, false, true),
    BOTTOM_LEFT("bottom-left", false, false, true);

    private static final List<String> COMMAND_VALUES = Arrays.stream(values())
            .map(HudPosition::commandValue)
            .toList();

    private final String commandValue;
    private final boolean right;
    private final boolean middle;
    private final boolean bottom;

    HudPosition(String commandValue, boolean right, boolean middle, boolean bottom) {
        this.commandValue = commandValue;
        this.right = right;
        this.middle = middle;
        this.bottom = bottom;
    }

    public String commandValue() {
        return commandValue;
    }

    public boolean isRight() {
        return right;
    }

    public boolean isMiddle() {
        return middle;
    }

    public boolean isBottom() {
        return bottom;
    }

    public static List<String> commandValues() {
        return COMMAND_VALUES;
    }

    public static HudPosition parse(String value) {
        String normalized = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (HudPosition position : values()) {
            if (position.commandValue.equals(normalized)) {
                return position;
            }
        }
        return null;
    }

    public static HudPosition parseOrDefault(String value, HudPosition fallback) {
        HudPosition parsed = parse(value);
        return parsed == null ? fallback : parsed;
    }

    public static String normalize(String value) {
        HudPosition parsed = parse(value);
        return parsed == null ? "" : parsed.commandValue;
    }
}
