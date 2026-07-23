package net.austizz.ultimatebankingsystem.account;

import java.math.BigDecimal;

public record AccountReadSnapshot(BigDecimal dailyLimit,
                                  BigDecimal dailyUsed,
                                  BigDecimal dailyRemaining,
                                  long dailyResetAtEpochMillis,
                                  BigDecimal temporaryLimit,
                                  long temporaryExpiresAtEpochMillis,
                                  boolean certificateLocked) {
    public record Raw(BigDecimal dailyLimit,
                      long dailyWindowDay,
                      BigDecimal dailyUsed,
                      long dailyResetAtEpochMillis,
                      BigDecimal temporaryLimit,
                      long temporaryExpiresAtGameTime,
                      long temporaryExpiresAtEpochMillis,
                      boolean certificateLocked,
                      long certificateMaturityGameTime) {
    }

    public static AccountReadSnapshot capture(Raw raw,
                                              long currentGameTime,
                                              long nowMillis,
                                              long currentEpochDay,
                                              long nextMidnightEpochMillis) {
        if (raw == null) {
            return new AccountReadSnapshot(BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, nextMidnightEpochMillis, null, -1L, false);
        }
        BigDecimal dailyLimit = nonNegative(raw.dailyLimit());
        boolean currentDailyWindow = raw.dailyWindowDay() == currentEpochDay
                && raw.dailyResetAtEpochMillis() > nowMillis;
        BigDecimal dailyUsed = currentDailyWindow ? nonNegative(raw.dailyUsed()) : BigDecimal.ZERO;
        BigDecimal dailyRemaining = dailyLimit.subtract(dailyUsed).max(BigDecimal.ZERO);
        long dailyReset = currentDailyWindow
                ? raw.dailyResetAtEpochMillis()
                : nextMidnightEpochMillis;

        boolean temporaryExpiredByClock = raw.temporaryExpiresAtEpochMillis() > 0L
                && nowMillis >= raw.temporaryExpiresAtEpochMillis();
        boolean temporaryExpiredByGameTime = raw.temporaryExpiresAtGameTime() >= 0L
                && currentGameTime >= raw.temporaryExpiresAtGameTime();
        BigDecimal temporaryLimit = temporaryExpiredByClock || temporaryExpiredByGameTime
                ? null
                : raw.temporaryLimit();
        long temporaryExpires = temporaryLimit == null
                ? -1L
                : raw.temporaryExpiresAtEpochMillis();
        boolean certificateLocked = raw.certificateLocked()
                && raw.certificateMaturityGameTime() > 0L
                && currentGameTime < raw.certificateMaturityGameTime();
        return new AccountReadSnapshot(dailyLimit, dailyUsed, dailyRemaining, dailyReset,
                temporaryLimit, temporaryExpires, certificateLocked);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }
}
