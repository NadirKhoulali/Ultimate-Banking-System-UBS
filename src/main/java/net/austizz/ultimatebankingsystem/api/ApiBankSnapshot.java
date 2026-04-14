package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.util.UUID;

@ApiStatus.AvailableSince("1.1.0")
public record ApiBankSnapshot(
        UUID bankId,
        String bankName,
        UUID ownerId,
        String status,
        BigDecimal declaredReserve,
        BigDecimal totalDeposits,
        BigDecimal minimumRequiredReserve,
        BigDecimal reserveRatio,
        BigDecimal outstandingLoanBalance,
        BigDecimal maxLendableAmount,
        double interestRate,
        int accountCount
) {}
