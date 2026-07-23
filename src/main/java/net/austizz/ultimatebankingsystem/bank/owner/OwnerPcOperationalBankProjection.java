package net.austizz.ultimatebankingsystem.bank.owner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class OwnerPcOperationalBankProjection {
    private OwnerPcOperationalBankProjection() {
    }

    public static Projection project(State persisted,
                                     BigDecimal reserve,
                                     BigDecimal deposits,
                                     long gameTime,
                                     double minimumReserveRatio,
                                     long reserveGraceTicks) {
        State raw = persisted == null ? State.empty() : persisted;
        String previousStatus = normalizedStatus(raw.status());
        String nextStatus = previousStatus;

        if ("LOCKDOWN".equals(previousStatus)
                && raw.lockdownUntilTick() != null
                && gameTime >= raw.lockdownUntilTick()) {
            nextStatus = "ACTIVE";
        }

        BigDecimal availableReserve = nonNegative(reserve);
        BigDecimal totalDeposits = nonNegative(deposits);
        BigDecimal minimumReserve = totalDeposits
                .multiply(BigDecimal.valueOf(minimumReserveRatio))
                .setScale(2, RoundingMode.HALF_EVEN);
        long graceTicks = Math.max(20L, reserveGraceTicks);

        Long breachStartTick = raw.reserveBreachStartTick();
        if (availableReserve.compareTo(minimumReserve) < 0) {
            long breachTick = breachStartTick == null ? gameTime : breachStartTick;
            breachStartTick = breachTick;
            if (!isAdministrativeStop(previousStatus)) {
                nextStatus = gameTime - breachTick >= graceTicks
                        ? "RESTRICTED"
                        : "WARNING";
            }
        } else {
            breachStartTick = null;
            if ("WARNING".equals(previousStatus) || "RESTRICTED".equals(previousStatus)) {
                nextStatus = "ACTIVE";
            }
        }

        long day = gameTime / 24_000L;
        boolean rollover = raw.dailyWindowDay() == null || raw.dailyWindowDay() != day;
        State projected = new State(
                nextStatus,
                raw.lockdownUntilTick(),
                breachStartTick,
                rollover ? day : raw.dailyWindowDay(),
                rollover ? "0" : raw.dailyWithdrawn(),
                rollover ? 0 : raw.queuedWithdrawalCount()
        );
        return new Projection(projected, previousStatus, nextStatus);
    }

    private static boolean isAdministrativeStop(String status) {
        return "SUSPENDED".equals(status) || "REVOKED".equals(status);
    }

    private static String normalizedStatus(String status) {
        return status == null || status.isBlank()
                ? "ACTIVE"
                : status.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }

    public record State(String status,
                        Long lockdownUntilTick,
                        Long reserveBreachStartTick,
                        Long dailyWindowDay,
                        String dailyWithdrawn,
                        int queuedWithdrawalCount) {
        static State empty() {
            return new State("ACTIVE", null, null, null, "0", 0);
        }
    }

    public record Projection(State state,
                             String previousStatus,
                             String status) {
    }
}
