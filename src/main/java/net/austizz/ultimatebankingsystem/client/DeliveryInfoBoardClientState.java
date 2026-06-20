package net.austizz.ultimatebankingsystem.client;

/**
 * Client-side state container for the delivery tracker board shown on the HUD.
 */
public final class DeliveryInfoBoardClientState {
    private static boolean active;
    private static String shopName = "";
    private static String itemName = "";
    private static int quantity;
    private static long rewardCents;
    private static long remainingSeconds;
    private static int timeoutMinutes;
    private static int activeOrders;
    private static int activeCap;
    private static String dropTarget = "";
    private static String distanceLabel = "";
    private static String rankLabel = "";
    private static long streak;
    private static int successRatePct;
    private static long completedOrders;
    private static long totalPayoutCents;
    private static boolean collapsed;

    private DeliveryInfoBoardClientState() {
    }

    public static void set(boolean nextActive,
                           String nextShopName,
                           String nextItemName,
                           int nextQuantity,
                           long nextRewardCents,
                           long nextRemainingSeconds,
                           int nextTimeoutMinutes,
                           int nextActiveOrders,
                           int nextActiveCap,
                           String nextDropTarget,
                           String nextDistanceLabel,
                           String nextRankLabel,
                           long nextStreak,
                           int nextSuccessRatePct,
                           long nextCompletedOrders,
                           long nextTotalPayoutCents) {
        active = nextActive;
        if (!active) {
            clear();
            return;
        }
        shopName = sanitize(nextShopName);
        itemName = sanitize(nextItemName);
        quantity = Math.max(1, nextQuantity);
        rewardCents = Math.max(0L, nextRewardCents);
        remainingSeconds = Math.max(0L, nextRemainingSeconds);
        timeoutMinutes = Math.max(1, nextTimeoutMinutes);
        activeOrders = Math.max(1, nextActiveOrders);
        activeCap = Math.max(activeOrders, nextActiveCap);
        dropTarget = sanitize(nextDropTarget);
        distanceLabel = sanitize(nextDistanceLabel);
        rankLabel = sanitize(nextRankLabel);
        streak = Math.max(0L, nextStreak);
        successRatePct = Math.max(0, Math.min(100, nextSuccessRatePct));
        completedOrders = Math.max(0L, nextCompletedOrders);
        totalPayoutCents = Math.max(0L, nextTotalPayoutCents);
    }

    public static void clear() {
        active = false;
        shopName = "";
        itemName = "";
        quantity = 0;
        rewardCents = 0L;
        remainingSeconds = 0L;
        timeoutMinutes = 1;
        activeOrders = 0;
        activeCap = 0;
        dropTarget = "";
        distanceLabel = "";
        rankLabel = "";
        streak = 0L;
        successRatePct = 0;
        completedOrders = 0L;
        totalPayoutCents = 0L;
        collapsed = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static String getShopName() {
        return shopName;
    }

    public static String getItemName() {
        return itemName;
    }

    public static int getQuantity() {
        return quantity;
    }

    public static long getRewardCents() {
        return rewardCents;
    }

    public static long getRemainingSeconds() {
        return remainingSeconds;
    }

    public static int getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public static int getActiveOrders() {
        return activeOrders;
    }

    public static int getActiveCap() {
        return activeCap;
    }

    public static String getDropTarget() {
        return dropTarget;
    }

    public static String getDistanceLabel() {
        return distanceLabel;
    }

    public static String getRankLabel() {
        return rankLabel;
    }

    public static long getStreak() {
        return streak;
    }

    public static int getSuccessRatePct() {
        return successRatePct;
    }

    public static long getCompletedOrders() {
        return completedOrders;
    }

    public static long getTotalPayoutCents() {
        return totalPayoutCents;
    }

    public static boolean isCollapsed() {
        return collapsed;
    }

    public static void toggleCollapsed() {
        if (!active) {
            collapsed = false;
            return;
        }
        collapsed = !collapsed;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
