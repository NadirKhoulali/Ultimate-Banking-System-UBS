package net.austizz.ultimatebankingsystem.migration.numismatics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumismaticsConversionMathTest {
    @Test
    void usesOfficialSpurRatios() {
        assertEquals(1, NumismaticsCoin.SPUR.spurs());
        assertEquals(8, NumismaticsCoin.BEVEL.spurs());
        assertEquals(16, NumismaticsCoin.SPROCKET.spurs());
        assertEquals(64, NumismaticsCoin.COG.spurs());
        assertEquals(512, NumismaticsCoin.CROWN.spurs());
        assertEquals(4096, NumismaticsCoin.SUN.spurs());
        assertEquals(409_600L, NumismaticsCoin.SUN.valueCents(100, 1));
    }

    @Test
    void compactCashPlanAlwaysReconcilesExactly() {
        for (long cents : List.of(1L, 99L, 100L, 12_345L, 1_000_000L, 9_876_543L)) {
            assertEquals(cents, NumismaticsCashPlan.totalCents(NumismaticsCashPlan.plan(cents)));
        }
    }

    @Test
    void optionsRequireWholePositiveCents() {
        assertThrows(IllegalArgumentException.class, () -> new NumismaticsMigrationOptions(
                0, NumismaticsMigrationOptions.Scope.FULL_ECONOMY, true, false));
        assertEquals(100, NumismaticsMigrationOptions.defaults().centsPerSpur());
        assertEquals(NumismaticsMigrationOptions.Scope.FULL_ECONOMY,
                NumismaticsMigrationOptions.defaults().scope());
    }
}
