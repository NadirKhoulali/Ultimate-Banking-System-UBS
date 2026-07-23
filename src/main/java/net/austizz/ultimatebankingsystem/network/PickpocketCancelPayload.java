package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PickpocketCancelPayload() implements CustomPacketPayload {

    public static final Type<PickpocketCancelPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "pickpocket_cancel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PickpocketCancelPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                    },
                    buf -> new PickpocketCancelPayload()
            );

    @Override
    public Type<PickpocketCancelPayload> type() {
        return TYPE;
    }
}
