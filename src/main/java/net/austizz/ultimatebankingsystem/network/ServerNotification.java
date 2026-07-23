package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.api.ApiNotificationPriority;
import net.austizz.ultimatebankingsystem.api.ApiNotificationRequest;
import net.austizz.ultimatebankingsystem.api.ApiNotificationType;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;
import java.util.UUID;

/** Server-side entry point for the versioned, stackable notification renderer. */
public final class ServerNotification {
    private ServerNotification() {
    }

    public static String send(ServerPlayer player, ApiNotificationRequest request) {
        if (player == null || request == null || request.message().isBlank()) {
            return "";
        }
        String id = request.id().isBlank()
                ? UUID.randomUUID().toString()
                : request.id();
        PacketDistributor.sendToPlayer(player, UiNotificationPayload.show(id, request));
        return id;
    }

    public static String send(ServerPlayer player,
                              String title,
                              String message,
                              DeliveryAlertPayload.AlertTone tone) {
        return send(player, title, message, tone, 4600);
    }

    public static String send(ServerPlayer player,
                              String title,
                              String message,
                              DeliveryAlertPayload.AlertTone tone,
                              int durationMs) {
        if (player == null || message == null || message.trim().isBlank()) {
            return "";
        }
        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedMessage = stripLegacyFormatting(message);
        DeliveryAlertPayload.AlertTone normalizedTone = tone == null
                ? DeliveryAlertPayload.AlertTone.INFO
                : tone;
        ApiNotificationType type = inferType(normalizedTitle, normalizedMessage, normalizedTone);
        ApiNotificationPriority priority = inferPriority(normalizedTitle, normalizedMessage, normalizedTone);
        String channel = inferChannel(type);

        return send(player, ApiNotificationRequest.builder(type, normalizedMessage)
                .channel(channel)
                .source(sourceLabel(type))
                .title(normalizedTitle)
                .priority(priority)
                .durationMs(durationMs)
                .build());
    }

    public static String sendLegacy(ServerPlayer player,
                                    String title,
                                    String legacyMessage,
                                    DeliveryAlertPayload.AlertTone tone,
                                    int durationMs) {
        return send(player, title, stripLegacyFormatting(legacyMessage), tone, durationMs);
    }

    public static String sendLegacyAuto(ServerPlayer player,
                                        String title,
                                        String legacyMessage,
                                        int durationMs) {
        return sendLegacy(player, title, legacyMessage, inferToneFromLegacy(legacyMessage), durationMs);
    }

    public static void dismiss(ServerPlayer player, String notificationId) {
        if (player == null || notificationId == null || notificationId.isBlank()) {
            return;
        }
        PacketDistributor.sendToPlayer(player, UiNotificationPayload.dismiss(notificationId));
    }

    public static void clearChannel(ServerPlayer player, String channel) {
        if (player == null || channel == null || channel.isBlank()) {
            return;
        }
        PacketDistributor.sendToPlayer(player, UiNotificationPayload.clearChannel(channel));
    }

    public static void clearAll(ServerPlayer player) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, UiNotificationPayload.clearAll());
        }
    }

    public static DeliveryAlertPayload.AlertTone inferToneFromLegacy(String legacyMessage) {
        return ServerActionAlert.inferToneFromLegacy(legacyMessage);
    }

    public static String stripLegacyFormatting(String raw) {
        return ServerActionAlert.stripLegacyFormatting(raw);
    }

    private static ApiNotificationType inferType(String title,
                                                 String message,
                                                 DeliveryAlertPayload.AlertTone tone) {
        if (tone == DeliveryAlertPayload.AlertTone.ERROR) {
            return ApiNotificationType.ERROR;
        }
        if (tone == DeliveryAlertPayload.AlertTone.WARNING) {
            return ApiNotificationType.WARNING;
        }
        String context = (title + " " + message).toLowerCase(Locale.ROOT);
        if (containsAny(context, "alarm", "security", "access denied", "pickpocket", "rfid", "vault")) {
            return ApiNotificationType.SECURITY;
        }
        if (containsAny(context, "payment", "transfer", "deposit", "withdraw", "balance", "account",
                "bank", "cashier", "checkout", "refund", "pay request", "loan")) {
            return ApiNotificationType.TRANSACTION;
        }
        if (containsAny(context, "message", "messenger", "phone", "contact")) {
            return ApiNotificationType.MESSAGE;
        }
        if (containsAny(context, "loading", "processing", "queued", "progress", "preparing")) {
            return ApiNotificationType.PROGRESS;
        }
        return tone == DeliveryAlertPayload.AlertTone.SUCCESS
                ? ApiNotificationType.SUCCESS
                : ApiNotificationType.INFO;
    }

    private static ApiNotificationPriority inferPriority(String title,
                                                         String message,
                                                         DeliveryAlertPayload.AlertTone tone) {
        String context = (title + " " + message).toLowerCase(Locale.ROOT);
        if (containsAny(context, "alarm active", "under attack", "critical", "breach")) {
            return ApiNotificationPriority.CRITICAL;
        }
        if (tone == DeliveryAlertPayload.AlertTone.ERROR
                || tone == DeliveryAlertPayload.AlertTone.WARNING
                || containsAny(context, "security", "access denied", "failed")) {
            return ApiNotificationPriority.HIGH;
        }
        if (containsAny(context, "copied", "selected", "refreshed", "locating")) {
            return ApiNotificationPriority.LOW;
        }
        return ApiNotificationPriority.NORMAL;
    }

    private static String inferChannel(ApiNotificationType type) {
        return switch (type) {
            case TRANSACTION -> "banking";
            case SECURITY -> "security";
            case MESSAGE -> "messaging";
            case PROGRESS -> "progress";
            case SYSTEM -> "system";
            default -> "actions";
        };
    }

    private static String sourceLabel(ApiNotificationType type) {
        return switch (type) {
            case TRANSACTION -> "UBS Banking";
            case SECURITY -> "UBS Security";
            case MESSAGE -> "UBS Phone";
            case PROGRESS -> "UBS Activity";
            default -> "Ultimate Banking System";
        };
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
