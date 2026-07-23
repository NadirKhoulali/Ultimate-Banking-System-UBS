package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SmartphoneNotificationPayload(String title,
                                            String message,
                                            int toneCode,
                                            int durationMs) implements CustomPacketPayload {
    public static final Type<SmartphoneNotificationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "smartphone_notification"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SmartphoneNotificationPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SmartphoneNotificationPayload::title,
                    ByteBufCodecs.STRING_UTF8, SmartphoneNotificationPayload::message,
                    ByteBufCodecs.VAR_INT, SmartphoneNotificationPayload::toneCode,
                    ByteBufCodecs.VAR_INT, SmartphoneNotificationPayload::durationMs,
                    SmartphoneNotificationPayload::new
            );

    public SmartphoneNotificationPayload {
        title = title == null ? "" : title.trim();
        message = message == null ? "" : message.trim();
        toneCode = DeliveryAlertPayload.AlertTone.fromId(toneCode).id();
        durationMs = Math.max(1800, Math.min(12000, durationMs));
    }

    public DeliveryAlertPayload.AlertTone tone() {
        return DeliveryAlertPayload.AlertTone.fromId(toneCode);
    }

    @Override
    public Type<SmartphoneNotificationPayload> type() {
        return TYPE;
    }
}
