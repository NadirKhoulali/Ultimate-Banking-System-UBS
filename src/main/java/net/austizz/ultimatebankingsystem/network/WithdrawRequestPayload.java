package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client → server payload requesting a withdrawal from the selected account.
 */
public record WithdrawRequestPayload(UUID accountId, String amount) implements CustomPacketPayload {

    public static final Type<WithdrawRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "withdraw_request"));

    /** StreamCodec for UUID — serialises as two longs (mostSigBits, leastSigBits). */
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
        StreamCodec.of(
            (buf, uuid) -> { buf.writeLong(uuid.getMostSignificantBits()); buf.writeLong(uuid.getLeastSignificantBits()); },
            buf -> new UUID(buf.readLong(), buf.readLong())
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, WithdrawRequestPayload> STREAM_CODEC =
        StreamCodec.composite(
            UUID_CODEC,                WithdrawRequestPayload::accountId,
            ByteBufCodecs.STRING_UTF8, WithdrawRequestPayload::amount,
            WithdrawRequestPayload::new
        );

    @Override
    public Type<WithdrawRequestPayload> type() { return TYPE; }
}
