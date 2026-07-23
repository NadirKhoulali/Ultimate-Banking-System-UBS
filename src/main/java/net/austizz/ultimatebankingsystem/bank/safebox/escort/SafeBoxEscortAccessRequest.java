package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record SafeBoxEscortAccessRequest(UUID playerId, ExactBox box) {
    public SafeBoxEscortAccessRequest {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(box, "box");
    }

    public static SafeBoxEscortAccessRequest fromTarget(UUID playerId, SafeBoxEscortTarget target) {
        Objects.requireNonNull(target, "target");
        return new SafeBoxEscortAccessRequest(playerId, ExactBox.fromTarget(target));
    }

    boolean matches(SafeBoxEscortTarget target) {
        return target != null && box.equals(ExactBox.fromTarget(target));
    }

    public record ExactBox(UUID bankId,
                           UUID accountId,
                           String dimension,
                           EscortBlockPosition rowPosition,
                           int doorIndex) {
        public ExactBox {
            Objects.requireNonNull(bankId, "bankId");
            Objects.requireNonNull(accountId, "accountId");
            if (dimension == null || dimension.isBlank()) {
                throw new IllegalArgumentException("dimension must not be blank");
            }
            dimension = dimension.trim().toLowerCase(Locale.ROOT);
            Objects.requireNonNull(rowPosition, "rowPosition");
            if (doorIndex < 0) {
                throw new IllegalArgumentException("doorIndex must not be negative");
            }
        }

        private static ExactBox fromTarget(SafeBoxEscortTarget target) {
            return new ExactBox(target.bankId(), target.accountId(), target.dimension(),
                    target.rowPosition(), target.doorIndex());
        }
    }
}
