package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("1.2.0")
public enum ApiAlertTone {
    SUCCESS(0),
    ERROR(1),
    INFO(2),
    WARNING(3);

    private final int id;

    ApiAlertTone(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static ApiAlertTone fromId(int id) {
        return switch (id) {
            case 1 -> ERROR;
            case 2 -> INFO;
            case 3 -> WARNING;
            default -> SUCCESS;
        };
    }
}
