package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record OwnerPcBankTellerPayload(UUID entityId,
                                      String name,
                                      int variant,
                                      String dimension,
                                      int x,
                                      int y,
                                      int z,
                                      boolean active) {
    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcBankTellerPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.entityId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.name());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.variant());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimension());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.x());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.y());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.z());
                        ByteBufCodecs.BOOL.encode(buf, payload.active());
                    },
                    buf -> new OwnerPcBankTellerPayload(
                            buf.readUUID(),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)
                    )
            );

    public OwnerPcBankTellerPayload {
        entityId = entityId == null ? new UUID(0L, 0L) : entityId;
        name = safe(name);
        dimension = safe(dimension);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
