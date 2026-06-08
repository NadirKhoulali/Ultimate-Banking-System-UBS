package net.austizz.ultimatebankingsystem.network;

import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side helper that emits the shared styled client alert card.
 */
public final class ServerActionAlert {
    private ServerActionAlert() {
    }

    public static void send(ServerPlayer player,
                            String title,
                            String message,
                            DeliveryAlertPayload.AlertTone tone) {
        send(player, title, message, tone, 4600);
    }

    public static void send(ServerPlayer player,
                            String title,
                            String message,
                            DeliveryAlertPayload.AlertTone tone,
                            int durationMs) {
        if (player == null || message == null) {
            return;
        }
        String normalizedMessage = message.trim();
        if (normalizedMessage.isBlank()) {
            return;
        }
        DeliveryAlertPayload.AlertTone normalizedTone = tone == null ? DeliveryAlertPayload.AlertTone.INFO : tone;
        PacketDistributor.sendToPlayer(player, new DeliveryAlertPayload(
                title == null ? "" : title.trim(),
                stripLegacyFormatting(normalizedMessage),
                normalizedTone != DeliveryAlertPayload.AlertTone.ERROR,
                durationMs,
                normalizedTone.id()
        ));
    }

    public static void sendLegacy(ServerPlayer player,
                                  String title,
                                  String legacyMessage,
                                  DeliveryAlertPayload.AlertTone tone,
                                  int durationMs) {
        send(player, title, stripLegacyFormatting(legacyMessage), tone, durationMs);
    }

    /**
     * Emits a legacy-formatted message using an inferred tone based on common error/warn markers.
     */
    public static void sendLegacyAuto(ServerPlayer player,
                                      String title,
                                      String legacyMessage,
                                      int durationMs) {
        sendLegacy(player, title, legacyMessage, inferToneFromLegacy(legacyMessage), durationMs);
    }

    /**
     * Maps legacy color/error wording to a stable alert tone.
     */
    public static DeliveryAlertPayload.AlertTone inferToneFromLegacy(String legacyMessage) {
        if (legacyMessage == null) {
            return DeliveryAlertPayload.AlertTone.INFO;
        }
        String raw = legacyMessage.trim();
        if (raw.isEmpty()) {
            return DeliveryAlertPayload.AlertTone.INFO;
        }
        String lowered = stripLegacyFormatting(raw).toLowerCase();
        if (raw.contains("§c")
                || lowered.contains("error")
                || lowered.contains("failed")
                || lowered.contains("invalid")
                || lowered.contains("blocked")
                || lowered.contains("insufficient")) {
            return DeliveryAlertPayload.AlertTone.ERROR;
        }
        if (raw.contains("§e")
                || lowered.contains("warning")
                || lowered.contains("timed out")
                || lowered.contains("cancelled")
                || lowered.contains("canceled")) {
            return DeliveryAlertPayload.AlertTone.WARNING;
        }
        if (raw.contains("§a") || lowered.contains("success") || lowered.contains("complete")) {
            return DeliveryAlertPayload.AlertTone.SUCCESS;
        }
        return DeliveryAlertPayload.AlertTone.INFO;
    }

    public static String stripLegacyFormatting(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }
}
