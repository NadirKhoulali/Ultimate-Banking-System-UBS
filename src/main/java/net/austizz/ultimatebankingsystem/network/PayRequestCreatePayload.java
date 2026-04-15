package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record PayRequestCreatePayload(
        UUID accountId,
        String targetPlayerName,
        String amount,
        String destinationAccountId
) implements CustomPacketPayload {

    public static final Type<PayRequestCreatePayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "pay_request_create"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, PayRequestCreatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUID_CODEC, PayRequestCreatePayload::accountId,
                    ByteBufCodecs.STRING_UTF8, PayRequestCreatePayload::targetPlayerName,
                    ByteBufCodecs.STRING_UTF8, PayRequestCreatePayload::amount,
                    ByteBufCodecs.STRING_UTF8, PayRequestCreatePayload::destinationAccountId,
                    PayRequestCreatePayload::new
            );

    @Override
    public Type<PayRequestCreatePayload> type() {
        return TYPE;
    }
}
