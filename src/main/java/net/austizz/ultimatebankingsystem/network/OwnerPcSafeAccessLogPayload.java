package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record OwnerPcSafeAccessLogPayload(
        String eventId,
        long occurredAtMillis,
        String category,
        String outcome,
        String action,
        String actorName,
        String subject,
        String detail,
        String dimension,
        int x,
        int y,
        int z
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcSafeAccessLogPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.eventId());
                        buf.writeLong(value.occurredAtMillis());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.category());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.outcome());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.action());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.actorName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.subject());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.detail());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.dimension());
                        buf.writeInt(value.x());
                        buf.writeInt(value.y());
                        buf.writeInt(value.z());
                    },
                    buf -> new OwnerPcSafeAccessLogPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            buf.readLong(),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt()
                    )
            );

    public OwnerPcSafeAccessLogPayload {
        eventId = safe(eventId);
        occurredAtMillis = Math.max(0L, occurredAtMillis);
        category = safe(category);
        outcome = safe(outcome);
        action = safe(action);
        actorName = safe(actorName);
        subject = safe(subject);
        detail = safe(detail);
        dimension = safe(dimension);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
