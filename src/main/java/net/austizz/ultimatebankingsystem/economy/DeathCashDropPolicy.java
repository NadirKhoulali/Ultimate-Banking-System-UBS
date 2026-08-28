package net.austizz.ultimatebankingsystem.economy;

/**
 * Chooses whether UBS may take custody of physical cash during a player death.
 * The decision is based on the completed drop pipeline rather than specific mod IDs.
 */
final class DeathCashDropPolicy {
    enum Decision {
        MANAGE_EXISTING_DROPS,
        FORCE_FROM_RETAINED_INVENTORY,
        SKIP
    }

    private DeathCashDropPolicy() {
    }

    static Decision decide(boolean dropsCanceled,
                           boolean hasCashDrops,
                           boolean keepInventory,
                           boolean applyWithKeepInventory,
                           boolean hasRetainedCash) {
        if (dropsCanceled) {
            return Decision.SKIP;
        }
        if (hasCashDrops) {
            return Decision.MANAGE_EXISTING_DROPS;
        }
        if (!keepInventory) {
            // Vanilla would have produced cash drops. Their absence means another system took custody.
            return Decision.SKIP;
        }
        if (!applyWithKeepInventory || !hasRetainedCash) {
            return Decision.SKIP;
        }
        return Decision.FORCE_FROM_RETAINED_INVENTORY;
    }
}
