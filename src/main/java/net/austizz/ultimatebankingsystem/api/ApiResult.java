package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;

@ApiStatus.AvailableSince("1.1.0")
public record ApiResult(boolean success, String reason, BigDecimal balanceAfter) {
    public static ApiResult ok(BigDecimal balanceAfter) {
        return new ApiResult(true, "", balanceAfter);
    }

    public static ApiResult fail(String reason, BigDecimal balanceAfter) {
        return new ApiResult(false, reason == null ? "Unknown error" : reason, balanceAfter);
    }
}
