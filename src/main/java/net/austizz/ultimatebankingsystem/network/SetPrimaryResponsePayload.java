package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client payload for primary account toggle result.
 */
public record SetPrimaryResponsePayload(boolean success, boolean newPrimaryState) implements CustomPacketPayload {

    public static final Type<SetPrimaryResponsePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "set_primary_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPrimaryResponsePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL, SetPrimaryResponsePayload::success,
            ByteBufCodecs.BOOL, SetPrimaryResponsePayload::newPrimaryState,
            SetPrimaryResponsePayload::new
        );

    @Override
    public Type<SetPrimaryResponsePayload> type() { return TYPE; }
}
