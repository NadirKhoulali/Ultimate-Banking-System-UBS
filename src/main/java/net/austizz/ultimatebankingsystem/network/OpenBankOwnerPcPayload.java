package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenBankOwnerPcPayload(
        String dimensionId,
        int x,
        int y,
        int z
) implements CustomPacketPayload {

    public static final Type<OpenBankOwnerPcPayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "open_bank_owner_pc"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBankOwnerPcPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, OpenBankOwnerPcPayload::dimensionId,
                    ByteBufCodecs.VAR_INT, OpenBankOwnerPcPayload::x,
                    ByteBufCodecs.VAR_INT, OpenBankOwnerPcPayload::y,
                    ByteBufCodecs.VAR_INT, OpenBankOwnerPcPayload::z,
                    OpenBankOwnerPcPayload::new
            );

    public OpenBankOwnerPcPayload() {
        this("", 0, 0, 0);
    }

    @Override
    public Type<OpenBankOwnerPcPayload> type() {
        return TYPE;
    }
}
