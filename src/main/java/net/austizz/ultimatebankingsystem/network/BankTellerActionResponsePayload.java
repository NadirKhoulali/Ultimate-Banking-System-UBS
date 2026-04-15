package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BankTellerActionResponsePayload(
        boolean success,
        String message,
        boolean closeScreen
) implements CustomPacketPayload {

    public static final Type<BankTellerActionResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "bank_teller_action_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BankTellerActionResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    BankTellerActionResponsePayload::success,
                    ByteBufCodecs.STRING_UTF8,
                    BankTellerActionResponsePayload::message,
                    ByteBufCodecs.BOOL,
                    BankTellerActionResponsePayload::closeScreen,
                    BankTellerActionResponsePayload::new
            );

    @Override
    public Type<BankTellerActionResponsePayload> type() {
        return TYPE;
    }
}
