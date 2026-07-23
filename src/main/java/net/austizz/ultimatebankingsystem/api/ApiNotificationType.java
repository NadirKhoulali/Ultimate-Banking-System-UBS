package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * Visual intent for the UBS notification renderer. Types affect iconography,
 * accent color, default title, and presentation without changing priority.
 */
@ApiStatus.AvailableSince("1.3.0")
public enum ApiNotificationType {
    SUCCESS(0),
    ERROR(1),
    WARNING(2),
    INFO(3),
    TRANSACTION(4),
    SECURITY(5),
    MESSAGE(6),
    PROGRESS(7),
    SYSTEM(8);

    private final int id;

    ApiNotificationType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static ApiNotificationType fromId(int id) {
        return switch (id) {
            case 0 -> SUCCESS;
            case 1 -> ERROR;
            case 2 -> WARNING;
            case 4 -> TRANSACTION;
            case 5 -> SECURITY;
            case 6 -> MESSAGE;
            case 7 -> PROGRESS;
            case 8 -> SYSTEM;
            default -> INFO;
        };
    }
}
