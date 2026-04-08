package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client → server payload requesting a transfer from sender to recipient account.
 */
public record TransferRequestPayload(UUID senderAccountId, UUID recipientAccountId, String amount) implements CustomPacketPayload {

    public static final Type<TransferRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "transfer_request"));

    /** StreamCodec for UUID — serialises as two longs (mostSigBits, leastSigBits). */
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
        StreamCodec.of(
            (buf, uuid) -> { buf.writeLong(uuid.getMostSignificantBits()); buf.writeLong(uuid.getLeastSignificantBits()); },
            buf -> new UUID(buf.readLong(), buf.readLong())
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, TransferRequestPayload> STREAM_CODEC =
        StreamCodec.composite(
            UUID_CODEC,                TransferRequestPayload::senderAccountId,
            UUID_CODEC,                TransferRequestPayload::recipientAccountId,
            ByteBufCodecs.STRING_UTF8, TransferRequestPayload::amount,
            TransferRequestPayload::new
        );

    @Override
    public Type<TransferRequestPayload> type() { return TYPE; }
}
