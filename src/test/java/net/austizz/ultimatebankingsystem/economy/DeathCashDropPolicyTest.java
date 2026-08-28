package net.austizz.ultimatebankingsystem.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeathCashDropPolicyTest {
    @Test
    void managesCashAlreadyProducedByVanillaDeathDrops() {
        assertEquals(
                DeathCashDropPolicy.Decision.MANAGE_EXISTING_DROPS,
                DeathCashDropPolicy.decide(false, true, false, true, false)
        );
    }

    @Test
    void skipsCanceledDropsUsedByCorpseAndTombstoneSystems() {
        assertEquals(
                DeathCashDropPolicy.Decision.SKIP,
                DeathCashDropPolicy.decide(true, true, false, true, true)
        );
    }

    @Test
    void treatsMissingVanillaDropsAsExternalInventoryCustody() {
        assertEquals(
                DeathCashDropPolicy.Decision.SKIP,
                DeathCashDropPolicy.decide(false, false, false, true, true)
        );
    }

    @Test
    void forcesConfiguredShareOnlyForRetainedVanillaInventory() {
        assertEquals(
                DeathCashDropPolicy.Decision.FORCE_FROM_RETAINED_INVENTORY,
                DeathCashDropPolicy.decide(false, false, true, true, true)
        );
    }

    @Test
    void respectsKeepInventoryOptOutAndEmptyInventory() {
        assertEquals(
                DeathCashDropPolicy.Decision.SKIP,
                DeathCashDropPolicy.decide(false, false, true, false, true)
        );
        assertEquals(
                DeathCashDropPolicy.Decision.SKIP,
                DeathCashDropPolicy.decide(false, false, true, true, false)
        );
    }
}
