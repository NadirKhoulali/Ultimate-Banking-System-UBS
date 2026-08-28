package net.austizz.ultimatebankingsystem.account;

import java.math.BigDecimal;

/**
 * Pure ATM cash-withdrawal policy shared by server authorization and ATM projections.
 */
public final class AtmWithdrawalPolicy {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private AtmWithdrawalPolicy() {
    }

    public static Decision evaluate(Input raw) {
        Input input = raw == null ? Input.empty() : raw.normalized();
        BigDecimal accountDailyRemaining = remaining(input.accountDailyLimit(), input.accountDailyUsed());
        BigDecimal bankPlayerDailyRemaining = remaining(input.bankPlayerDailyLimit(), input.bankPlayerDailyUsed());
        BigDecimal bankDailyRemaining = remaining(input.bankDailyLimit(), input.bankDailyUsed());
        BigDecimal availableNow = minimum(
                input.balance(),
                input.accountSingleLimit(),
                accountDailyRemaining,
                input.bankSingleLimit(),
                bankPlayerDailyRemaining,
                bankDailyRemaining
        );

        BigDecimal requested = input.requested();
        if (requested.signum() <= 0) {
            return denied(Denial.INVALID_AMOUNT, availableNow, accountDailyRemaining,
                    bankPlayerDailyRemaining, bankDailyRemaining);
        }
        if (requested.compareTo(input.balance()) > 0) {
            return denied(Denial.INSUFFICIENT_FUNDS, availableNow, accountDailyRemaining,
                    bankPlayerDailyRemaining, bankDailyRemaining);
        }
        if (requested.compareTo(input.accountSingleLimit()) > 0) {
            return denied(Denial.ACCOUNT_SINGLE_LIMIT, availableNow, accountDailyRemaining,
                    bankPlayerDailyRemaining, bankDailyRemaining);
        }
        if (requested.compareTo(input.bankSingleLimit()) > 0) {
            return denied(Denial.BANK_SINGLE_LIMIT, availableNow, accountDailyRemaining,
                    bankPlayerDailyRemaining, bankDailyRemaining);
        }
        if (requested.compareTo(accountDailyRemaining) > 0) {
            return denied(Denial.ACCOUNT_DAILY_LIMIT, availableNow, accountDailyRemaining,
                    bankPlayerDailyRemaining, bankDailyRemaining);
        }
        if (requested.compareTo(bankPlayerDailyRemaining) > 0) {
            return denied(Denial.BANK_PLAYER_DAILY_LIMIT, availableNow, accountDailyRemaining,
                    bankPlayerDailyRemaining, bankDailyRemaining);
        }
        if (requested.compareTo(bankDailyRemaining) > 0) {
            return denied(Denial.BANK_DAILY_LIMIT, availableNow, accountDailyRemaining,
                    bankPlayerDailyRemaining, bankDailyRemaining);
        }
        return new Decision(true, Denial.NONE, availableNow, accountDailyRemaining,
                bankPlayerDailyRemaining, bankDailyRemaining);
    }

    private static Decision denied(Denial denial,
                                   BigDecimal availableNow,
                                   BigDecimal accountDailyRemaining,
                                   BigDecimal bankPlayerDailyRemaining,
                                   BigDecimal bankDailyRemaining) {
        return new Decision(false, denial, availableNow, accountDailyRemaining,
                bankPlayerDailyRemaining, bankDailyRemaining);
    }

    private static BigDecimal remaining(BigDecimal limit, BigDecimal used) {
        return limit.subtract(used).max(ZERO);
    }

    private static BigDecimal minimum(BigDecimal first, BigDecimal... rest) {
        BigDecimal minimum = first.max(ZERO);
        for (BigDecimal value : rest) {
            if (value.compareTo(minimum) < 0) {
                minimum = value;
            }
        }
        return minimum.max(ZERO);
    }

    public enum Denial {
        NONE,
        INVALID_AMOUNT,
        INSUFFICIENT_FUNDS,
        ACCOUNT_SINGLE_LIMIT,
        ACCOUNT_DAILY_LIMIT,
        BANK_SINGLE_LIMIT,
        BANK_PLAYER_DAILY_LIMIT,
        BANK_DAILY_LIMIT
    }

    public record Input(BigDecimal requested,
                        BigDecimal balance,
                        BigDecimal accountSingleLimit,
                        BigDecimal accountDailyLimit,
                        BigDecimal accountDailyUsed,
                        BigDecimal bankSingleLimit,
                        BigDecimal bankPlayerDailyLimit,
                        BigDecimal bankPlayerDailyUsed,
                        BigDecimal bankDailyLimit,
                        BigDecimal bankDailyUsed) {
        private static Input empty() {
            return new Input(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
        }

        private Input normalized() {
            return new Input(nonNegative(requested), nonNegative(balance), nonNegative(accountSingleLimit),
                    nonNegative(accountDailyLimit), nonNegative(accountDailyUsed), nonNegative(bankSingleLimit),
                    nonNegative(bankPlayerDailyLimit), nonNegative(bankPlayerDailyUsed),
                    nonNegative(bankDailyLimit), nonNegative(bankDailyUsed));
        }

        private static BigDecimal nonNegative(BigDecimal value) {
            return value == null ? ZERO : value.max(ZERO);
        }
    }

    public record Decision(boolean allowed,
                           Denial denial,
                           BigDecimal availableNow,
                           BigDecimal accountDailyRemaining,
                           BigDecimal bankPlayerDailyRemaining,
                           BigDecimal bankDailyRemaining) {
    }
}
