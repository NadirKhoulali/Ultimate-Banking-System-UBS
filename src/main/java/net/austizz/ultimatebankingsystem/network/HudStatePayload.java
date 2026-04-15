package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HudStatePayload(String balance, boolean enabled) implements CustomPacketPayload {
    public static final Type<HudStatePayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "hud_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HudStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, HudStatePayload::balance,
                    ByteBufCodecs.BOOL, HudStatePayload::enabled,
                    HudStatePayload::new
            );

    @Override
    public Type<HudStatePayload> type() {
        return TYPE;
    }
}
