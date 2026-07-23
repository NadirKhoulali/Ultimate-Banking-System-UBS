package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

@ApiStatus.AvailableSince("1.2.0")
public record ApiAlertResult(
        boolean success,
        String reason,
        UUID playerId,
        String title,
        String message,
        boolean alertSuccess,
        int durationMs,
        ApiAlertTone tone,
        int toneCode
) {
    public static ApiAlertResult ok(UUID playerId,
                                    String title,
                                    String message,
                                    boolean alertSuccess,
                                    int durationMs,
                                    ApiAlertTone tone) {
        ApiAlertTone resolvedTone = tone == null ? ApiAlertTone.INFO : tone;
        return new ApiAlertResult(
                true,
                "",
                playerId,
                title == null ? "" : title,
                message == null ? "" : message,
                alertSuccess,
                durationMs,
                resolvedTone,
                resolvedTone.id()
        );
    }

    public static ApiAlertResult fail(String reason, UUID playerId) {
        return new ApiAlertResult(
                false,
                reason == null || reason.isBlank() ? "Alert failed" : reason,
                playerId,
                "",
                "",
                false,
                0,
                ApiAlertTone.ERROR,
                ApiAlertTone.ERROR.id()
        );
    }
}
