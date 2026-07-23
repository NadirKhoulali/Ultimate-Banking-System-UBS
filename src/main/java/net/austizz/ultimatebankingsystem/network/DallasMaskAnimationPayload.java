package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record DallasMaskAnimationPayload(UUID playerId, boolean puttingOn, int durationTicks)
        implements CustomPacketPayload {
    public static final Type<DallasMaskAnimationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "dallas_mask_animation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DallasMaskAnimationPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.playerId());
                        buf.writeBoolean(payload.puttingOn());
                        buf.writeVarInt(payload.durationTicks());
                    },
                    buf -> new DallasMaskAnimationPayload(buf.readUUID(), buf.readBoolean(), buf.readVarInt())
            );

    @Override
    public Type<DallasMaskAnimationPayload> type() {
        return TYPE;
    }
}
