package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SmartphoneOpenRequestPayload(boolean animate) implements CustomPacketPayload {
    public static final Type<SmartphoneOpenRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "smartphone_open_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SmartphoneOpenRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SmartphoneOpenRequestPayload::animate,
                    SmartphoneOpenRequestPayload::new
            );

    @Override
    public Type<SmartphoneOpenRequestPayload> type() {
        return TYPE;
    }
}
