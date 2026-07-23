package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.util.UUID;

@ApiStatus.AvailableSince("1.2.0")
public record ApiTransactionResult(
        boolean success,
        String reason,
        UUID transactionId,
        UUID senderAccountId,
        UUID receiverAccountId,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description
) {
    public static ApiTransactionResult ok(UUID transactionId,
                                          UUID senderAccountId,
                                          UUID receiverAccountId,
                                          BigDecimal amount,
                                          BigDecimal balanceAfter,
                                          String description) {
        return new ApiTransactionResult(
                true,
                "",
                transactionId,
                senderAccountId,
                receiverAccountId,
                amount == null ? BigDecimal.ZERO : amount,
                balanceAfter == null ? BigDecimal.ZERO : balanceAfter,
                description == null ? "" : description
        );
    }

    public static ApiTransactionResult fail(String reason,
                                            UUID senderAccountId,
                                            UUID receiverAccountId,
                                            BigDecimal amount,
                                            BigDecimal balanceAfter,
                                            String description) {
        return new ApiTransactionResult(
                false,
                reason == null ? "Unknown error" : reason,
                null,
                senderAccountId,
                receiverAccountId,
                amount == null ? BigDecimal.ZERO : amount,
                balanceAfter == null ? BigDecimal.ZERO : balanceAfter,
                description == null ? "" : description
        );
    }
}
