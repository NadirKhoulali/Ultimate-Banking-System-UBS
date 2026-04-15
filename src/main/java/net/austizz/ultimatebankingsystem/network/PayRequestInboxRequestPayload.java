package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record PayRequestInboxRequestPayload(UUID accountId) implements CustomPacketPayload {

    public static final Type<PayRequestInboxRequestPayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "pay_request_inbox_request"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, PayRequestInboxRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUID_CODEC, PayRequestInboxRequestPayload::accountId,
                    PayRequestInboxRequestPayload::new
            );

    @Override
    public Type<PayRequestInboxRequestPayload> type() {
        return TYPE;
    }
}
