package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SafeBoxEscortRecordInvariantTest {
    @Test
    void publicResultsRejectContradictorySuccessStates() {
        assertThrows(IllegalArgumentException.class, () -> new SafeBoxEscortRuntime.StartResult(
                SafeBoxEscortRuntime.StartStatus.STARTED, null));
        assertThrows(IllegalArgumentException.class, () -> new SafeBoxEscortRuntime.StartResult(
                SafeBoxEscortRuntime.StartStatus.INVALID, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new SafeBoxEscortStartResult(
                SafeBoxEscortStartResult.Status.STARTED, null));
    }

    @Test
    void sessionsAndMutationsRejectContradictoryTerminalStates() {
        SafeBoxEscortTarget target = new SafeBoxEscortTarget(
                UUID.randomUUID(), "vault", UUID.randomUUID(), "minecraft:overworld",
                new EscortBlockPosition(1, 2, 3), 0, UUID.randomUUID());
        SafeBoxArea area = new SafeBoxArea("minecraft:overworld", 0, 0, 0, 5, 5, 5);

        assertThrows(IllegalArgumentException.class, () -> new SafeBoxEscortSession(
                UUID.randomUUID(), UUID.randomUUID(), target, area,
                SafeBoxEscortPhase.COMPLETE, -1, null));
        assertThrows(IllegalArgumentException.class, () -> new SafeBoxEscortMutation(
                SafeBoxEscortMutation.Status.NOT_FOUND,
                SafeBoxEscortMutation.Action.GRANT_TARGET_ACCESS,
                null, target, null, false, false));
    }
}
