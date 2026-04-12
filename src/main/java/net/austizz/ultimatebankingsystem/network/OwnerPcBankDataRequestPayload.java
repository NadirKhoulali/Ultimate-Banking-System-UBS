package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record OwnerPcBankDataRequestPayload(UUID bankId) implements CustomPacketPayload {

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final Type<OwnerPcBankDataRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "owner_pc_bank_data_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcBankDataRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUID_CODEC, OwnerPcBankDataRequestPayload::bankId,
                    OwnerPcBankDataRequestPayload::new
            );

    @Override
    public Type<OwnerPcBankDataRequestPayload> type() {
        return TYPE;
    }
}
