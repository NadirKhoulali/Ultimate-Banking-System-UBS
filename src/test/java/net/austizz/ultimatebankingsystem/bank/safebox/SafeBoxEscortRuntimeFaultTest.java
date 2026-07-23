package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.EscortBlockPosition;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.Call;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.PLAYER;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.SESSION;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.TELLER;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.fixture;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxEscortRuntimeFaultTest {
    private static final List<Call> CLEANUP_EFFECTS = List.of(
            Call.REVOKE, Call.CLEAR, Call.RELEASE, Call.EJECT);

    @Test
    void transientCleanupFaultsDoNotSkipPeersAndRetryBeforeReturn() {
        var fixture = fixture();
        fixture.inspectingAt(100);
        CLEANUP_EFFECTS.forEach(fixture.faults::once);

        assertDoesNotThrow(() -> fixture.runtime.tick(6_100));

        assertFalse(fixture.runtime.busyPlayer(PLAYER));
        assertTrue(fixture.runtime.busyTeller(TELLER));
        CLEANUP_EFFECTS.forEach(call -> assertEquals(1, fixture.faults.calls(call), call.name()));
        assertEquals(1, fixture.faults.calls(Call.START));

        fixture.runtime.tick(6_101);
        CLEANUP_EFFECTS.forEach(call -> assertEquals(2, fixture.faults.calls(call), call.name()));
        assertEquals(2, fixture.faults.calls(Call.START));
        fixture.navigation.arrive(SESSION);
        fixture.runtime.tick(6_102);
        assertFalse(fixture.runtime.busyTeller(TELLER));
    }

    @Test
    void eachPersistentEffectFaultRetainsExplicitTellerOwnershipAndRetries() {
        for (Call failed : CLEANUP_EFFECTS) {
            var fixture = fixture();
            fixture.inspectingAt(100);
            fixture.faults.always(failed);

            assertDoesNotThrow(() -> fixture.runtime.tick(6_100), failed.name());

            assertFalse(fixture.runtime.busyPlayer(PLAYER), failed.name());
            assertTrue(fixture.runtime.busyTeller(TELLER), failed.name());
            assertEquals(SafeBoxEscortRuntime.RecoveryState.RETURN_PREPARING,
                    fixture.runtime.recoveryState(SESSION).orElseThrow(), failed.name());
            CLEANUP_EFFECTS.forEach(call -> assertTrue(
                    fixture.faults.calls(call) >= 1, failed + " skipped " + call));
            fixture.runtime.tick(6_101);
            assertTrue(fixture.faults.calls(failed) >= 2, failed.name());
            assertTrue(fixture.runtime.busyTeller(TELLER), failed.name());
        }
    }

    @Test
    void navigationTickAndStateExceptionsDoNotBlockLaterArrival() {
        var fixture = fixture();
        assertEquals(SafeBoxEscortRuntime.StartStatus.STARTED,
                fixture.runtime.start(SafeBoxEscortRuntimeTestSupport.context()).status());
        fixture.faults.once(Call.TICK);
        fixture.faults.once(Call.STATE);

        assertDoesNotThrow(() -> fixture.runtime.tick(10));
        fixture.navigation.arrive(SESSION);
        fixture.runtime.tick(11);

        assertEquals(List.of("hold+"), fixture.effects.events);
    }

    @Test
    void arrivalForgetExceptionRetainsTrackedDoorHoldAndCleanupOwnership() {
        var fixture = fixture();
        fixture.runtime.start(SafeBoxEscortRuntimeTestSupport.context());
        fixture.navigation.arrive(SESSION);
        fixture.faults.once(Call.FORGET);

        assertDoesNotThrow(() -> fixture.runtime.tick(10));
        assertEquals(1, fixture.faults.calls(Call.ACQUIRE));
        fixture.runtime.onLogout(PLAYER);
        fixture.runtime.tick(11);

        assertFalse(fixture.runtime.busyPlayer(PLAYER));
        assertTrue(fixture.runtime.busyTeller(TELLER));
        assertEquals(SafeBoxEscortRuntime.RecoveryState.RETURN_PREPARING,
                fixture.runtime.recoveryState(SESSION).orElseThrow());
        assertTrue(fixture.faults.calls(Call.FORGET) >= 3);
        assertEquals(1, fixture.faults.calls(Call.RELEASE));
    }

    @Test
    void transientFalseCancelRetainsCleanupUntilAcknowledged() {
        var fixture = fixture();
        fixture.runtime.start(SafeBoxEscortRuntimeTestSupport.context());
        fixture.faults.refuseOnce(Call.CANCEL);

        assertDoesNotThrow(() -> fixture.runtime.onLogout(PLAYER));

        assertFalse(fixture.runtime.busyPlayer(PLAYER));
        assertTrue(fixture.runtime.busyTeller(TELLER));
        assertEquals(SafeBoxEscortRuntime.RecoveryState.CLEANING,
                fixture.runtime.recoveryState(SESSION).orElseThrow());
        assertEquals(1, fixture.faults.calls(Call.CANCEL));
        assertEquals(1, fixture.faults.calls(Call.FORGET));
        assertEquals(1, fixture.faults.calls(Call.START));
        fixture.runtime.tick(11);
        assertFalse(fixture.runtime.busyTeller(TELLER));
        assertEquals(2, fixture.faults.calls(Call.CANCEL));
        assertEquals(2, fixture.faults.calls(Call.FORGET));
    }

    @Test
    void transientFalseReturnForgetRetainsCleaningUntilAcknowledged() {
        var fixture = fixture();
        fixture.waitingAt(100);
        fixture.runtime.onPlayerPosition(PLAYER, "minecraft:overworld",
                new EscortBlockPosition(30, 64, 30));
        fixture.navigation.arrive(SESSION);
        fixture.faults.refuseOnce(Call.FORGET);

        assertDoesNotThrow(() -> fixture.runtime.tick(102));
        assertTrue(fixture.runtime.busyTeller(TELLER));
        assertEquals(SafeBoxEscortRuntime.RecoveryState.CLEANING,
                fixture.runtime.recoveryState(SESSION).orElseThrow());
        fixture.runtime.tick(103);

        assertFalse(fixture.runtime.busyTeller(TELLER));
    }

    @Test
    void persistentNavigationStateFailureKeepsTypedOwnershipAndCanRecover() {
        var fixture = fixture();
        fixture.runtime.start(SafeBoxEscortRuntimeTestSupport.context());
        fixture.faults.always(Call.STATE);

        assertDoesNotThrow(() -> fixture.runtime.tick(10));
        assertDoesNotThrow(() -> fixture.runtime.tick(11));

        assertTrue(fixture.runtime.busyTeller(TELLER));
        assertEquals(SafeBoxEscortRuntime.RecoveryState.OUTBOUND,
                fixture.runtime.recoveryState(SESSION).orElseThrow());
        assertTrue(fixture.faults.calls(Call.STATE) >= 2);
        fixture.faults.clear(Call.STATE);
        fixture.navigation.arrive(SESSION);
        fixture.runtime.tick(12);
        assertEquals(SafeBoxEscortRuntime.RecoveryState.AT_VAULT,
                fixture.runtime.recoveryState(SESSION).orElseThrow());
    }

    @Test
    void persistentReturnStateFailureCannotBeMistakenForMissingOrCleanedEarly() {
        var fixture = fixture();
        fixture.waitingAt(10);
        fixture.runtime.onPlayerPosition(PLAYER, "minecraft:overworld",
                new EscortBlockPosition(30, 64, 30));
        fixture.navigation.arrive(SESSION);
        fixture.faults.always(Call.STATE);

        fixture.runtime.tick(12);

        assertTrue(fixture.runtime.busyTeller(TELLER));
        assertEquals(SafeBoxEscortRuntime.RecoveryState.RETURNING,
                fixture.runtime.recoveryState(SESSION).orElseThrow());
        fixture.faults.clear(Call.STATE);
        fixture.runtime.tick(13);
        assertFalse(fixture.runtime.busyTeller(TELLER));
    }

    @Test
    void persistentFalseCancelRetainsCleanupUntilTheFaultClears() {
        var fixture = fixture();
        fixture.runtime.start(SafeBoxEscortRuntimeTestSupport.context());
        fixture.faults.refuseAlways(Call.CANCEL);

        fixture.runtime.onLogout(PLAYER);
        fixture.runtime.tick(11);

        assertFalse(fixture.runtime.busyPlayer(PLAYER));
        assertTrue(fixture.runtime.busyTeller(TELLER));
        assertEquals(SafeBoxEscortRuntime.RecoveryState.CLEANING,
                fixture.runtime.recoveryState(SESSION).orElseThrow());
        assertTrue(fixture.faults.calls(Call.CANCEL) >= 2);
        fixture.faults.clear(Call.CANCEL);
        fixture.runtime.tick(12);
        assertFalse(fixture.runtime.busyTeller(TELLER));
    }

    @Test
    void persistentFalseForgetRetainsReturnPreparationUntilTheFaultClears() {
        var fixture = fixture();
        fixture.runtime.start(SafeBoxEscortRuntimeTestSupport.context());
        fixture.navigation.arrive(SESSION);
        fixture.faults.refuseAlways(Call.FORGET);
        fixture.runtime.tick(10);

        fixture.runtime.onLogout(PLAYER);
        fixture.runtime.tick(11);

        assertFalse(fixture.runtime.busyPlayer(PLAYER));
        assertTrue(fixture.runtime.busyTeller(TELLER));
        assertEquals(SafeBoxEscortRuntime.RecoveryState.RETURN_PREPARING,
                fixture.runtime.recoveryState(SESSION).orElseThrow());
        assertTrue(fixture.faults.calls(Call.FORGET) >= 3);
        assertEquals(1, fixture.faults.calls(Call.START));
        fixture.faults.clear(Call.FORGET);
        fixture.runtime.tick(12);
        assertEquals(2, fixture.faults.calls(Call.START));
    }

    @Test
    void persistentNavigationTickFailureDoesNotSkipSessionStateProgress() {
        var fixture = fixture();
        fixture.runtime.start(SafeBoxEscortRuntimeTestSupport.context());
        fixture.navigation.arrive(SESSION);
        fixture.faults.always(Call.TICK);

        assertDoesNotThrow(() -> fixture.runtime.tick(10));

        assertEquals(SafeBoxEscortRuntime.RecoveryState.AT_VAULT,
                fixture.runtime.recoveryState(SESSION).orElseThrow());
        assertEquals(1, fixture.faults.calls(Call.ACQUIRE));
    }
}
