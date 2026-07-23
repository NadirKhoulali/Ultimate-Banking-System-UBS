package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record OwnerPcPlayerEmployeePayload(UUID playerId,
                                           String name,
                                           String role,
                                           String salary,
                                           boolean online,
                                           boolean safeAccess) {
    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcPlayerEmployeePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.playerId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.name());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.role());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.salary());
                        ByteBufCodecs.BOOL.encode(buf, payload.online());
                        ByteBufCodecs.BOOL.encode(buf, payload.safeAccess());
                    },
                    buf -> new OwnerPcPlayerEmployeePayload(
                            buf.readUUID(),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)
                    )
            );

    public OwnerPcPlayerEmployeePayload {
        playerId = playerId == null ? new UUID(0L, 0L) : playerId;
        name = safe(name);
        role = safe(role);
        salary = safe(salary);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
