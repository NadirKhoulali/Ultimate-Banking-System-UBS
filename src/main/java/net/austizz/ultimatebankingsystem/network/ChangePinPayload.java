package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client → server payload requesting a PIN/password change.
 */
public record ChangePinPayload(UUID accountId, String currentPin, String newPin) implements CustomPacketPayload {

    public static final Type<ChangePinPayload> TYPE = new Type<>(
        new ResourceLocation(UltimateBankingSystem.MODID, "change_pin"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
        StreamCodec.of(
            (buf, uuid) -> { buf.writeLong(uuid.getMostSignificantBits()); buf.writeLong(uuid.getLeastSignificantBits()); },
            buf -> new UUID(buf.readLong(), buf.readLong())
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, ChangePinPayload> STREAM_CODEC =
        StreamCodec.composite(
            UUID_CODEC,                ChangePinPayload::accountId,
            ByteBufCodecs.STRING_UTF8, ChangePinPayload::currentPin,
            ByteBufCodecs.STRING_UTF8, ChangePinPayload::newPin,
            ChangePinPayload::new
        );

    @Override
    public Type<ChangePinPayload> type() { return TYPE; }
}
