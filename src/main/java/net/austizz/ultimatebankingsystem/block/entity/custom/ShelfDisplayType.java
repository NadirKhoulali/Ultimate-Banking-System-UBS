package net.austizz.ultimatebankingsystem.block.entity.custom;

import java.util.Locale;

public enum ShelfDisplayType {
    TALL_WALL("tall_wall"),
    GLASS_COUNTER("glass_counter"),
    MODULAR_WALL("modular_wall"),
    SELLING_TABLE("selling_table"),
    INVISIBLE_DISPLAY("invisible_display"),
    UNKNOWN("unknown");

    private final String id;

    ShelfDisplayType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ShelfDisplayType fromId(String id) {
        if (id == null || id.isBlank()) {
            return UNKNOWN;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (ShelfDisplayType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
