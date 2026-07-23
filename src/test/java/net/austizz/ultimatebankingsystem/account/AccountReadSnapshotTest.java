package net.austizz.ultimatebankingsystem.account;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountReadSnapshotTest {
    @Test
    void staleDailyAndTemporaryValuesAreProjectedWithoutChangingRawState() {
        AccountReadSnapshot.Raw raw = new AccountReadSnapshot.Raw(
                new BigDecimal("500"), 100L, new BigDecimal("125"), 5_000L,
                new BigDecimal("250"), 99L, 20_000L,
                true, 99L);

        AccountReadSnapshot snapshot = AccountReadSnapshot.capture(
                raw, 100L, 10_000L, 101L, 20_000L);

        assertEquals(BigDecimal.ZERO, snapshot.dailyUsed());
        assertEquals(new BigDecimal("500"), snapshot.dailyRemaining());
        assertNull(snapshot.temporaryLimit());
        assertEquals(-1L, snapshot.temporaryExpiresAtEpochMillis());
        assertFalse(snapshot.certificateLocked());
        assertEquals(new BigDecimal("125"), raw.dailyUsed());
        assertEquals(new BigDecimal("250"), raw.temporaryLimit());
        assertTrue(raw.certificateLocked());
    }

    @Test
    void activeValuesAreReturnedAsAnImmutableProjection() {
        AccountReadSnapshot.Raw raw = new AccountReadSnapshot.Raw(
                new BigDecimal("500"), 101L, new BigDecimal("125"), 20_000L,
                new BigDecimal("250"), 200L, 20_000L,
                true, 200L);

        AccountReadSnapshot snapshot = AccountReadSnapshot.capture(
                raw, 100L, 10_000L, 101L, 20_000L);

        assertEquals(new BigDecimal("125"), snapshot.dailyUsed());
        assertEquals(new BigDecimal("375"), snapshot.dailyRemaining());
        assertEquals(new BigDecimal("250"), snapshot.temporaryLimit());
        assertEquals(20_000L, snapshot.temporaryExpiresAtEpochMillis());
        assertTrue(snapshot.certificateLocked());
    }
}
