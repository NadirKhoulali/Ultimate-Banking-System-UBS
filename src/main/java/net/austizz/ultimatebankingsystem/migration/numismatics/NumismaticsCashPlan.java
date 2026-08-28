package net.austizz.ultimatebankingsystem.migration.numismatics;

import java.util.ArrayList;
import java.util.List;

/** Deterministic compact decomposition into UBS physical tender. */
public final class NumismaticsCashPlan {
    private static final Denomination[] DENOMINATIONS = {
            new Denomination("hundred_dollar_money_stack", 1_000_000L),
            new Denomination("fifty_dollar_money_stack", 500_000L),
            new Denomination("twenty_dollar_money_stack", 200_000L),
            new Denomination("ten_dollar_money_stack", 100_000L),
            new Denomination("five_dollar_money_stack", 50_000L),
            new Denomination("two_dollar_money_stack", 20_000L),
            new Denomination("one_dollar_money_stack", 10_000L),
            new Denomination("hundred_dollar_bill", 10_000L),
            new Denomination("fifty_dollar_bill", 5_000L),
            new Denomination("twenty_dollar_bill", 2_000L),
            new Denomination("ten_dollar_bill", 1_000L),
            new Denomination("five_dollar_bill", 500L),
            new Denomination("two_dollar_bill", 200L),
            new Denomination("one_dollar_bill", 100L),
            new Denomination("half_dollar_coin", 50L),
            new Denomination("quarter_coin", 25L),
            new Denomination("dime_coin", 10L),
            new Denomination("nickel_coin", 5L),
            new Denomination("penny_coin", 1L)
    };

    private NumismaticsCashPlan() {
    }

    public static List<Output> plan(long cents) {
        if (cents <= 0L) return List.of();
        long remaining = cents;
        List<Output> result = new ArrayList<>();
        for (Denomination denomination : DENOMINATIONS) {
            long count = remaining / denomination.cents;
            remaining %= denomination.cents;
            while (count > 0L) {
                int stackCount = (int) Math.min(64L, count);
                result.add(new Output(denomination.itemPath, denomination.cents, stackCount));
                count -= stackCount;
            }
        }
        if (remaining != 0L) throw new IllegalStateException("Cash plan did not reconcile: " + remaining);
        return List.copyOf(result);
    }

    public static long totalCents(List<Output> outputs) {
        long total = 0L;
        if (outputs == null) return total;
        for (Output output : outputs) total = Math.addExact(total, Math.multiplyExact(output.centsEach(), output.count()));
        return total;
    }

    public record Output(String itemPath, long centsEach, int count) {
    }

    private record Denomination(String itemPath, long cents) {
    }
}
