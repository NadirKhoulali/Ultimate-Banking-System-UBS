package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@ApiStatus.AvailableSince("1.0.0")
public record ApiTransactionSnapshot(
        UUID transactionId,
        UUID senderAccountId,
        UUID receiverAccountId,
        BigDecimal amount,
        LocalDateTime timestamp,
        String description
) {}
