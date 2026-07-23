package net.austizz.ultimatebankingsystem.npc.escort;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteFace;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePosition;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TellerEscortNavigationFailureTest {
    private static final SafeTellerRoutePosition TARGET = new SafeTellerRoutePosition(4, 64, 7);

    @Test
    void rfidStepActivatesOnceAndIsRevokedDuringTerminalCleanup() {
        UUID sessionId = UUID.randomUUID();
        UUID tellerId = UUID.randomUUID();
        TellerEscortTestActor actor = new TellerEscortTestActor();
        TellerEscortNavigationCoordinator coordinator = new TellerEscortNavigationCoordinator();

        coordinator.start(sessionId, tellerId,
                route(tellerId, new SafeTellerRouteStep.Rfid(TARGET)), actor);
        coordinator.tick();

        assertEquals(1, actor.rfidActivationCount());
        assertEquals(1, actor.rfidClearCount());
        assertEquals(TellerEscortNavigationState.Status.ARRIVED,
                coordinator.status(sessionId).orElseThrow().status());
        assertEquals(0, coordinator.activeCount());
    }

    @Test
    void tellerLeavingPremiseFailsBeforeExecutingNextStep() {
        UUID sessionId = UUID.randomUUID();
        UUID tellerId = UUID.randomUUID();
        TellerEscortTestActor actor = new TellerEscortTestActor();
        actor.setInsidePremise(false);
        TellerEscortNavigationCoordinator coordinator = new TellerEscortNavigationCoordinator();

        coordinator.start(sessionId, tellerId,
                route(tellerId, new SafeTellerRouteStep.Wait(20)), actor);
        coordinator.tick();

        TellerEscortNavigationState state = coordinator.status(sessionId).orElseThrow();
        assertEquals(TellerEscortNavigationState.Status.FAILED, state.status());
        assertEquals(TellerEscortNavigationState.FailureReason.OUTSIDE_BANK_PREMISE,
                state.failureReason());
        assertEquals(0, coordinator.activeCount());
        assertFalse(actor.leased());
    }

    @Test
    void throwingMoveAndRedstoneBecomeFailedAndCoordinatorEvictsThem() {
        assertStepFailureIsContained(TellerEscortTestActor.ThrowPoint.MOVE,
                new SafeTellerRouteStep.Walk(TARGET));
        assertStepFailureIsContained(TellerEscortTestActor.ThrowPoint.REDSTONE,
                new SafeTellerRouteStep.Redstone(TARGET, SafeTellerRouteFace.NORTH, 9, 20));
    }
    @Test
    void placementExceptionAfterMutationRestoresSnapshotAndOwnership() {
        FaultingRelayOperations operations = new FaultingRelayOperations();
        operations.throwAfterPlacementMutation = true;
        TemporaryRelayTransaction.Attempt attempt = TemporaryRelayTransaction.acquire(operations);
        assertFalse(attempt.success());
        assertNull(attempt.transaction(), "completed rollback must not retain a cleanup handle");
        assertEquals("prior", operations.blockState);
        assertEquals("prior-block-entity", operations.blockEntity);
        assertFalse(operations.owned());
        assertTrue(operations.fallbackScheduled);
    }
    @Test
    void ownershipInstallExceptionRetainsCleanupUntilRollbackCanFinish() {
        FaultingRelayOperations operations = new FaultingRelayOperations();
        operations.throwAfterOwnershipMutation = true;
        operations.releaseFailures = 2;
        TemporaryRelayTransaction.Attempt attempt = TemporaryRelayTransaction.acquire(operations);
        assertFalse(attempt.success());
        assertNotNull(attempt.transaction(), "failed rollback must remain retryable");
        assertEquals("temporary", operations.blockState);
        assertTrue(operations.owned());
        assertThrows(IllegalStateException.class, attempt.transaction()::close);
        assertEquals("temporary", operations.blockState);
        assertTrue(operations.owned());
        assertDoesNotThrow(attempt.transaction()::close);
        assertEquals("prior", operations.blockState);
        assertEquals("prior-block-entity", operations.blockEntity);
        assertFalse(operations.owned());
    }

    @Test
    void cleanupFailuresAcrossSeveralTicksEventuallyRestoreEveryResource() {
        UUID sessionId = UUID.randomUUID();
        UUID tellerId = UUID.randomUUID();
        TellerEscortTestActor actor = new TellerEscortTestActor();
        TellerEscortNavigationCoordinator coordinator = new TellerEscortNavigationCoordinator();
        coordinator.start(sessionId, tellerId, route(tellerId, redstone()), actor);
        coordinator.tick();
        actor.activateNavigationForTest();
        actor.failNext(TellerEscortTestActor.ThrowPoint.STOP_NAVIGATION, 2);
        actor.failNext(TellerEscortTestActor.ThrowPoint.CLEAR_REDSTONE, 7);
        actor.failNext(TellerEscortTestActor.ThrowPoint.RELEASE_LEASE, 3);
        assertTrue(coordinator.cancelSession(sessionId));
        tick(coordinator, 6);
        assertEquals(1, coordinator.activeCount(), "session cannot be evicted with a live relay");
        assertTrue(actor.redstoneActive());
        assertCleanupIncomplete(coordinator.status(sessionId).orElseThrow());
        runUntilInactive(coordinator);
        assertFailed(coordinator.status(sessionId).orElseThrow());
        assertRestored(actor);
    }

    @Test
    void persistentResourceFailuresStayOwnedBusyAndRecoverable() {
        assertPersistentResourceFailure(TellerEscortTestActor.ThrowPoint.CLEAR_REDSTONE);
        assertPersistentResourceFailure(TellerEscortTestActor.ThrowPoint.RELEASE_LEASE);
    }

    private static void assertPersistentResourceFailure(TellerEscortTestActor.ThrowPoint failure) {
        UUID sessionId = UUID.randomUUID();
        UUID tellerId = UUID.randomUUID();
        TellerEscortTestActor actor = new TellerEscortTestActor();
        TellerEscortNavigationCoordinator coordinator = new TellerEscortNavigationCoordinator();
        coordinator.start(sessionId, tellerId, route(tellerId, redstone()), actor);
        coordinator.tick();
        actor.activateNavigationForTest();
        actor.failAlways(failure);
        assertTrue(coordinator.cancelSession(sessionId));
        for (int tick = 0; tick < 16; tick++) {
            coordinator.tick();
            if (failure == TellerEscortTestActor.ThrowPoint.CLEAR_REDSTONE
                    ? actor.redstoneActive() : actor.leased()) {
                assertEquals(1, coordinator.activeCount(),
                        "coordinator removed the only owner of retryable cleanup");
            }
        }
        assertEquals(1, coordinator.activeCount(), "owned resource requires a retained cleanup entry");
        assertCleanupIncomplete(coordinator.status(sessionId).orElseThrow());
        assertTrue(failure == TellerEscortTestActor.ThrowPoint.CLEAR_REDSTONE
                ? actor.redstoneActive() : actor.leased());
        TellerEscortStartResult blocked = coordinator.start(
                UUID.randomUUID(), tellerId, route(tellerId, new SafeTellerRouteStep.Wait(1)),
                new TellerEscortTestActor());
        assertEquals(TellerEscortStartResult.Status.BUSY, blocked.status());
        assertFalse(coordinator.forget(sessionId), "recoverable cleanup cannot be forgotten");
        actor.allow(failure);
        runUntilInactive(coordinator);
        assertFailed(coordinator.status(sessionId).orElseThrow());
        assertRestored(actor);
    }

    private static void assertStepFailureIsContained(TellerEscortTestActor.ThrowPoint point,
                                                      SafeTellerRouteStep step) {
        UUID sessionId = UUID.randomUUID();
        UUID tellerId = UUID.randomUUID();
        TellerEscortTestActor actor = new TellerEscortTestActor();
        actor.failAlways(point);
        TellerEscortNavigationCoordinator coordinator = new TellerEscortNavigationCoordinator();
        coordinator.start(sessionId, tellerId, route(tellerId, step), actor);

        assertDoesNotThrow(coordinator::tick);

        assertFailed(coordinator.status(sessionId).orElseThrow());
        assertEquals(0, coordinator.activeCount());
        assertRestored(actor);
    }

    private static void runUntilInactive(TellerEscortNavigationCoordinator coordinator) {
        for (int tick = 0; tick < 32 && coordinator.activeCount() > 0; tick++) {
            coordinator.tick();
        }
        assertEquals(0, coordinator.activeCount(), "recoverable cleanup did not finish");
    }

    private static void tick(TellerEscortNavigationCoordinator coordinator, int count) {
        for (int tick = 0; tick < count; tick++) {
            coordinator.tick();
        }
    }

    private static void assertFailed(TellerEscortNavigationState state) {
        assertEquals(TellerEscortNavigationState.Status.FAILED, state.status());
        assertEquals(TellerEscortNavigationState.FailureReason.INTERNAL_ERROR, state.failureReason());
    }

    private static void assertCleanupIncomplete(TellerEscortNavigationState state) {
        assertEquals(TellerEscortNavigationState.Status.FAILED, state.status());
        assertEquals(TellerEscortNavigationState.FailureReason.CLEANUP_INCOMPLETE,
                state.failureReason());
    }

    private static void assertRestored(TellerEscortTestActor actor) {
        assertFalse(actor.navigationActive());
        assertFalse(actor.redstoneActive());
        assertFalse(actor.leased());
    }

    private static SafeTellerRouteStep.Redstone redstone() {
        return new SafeTellerRouteStep.Redstone(TARGET, SafeTellerRouteFace.NORTH, 9, 20);
    }

    private static SafeTellerRoute route(UUID tellerId, SafeTellerRouteStep... steps) {
        return SafeTellerRoute.create("bank", "vault", tellerId.toString(),
                SafeTellerRouteDirection.OUTBOUND, "minecraft:overworld",
                new SafeTellerRoutePosition(0, 64, 0), TARGET, List.of(steps));
    }

    private static final class FaultingRelayOperations implements TemporaryRelayTransaction.Operations {
        private String blockState = "prior";
        private String blockEntity = "prior-block-entity";
        private UUID owner;
        private boolean fallbackScheduled;
        private boolean throwAfterPlacementMutation;
        private boolean throwAfterOwnershipMutation;
        private int releaseFailures;

        @Override
        public void scheduleFallback() {
            fallbackScheduled = true;
        }

        @Override
        public boolean place() {
            blockState = "temporary";
            blockEntity = null;
            if (throwAfterPlacementMutation) {
                throw new IllegalStateException("placement failed after mutation");
            }
            return true;
        }

        @Override
        public boolean claim(UUID token) {
            owner = token;
            if (throwAfterOwnershipMutation) {
                throw new IllegalStateException("claim failed after mutation");
            }
            return true;
        }

        @Override
        public TemporaryRelayTransaction.Ownership ownership(UUID token) {
            if (owner == null) {
                return TemporaryRelayTransaction.Ownership.UNOWNED;
            }
            return owner.equals(token)
                    ? TemporaryRelayTransaction.Ownership.OWNED_BY_TOKEN
                    : TemporaryRelayTransaction.Ownership.OWNED_BY_OTHER;
        }

        @Override
        public boolean release(UUID token) {
            if (releaseFailures > 0) {
                releaseFailures--;
                throw new IllegalStateException("release failed");
            }
            if (!token.equals(owner)) {
                return false;
            }
            owner = null;
            return true;
        }

        @Override
        public boolean restore() {
            blockState = "prior";
            blockEntity = "prior-block-entity";
            return true;
        }

        @Override
        public void notifyTarget() {
        }

        boolean owned() {
            return owner != null;
        }
    }
}
