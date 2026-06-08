package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sync payload for the right-side courier delivery tracker board.
 */
public record DeliveryInfoBoardPayload(
        boolean active,
        String shopName,
        String itemName,
        int quantity,
        long rewardCents,
        long remainingSeconds,
        int timeoutMinutes,
        int activeOrders,
        int activeCap,
        String dropTarget,
        String distanceLabel,
        String rankLabel,
        long streak,
        int successRatePct,
        long completedOrders,
        long totalPayoutCents
) implements CustomPacketPayload {

    public static final Type<DeliveryInfoBoardPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "delivery_info_board"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeliveryInfoBoardPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.BOOL.encode(buf, payload.active());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.shopName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.itemName());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.quantity());
                        ByteBufCodecs.VAR_LONG.encode(buf, payload.rewardCents());
                        ByteBufCodecs.VAR_LONG.encode(buf, payload.remainingSeconds());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.timeoutMinutes());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.activeOrders());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.activeCap());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dropTarget());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.distanceLabel());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.rankLabel());
                        ByteBufCodecs.VAR_LONG.encode(buf, payload.streak());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.successRatePct());
                        ByteBufCodecs.VAR_LONG.encode(buf, payload.completedOrders());
                        ByteBufCodecs.VAR_LONG.encode(buf, payload.totalPayoutCents());
                    },
                    buf -> new DeliveryInfoBoardPayload(
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_LONG.decode(buf),
                            ByteBufCodecs.VAR_LONG.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_LONG.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_LONG.decode(buf),
                            ByteBufCodecs.VAR_LONG.decode(buf)
                    )
            );

    public DeliveryInfoBoardPayload {
        shopName = trimTo(shopName, 96);
        itemName = trimTo(itemName, 96);
        dropTarget = trimTo(dropTarget, 160);
        distanceLabel = trimTo(distanceLabel, 64);
        rankLabel = trimTo(rankLabel, 64);
        quantity = Math.max(1, quantity);
        rewardCents = Math.max(0L, rewardCents);
        remainingSeconds = Math.max(0L, remainingSeconds);
        timeoutMinutes = Math.max(1, timeoutMinutes);
        activeOrders = Math.max(1, activeOrders);
        activeCap = Math.max(activeOrders, activeCap);
        streak = Math.max(0L, streak);
        successRatePct = Math.max(0, Math.min(100, successRatePct));
        completedOrders = Math.max(0L, completedOrders);
        totalPayoutCents = Math.max(0L, totalPayoutCents);
    }

    public static DeliveryInfoBoardPayload inactive() {
        return new DeliveryInfoBoardPayload(
                false,
                "",
                "",
                1,
                0L,
                0L,
                1,
                1,
                1,
                "",
                "",
                "",
                0L,
                0,
                0L,
                0L
        );
    }

    private static String trimTo(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength));
    }

    @Override
    public Type<DeliveryInfoBoardPayload> type() {
        return TYPE;
    }
}
