package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record OwnerPcActionPayload(
        UUID bankId,
        String action,
        String arg1,
        String arg2,
        String arg3,
        String arg4
) implements CustomPacketPayload {

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final Type<OwnerPcActionPayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "owner_pc_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        UUID_CODEC.encode(buf, payload.bankId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.action());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.arg1());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.arg2());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.arg3());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.arg4());
                    },
                    buf -> new OwnerPcActionPayload(
                            UUID_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)
                    )
            );

    @Override
    public Type<OwnerPcActionPayload> type() {
        return TYPE;
    }
}
