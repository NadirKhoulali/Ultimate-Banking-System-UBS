package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.api.ApiNotificationPlacement;
import net.austizz.ultimatebankingsystem.api.ApiNotificationPriority;
import net.austizz.ultimatebankingsystem.api.ApiNotificationRequest;
import net.austizz.ultimatebankingsystem.api.ApiNotificationType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Versioned transport for the stackable UBS notification API. */
public record UiNotificationPayload(
        Operation operation,
        String id,
        String channel,
        String source,
        String title,
        String message,
        String detail,
        ApiNotificationType notificationType,
        ApiNotificationPriority priority,
        ApiNotificationPlacement placement,
        int durationMs,
        float progress,
        boolean sticky,
        boolean playSound,
        boolean replaceExisting
) implements CustomPacketPayload {
    public static final int MAX_ID_CHARS = 72;
    public static final int MAX_CHANNEL_CHARS = 48;
    public static final int MAX_SOURCE_CHARS = 64;
    public static final int MAX_TITLE_CHARS = 96;
    public static final int MAX_MESSAGE_CHARS = 512;
    public static final int MAX_DETAIL_CHARS = 256;

    public enum Operation {
        SHOW(0),
        DISMISS(1),
        CLEAR_CHANNEL(2),
        CLEAR_ALL(3);

        private final int id;

        Operation(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static Operation fromId(int id) {
            return switch (id) {
                case 1 -> DISMISS;
                case 2 -> CLEAR_CHANNEL;
                case 3 -> CLEAR_ALL;
                default -> SHOW;
            };
        }
    }

    public static final Type<UiNotificationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "ui_notification_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UiNotificationPayload> STREAM_CODEC = StreamCodec.of(
            UiNotificationPayload::encode,
            UiNotificationPayload::decode
    );

    public UiNotificationPayload {
        operation = operation == null ? Operation.SHOW : operation;
        id = limit(id, MAX_ID_CHARS);
        channel = limit(channel, MAX_CHANNEL_CHARS);
        if (channel.isBlank()) {
            channel = "general";
        }
        source = limit(source, MAX_SOURCE_CHARS);
        title = limit(title, MAX_TITLE_CHARS);
        message = limit(message, MAX_MESSAGE_CHARS);
        detail = limit(detail, MAX_DETAIL_CHARS);
        notificationType = notificationType == null ? ApiNotificationType.INFO : notificationType;
        priority = priority == null ? ApiNotificationPriority.NORMAL : priority;
        placement = placement == null ? ApiNotificationPlacement.AUTO : placement;
        durationMs = Math.max(1500, Math.min(30_000, durationMs));
        if (!Float.isFinite(progress)) {
            progress = ApiNotificationRequest.NO_PROGRESS;
        } else if (progress != ApiNotificationRequest.NO_PROGRESS
                && progress != ApiNotificationRequest.INDETERMINATE_PROGRESS) {
            progress = Math.max(0.0F, Math.min(1.0F, progress));
        }
    }

    public static UiNotificationPayload show(String id, ApiNotificationRequest request) {
        ApiNotificationRequest resolved = request == null
                ? ApiNotificationRequest.info("").build()
                : request;
        return new UiNotificationPayload(
                Operation.SHOW,
                id,
                resolved.channel(),
                resolved.source(),
                resolved.title(),
                resolved.message(),
                resolved.detail(),
                resolved.type(),
                resolved.priority(),
                resolved.placement(),
                resolved.durationMs(),
                resolved.progress(),
                resolved.sticky(),
                resolved.playSound(),
                resolved.replaceExisting()
        );
    }

    public static UiNotificationPayload dismiss(String id) {
        return control(Operation.DISMISS, id, "general");
    }

    public static UiNotificationPayload clearChannel(String channel) {
        return control(Operation.CLEAR_CHANNEL, "", channel);
    }

    public static UiNotificationPayload clearAll() {
        return control(Operation.CLEAR_ALL, "", "general");
    }

    private static UiNotificationPayload control(Operation operation, String id, String channel) {
        return new UiNotificationPayload(
                operation, id, channel, "", "", "", "",
                ApiNotificationType.INFO, ApiNotificationPriority.NORMAL,
                ApiNotificationPlacement.AUTO, 1500,
                ApiNotificationRequest.NO_PROGRESS, false, false, true
        );
    }

    private static void encode(RegistryFriendlyByteBuf buf, UiNotificationPayload value) {
        buf.writeVarInt(value.operation().id());
        buf.writeUtf(value.id(), MAX_ID_CHARS);
        buf.writeUtf(value.channel(), MAX_CHANNEL_CHARS);
        buf.writeUtf(value.source(), MAX_SOURCE_CHARS);
        buf.writeUtf(value.title(), MAX_TITLE_CHARS);
        buf.writeUtf(value.message(), MAX_MESSAGE_CHARS);
        buf.writeUtf(value.detail(), MAX_DETAIL_CHARS);
        buf.writeVarInt(value.notificationType().id());
        buf.writeVarInt(value.priority().id());
        buf.writeVarInt(value.placement().id());
        buf.writeVarInt(value.durationMs());
        buf.writeFloat(value.progress());
        buf.writeBoolean(value.sticky());
        buf.writeBoolean(value.playSound());
        buf.writeBoolean(value.replaceExisting());
    }

    private static UiNotificationPayload decode(RegistryFriendlyByteBuf buf) {
        return new UiNotificationPayload(
                Operation.fromId(buf.readVarInt()),
                buf.readUtf(MAX_ID_CHARS),
                buf.readUtf(MAX_CHANNEL_CHARS),
                buf.readUtf(MAX_SOURCE_CHARS),
                buf.readUtf(MAX_TITLE_CHARS),
                buf.readUtf(MAX_MESSAGE_CHARS),
                buf.readUtf(MAX_DETAIL_CHARS),
                ApiNotificationType.fromId(buf.readVarInt()),
                ApiNotificationPriority.fromId(buf.readVarInt()),
                ApiNotificationPlacement.fromId(buf.readVarInt()),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    private static String limit(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    @Override
    public Type<UiNotificationPayload> type() {
        return TYPE;
    }
}
