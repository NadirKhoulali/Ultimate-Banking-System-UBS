package net.austizz.ultimatebankingsystem.npc.escort;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteFace;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePosition;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteStep;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TellerEscortNavigationLeaseTest {
    private static final UUID SESSION = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TELLER = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final SafeTellerRoutePosition TARGET = new SafeTellerRoutePosition(4, 64, 7);

    @Test
    void movementLeaseIsReleasedWhenRouteArrives() {
        TellerEscortTestActor actor = new TellerEscortTestActor();
        TellerEscortNavigationCoordinator coordinator = new TellerEscortNavigationCoordinator();
        assertEquals(TellerEscortStartResult.Status.STARTED,
                coordinator.start(SESSION, TELLER, route(new SafeTellerRouteStep.Wait(1)), actor).status());
        assertTrue(actor.leased());

        coordinator.tick();

        assertEquals(TellerEscortNavigationState.Status.ARRIVED, state(coordinator).status());
        assertFalse(actor.leased());
        assertFalse(actor.navigationActive());
        assertFalse(actor.redstoneActive());
        assertEquals(1, actor.releaseCount());
    }

    @Test
    void walkWaitAndRedstoneExecuteStrictlyInOrderForExactTicks() {
        TellerEscortTestActor actor = new TellerEscortTestActor();
        TellerEscortNavigationCoordinator coordinator = new TellerEscortNavigationCoordinator();
        SafeTellerRouteStep.Redstone redstone =
                new SafeTellerRouteStep.Redstone(TARGET, SafeTellerRouteFace.NORTH, 9, 2);
        assertEquals(TellerEscortStartResult.Status.STARTED, coordinator.start(SESSION, TELLER, route(
                new SafeTellerRouteStep.Walk(TARGET),
                new SafeTellerRouteStep.Wait(2),
                redstone), actor).status());

        coordinator.tick();
        assertState(coordinator, TellerEscortNavigationState.Status.RUNNING, 0);
        assertEquals(1, actor.moveCount());
        assertEquals(TARGET, actor.lastMoveTarget());
        assertEquals(TellerRouteExecution.DEFAULT_LIMITS.movementSpeed(), actor.lastMoveSpeed());
        assertEquals(0, actor.redstoneStartCount());

        actor.arriveAt(TARGET);
        coordinator.tick();
        assertState(coordinator, TellerEscortNavigationState.Status.RUNNING, 1);
        assertEquals(1, actor.moveCount());
        assertEquals(0, actor.redstoneStartCount());

        coordinator.tick();
        assertState(coordinator, TellerEscortNavigationState.Status.RUNNING, 1);
        assertEquals(0, actor.redstoneStartCount());

        coordinator.tick();
        assertState(coordinator, TellerEscortNavigationState.Status.RUNNING, 2);
        assertEquals(0, actor.redstoneStartCount());

        coordinator.tick();
        assertState(coordinator, TellerEscortNavigationState.Status.RUNNING, 2);
        assertEquals(1, actor.redstoneStartCount());
        assertEquals(redstone, actor.lastRedstoneStep());
        assertTrue(actor.redstoneActive());

        coordinator.tick();
        assertState(coordinator, TellerEscortNavigationState.Status.RUNNING, 2);
        assertEquals(1, actor.redstoneStartCount());
        assertTrue(actor.redstoneActive());

        coordinator.tick();

        assertState(coordinator, TellerEscortNavigationState.Status.ARRIVED, 3);
        assertEquals(1, actor.moveCount());
        assertEquals(1, actor.redstoneStartCount());
        assertEquals(1, actor.redstoneClearCount());
        assertEquals(1, actor.releaseCount());
        assertFalse(actor.navigationActive());
        assertFalse(actor.redstoneActive());
        assertFalse(actor.leased());
    }

    @Test
    void missingStepFailsInternalInsteadOfStallingOrArriving() {
        TellerEscortTestActor actor = new TellerEscortTestActor();
        SafeTellerRoute malformed = SafeTellerRoute.create("bank", "vault", TELLER.toString(),
                SafeTellerRouteDirection.OUTBOUND, "minecraft:overworld",
                new SafeTellerRoutePosition(0, 64, 0), TARGET, Collections.singletonList(null));
        TellerRouteExecution execution = new TellerRouteExecution(
                SESSION, TELLER, malformed, actor, TellerRouteExecution.DEFAULT_LIMITS);
        assertTrue(execution.start());

        execution.tick();

        assertEquals(TellerEscortNavigationState.Status.FAILED, execution.snapshot().status());
        assertEquals(TellerEscortNavigationState.FailureReason.INTERNAL_ERROR,
                execution.snapshot().failureReason());
        assertTrue(execution.terminal());
        assertFalse(actor.leased());
        assertEquals(0, actor.moveCount());
        assertEquals(0, actor.redstoneStartCount());
    }

    @Test
    void secondStartForSameTellerIsBusyWithoutQueue() {
        TellerEscortNavigationCoordinator coordinator = new TellerEscortNavigationCoordinator();
        TellerEscortTestActor first = new TellerEscortTestActor();
        TellerEscortTestActor second = new TellerEscortTestActor();
        coordinator.start(SESSION, TELLER, route(new SafeTellerRouteStep.Wait(10)), first);

        TellerEscortStartResult result = coordinator.start(UUID.randomUUID(), TELLER,
                route(new SafeTellerRouteStep.Wait(1)), second);

        assertEquals(TellerEscortStartResult.Status.BUSY, result.status());
        assertEquals(1, coordinator.activeCount());
        assertFalse(second.leased());
    }

    @Test
    void stalledWalkFailsWithDeterministicReasonAndRestoresLease() {
        TellerEscortTestActor actor = new TellerEscortTestActor();
        TellerEscortNavigationCoordinator coordinator = new TellerEscortNavigationCoordinator(
                new TellerRouteExecution.Limits(0.75D, 0.35D, 100, 2));
        coordinator.start(SESSION, TELLER, route(new SafeTellerRouteStep.Walk(TARGET)), actor);

        coordinator.tick();
        coordinator.tick();
        coordinator.tick();

        assertEquals(TellerEscortNavigationState.Status.FAILED, state(coordinator).status());
        assertEquals(TellerEscortNavigationState.FailureReason.WALK_STALLED,
                state(coordinator).failureReason());
        assertFalse(actor.leased());
        assertFalse(actor.navigationActive());
    }

    @Test
    void cancelBySessionOrTellerClearsNavigationRelayAndLease() {
        TellerEscortTestActor sessionActor = activeRedstone(
                new TellerEscortNavigationCoordinator(), SESSION, TELLER, SafeTellerRouteFace.NORTH);
        TellerEscortNavigationCoordinator bySession = sessionActor.coordinator();
        assertTrue(bySession.cancelSession(SESSION));
        assertCleanup(bySession, SESSION, sessionActor);

        UUID otherSession = UUID.randomUUID();
        UUID otherTeller = UUID.randomUUID();
        TellerEscortNavigationCoordinator byTeller = new TellerEscortNavigationCoordinator();
        TellerEscortTestActor tellerActor = activeRedstone(
                byTeller, otherSession, otherTeller, SafeTellerRouteFace.SOUTH);
        assertTrue(byTeller.cancelTeller(otherTeller));
        assertCleanup(byTeller, otherSession, tellerActor);
    }

    @Test
    void timeoutAndRelayFailureAlwaysRestoreTeller() {
        TellerEscortTestActor timeoutActor = new TellerEscortTestActor();
        TellerEscortNavigationCoordinator timeout = new TellerEscortNavigationCoordinator(
                new TellerRouteExecution.Limits(0.75D, 0.35D, 2, 100));
        timeout.start(SESSION, TELLER, route(new SafeTellerRouteStep.Walk(TARGET)), timeoutActor);
        timeout.tick();
        timeoutActor.moveCloser(0.1D);
        timeout.tick();
        assertEquals(TellerEscortNavigationState.FailureReason.WALK_TIMEOUT,
                state(timeout).failureReason());
        assertFalse(timeoutActor.leased());

        TellerEscortTestActor relayActor = new TellerEscortTestActor();
        relayActor.failRedstoneWith(TellerEscortNavigationState.FailureReason.RELAY_POSITION_OCCUPIED);
        TellerEscortNavigationCoordinator relay = new TellerEscortNavigationCoordinator();
        relay.start(SESSION, TELLER, route(new SafeTellerRouteStep.Redstone(
                TARGET, SafeTellerRouteFace.EAST, 15, 4)), relayActor);
        relay.tick();
        assertEquals(TellerEscortNavigationState.FailureReason.RELAY_POSITION_OCCUPIED,
                state(relay).failureReason());
        assertFalse(relayActor.leased());
    }

    private static TellerEscortTestActor activeRedstone(TellerEscortNavigationCoordinator coordinator,
                                                         UUID sessionId,
                                                         UUID tellerId,
                                                         SafeTellerRouteFace face) {
        TellerEscortTestActor actor = new TellerEscortTestActor();
        actor.attach(coordinator);
        coordinator.start(sessionId, tellerId, route(tellerId, new SafeTellerRouteStep.Redstone(
                TARGET, face, 9, 20)), actor);
        coordinator.tick();
        assertTrue(actor.redstoneActive());
        return actor;
    }

    private static void assertCleanup(TellerEscortNavigationCoordinator coordinator,
                                      UUID sessionId,
                                      TellerEscortTestActor actor) {
        TellerEscortNavigationState state = coordinator.status(sessionId).orElseThrow();
        assertEquals(TellerEscortNavigationState.Status.CANCELLED, state.status());
        assertFalse(actor.navigationActive());
        assertFalse(actor.redstoneActive());
        assertFalse(actor.leased());
    }

    private static TellerEscortNavigationState state(TellerEscortNavigationCoordinator coordinator) {
        return coordinator.status(SESSION).orElseThrow();
    }

    private static void assertState(TellerEscortNavigationCoordinator coordinator,
                                    TellerEscortNavigationState.Status status,
                                    int stepIndex) {
        TellerEscortNavigationState state = state(coordinator);
        assertEquals(status, state.status());
        assertEquals(stepIndex, state.stepIndex());
    }

    private static SafeTellerRoute route(SafeTellerRouteStep... steps) {
        return route(TELLER, steps);
    }

    private static SafeTellerRoute route(UUID tellerId, SafeTellerRouteStep... steps) {
        return SafeTellerRoute.create("bank", "vault", tellerId.toString(),
                SafeTellerRouteDirection.OUTBOUND, "minecraft:overworld",
                new SafeTellerRoutePosition(0, 64, 0), TARGET, List.of(steps));
    }
}
