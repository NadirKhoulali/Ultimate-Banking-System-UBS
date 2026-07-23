package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

/** Result returned after sending or controlling a UBS notification. */
@ApiStatus.AvailableSince("1.3.0")
public record ApiNotificationResult(
        boolean success,
        String reason,
        UUID playerId,
        String notificationId,
        String channel
) {
    public static ApiNotificationResult ok(UUID playerId, String notificationId, String channel) {
        return new ApiNotificationResult(
                true,
                "",
                playerId,
                notificationId == null ? "" : notificationId,
                channel == null ? "" : channel
        );
    }

    public static ApiNotificationResult fail(String reason, UUID playerId) {
        return new ApiNotificationResult(
                false,
                reason == null || reason.isBlank() ? "Notification action failed" : reason,
                playerId,
                "",
                ""
        );
    }
}
