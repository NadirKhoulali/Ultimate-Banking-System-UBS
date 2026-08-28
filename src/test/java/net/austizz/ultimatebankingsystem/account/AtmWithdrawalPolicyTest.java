package net.austizz.ultimatebankingsystem.account;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmWithdrawalPolicyTest {

    @Test
    void bankSingleWithdrawalCeilingCannotBeBypassedByAtm() {
        AtmWithdrawalPolicy.Decision decision = evaluate("250", "500", "1000", "0", "100", "900", "5000");

        assertFalse(decision.allowed());
        assertEquals(AtmWithdrawalPolicy.Denial.BANK_SINGLE_LIMIT, decision.denial());
        assertEquals(new BigDecimal("100"), decision.availableNow());
    }

    @Test
    void bankPlayerDailyCeilingIncludesPriorOutgoingVolume() {
        AtmWithdrawalPolicy.Decision decision = AtmWithdrawalPolicy.evaluate(new AtmWithdrawalPolicy.Input(
                money("75"), money("500"), money("500"), money("1000"), money("50"),
                money("500"), money("100"), money("40"), money("5000"), money("0")));

        assertFalse(decision.allowed());
        assertEquals(AtmWithdrawalPolicy.Denial.BANK_PLAYER_DAILY_LIMIT, decision.denial());
        assertEquals(new BigDecimal("60"), decision.availableNow());
    }

    @Test
    void bankDailyCashCapacityLimitsEveryAccountAtThatBank() {
        AtmWithdrawalPolicy.Decision decision = AtmWithdrawalPolicy.evaluate(new AtmWithdrawalPolicy.Input(
                money("50"), money("500"), money("500"), money("1000"), money("0"),
                money("500"), money("1000"), money("0"), money("500"), money("475")));

        assertFalse(decision.allowed());
        assertEquals(AtmWithdrawalPolicy.Denial.BANK_DAILY_LIMIT, decision.denial());
        assertEquals(new BigDecimal("25"), decision.availableNow());
    }

    @Test
    void availableNowUsesLowestBalanceAndConfiguredCeiling() {
        AtmWithdrawalPolicy.Decision decision = AtmWithdrawalPolicy.evaluate(new AtmWithdrawalPolicy.Input(
                money("70"), money("90"), money("500"), money("100"), money("25"),
                money("200"), money("1000"), money("25"), money("5000"), money("0")));

        assertTrue(decision.allowed());
        assertEquals(AtmWithdrawalPolicy.Denial.NONE, decision.denial());
        assertEquals(new BigDecimal("75"), decision.availableNow());
    }

    private static AtmWithdrawalPolicy.Decision evaluate(String requested,
                                                         String balance,
                                                         String accountSingle,
                                                         String accountDailyUsed,
                                                         String bankSingle,
                                                         String bankDailyRemaining,
                                                         String bankTotalRemaining) {
        return AtmWithdrawalPolicy.evaluate(new AtmWithdrawalPolicy.Input(
                money(requested), money(balance), money(accountSingle), money("1000"), money(accountDailyUsed),
                money(bankSingle), money("1000"), money(accountDailyUsed),
                money("5000"), money("5000").subtract(money(bankTotalRemaining))));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
