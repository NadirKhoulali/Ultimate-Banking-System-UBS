package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client -> server payload requesting ATM PIN authentication.
 */
public record PinAuthRequestPayload(UUID accountId, String pin) implements CustomPacketPayload {

    public static final Type<PinAuthRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "pin_auth_request"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, PinAuthRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUID_CODEC, PinAuthRequestPayload::accountId,
                    ByteBufCodecs.STRING_UTF8, PinAuthRequestPayload::pin,
                    PinAuthRequestPayload::new
            );

    @Override
    public Type<PinAuthRequestPayload> type() {
        return TYPE;
    }
}
