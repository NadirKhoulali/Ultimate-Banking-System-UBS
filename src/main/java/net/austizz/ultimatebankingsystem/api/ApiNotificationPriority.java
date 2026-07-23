package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

/** Controls ordering and visual emphasis independently from notification type. */
@ApiStatus.AvailableSince("1.3.0")
public enum ApiNotificationPriority {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    CRITICAL(3);

    private final int id;

    ApiNotificationPriority(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static ApiNotificationPriority fromId(int id) {
        return switch (id) {
            case 0 -> LOW;
            case 2 -> HIGH;
            case 3 -> CRITICAL;
            default -> NORMAL;
        };
    }
}
