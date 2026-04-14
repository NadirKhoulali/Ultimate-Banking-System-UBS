package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@ApiStatus.AvailableSince("1.0.0")
public record ApiAccountSnapshot(
        UUID accountId,
        UUID playerId,
        UUID bankId,
        String accountType,
        String accountTypeLabel,
        BigDecimal balance,
        boolean primary,
        boolean frozen,
        String frozenReason,
        LocalDateTime createdAt
) {}
