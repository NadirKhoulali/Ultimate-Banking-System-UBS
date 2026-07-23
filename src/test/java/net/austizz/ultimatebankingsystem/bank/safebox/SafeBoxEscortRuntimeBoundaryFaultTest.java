package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.EscortBlockPosition;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.Call;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.PLAYER;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.SESSION;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.TELLER;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.accessRequest;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.fixture;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxEscortRuntimeBoundaryFaultTest {
    @Test
    void initialStartExceptionAfterMutationCannotLeaveRegistryOnlyBusy() {
        var fixture = fixture();
        fixture.faults.once(Call.START);

        SafeBoxEscortRuntime.StartResult result = assertDoesNotThrow(
                () -> fixture.runtime.start(SafeBoxEscortRuntimeTestSupport.context()));

        assertEquals(SafeBoxEscortRuntime.StartStatus.TELLER_UNAVAILABLE, result.status());
        assertTrue(fixture.runtime.activeForPlayer(PLAYER).isEmpty());
        assertFalse(fixture.runtime.busyPlayer(PLAYER));
        assertFalse(fixture.runtime.busyTeller(TELLER));
    }

    @Test
    void thrownStartWithMissingStateReconcilesToFinalRemoval() {
        var fixture = fixture();
        fixture.navigation.mutateBeforeStartFault = false;
        fixture.faults.once(Call.START);

        SafeBoxEscortRuntime.StartResult result = assertDoesNotThrow(
                () -> fixture.runtime.start(SafeBoxEscortRuntimeTestSupport.context()));

        assertEquals(SafeBoxEscortRuntime.StartStatus.TELLER_UNAVAILABLE, result.status());
        assertFalse(fixture.runtime.busyPlayer(PLAYER));
        assertFalse(fixture.runtime.busyTeller(TELLER));
        assertTrue(fixture.runtime.recoveryState(SESSION).isEmpty());
        assertEquals(1, fixture.faults.calls(Call.CANCEL));
        assertEquals(1, fixture.faults.calls(Call.STATE));
        assertEquals(0, fixture.faults.calls(Call.FORGET));
    }

    @Test
    void doorAcquireExceptionIsTrackedReleasedAndRecovered() {
        var fixture = fixture();
        fixture.runtime.start(SafeBoxEscortRuntimeTestSupport.context());
        fixture.navigation.arrive(SESSION);
        fixture.faults.once(Call.ACQUIRE);

        assertDoesNotThrow(() -> fixture.runtime.tick(10));

        assertFalse(fixture.runtime.busyPlayer(PLAYER));
        assertTrue(fixture.runtime.busyTeller(TELLER));
        assertEquals(1, fixture.faults.calls(Call.RELEASE));
        assertEquals(2, fixture.faults.calls(Call.START));
        fixture.navigation.arrive(SESSION);
        fixture.runtime.tick(11);
        assertFalse(fixture.runtime.busyTeller(TELLER));
    }

    @Test
    void refusedDoorAcquireStartsRecoveryWithoutPhantomRelease() {
        var fixture = fixture();
        fixture.effects.holdResult = false;
        fixture.runtime.start(SafeBoxEscortRuntimeTestSupport.context());
        fixture.navigation.arrive(SESSION);

        fixture.runtime.tick(10);

        assertFalse(fixture.runtime.busyPlayer(PLAYER));
        assertTrue(fixture.runtime.busyTeller(TELLER));
        assertEquals(0, fixture.faults.calls(Call.RELEASE));
        assertEquals(2, fixture.faults.calls(Call.START));
    }

    @Test
    void persistentReturnStartFailureCompensatesUnknownNavigationMutation() {
        var fixture = fixture();
        fixture.waitingAt(10);
        fixture.faults.always(Call.START);

        assertDoesNotThrow(() -> fixture.runtime.onPlayerPosition(PLAYER, "minecraft:overworld",
                new EscortBlockPosition(30, 64, 30)));

        assertFalse(fixture.runtime.busyPlayer(PLAYER));
        assertFalse(fixture.runtime.busyTeller(TELLER));
        assertTrue(fixture.faults.calls(Call.CANCEL) >= 1);
        assertTrue(fixture.faults.calls(Call.FORGET) >= 1);
    }

    @Test
    void transactionalGrantFailureWithCleanupFaultNeverExposesAccess() {
        for (List<Call> pair : List.of(
                List.of(Call.GRANT, Call.REVOKE), List.of(Call.SHOW, Call.CLEAR))) {
            var fixture = fixture();
            fixture.outboundArrived(10);
            fixture.faults.once(pair.get(0));
            fixture.faults.always(pair.get(1));

            assertEquals(SafeBoxEscortRuntime.InteractionStatus.NOT_FOUND,
                    fixture.runtime.handleTellerInteraction(PLAYER, TELLER, 11));

            assertFalse(fixture.runtime.busyPlayer(PLAYER));
            assertEquals(SafeBoxEscortRuntime.AccessDecision.NO_ACTIVE_ESCORT,
                    fixture.runtime.inspectAccess(
                            accessRequest(PLAYER, SafeBoxEscortRuntimeTestSupport.target()), 11));
            assertTrue(fixture.runtime.busyTeller(TELLER));
            fixture.runtime.tick(12);
            assertTrue(fixture.faults.calls(pair.get(1)) >= 2);
        }
    }
}
