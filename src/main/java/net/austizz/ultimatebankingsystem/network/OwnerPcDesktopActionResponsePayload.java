package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OwnerPcDesktopActionResponsePayload(
        String action,
        boolean success,
        String message
) implements CustomPacketPayload {

    public static final Type<OwnerPcDesktopActionResponsePayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "owner_pc_desktop_action_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcDesktopActionResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, OwnerPcDesktopActionResponsePayload::action,
                    ByteBufCodecs.BOOL, OwnerPcDesktopActionResponsePayload::success,
                    ByteBufCodecs.STRING_UTF8, OwnerPcDesktopActionResponsePayload::message,
                    OwnerPcDesktopActionResponsePayload::new
            );

    @Override
    public Type<OwnerPcDesktopActionResponsePayload> type() {
        return TYPE;
    }
}
