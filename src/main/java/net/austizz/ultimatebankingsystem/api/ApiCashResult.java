package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("1.1.0")
public record ApiCashResult(
        boolean success,
        String reason,
        int denomination,
        int billCount,
        int totalDollarValue
) {
    public static ApiCashResult ok(int denomination, int billCount) {
        int safeDenomination = Math.max(0, denomination);
        int safeCount = Math.max(0, billCount);
        return new ApiCashResult(true, "", safeDenomination, safeCount, safeDenomination * safeCount);
    }

    public static ApiCashResult fail(String reason, int denomination, int billCount) {
        int safeDenomination = Math.max(0, denomination);
        int safeCount = Math.max(0, billCount);
        return new ApiCashResult(false, reason == null ? "Unknown error" : reason, safeDenomination, safeCount, 0);
    }
}
