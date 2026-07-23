package net.austizz.ultimatebankingsystem.bank.safebox.escort.events;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.EscortBlockPosition;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxArea;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortPhase;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntime;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortSession;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortTarget;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxTellerInteractionPolicyTest {
    private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID TELLER = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TELLER = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void noEscortPassesThroughAndMatchingEscortIsIntercepted() {
        assertFalse(SafeBoxTellerInteractionPolicy.gate(
                PLAYER, TELLER, false, false, Optional.empty(), Optional.empty()).intercept());

        SafeBoxTellerInteractionPolicy.Gate matching = SafeBoxTellerInteractionPolicy.gate(
                PLAYER, TELLER, true, true,
                Optional.of(session(PLAYER, TELLER, SafeBoxEscortPhase.AT_VAULT)),
                Optional.of(session(PLAYER, TELLER, SafeBoxEscortPhase.AT_VAULT)));
        assertTrue(matching.intercept());
        assertTrue(matching.invokeCoordinator());
    }

    @Test
    void recoveringTellerRemainsBusyWithoutDomainSession() {
        SafeBoxTellerInteractionPolicy.Gate recovering = SafeBoxTellerInteractionPolicy.gate(
                PLAYER, TELLER, false, true, Optional.empty(), Optional.empty());

        assertTrue(recovering.intercept());
        assertFalse(recovering.invokeCoordinator());
        assertEquals("This safe-deposit teller is busy. No queue was created.", recovering.message());

        SafeBoxTellerInteractionPolicy.Gate recoveringForBusyPlayer = SafeBoxTellerInteractionPolicy.gate(
                PLAYER, TELLER, true, true,
                Optional.of(session(PLAYER, OTHER_TELLER, SafeBoxEscortPhase.OUTBOUND)), Optional.empty());
        assertEquals("This safe-deposit teller is busy. No queue was created.",
                recoveringForBusyPlayer.message());
    }

    @Test
    void busyTellerAndPlayerWithAnotherEscortCannotOpenNormalUiOrQueue() {
        SafeBoxTellerInteractionPolicy.Gate tellerBusy = SafeBoxTellerInteractionPolicy.gate(
                PLAYER, TELLER, false, true, Optional.empty(),
                Optional.of(session(OTHER_PLAYER, TELLER, SafeBoxEscortPhase.OUTBOUND)));
        assertTrue(tellerBusy.intercept());
        assertFalse(tellerBusy.invokeCoordinator());
        assertTrue(tellerBusy.message().contains("busy"));
        assertTrue(tellerBusy.message().contains("No queue"));

        SafeBoxTellerInteractionPolicy.Gate playerBusy = SafeBoxTellerInteractionPolicy.gate(
                PLAYER, TELLER, true, false,
                Optional.of(session(PLAYER, OTHER_TELLER, SafeBoxEscortPhase.OUTBOUND)), Optional.empty());
        assertTrue(playerBusy.intercept());
        assertFalse(playerBusy.invokeCoordinator());
        assertTrue(playerBusy.message().contains("assigned teller"));
    }

    @Test
    void liveStatusesMapToExactBoxThenLeaveAreaPrompts() {
        assertEquals(
                "Your exact safety deposit box is highlighted. Inspect that box now.",
                SafeBoxTellerInteractionPolicy.message(
                        SafeBoxEscortRuntime.InteractionStatus.ACCESS_GRANTED, SafeBoxEscortPhase.AT_VAULT));
        assertEquals(
                "Please leave the safe area so the teller can close your box and return.",
                SafeBoxTellerInteractionPolicy.message(
                        SafeBoxEscortRuntime.InteractionStatus.WAITING_FOR_EXIT, SafeBoxEscortPhase.INSPECTING));
    }

    @Test
    void outboundReturningAndInvalidStatusesMapToFollowOrWait() {
        assertTrue(SafeBoxTellerInteractionPolicy.message(
                SafeBoxEscortRuntime.InteractionStatus.INVALID_PHASE,
                SafeBoxEscortPhase.OUTBOUND).contains("Follow"));
        assertTrue(SafeBoxTellerInteractionPolicy.message(
                SafeBoxEscortRuntime.InteractionStatus.NOT_FOUND,
                SafeBoxEscortPhase.RETURNING).contains("wait"));
        assertTrue(SafeBoxTellerInteractionPolicy.message(
                SafeBoxEscortRuntime.InteractionStatus.AUTHORIZATION_DENIED,
                SafeBoxEscortPhase.AT_VAULT).contains("wait"));
    }

    private static SafeBoxEscortSession session(UUID playerId, UUID tellerId, SafeBoxEscortPhase phase) {
        SafeBoxEscortTarget target = new SafeBoxEscortTarget(UUID.randomUUID(), "vault", UUID.randomUUID(),
                "minecraft:overworld", new EscortBlockPosition(5, 64, 5), 0, tellerId);
        return new SafeBoxEscortSession(UUID.randomUUID(), playerId, target,
                new SafeBoxArea("minecraft:overworld", 0, 60, 0, 10, 70, 10), phase, -1L, null);
    }
}
