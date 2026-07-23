package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DallasMaskTogglePayload() implements CustomPacketPayload {
    public static final Type<DallasMaskTogglePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "dallas_mask_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DallasMaskTogglePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                    },
                    buf -> new DallasMaskTogglePayload()
            );

    @Override
    public Type<DallasMaskTogglePayload> type() {
        return TYPE;
    }
}
