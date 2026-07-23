package net.austizz.ultimatebankingsystem.bank.owner;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OwnerPcOperationalBankProjectionTest {
    private static final BigDecimal SUFFICIENT_RESERVE = new BigDecimal("1000.00");
    private static final BigDecimal LOW_RESERVE = BigDecimal.ZERO;
    private static final BigDecimal DEPOSITS = new BigDecimal("900.00");
    private static final long NOW = 72_000L;
    private static final long GRACE = 1_200L;

    @Test
    void expiredLockdownProjectsActiveWithoutChangingStaleMetadata() {
        OwnerPcOperationalBankProjection.State stale = new OwnerPcOperationalBankProjection.State(
                "LOCKDOWN", NOW - 1L, null, NOW / 24_000L, "0", 0);

        OwnerPcOperationalBankProjection.Projection projection = project(
                stale, SUFFICIENT_RESERVE);

        assertEquals("LOCKDOWN", projection.previousStatus());
        assertEquals("ACTIVE", projection.status());
        assertEquals("ACTIVE", projection.state().status());
        assertEquals("LOCKDOWN", stale.status());
    }

    @Test
    void newReserveBreachProjectsWarningAndStartsGraceOnlyInCopy() {
        OwnerPcOperationalBankProjection.State stale = baseline();

        OwnerPcOperationalBankProjection.Projection projection = project(stale, LOW_RESERVE);

        assertEquals("WARNING", projection.status());
        assertEquals(NOW, projection.state().reserveBreachStartTick());
        assertNull(stale.reserveBreachStartTick());
    }

    @Test
    void elapsedReserveGraceRestrictsAndRecoveredReserveProjectsActive() {
        OwnerPcOperationalBankProjection.State breached = new OwnerPcOperationalBankProjection.State(
                "WARNING", null, NOW - GRACE, NOW / 24_000L, "0", 0);

        OwnerPcOperationalBankProjection.Projection restricted = project(breached, LOW_RESERVE);
        OwnerPcOperationalBankProjection.Projection recovered = project(
                restricted.state(), SUFFICIENT_RESERVE);

        assertEquals("RESTRICTED", restricted.status());
        assertEquals("ACTIVE", recovered.status());
        assertNull(recovered.state().reserveBreachStartTick());
        assertEquals("WARNING", breached.status());
        assertEquals(NOW - GRACE, breached.reserveBreachStartTick());
    }

    @Test
    void staleDailyWindowProjectsZeroUsageAndQueueWithoutChangingMetadata() {
        OwnerPcOperationalBankProjection.State stale = new OwnerPcOperationalBankProjection.State(
                "ACTIVE", null, null, NOW / 24_000L - 1L, "75.00", 3);

        OwnerPcOperationalBankProjection.Projection projection = project(
                stale, SUFFICIENT_RESERVE);

        assertEquals(NOW / 24_000L, projection.state().dailyWindowDay());
        assertEquals("0", projection.state().dailyWithdrawn());
        assertEquals(0, projection.state().queuedWithdrawalCount());
        assertEquals("75.00", stale.dailyWithdrawn());
    }

    private static OwnerPcOperationalBankProjection.Projection project(
            OwnerPcOperationalBankProjection.State metadata,
            BigDecimal reserve) {
        return OwnerPcOperationalBankProjection.project(
                metadata, reserve, DEPOSITS, NOW, 0.10D, GRACE);
    }

    private static OwnerPcOperationalBankProjection.State baseline() {
        return new OwnerPcOperationalBankProjection.State(
                "ACTIVE", null, null, NOW / 24_000L, "0", 0);
    }
}
