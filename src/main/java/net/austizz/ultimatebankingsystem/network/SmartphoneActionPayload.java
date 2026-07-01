package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SmartphoneActionPayload(String action, String param1, String param2, String param3) implements CustomPacketPayload {
    public static final Type<SmartphoneActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "smartphone_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SmartphoneActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SmartphoneActionPayload::action,
                    ByteBufCodecs.STRING_UTF8, SmartphoneActionPayload::param1,
                    ByteBufCodecs.STRING_UTF8, SmartphoneActionPayload::param2,
                    ByteBufCodecs.STRING_UTF8, SmartphoneActionPayload::param3,
                    SmartphoneActionPayload::new
            );

    public SmartphoneActionPayload {
        action = action == null ? "" : action;
        param1 = param1 == null ? "" : param1;
        param2 = param2 == null ? "" : param2;
        param3 = param3 == null ? "" : param3;
    }

    @Override
    public Type<SmartphoneActionPayload> type() {
        return TYPE;
    }
}
