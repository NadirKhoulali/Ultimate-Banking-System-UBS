package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("2.0.0")
public record ApiManagementResult(boolean success, String message) {
    public ApiManagementResult {
        message = message == null ? "" : message;
    }

    public static ApiManagementResult ok(String message) {
        return new ApiManagementResult(true, message);
    }

    public static ApiManagementResult fail(String message) {
        return new ApiManagementResult(false, message);
    }
}
