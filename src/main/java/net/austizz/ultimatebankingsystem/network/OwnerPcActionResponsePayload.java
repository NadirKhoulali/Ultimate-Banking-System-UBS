package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record OwnerPcActionResponsePayload(
        UUID bankId,
        boolean success,
        String message
) implements CustomPacketPayload {

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final Type<OwnerPcActionResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "owner_pc_action_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcActionResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUID_CODEC, OwnerPcActionResponsePayload::bankId,
                    ByteBufCodecs.BOOL, OwnerPcActionResponsePayload::success,
                    ByteBufCodecs.STRING_UTF8, OwnerPcActionResponsePayload::message,
                    OwnerPcActionResponsePayload::new
            );

    @Override
    public Type<OwnerPcActionResponsePayload> type() {
        return TYPE;
    }
}
