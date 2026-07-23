package net.austizz.ultimatebankingsystem.npc;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankTellerUseLeaseTest {
    @Test
    void allowsOnlyOneCustomerUntilReleased() {
        BankTellerUseLease lease = new BankTellerUseLease(200L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(lease.acquire(first, 10L));
        assertFalse(lease.acquire(second, 11L));
        assertEquals(first, lease.holder(11L));
        assertTrue(lease.release(first));
        assertTrue(lease.acquire(second, 12L));
        assertEquals(second, lease.holder(12L));
    }

    @Test
    void keepaliveExtendsLeaseAndStaleLeaseExpires() {
        BankTellerUseLease lease = new BankTellerUseLease(20L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(lease.acquire(first, 100L));
        assertTrue(lease.refresh(first, 110L));
        assertEquals(first, lease.holder(129L));
        assertNull(lease.holder(130L));
        assertTrue(lease.acquire(second, 130L));
    }

    @Test
    void anotherCustomerCannotRefreshOrReleaseLease() {
        BankTellerUseLease lease = new BankTellerUseLease(20L);
        UUID holder = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        assertTrue(lease.acquire(holder, 0L));
        assertFalse(lease.refresh(other, 1L));
        assertFalse(lease.release(other));
        assertEquals(holder, lease.holder(1L));
    }
}
