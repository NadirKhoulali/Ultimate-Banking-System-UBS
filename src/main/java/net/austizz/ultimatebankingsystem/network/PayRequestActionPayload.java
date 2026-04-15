package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record PayRequestActionPayload(
        UUID accountId,
        UUID requestId,
        String action,
        String senderAccountId
) implements CustomPacketPayload {

    public static final Type<PayRequestActionPayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "pay_request_action"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, PayRequestActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUID_CODEC, PayRequestActionPayload::accountId,
                    UUID_CODEC, PayRequestActionPayload::requestId,
                    ByteBufCodecs.STRING_UTF8, PayRequestActionPayload::action,
                    ByteBufCodecs.STRING_UTF8, PayRequestActionPayload::senderAccountId,
                    PayRequestActionPayload::new
            );

    @Override
    public Type<PayRequestActionPayload> type() {
        return TYPE;
    }
}
