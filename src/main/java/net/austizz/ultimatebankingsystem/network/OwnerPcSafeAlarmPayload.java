package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record OwnerPcSafeAlarmPayload(
        boolean enabled,
        String soundEventId,
        float volume,
        float primaryPitch,
        float secondaryPitch,
        int intervalTicks,
        boolean alarmActive,
        String activeReason,
        int linkedScannerCount
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcSafeAlarmPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        buf.writeBoolean(value.enabled());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.soundEventId());
                        buf.writeFloat(value.volume());
                        buf.writeFloat(value.primaryPitch());
                        buf.writeFloat(value.secondaryPitch());
                        buf.writeVarInt(value.intervalTicks());
                        buf.writeBoolean(value.alarmActive());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.activeReason());
                        buf.writeVarInt(value.linkedScannerCount());
                    },
                    buf -> new OwnerPcSafeAlarmPayload(
                            buf.readBoolean(),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readVarInt(),
                            buf.readBoolean(),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            buf.readVarInt()
                    )
            );

    public OwnerPcSafeAlarmPayload {
        soundEventId = soundEventId == null ? "" : soundEventId;
        volume = Math.max(0.0F, volume);
        primaryPitch = Math.max(0.0F, primaryPitch);
        secondaryPitch = Math.max(0.0F, secondaryPitch);
        intervalTicks = Math.max(1, intervalTicks);
        activeReason = activeReason == null ? "" : activeReason;
        linkedScannerCount = Math.max(0, linkedScannerCount);
    }
}
