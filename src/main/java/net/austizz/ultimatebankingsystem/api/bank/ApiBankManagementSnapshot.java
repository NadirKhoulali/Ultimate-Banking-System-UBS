package net.austizz.ultimatebankingsystem.api.bank;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.util.UUID;

@ApiStatus.AvailableSince("2.0.0")
public record ApiBankManagementSnapshot(UUID bankId,
                                        String name,
                                        UUID ownerId,
                                        boolean centralBank,
                                        String status,
                                        int accountCount,
                                        BigDecimal totalDeposits,
                                        BigDecimal reserve,
                                        BigDecimal minimumRequiredReserve,
                                        BigDecimal reserveRatio,
                                        BigDecimal outstandingLoans,
                                        BigDecimal maxLendableAmount,
                                        double interestRate,
                                        int premiseCount,
                                        int safeAreaCount,
                                        int vaultCount,
                                        int readyVaultCount,
                                        int employeeCount,
                                        int tellerCount,
                                        boolean underAttack) {
    public ApiBankManagementSnapshot {
        name = name == null ? "" : name;
        status = status == null ? "UNKNOWN" : status;
        accountCount = Math.max(0, accountCount);
        totalDeposits = nonNull(totalDeposits);
        reserve = nonNull(reserve);
        minimumRequiredReserve = nonNull(minimumRequiredReserve);
        reserveRatio = nonNull(reserveRatio);
        outstandingLoans = nonNull(outstandingLoans);
        maxLendableAmount = nonNull(maxLendableAmount);
        premiseCount = Math.max(0, premiseCount);
        safeAreaCount = Math.max(0, safeAreaCount);
        vaultCount = Math.max(0, vaultCount);
        readyVaultCount = Math.max(0, readyVaultCount);
        employeeCount = Math.max(0, employeeCount);
        tellerCount = Math.max(0, tellerCount);
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
