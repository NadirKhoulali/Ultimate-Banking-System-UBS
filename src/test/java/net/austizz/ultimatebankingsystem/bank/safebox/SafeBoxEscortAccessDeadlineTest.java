package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRegistry;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntime;
import org.junit.jupiter.api.Test;

import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.PLAYER;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.accessRequest;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.fixture;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.target;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxEscortAccessDeadlineTest {
    @Test
    void authorizationRejectsAtDeadlineBeforeRegistryCleanupTick() {
        var fixture = fixture();
        fixture.inspectingAt(100L);

        assertEquals(SafeBoxEscortRuntime.AccessDecision.ALLOWED,
                fixture.runtime.inspectAccess(
                        accessRequest(PLAYER, target()),
                        100L + SafeBoxEscortRegistry.INSPECTION_TIMEOUT_TICKS - 1L));
        assertEquals(SafeBoxEscortRuntime.AccessDecision.DENIED_ACTIVE_ESCORT,
                fixture.runtime.inspectAccess(
                        accessRequest(PLAYER, target()),
                        100L + SafeBoxEscortRegistry.INSPECTION_TIMEOUT_TICKS));
        assertTrue(fixture.runtime.activeForPlayer(PLAYER).isPresent(),
                "authorization must fail immediately without depending on cleanup ordering");
    }
}
