package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OwnerPcDesktopActionPayload(
        String action,
        String arg1,
        String arg2
) implements CustomPacketPayload {

    public static final Type<OwnerPcDesktopActionPayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "owner_pc_desktop_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcDesktopActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, OwnerPcDesktopActionPayload::action,
                    ByteBufCodecs.STRING_UTF8, OwnerPcDesktopActionPayload::arg1,
                    ByteBufCodecs.STRING_UTF8, OwnerPcDesktopActionPayload::arg2,
                    OwnerPcDesktopActionPayload::new
            );

    @Override
    public Type<OwnerPcDesktopActionPayload> type() {
        return TYPE;
    }
}
