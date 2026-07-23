package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * Preferred notification location. {@link #AUTO} adapts to priority and whether
 * a full-screen menu is open.
 */
@ApiStatus.AvailableSince("1.3.0")
public enum ApiNotificationPlacement {
    AUTO(0),
    TOP_RIGHT(1),
    TOP_CENTER(2),
    BOTTOM_RIGHT(3);

    private final int id;

    ApiNotificationPlacement(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static ApiNotificationPlacement fromId(int id) {
        return switch (id) {
            case 1 -> TOP_RIGHT;
            case 2 -> TOP_CENTER;
            case 3 -> BOTTOM_RIGHT;
            default -> AUTO;
        };
    }
}
