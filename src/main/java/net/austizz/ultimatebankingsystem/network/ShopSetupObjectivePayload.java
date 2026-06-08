package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Syncs the owner's guided shop setup objective card to the client HUD.
 */
public record ShopSetupObjectivePayload(
        boolean active,
        String shopName,
        int step,
        int totalSteps,
        String objectiveTitle,
        String objectiveDetail
) implements CustomPacketPayload {

    public static final Type<ShopSetupObjectivePayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "shop_setup_objective"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopSetupObjectivePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ShopSetupObjectivePayload::active,
                    ByteBufCodecs.STRING_UTF8, ShopSetupObjectivePayload::shopName,
                    ByteBufCodecs.VAR_INT, ShopSetupObjectivePayload::step,
                    ByteBufCodecs.VAR_INT, ShopSetupObjectivePayload::totalSteps,
                    ByteBufCodecs.STRING_UTF8, ShopSetupObjectivePayload::objectiveTitle,
                    ByteBufCodecs.STRING_UTF8, ShopSetupObjectivePayload::objectiveDetail,
                    ShopSetupObjectivePayload::new
            );

    public ShopSetupObjectivePayload {
        shopName = trimTo(shopName, 72);
        objectiveTitle = trimTo(objectiveTitle, 128);
        objectiveDetail = trimTo(objectiveDetail, 256);
        totalSteps = Math.max(1, Math.min(32, totalSteps));
        step = Math.max(1, Math.min(totalSteps, step));
    }

    public static ShopSetupObjectivePayload inactive() {
        return new ShopSetupObjectivePayload(false, "", 1, 1, "", "");
    }

    private static String trimTo(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength));
    }

    @Override
    public Type<ShopSetupObjectivePayload> type() {
        return TYPE;
    }
}
