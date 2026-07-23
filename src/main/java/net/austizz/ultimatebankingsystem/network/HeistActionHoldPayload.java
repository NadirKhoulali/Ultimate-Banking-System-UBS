package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HeistActionHoldPayload(boolean held) implements CustomPacketPayload {
    public static final Type<HeistActionHoldPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "heist_action_hold"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HeistActionHoldPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, HeistActionHoldPayload::held, HeistActionHoldPayload::new);

    @Override
    public Type<HeistActionHoldPayload> type() { return TYPE; }
}
