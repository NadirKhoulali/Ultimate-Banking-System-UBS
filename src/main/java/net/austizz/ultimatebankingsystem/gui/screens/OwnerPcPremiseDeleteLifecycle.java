package net.austizz.ultimatebankingsystem.gui.screens;

import java.util.Objects;
import java.util.UUID;

public final class OwnerPcPremiseDeleteLifecycle {
    private static final State CLOSED = new State(null, "", null);

    private OwnerPcPremiseDeleteLifecycle() {}

    public static State closed() {
        return CLOSED;
    }

    public static State confirming(UUID bankId, String premiseId) {
        return new State(Objects.requireNonNull(bankId, "bankId"), premiseId, null);
    }

    public enum Outcome {
        IGNORED,
        RETRY,
        SUCCESS
    }

    public enum ResponseAction {
        DELETE,
        OTHER
    }

    public record State(UUID bankId, String premiseId, UUID operationId) {
        public State {
            premiseId = premiseId == null ? "" : premiseId;
            if (bankId == null) {
                if (!premiseId.isEmpty() || operationId != null) {
                    throw new IllegalArgumentException("A closed delete lifecycle cannot retain request identity.");
                }
            } else if (premiseId.isBlank()) {
                throw new IllegalArgumentException("An open delete lifecycle requires a premise id.");
            }
        }

        public boolean modalOpen() {
            return bankId != null;
        }

        public boolean pending() {
            return modalOpen() && operationId != null;
        }

        public boolean canConfirm() {
            return modalOpen() && !pending();
        }

        public boolean canDismiss() {
            return canConfirm();
        }

        public Confirmation confirm(UUID nextOperationId) {
            if (!canConfirm()) {
                return new Confirmation(this, null);
            }
            UUID operation = Objects.requireNonNull(nextOperationId, "nextOperationId");
            State next = new State(bankId, premiseId, operation);
            return new Confirmation(next, new Request(bankId, operation, premiseId));
        }

        public Response handle(ResponseAction action,
                               UUID responseBankId,
                               String responsePremiseId,
                               UUID responseOperationId,
                               boolean success) {
            boolean matches = pending()
                    && action == ResponseAction.DELETE
                    && bankId.equals(responseBankId)
                    && premiseId.equals(responsePremiseId)
                    && operationId.equals(responseOperationId);
            if (!matches) {
                return new Response(this, Outcome.IGNORED);
            }
            return success
                    ? new Response(closed(), Outcome.SUCCESS)
                    : new Response(confirming(bankId, premiseId), Outcome.RETRY);
        }
    }

    public record Request(UUID bankId, UUID operationId, String premiseId) {}

    public record Confirmation(State state, Request request) {
        public boolean shouldSend() {
            return request != null;
        }
    }

    public record Response(State state, Outcome outcome) {
        public boolean accepted() {
            return outcome != Outcome.IGNORED;
        }

        public boolean shouldRefresh() {
            return outcome == Outcome.SUCCESS;
        }
    }
}
