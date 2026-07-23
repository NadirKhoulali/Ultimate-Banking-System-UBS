package net.austizz.ultimatebankingsystem.bank.safebox.escort.events;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortPhase;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntime;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortSession;

import java.util.Optional;
import java.util.UUID;

public final class SafeBoxTellerInteractionPolicy {
    private static final String TELLER_BUSY =
            "This safe-deposit teller is busy. No queue was created.";
    private static final String PLAYER_BUSY =
            "Your escort is already active. Follow your assigned teller; no queue was created.";

    private SafeBoxTellerInteractionPolicy() {
    }

    public static Gate gate(UUID playerId,
                            UUID clickedTellerId,
                            boolean playerBusy,
                            boolean tellerBusy,
                            Optional<SafeBoxEscortSession> playerEscort,
                            Optional<SafeBoxEscortSession> tellerEscort) {
        Optional<SafeBoxEscortSession> playerSession = safe(playerEscort);
        Optional<SafeBoxEscortSession> tellerSession = safe(tellerEscort);
        if (tellerSession.filter(session -> !session.playerId().equals(playerId)).isPresent()) {
            return new Gate(true, false, TELLER_BUSY, null);
        }
        SafeBoxEscortSession matching = playerSession.filter(session ->
                        session.target().requestedTellerId().equals(clickedTellerId))
                .or(() -> tellerSession.filter(session -> session.playerId().equals(playerId)))
                .orElse(null);
        if (matching != null) {
            return new Gate(true, true, "", matching.phase());
        }
        if (tellerBusy) {
            return new Gate(true, false, TELLER_BUSY, null);
        }
        if (playerSession.filter(session ->
                !session.target().requestedTellerId().equals(clickedTellerId)).isPresent()) {
            return new Gate(true, false, PLAYER_BUSY, playerSession.get().phase());
        }
        if (playerBusy) {
            return new Gate(true, false, PLAYER_BUSY, null);
        }
        return new Gate(false, false, "", null);
    }

    public static String message(SafeBoxEscortRuntime.InteractionStatus status,
                                 SafeBoxEscortPhase phase) {
        if (status == SafeBoxEscortRuntime.InteractionStatus.ACCESS_GRANTED) {
            return "Your exact safety deposit box is highlighted. Inspect that box now.";
        }
        if (status == SafeBoxEscortRuntime.InteractionStatus.WAITING_FOR_EXIT
                || phase == SafeBoxEscortPhase.INSPECTING
                || phase == SafeBoxEscortPhase.WAITING_FOR_EXIT) {
            return "Please leave the safe area so the teller can close your box and return.";
        }
        if (status == SafeBoxEscortRuntime.InteractionStatus.AUTHORIZATION_DENIED) {
            return "Your box access could not be verified. Please wait and try again.";
        }
        if (status == SafeBoxEscortRuntime.InteractionStatus.WRONG_TELLER) {
            return PLAYER_BUSY;
        }
        if (phase == SafeBoxEscortPhase.OUTBOUND || phase == SafeBoxEscortPhase.AT_VAULT) {
            return "Follow your teller and wait until you arrive at the vault.";
        }
        return "Please wait for the teller to close the safe area and return.";
    }

    private static Optional<SafeBoxEscortSession> safe(Optional<SafeBoxEscortSession> session) {
        return session == null ? Optional.empty() : session;
    }

    public record Gate(boolean intercept,
                       boolean invokeCoordinator,
                       String message,
                       SafeBoxEscortPhase phase) {
    }
}
