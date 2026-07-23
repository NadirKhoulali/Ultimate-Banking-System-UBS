package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HeistPlanningPayload(boolean open, String phase, boolean leader, String leaderName,
                                   String bankName, String premiseId, String crewData,
                                   String targetData, String status) implements CustomPacketPayload {
    public static final Type<HeistPlanningPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "heist_planning"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HeistPlanningPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                ByteBufCodecs.BOOL.encode(buf, value.open());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.phase());
                ByteBufCodecs.BOOL.encode(buf, value.leader());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.leaderName());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.bankName());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.premiseId());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.crewData());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.targetData());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.status());
            },
            buf -> new HeistPlanningPayload(
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)));

    @Override
    public Type<HeistPlanningPayload> type() { return TYPE; }
}
