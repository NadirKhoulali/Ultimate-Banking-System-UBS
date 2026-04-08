package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client → server payload requesting account balance details.
 */
public record BalanceRequestPayload(UUID accountId) implements CustomPacketPayload {

    public static final Type<BalanceRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "balance_request"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
        StreamCodec.of(
            (buf, uuid) -> { buf.writeLong(uuid.getMostSignificantBits()); buf.writeLong(uuid.getLeastSignificantBits()); },
            buf -> new UUID(buf.readLong(), buf.readLong())
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, BalanceRequestPayload> STREAM_CODEC =
        StreamCodec.composite(
            UUID_CODEC, BalanceRequestPayload::accountId,
            BalanceRequestPayload::new
        );

    @Override
    public Type<BalanceRequestPayload> type() { return TYPE; }
}
