package net.austizz.ultimatebankingsystem.api.economy;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@ApiStatus.AvailableSince("2.1.0")
public record ApiEconomyTransactionSnapshot(
        UUID transactionId,
        UUID senderAccountId,
        UUID receiverAccountId,
        BigDecimal amount,
        LocalDateTime occurredAt,
        String description
) {
    public ApiEconomyTransactionSnapshot {
        amount = amount == null ? BigDecimal.ZERO : amount;
        description = description == null ? "" : description;
    }
}
