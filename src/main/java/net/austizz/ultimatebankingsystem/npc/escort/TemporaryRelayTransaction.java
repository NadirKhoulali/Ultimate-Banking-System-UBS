package net.austizz.ultimatebankingsystem.npc.escort;

import java.util.UUID;

final class TemporaryRelayTransaction implements AutoCloseable {
    enum Ownership {
        OWNED_BY_TOKEN,
        OWNED_BY_OTHER,
        UNOWNED
    }

    interface Operations {
        void scheduleFallback();

        boolean place();

        boolean claim(UUID token);

        Ownership ownership(UUID token);

        boolean release(UUID token);

        boolean restore();

        void notifyTarget();
    }

    private final Operations operations;
    private final UUID token = UUID.randomUUID();
    private boolean placementAttempted;
    private boolean active = true;

    private TemporaryRelayTransaction(Operations operations) {
        this.operations = operations;
    }

    static Attempt acquire(Operations operations) {
        TemporaryRelayTransaction transaction = new TemporaryRelayTransaction(operations);
        try {
            operations.scheduleFallback();
            if (operations.ownership(transaction.token) != Ownership.UNOWNED) {
                transaction.active = false;
                return Attempt.failure(null);
            }
            transaction.placementAttempted = true;
            if (!operations.place() || !operations.claim(transaction.token)) {
                return rollback(transaction);
            }
            operations.notifyTarget();
            return Attempt.success(transaction);
        } catch (RuntimeException exception) {
            return rollback(transaction);
        }
    }

    private static Attempt rollback(TemporaryRelayTransaction transaction) {
        try {
            transaction.close();
        } catch (RuntimeException ignored) {
            // The caller receives the still-active transaction and owns later retries.
        }
        return Attempt.failure(transaction.active ? transaction : null);
    }

    @Override
    public void close() {
        if (!active) {
            return;
        }
        Ownership ownership = operations.ownership(token);
        if (ownership == Ownership.OWNED_BY_OTHER) {
            active = false;
            return;
        }
        if (ownership == Ownership.OWNED_BY_TOKEN && !operations.release(token)) {
            throw new IllegalStateException("Temporary relay ownership could not be released");
        }
        if (placementAttempted && !operations.restore()) {
            throw new IllegalStateException("Temporary relay block could not be restored");
        }
        active = false;
    }

    record Attempt(boolean success, TemporaryRelayTransaction transaction) {
        static Attempt success(TemporaryRelayTransaction transaction) {
            return new Attempt(true, transaction);
        }

        static Attempt failure(TemporaryRelayTransaction transaction) {
            return new Attempt(false, transaction);
        }
    }
}
