package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client payload carrying ATM PIN authentication result.
 */
public record PinAuthResponsePayload(boolean success, boolean pinSetupRequired, String message)
        implements CustomPacketPayload {

    public static final Type<PinAuthResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "pin_auth_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PinAuthResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, PinAuthResponsePayload::success,
                    ByteBufCodecs.BOOL, PinAuthResponsePayload::pinSetupRequired,
                    ByteBufCodecs.STRING_UTF8, PinAuthResponsePayload::message,
                    PinAuthResponsePayload::new
            );

    @Override
    public Type<PinAuthResponsePayload> type() {
        return TYPE;
    }
}
