package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PickpocketStatePayload(boolean active,
                                     String targetName,
                                     int elapsedTicks,
                                     int durationTicks,
                                     int cooldownRemainingTicks) implements CustomPacketPayload {

    public static final Type<PickpocketStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "pickpocket_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PickpocketStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, PickpocketStatePayload::active,
                    ByteBufCodecs.STRING_UTF8, PickpocketStatePayload::targetName,
                    ByteBufCodecs.VAR_INT, PickpocketStatePayload::elapsedTicks,
                    ByteBufCodecs.VAR_INT, PickpocketStatePayload::durationTicks,
                    ByteBufCodecs.VAR_INT, PickpocketStatePayload::cooldownRemainingTicks,
                    PickpocketStatePayload::new
            );

    public PickpocketStatePayload {
        targetName = targetName == null ? "" : targetName.trim();
        elapsedTicks = Math.max(0, elapsedTicks);
        durationTicks = Math.max(0, durationTicks);
        cooldownRemainingTicks = Math.max(0, cooldownRemainingTicks);
    }

    @Override
    public Type<PickpocketStatePayload> type() {
        return TYPE;
    }
}
