package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record BankTellerActionPayload(
        UUID tellerId,
        String action,
        String accountId,
        String amount,
        String recipient,
        boolean confirmed,
        String paymentMode
) implements CustomPacketPayload {

    public static final Type<BankTellerActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "bank_teller_action"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, BankTellerActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        UUID_CODEC.encode(buf, payload.tellerId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.action());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.accountId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.amount());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.recipient());
                        ByteBufCodecs.BOOL.encode(buf, payload.confirmed());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.paymentMode());
                    },
                    buf -> new BankTellerActionPayload(
                            UUID_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)
                    )
            );

    @Override
    public Type<BankTellerActionPayload> type() {
        return TYPE;
    }
}
