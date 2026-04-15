package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client → server payload for toggling primary account state.
 */
public record SetPrimaryPayload(UUID accountId, boolean setPrimary) implements CustomPacketPayload {

    public static final Type<SetPrimaryPayload> TYPE = new Type<>(
        new ResourceLocation(UltimateBankingSystem.MODID, "set_primary"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
        StreamCodec.of(
            (buf, uuid) -> { buf.writeLong(uuid.getMostSignificantBits()); buf.writeLong(uuid.getLeastSignificantBits()); },
            buf -> new UUID(buf.readLong(), buf.readLong())
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPrimaryPayload> STREAM_CODEC =
        StreamCodec.composite(
            UUID_CODEC,             SetPrimaryPayload::accountId,
            ByteBufCodecs.BOOL,     SetPrimaryPayload::setPrimary,
            SetPrimaryPayload::new
        );

    @Override
    public Type<SetPrimaryPayload> type() { return TYPE; }
}
