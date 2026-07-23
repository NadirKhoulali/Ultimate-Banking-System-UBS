package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * Immutable request for the UBS notification API.
 *
 * <p>An empty ID is replaced by a generated ID. Reusing an ID with
 * {@code replaceExisting(true)} updates a visible notification in place, which
 * is useful for progress reporting.</p>
 */
@ApiStatus.AvailableSince("1.3.0")
public record ApiNotificationRequest(
        String id,
        String channel,
        String source,
        String title,
        String message,
        String detail,
        ApiNotificationType type,
        ApiNotificationPriority priority,
        ApiNotificationPlacement placement,
        int durationMs,
        float progress,
        boolean sticky,
        boolean playSound,
        boolean replaceExisting
) {
    public static final float NO_PROGRESS = -1.0F;
    public static final float INDETERMINATE_PROGRESS = -2.0F;

    public ApiNotificationRequest {
        id = clean(id);
        channel = clean(channel).isBlank() ? "general" : clean(channel);
        source = clean(source);
        title = clean(title);
        message = clean(message);
        detail = clean(detail);
        type = type == null ? ApiNotificationType.INFO : type;
        priority = priority == null ? ApiNotificationPriority.NORMAL : priority;
        placement = placement == null ? ApiNotificationPlacement.AUTO : placement;
        durationMs = Math.max(1500, Math.min(30_000, durationMs));
        if (progress != NO_PROGRESS && progress != INDETERMINATE_PROGRESS) {
            progress = Math.max(0.0F, Math.min(1.0F, progress));
        }
    }

    public static Builder builder(ApiNotificationType type, String message) {
        return new Builder(type, message);
    }

    public static Builder success(String message) {
        return builder(ApiNotificationType.SUCCESS, message);
    }

    public static Builder error(String message) {
        return builder(ApiNotificationType.ERROR, message).priority(ApiNotificationPriority.HIGH);
    }

    public static Builder warning(String message) {
        return builder(ApiNotificationType.WARNING, message).priority(ApiNotificationPriority.HIGH);
    }

    public static Builder info(String message) {
        return builder(ApiNotificationType.INFO, message);
    }

    public static Builder transaction(String message) {
        return builder(ApiNotificationType.TRANSACTION, message).channel("banking");
    }

    public static Builder security(String message) {
        return builder(ApiNotificationType.SECURITY, message)
                .channel("security")
                .priority(ApiNotificationPriority.HIGH);
    }

    public static Builder progress(String id, String message) {
        return builder(ApiNotificationType.PROGRESS, message)
                .id(id)
                .progress(0.0F)
                .replaceExisting(true);
    }

    public boolean hasProgress() {
        return progress >= 0.0F || progress == INDETERMINATE_PROGRESS;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    @ApiStatus.AvailableSince("1.3.0")
    public static final class Builder {
        private String id = "";
        private String channel = "general";
        private String source = "";
        private String title = "";
        private final String message;
        private String detail = "";
        private final ApiNotificationType type;
        private ApiNotificationPriority priority = ApiNotificationPriority.NORMAL;
        private ApiNotificationPlacement placement = ApiNotificationPlacement.AUTO;
        private int durationMs = 4600;
        private float progress = NO_PROGRESS;
        private boolean sticky;
        private boolean playSound = true;
        private boolean replaceExisting = true;

        private Builder(ApiNotificationType type, String message) {
            this.type = type == null ? ApiNotificationType.INFO : type;
            this.message = message == null ? "" : message;
        }

        public Builder id(String value) {
            this.id = value;
            return this;
        }

        public Builder channel(String value) {
            this.channel = value;
            return this;
        }

        public Builder source(String value) {
            this.source = value;
            return this;
        }

        public Builder title(String value) {
            this.title = value;
            return this;
        }

        public Builder detail(String value) {
            this.detail = value;
            return this;
        }

        public Builder priority(ApiNotificationPriority value) {
            this.priority = value;
            return this;
        }

        public Builder placement(ApiNotificationPlacement value) {
            this.placement = value;
            return this;
        }

        public Builder durationMs(int value) {
            this.durationMs = value;
            return this;
        }

        public Builder progress(float value) {
            this.progress = value;
            return this;
        }

        public Builder indeterminateProgress() {
            this.progress = INDETERMINATE_PROGRESS;
            return this;
        }

        public Builder noProgress() {
            this.progress = NO_PROGRESS;
            return this;
        }

        public Builder sticky(boolean value) {
            this.sticky = value;
            return this;
        }

        public Builder playSound(boolean value) {
            this.playSound = value;
            return this;
        }

        public Builder replaceExisting(boolean value) {
            this.replaceExisting = value;
            return this;
        }

        public ApiNotificationRequest build() {
            return new ApiNotificationRequest(
                    id, channel, source, title, message, detail, type, priority, placement,
                    durationMs, progress, sticky, playSound, replaceExisting
            );
        }
    }
}
