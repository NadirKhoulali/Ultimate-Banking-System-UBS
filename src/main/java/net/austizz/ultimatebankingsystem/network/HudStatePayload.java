package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HudStatePayload(
        String balance,
        boolean enabled,
        String bankName,
        String accountType,
        boolean primaryAccount,
        String position
) implements CustomPacketPayload {
    public static final Type<HudStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "hud_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HudStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, HudStatePayload::balance,
                    ByteBufCodecs.BOOL, HudStatePayload::enabled,
                    ByteBufCodecs.STRING_UTF8, HudStatePayload::bankName,
                    ByteBufCodecs.STRING_UTF8, HudStatePayload::accountType,
                    ByteBufCodecs.BOOL, HudStatePayload::primaryAccount,
                    ByteBufCodecs.STRING_UTF8, HudStatePayload::position,
                    HudStatePayload::new
            );

    public HudStatePayload {
        balance = balance == null ? "" : balance.trim();
        bankName = bankName == null ? "" : bankName.trim();
        accountType = accountType == null ? "" : accountType.trim();
        position = position == null ? "" : position.trim();
    }

    @Override
    public Type<HudStatePayload> type() {
        return TYPE;
    }
}
