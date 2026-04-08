package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client payload carrying PIN/password change result.
 */
public record ChangePinResponsePayload(boolean success, String errorMessage) implements CustomPacketPayload {

    public static final Type<ChangePinResponsePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "change_pin_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChangePinResponsePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL,        ChangePinResponsePayload::success,
            ByteBufCodecs.STRING_UTF8, ChangePinResponsePayload::errorMessage,
            ChangePinResponsePayload::new
        );

    @Override
    public Type<ChangePinResponsePayload> type() { return TYPE; }
}
