package net.austizz.ultimatebankingsystem.account;

/**
 * Shared account access denial text for screens, server commands, and public API callers.
 */
public final class AccountAccessMessages {
    private AccountAccessMessages() {
    }

    public static String frozen(String interactionName, AccountHolder account) {
        String interaction = normalizeInteraction(interactionName);
        String reason = account == null ? "" : account.getFrozenReason();
        String message = "Account is frozen while trying to use " + interaction + ".";
        if (reason != null && !reason.isBlank()) {
            message += " Reason: " + reason.trim();
        }
        return message;
    }

    public static String destinationFrozen(String interactionName, AccountHolder account) {
        String interaction = normalizeInteraction(interactionName);
        String reason = account == null ? "" : account.getFrozenReason();
        String message = "Destination account is frozen while trying to use " + interaction + ".";
        if (reason != null && !reason.isBlank()) {
            message += " Reason: " + reason.trim();
        }
        return message;
    }

    public static String normalizeInteraction(String interactionName) {
        if (interactionName == null || interactionName.isBlank()) {
            return "banking";
        }
        return interactionName.trim().replace('_', ' ');
    }
}
