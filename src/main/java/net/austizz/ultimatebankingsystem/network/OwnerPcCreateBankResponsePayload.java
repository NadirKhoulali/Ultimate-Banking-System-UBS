package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OwnerPcCreateBankResponsePayload(
        boolean success,
        String message
) implements CustomPacketPayload {

    public static final Type<OwnerPcCreateBankResponsePayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "owner_pc_create_bank_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcCreateBankResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, OwnerPcCreateBankResponsePayload::success,
                    ByteBufCodecs.STRING_UTF8, OwnerPcCreateBankResponsePayload::message,
                    OwnerPcCreateBankResponsePayload::new
            );

    @Override
    public Type<OwnerPcCreateBankResponsePayload> type() {
        return TYPE;
    }
}
