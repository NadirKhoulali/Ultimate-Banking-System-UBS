package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record OwnerPcBootstrapPayload(
        List<OwnerPcBankAppSummary> apps,
        int ownedCount,
        int maxBanks
) implements CustomPacketPayload {

    public static final Type<OwnerPcBootstrapPayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "owner_pc_bootstrap"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcBootstrapPayload> STREAM_CODEC =
            StreamCodec.composite(
                    OwnerPcBankAppSummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)), OwnerPcBootstrapPayload::apps,
                    ByteBufCodecs.VAR_INT, OwnerPcBootstrapPayload::ownedCount,
                    ByteBufCodecs.VAR_INT, OwnerPcBootstrapPayload::maxBanks,
                    OwnerPcBootstrapPayload::new
            );

    @Override
    public Type<OwnerPcBootstrapPayload> type() {
        return TYPE;
    }
}
