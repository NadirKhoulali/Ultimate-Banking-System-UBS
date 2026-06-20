package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.network.ShopSetupObjectivePayload;

import java.util.List;

/**
 * Client-side state for the guided "shop requirements" objective card.
 */
public final class ShopSetupObjectiveClientState {
    private static boolean active;
    private static boolean collapsed;
    private static boolean dismissed;
    private static String shopName = "";
    private static int step = 1;
    private static int totalSteps = 1;
    private static String objectiveTitle = "";
    private static String objectiveDetail = "";
    private static List<ShopSetupObjectivePayload.RequirementProgress> requirements = List.of();

    private ShopSetupObjectiveClientState() {
    }

    public static void set(boolean nextActive,
                           String nextShopName,
                           int nextStep,
                           int nextTotalSteps,
                           String nextObjectiveTitle,
                           String nextObjectiveDetail,
                           List<ShopSetupObjectivePayload.RequirementProgress> nextRequirements) {
        active = nextActive;
        if (!active) {
            clear();
            return;
        }
        shopName = sanitize(nextShopName);
        step = Math.max(1, nextStep);
        totalSteps = Math.max(step, nextTotalSteps);
        objectiveTitle = sanitize(nextObjectiveTitle);
        objectiveDetail = sanitize(nextObjectiveDetail);
        requirements = nextRequirements == null ? List.of() : List.copyOf(nextRequirements);
    }

    public static void clear() {
        active = false;
        collapsed = false;
        dismissed = false;
        shopName = "";
        step = 1;
        totalSteps = 1;
        objectiveTitle = "";
        objectiveDetail = "";
        requirements = List.of();
    }

    public static boolean isActive() {
        return active && !dismissed;
    }

    public static boolean isCollapsed() {
        return collapsed;
    }

    public static void toggleCollapsed() {
        if (!active) {
            return;
        }
        collapsed = !collapsed;
    }

    public static void dismiss() {
        if (!active) {
            return;
        }
        dismissed = true;
        collapsed = false;
    }

    public static String getShopName() {
        return shopName;
    }

    public static int getStep() {
        return step;
    }

    public static int getTotalSteps() {
        return totalSteps;
    }

    public static String getObjectiveTitle() {
        return objectiveTitle;
    }

    public static String getObjectiveDetail() {
        return objectiveDetail;
    }

    public static List<ShopSetupObjectivePayload.RequirementProgress> getRequirements() {
        return requirements;
    }

    private static String sanitize(String text) {
        return text == null ? "" : text.trim();
    }
}
