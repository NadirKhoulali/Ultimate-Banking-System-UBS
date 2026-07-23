package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SafeBoxEscortSessionTest {
    @Test
    void busyRequestsAndDuplicateSessionIdsPreserveExactIndexesWithoutAQueue() {
        SafeBoxEscortRegistry registry = new SafeBoxEscortRegistry();
        UUID sessionId = id(10);
        SafeBoxEscortTarget exact = target(ACCOUNT, TELLER, 3);
        assertEquals(SafeBoxEscortStartResult.Status.STARTED,
                registry.start(sessionId, PLAYER, exact, AREA).status());
        assertEquals(SafeBoxEscortStartResult.Status.BUSY,
                registry.start(id(11), PLAYER, target(ACCOUNT, id(4), 4), AREA).status());
        assertEquals(SafeBoxEscortStartResult.Status.BUSY,
                registry.start(id(12), id(5), exact, AREA).status());
        assertEquals(SafeBoxEscortStartResult.Status.BUSY,
                registry.start(sessionId, id(6), target(ACCOUNT, id(7), 5), AREA).status());
        assertEquals(1, registry.activeCount());
        assertEquals(exact, active(registry).target());
        assertEquals(sessionId, registry.activeForTeller(TELLER).orElseThrow().sessionId());
    }

    @Test
    void grantRequiresFreshAuthorizationForTheExactAccountAndBox() {
        SafeBoxEscortTarget exact = target(ACCOUNT, TELLER, 3);
        AtomicBoolean allowed = new AtomicBoolean();
        AtomicInteger decisions = new AtomicInteger();
        AtomicReference<SafeBoxEscortTarget> observed = new AtomicReference<>();
        SafeBoxEscortRegistry registry = started((player, candidate) -> {
            decisions.incrementAndGet();
            observed.set(candidate);
            return allowed.get() && candidate.equals(exact)
                    ? SafeBoxEscortAuthorizationPolicy.Decision.GRANTED
                    : SafeBoxEscortAuthorizationPolicy.Decision.DENIED;
        }, exact);
        UUID sessionId = active(registry).sessionId();
        registry.onArrivedAtVault(PLAYER, sessionId);
        SafeBoxEscortSession before = active(registry);

        SafeBoxEscortMutation denied = registry.onTellerInteraction(PLAYER, sessionId, TELLER, 100);
        assertEquals(SafeBoxEscortMutation.Status.AUTHORIZATION_DENIED, denied.status());
        assertEquals(SafeBoxEscortMutation.Action.NONE, denied.action());
        assertTrue(denied.accessTarget().isEmpty());
        assertSame(before, active(registry));
        allowed.set(true);
        SafeBoxEscortMutation granted = registry.onTellerInteraction(PLAYER, sessionId, TELLER, 100);
        assertEquals(2, decisions.get());
        assertEquals(exact, observed.get());
        assertEquals(exact, granted.accessTarget().orElseThrow());
        assertEquals(SafeBoxEscortPhase.INSPECTING, active(registry).phase());

        SafeBoxEscortTarget substituted = target(id(30), TELLER, 4);
        SafeBoxEscortRegistry wrong = started((player, candidate) -> candidate.equals(exact)
                ? SafeBoxEscortAuthorizationPolicy.Decision.GRANTED
                : SafeBoxEscortAuthorizationPolicy.Decision.DENIED, substituted);
        UUID wrongId = active(wrong).sessionId();
        wrong.onArrivedAtVault(PLAYER, wrongId);
        assertEquals(SafeBoxEscortMutation.Status.AUTHORIZATION_DENIED,
                wrong.onTellerInteraction(PLAYER, wrongId, TELLER, 1).status());
        assertEquals(SafeBoxEscortPhase.AT_VAULT, active(wrong).phase());
    }

    @Test
    void denialIsDefaultAndPolicyFailureRollsBackWithoutAccess() {
        SafeBoxEscortRegistry denied = new SafeBoxEscortRegistry();
        denied.start(id(20), PLAYER, target(ACCOUNT, TELLER, 3), AREA);
        UUID deniedId = active(denied).sessionId();
        denied.onArrivedAtVault(PLAYER, deniedId);
        assertEquals(SafeBoxEscortMutation.Status.AUTHORIZATION_DENIED,
                denied.onTellerInteraction(PLAYER, deniedId, TELLER, 0).status());
        assertEquals(SafeBoxEscortPhase.AT_VAULT, active(denied).phase());

        SafeBoxEscortRegistry nullDecision = started((player, target) -> null,
                target(ACCOUNT, TELLER, 3));
        UUID nullId = active(nullDecision).sessionId();
        nullDecision.onArrivedAtVault(PLAYER, nullId);
        assertEquals(SafeBoxEscortMutation.Status.AUTHORIZATION_DENIED,
                nullDecision.onTellerInteraction(PLAYER, nullId, TELLER, 1).status());
        assertEquals(SafeBoxEscortPhase.AT_VAULT, active(nullDecision).phase());

        SafeBoxEscortRegistry failing = started((player, target) -> {
            throw new IllegalStateException("authorization unavailable");
        }, target(ACCOUNT, TELLER, 3));
        UUID failingId = active(failing).sessionId();
        failing.onArrivedAtVault(PLAYER, failingId);
        SafeBoxEscortSession before = active(failing);
        assertThrows(IllegalStateException.class,
                () -> failing.onTellerInteraction(PLAYER, failingId, TELLER, 10));
        assertSame(before, active(failing));
        assertEquals(1, failing.activeCount());
    }

    @Test
    void lifecycleRejectsIllegalEventsAndWaitsForAnActualSafeAreaExit() {
        SafeBoxEscortRegistry registry = authorizedRegistry();
        UUID sessionId = active(registry).sessionId();
        assertInvalid(registry.onReturnRouteComplete(PLAYER, sessionId), SafeBoxEscortPhase.OUTBOUND);
        assertInvalid(registry.onPlayerPosition(PLAYER, sessionId, "minecraft:overworld",
                new EscortBlockPosition(21, 64, 15)), SafeBoxEscortPhase.OUTBOUND);
        assertPhase(registry.onArrivedAtVault(PLAYER, sessionId), SafeBoxEscortPhase.AT_VAULT);
        assertInvalid(registry.onArrivedAtVault(PLAYER, sessionId), SafeBoxEscortPhase.AT_VAULT);
        assertEquals(SafeBoxEscortMutation.Status.WRONG_TELLER,
                registry.onTellerInteraction(PLAYER, sessionId, id(9), 100).status());
        assertPhase(registry.onTellerInteraction(PLAYER, sessionId, TELLER, 100),
                SafeBoxEscortPhase.INSPECTING);
        assertPhase(registry.onTellerInteraction(PLAYER, sessionId, TELLER, 101),
                SafeBoxEscortPhase.WAITING_FOR_EXIT);
        assertInvalid(registry.onTellerInteraction(PLAYER, sessionId, TELLER, 102),
                SafeBoxEscortPhase.WAITING_FOR_EXIT);
        assertEquals(SafeBoxEscortMutation.Status.NO_CHANGE,
                registry.onPlayerPosition(PLAYER, sessionId, "minecraft:overworld",
                        new EscortBlockPosition(20, 70, 20)).status());
        assertPhase(registry.onPlayerPosition(PLAYER, sessionId, "minecraft:overworld",
                new EscortBlockPosition(21, 64, 15)), SafeBoxEscortPhase.RETURNING);
        assertTrue(registry.tick(6_500).isEmpty());
        assertEquals(sessionId, registry.activeForTeller(TELLER).orElseThrow().sessionId());
        SafeBoxEscortMutation complete = registry.onReturnRouteComplete(PLAYER, sessionId);
        assertEquals(SafeBoxEscortTerminalReason.COMPLETED, complete.terminalReason().orElseThrow());
        assertReleased(registry);
    }

    @Test
    void exactDeadlineTimesOutOnlyInspectingAndWaitingPhases() {
        SafeBoxEscortRegistry inspecting = inspectingRegistry();
        assertTrue(inspecting.tick(6_099).isEmpty());
        assertTimedOut(inspecting.tick(6_100).getFirst(), inspecting);

        SafeBoxEscortRegistry waiting = inspectingRegistry();
        UUID waitingId = active(waiting).sessionId();
        waiting.onTellerInteraction(PLAYER, waitingId, TELLER, 101);
        assertTimedOut(waiting.tick(6_100).getFirst(), waiting);

        SafeBoxEscortRegistry preGrant = authorizedRegistry();
        UUID preGrantId = active(preGrant).sessionId();
        assertTrue(preGrant.tick(10_000).isEmpty());
        preGrant.onArrivedAtVault(PLAYER, preGrantId);
        assertTrue(preGrant.tick(10_001).isEmpty());
        assertEquals(SafeBoxEscortPhase.AT_VAULT, active(preGrant).phase());
    }

    @Test
    void nullAndClockRollbackInputsNeverMutateSessionOrIndexes() {
        AtomicInteger calls = new AtomicInteger();
        SafeBoxEscortRegistry registry = started((player, target) -> {
            calls.incrementAndGet();
            return SafeBoxEscortAuthorizationPolicy.Decision.GRANTED;
        }, target(ACCOUNT, TELLER, 3));
        UUID sessionId = active(registry).sessionId();
        registry.onArrivedAtVault(PLAYER, sessionId);
        SafeBoxEscortSession before = active(registry);
        assertThrows(IllegalArgumentException.class,
                () -> AREA.contains("minecraft:overworld", null));
        assertThrows(IllegalArgumentException.class,
                () -> registry.onPlayerPosition(PLAYER, sessionId, null, new EscortBlockPosition(1, 2, 3)));
        assertThrows(IllegalArgumentException.class,
                () -> registry.onPlayerPosition(PLAYER, sessionId, " ", new EscortBlockPosition(1, 2, 3)));
        assertThrows(IllegalArgumentException.class,
                () -> registry.onPlayerPosition(PLAYER, sessionId, "minecraft:overworld", null));
        assertThrows(IllegalArgumentException.class,
                () -> registry.onArrivedAtVault(null, sessionId));
        assertThrows(IllegalArgumentException.class,
                () -> registry.onArrivedAtVault(PLAYER, null));
        assertThrows(IllegalArgumentException.class,
                () -> registry.onTellerInteraction(PLAYER, sessionId, null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> registry.onTellerInteraction(PLAYER, sessionId, TELLER, -1));
        assertSame(before, active(registry));
        assertEquals(0, calls.get());
        registry.onTellerInteraction(PLAYER, sessionId, TELLER, 100);
        SafeBoxEscortSession inspecting = active(registry);
        assertThrows(IllegalArgumentException.class, () -> registry.tick(99));
        assertThrows(IllegalArgumentException.class,
                () -> registry.onTellerInteraction(PLAYER, sessionId, TELLER, 99));
        assertSame(inspecting, active(registry));
        assertEquals(sessionId, registry.activeForTeller(TELLER).orElseThrow().sessionId());
    }

    @Test
    void typedTerminationsAndStaleCallbacksReleaseOnlyTheirOwnSession() {
        List<TerminationCase> cases = List.of(
                new TerminationCase(SafeBoxEscortTerminalReason.LOGOUT, SafeBoxEscortRegistry::onLogout),
                new TerminationCase(SafeBoxEscortTerminalReason.DEATH, SafeBoxEscortRegistry::onDeath),
                new TerminationCase(SafeBoxEscortTerminalReason.DIMENSION_CHANGE,
                        SafeBoxEscortRegistry::onDimensionChange),
                new TerminationCase(SafeBoxEscortTerminalReason.ROUTE_FAILURE,
                        SafeBoxEscortRegistry::onRouteFailure),
                new TerminationCase(SafeBoxEscortTerminalReason.CANCELLED, SafeBoxEscortRegistry::cancel));
        for (TerminationCase termination : cases) {
            SafeBoxEscortRegistry registry = authorizedRegistry();
            SafeBoxEscortMutation result = termination.action().apply(
                    registry, PLAYER, active(registry).sessionId());
            assertEquals(termination.reason(), result.terminalReason().orElseThrow());
            assertReleased(registry);
        }
        SafeBoxEscortRegistry stopped = authorizedRegistry();
        assertEquals(SafeBoxEscortTerminalReason.SERVER_STOP,
                stopped.onServerStop().getFirst().terminalReason().orElseThrow());
        assertReleased(stopped);

        SafeBoxEscortRegistry replacement = authorizedRegistry();
        UUID staleId = active(replacement).sessionId();
        replacement.cancel(PLAYER, staleId);
        UUID currentId = id(40);
        replacement.start(currentId, PLAYER, target(ACCOUNT, TELLER, 3), AREA);
        assertEquals(SafeBoxEscortMutation.Status.STALE_SESSION,
                replacement.onRouteFailure(PLAYER, staleId).status());
        assertEquals(currentId, active(replacement).sessionId());
        assertEquals(currentId, replacement.activeForTeller(TELLER).orElseThrow().sessionId());
    }

    private record TerminationCase(SafeBoxEscortTerminalReason reason, Termination action) {
    }

    @FunctionalInterface
    private interface Termination {
        SafeBoxEscortMutation apply(SafeBoxEscortRegistry registry, UUID playerId, UUID sessionId);
    }
}
