package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client → server payload requesting transaction history for an account.
 */
public record TxHistoryRequestPayload(UUID accountId, int maxEntries) implements CustomPacketPayload {

    public static final Type<TxHistoryRequestPayload> TYPE = new Type<>(
        new ResourceLocation(UltimateBankingSystem.MODID, "tx_history_request"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
        StreamCodec.of(
            (buf, uuid) -> { buf.writeLong(uuid.getMostSignificantBits()); buf.writeLong(uuid.getLeastSignificantBits()); },
            buf -> new UUID(buf.readLong(), buf.readLong())
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, TxHistoryRequestPayload> STREAM_CODEC =
        StreamCodec.composite(
            UUID_CODEC,              TxHistoryRequestPayload::accountId,
            ByteBufCodecs.VAR_INT,   TxHistoryRequestPayload::maxEntries,
            TxHistoryRequestPayload::new
        );

    @Override
    public Type<TxHistoryRequestPayload> type() { return TYPE; }
}
