package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OwnerPcCreateBankPayload(
        String bankName,
        String ownershipModel
) implements CustomPacketPayload {

    public static final Type<OwnerPcCreateBankPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "owner_pc_create_bank"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcCreateBankPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, OwnerPcCreateBankPayload::bankName,
                    ByteBufCodecs.STRING_UTF8, OwnerPcCreateBankPayload::ownershipModel,
                    OwnerPcCreateBankPayload::new
            );

    @Override
    public Type<OwnerPcCreateBankPayload> type() {
        return TYPE;
    }
}
