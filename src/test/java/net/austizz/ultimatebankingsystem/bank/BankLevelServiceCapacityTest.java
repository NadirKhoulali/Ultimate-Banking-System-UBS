package net.austizz.ultimatebankingsystem.bank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankLevelServiceCapacityTest {

    @Test
    void tellerCapacityKeepsHistoricalBaselineAtLevelOne() {
        assertEquals(5, BankLevelService.bankTellerCapacityForLevel(1));
    }

    @Test
    void tellerCapacityGrowsByOneEveryLevel() {
        for (int level = 1; level < BankLevelService.MAX_LEVEL; level++) {
            assertEquals(BankLevelService.bankTellerCapacityForLevel(level) + 1,
                    BankLevelService.bankTellerCapacityForLevel(level + 1),
                    "capacity must increase by exactly 1 from level " + level);
        }
    }

    @Test
    void tellerCapacityClampsOutOfRangeLevels() {
        assertEquals(BankLevelService.bankTellerCapacityForLevel(1),
                BankLevelService.bankTellerCapacityForLevel(0));
        assertEquals(BankLevelService.bankTellerCapacityForLevel(1),
                BankLevelService.bankTellerCapacityForLevel(-25));
        assertEquals(BankLevelService.bankTellerCapacityForLevel(BankLevelService.MAX_LEVEL),
                BankLevelService.bankTellerCapacityForLevel(BankLevelService.MAX_LEVEL + 50));
    }

    @Test
    void tellerCapacityAtMaxLevelMatchesFormula() {
        assertEquals(5 + (BankLevelService.MAX_LEVEL - 1),
                BankLevelService.bankTellerCapacityForLevel(BankLevelService.MAX_LEVEL));
    }

    @Test
    void safeRowCapacityStillGrowsPerLevel() {
        assertEquals(16, BankLevelService.safeRowCapacityForLevel(1));
        assertEquals(24, BankLevelService.safeRowCapacityForLevel(2));
        assertTrue(BankLevelService.safeRowCapacityForLevel(3)
                > BankLevelService.safeRowCapacityForLevel(2));
    }
}
