package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record PickpocketStartPayload(UUID targetPlayerId) implements CustomPacketPayload {

    public static final Type<PickpocketStartPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "pickpocket_start"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, PickpocketStartPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUID_CODEC, PickpocketStartPayload::targetPlayerId,
                    PickpocketStartPayload::new
            );

    @Override
    public Type<PickpocketStartPayload> type() {
        return TYPE;
    }
}
