package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.EscortBlockPosition;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxArea;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortAuthorizationPolicy;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortMutation;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortPhase;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRegistry;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortSession;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortTarget;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortTerminalReason;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SafeBoxEscortTestSupport {
    static final UUID PLAYER = id(1);
    static final UUID TELLER = id(2);
    static final UUID ACCOUNT = id(3);
    static final SafeBoxArea AREA = new SafeBoxArea(
            "minecraft:overworld", 10, 60, 10, 20, 70, 20);

    private SafeBoxEscortTestSupport() {
    }

    static SafeBoxEscortRegistry authorizedRegistry() {
        return started((player, target) -> SafeBoxEscortAuthorizationPolicy.Decision.GRANTED,
                target(ACCOUNT, TELLER, 3));
    }

    static SafeBoxEscortRegistry inspectingRegistry() {
        SafeBoxEscortRegistry registry = authorizedRegistry();
        UUID sessionId = active(registry).sessionId();
        registry.onArrivedAtVault(PLAYER, sessionId);
        registry.onTellerInteraction(PLAYER, sessionId, TELLER, 100);
        return registry;
    }

    static SafeBoxEscortRegistry started(SafeBoxEscortAuthorizationPolicy policy,
                                         SafeBoxEscortTarget target) {
        SafeBoxEscortRegistry registry = new SafeBoxEscortRegistry(policy);
        registry.start(id(20), PLAYER, target, AREA);
        return registry;
    }

    static SafeBoxEscortTarget target(UUID account, UUID teller, int door) {
        return new SafeBoxEscortTarget(id(21), "vault-a", account, "minecraft:overworld",
                new EscortBlockPosition(12, 64, 14), door, teller);
    }

    static UUID id(long value) {
        return new UUID(0, value);
    }

    static SafeBoxEscortSession active(SafeBoxEscortRegistry registry) {
        return registry.activeForPlayer(PLAYER).orElseThrow();
    }

    static void assertPhase(SafeBoxEscortMutation mutation, SafeBoxEscortPhase phase) {
        assertEquals(SafeBoxEscortMutation.Status.APPLIED, mutation.status());
        assertEquals(phase, mutation.session().orElseThrow().phase());
    }

    static void assertInvalid(SafeBoxEscortMutation mutation, SafeBoxEscortPhase phase) {
        assertEquals(SafeBoxEscortMutation.Status.INVALID_PHASE, mutation.status());
        assertEquals(phase, mutation.session().orElseThrow().phase());
    }

    static void assertTimedOut(SafeBoxEscortMutation mutation, SafeBoxEscortRegistry registry) {
        assertEquals(SafeBoxEscortTerminalReason.TIMED_OUT, mutation.terminalReason().orElseThrow());
        assertTrue(mutation.ejectionRequired());
        assertTrue(mutation.cleanupRequired());
        assertReleased(registry);
    }

    static void assertReleased(SafeBoxEscortRegistry registry) {
        assertEquals(0, registry.activeCount());
        assertTrue(registry.activeForPlayer(PLAYER).isEmpty());
        assertTrue(registry.activeForTeller(TELLER).isEmpty());
    }
}
