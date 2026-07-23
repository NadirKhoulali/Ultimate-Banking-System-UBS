package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.Call;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.PLAYER;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.SESSION;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.TELLER;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.count;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.fixture;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxEscortRuntimeLifecycleTest {
    @Test
    void logoutAfterArrivalRetriesCleanupAndStartsRecoveryReturn() {
        assertLifecycleRecovery(SafeBoxEscortRuntime::onLogout);
    }

    @Test
    void deathAfterArrivalRetriesCleanupAndStartsRecoveryReturn() {
        assertLifecycleRecovery(SafeBoxEscortRuntime::onDeath);
    }

    @Test
    void dimensionChangeAfterArrivalRetriesCleanupAndStartsRecoveryReturn() {
        assertLifecycleRecovery(SafeBoxEscortRuntime::onDimensionChange);
    }

    @Test
    void lifecycleAndServerStopCleanupAreIdempotent() {
        var fixture = fixture();
        fixture.inspectingAt(10);

        fixture.runtime.onLogout(PLAYER);
        fixture.runtime.onLogout(PLAYER);
        fixture.runtime.stop();
        fixture.runtime.stop();

        assertTrue(fixture.runtime.activeForPlayer(PLAYER).isEmpty());
        assertFalse(fixture.runtime.busyTeller(TELLER));
        assertEquals(1, count(fixture.effects.events, "access-"));
        assertEquals(1, count(fixture.effects.events, "marker-"));
        assertEquals(1, count(fixture.effects.events, "hold-"));
    }

    @Test
    void stopAttemptsEveryCleanupEvenWhenOnePersists() {
        for (Call failed : List.of(Call.REVOKE, Call.CLEAR, Call.RELEASE)) {
            var fixture = fixture();
            fixture.inspectingAt(10);
            fixture.faults.always(failed);

            assertDoesNotThrow(fixture.runtime::stop, failed.name());

            assertTrue(fixture.faults.calls(Call.REVOKE) >= 1, failed.name());
            assertTrue(fixture.faults.calls(Call.CLEAR) >= 1, failed.name());
            assertTrue(fixture.faults.calls(Call.RELEASE) >= 1, failed.name());
        }
        for (Call failed : List.of(Call.CANCEL, Call.FORGET)) {
            var fixture = fixture();
            fixture.runtime.start(SafeBoxEscortRuntimeTestSupport.context());
            fixture.faults.always(failed);

            assertDoesNotThrow(fixture.runtime::stop, failed.name());

            assertTrue(fixture.faults.calls(Call.CANCEL) >= 1, failed.name());
            assertTrue(fixture.faults.calls(Call.FORGET) >= 1, failed.name());
        }
    }

    private static void assertLifecycleRecovery(LifecycleAction action) {
        var fixture = fixture();
        fixture.outboundArrived(10);
        fixture.faults.once(Call.RELEASE);

        assertDoesNotThrow(() -> action.apply(fixture.runtime, PLAYER));

        assertTrue(fixture.runtime.activeForPlayer(PLAYER).isEmpty());
        assertFalse(fixture.runtime.busyPlayer(PLAYER));
        assertTrue(fixture.runtime.busyTeller(TELLER));
        assertEquals(1, fixture.faults.calls(Call.START));
        fixture.runtime.tick(11);
        assertEquals(2, fixture.faults.calls(Call.START));
        fixture.navigation.arrive(SESSION);
        fixture.runtime.tick(12);
        assertFalse(fixture.runtime.busyTeller(TELLER));
    }

    @FunctionalInterface
    private interface LifecycleAction {
        void apply(SafeBoxEscortRuntime runtime, UUID playerId);
    }
}
