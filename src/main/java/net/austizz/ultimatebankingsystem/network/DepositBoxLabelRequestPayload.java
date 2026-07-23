package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DepositBoxLabelRequestPayload(int x, int y, int z) implements CustomPacketPayload {
    public static final Type<DepositBoxLabelRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "deposit_box_label_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DepositBoxLabelRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DepositBoxLabelRequestPayload::x,
                    ByteBufCodecs.VAR_INT, DepositBoxLabelRequestPayload::y,
                    ByteBufCodecs.VAR_INT, DepositBoxLabelRequestPayload::z,
                    DepositBoxLabelRequestPayload::new);

    @Override
    public Type<DepositBoxLabelRequestPayload> type() {
        return TYPE;
    }
}
