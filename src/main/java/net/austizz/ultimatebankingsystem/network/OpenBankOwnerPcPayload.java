package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenBankOwnerPcPayload() implements CustomPacketPayload {

    public static final Type<OpenBankOwnerPcPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "open_bank_owner_pc"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBankOwnerPcPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {}, buf -> new OpenBankOwnerPcPayload());

    @Override
    public Type<OpenBankOwnerPcPayload> type() {
        return TYPE;
    }
}
