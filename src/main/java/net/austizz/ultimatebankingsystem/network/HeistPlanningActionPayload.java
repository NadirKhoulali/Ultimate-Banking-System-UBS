package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HeistPlanningActionPayload(String action, String arg1, String arg2) implements CustomPacketPayload {
    public static final Type<HeistPlanningActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "heist_planning_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HeistPlanningActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, HeistPlanningActionPayload::action,
                    ByteBufCodecs.STRING_UTF8, HeistPlanningActionPayload::arg1,
                    ByteBufCodecs.STRING_UTF8, HeistPlanningActionPayload::arg2,
                    HeistPlanningActionPayload::new);

    public HeistPlanningActionPayload {
        action = clean(action, 40);
        arg1 = clean(arg1, 160);
        arg2 = clean(arg2, 160);
    }

    @Override
    public Type<HeistPlanningActionPayload> type() { return TYPE; }

    private static String clean(String value, int max) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
