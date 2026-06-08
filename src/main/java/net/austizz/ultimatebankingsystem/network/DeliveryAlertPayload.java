package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DeliveryAlertPayload(
        String title,
        String message,
        boolean success,
        int durationMs,
        int toneCode
) implements CustomPacketPayload {

    public enum AlertTone {
        SUCCESS(0),
        ERROR(1),
        INFO(2),
        WARNING(3);

        private final int id;

        AlertTone(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static AlertTone fromId(int id) {
            return switch (id) {
                case 1 -> ERROR;
                case 2 -> INFO;
                case 3 -> WARNING;
                default -> SUCCESS;
            };
        }
    }

    public static final Type<DeliveryAlertPayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "delivery_alert"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeliveryAlertPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, DeliveryAlertPayload::title,
                    ByteBufCodecs.STRING_UTF8, DeliveryAlertPayload::message,
                    ByteBufCodecs.BOOL, DeliveryAlertPayload::success,
                    ByteBufCodecs.VAR_INT, DeliveryAlertPayload::durationMs,
                    ByteBufCodecs.VAR_INT, DeliveryAlertPayload::toneCode,
                    DeliveryAlertPayload::new
            );

    public DeliveryAlertPayload(String title, String message, boolean success, int durationMs) {
        this(title, message, success, durationMs, success ? AlertTone.SUCCESS.id() : AlertTone.ERROR.id());
    }

    public DeliveryAlertPayload {
        title = title == null ? "" : title.trim();
        message = message == null ? "" : message.trim();
        durationMs = Math.max(1200, Math.min(12000, durationMs));
        toneCode = AlertTone.fromId(toneCode).id();
    }

    public AlertTone tone() {
        return AlertTone.fromId(toneCode);
    }

    @Override
    public Type<DeliveryAlertPayload> type() {
        return TYPE;
    }
}
