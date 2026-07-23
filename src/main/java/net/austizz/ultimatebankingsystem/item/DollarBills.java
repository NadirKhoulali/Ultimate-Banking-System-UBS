package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.block.custom.MoneyStackBlock;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigDecimal;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/**
 * Utility methods for handling physical USD cash items (bills + coins).
 */
public final class DollarBills {

    public static final int[] DENOMINATIONS_DESC = {100, 50, 20, 10, 5, 2, 1};
    public static final int[] CASH_DENOMINATIONS_CENTS_DESC = {10_000, 5_000, 2_000, 1_000, 500, 200, 100, 50, 25, 10, 5, 1};

    private DollarBills() {}

    public static int[] buildWithdrawPlan(int dollars) {
        return buildWithdrawPlanForDenoms(dollars, DENOMINATIONS_DESC);
    }

    public static int[] getAvailableBillCounts(ServerPlayer player) {
        int[] counts = new int[DENOMINATIONS_DESC.length];
        tallyStacks(player.getInventory().items, counts, DollarBills::indexForItem);
        tallyStacks(player.getInventory().offhand, counts, DollarBills::indexForItem);
        return counts;
    }

    public static int[] findDepositPlan(int dollars, int[] available) {
        int[] selected = new int[DENOMINATIONS_DESC.length];
        return searchPlan(0, dollars, available, selected, DENOMINATIONS_DESC) ? selected : null;
    }

    public static int totalValue(int[] counts) {
        return totalValueForDenoms(counts, DENOMINATIONS_DESC);
    }

    public static void removeBills(ServerPlayer player, int[] plan) {
        int[] remaining = plan.clone();
        removeFromStacks(player.getInventory().items, remaining, DollarBills::indexForItem);
        removeFromStacks(player.getInventory().offhand, remaining, DollarBills::indexForItem);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    public static void giveBills(ServerPlayer player, int[] plan) {
        giveFromPlan(player, plan, DENOMINATIONS_DESC, DollarBills::getItemForDenomination);
    }

    public static String formatPlan(int[] plan) {
        return formatPlanForDenoms(plan, DENOMINATIONS_DESC);
    }

    public static int[] buildCashWithdrawPlan(int cents) {
        return buildWithdrawPlanForDenoms(cents, CASH_DENOMINATIONS_CENTS_DESC);
    }

    public static int[] getAvailableCashCounts(ServerPlayer player) {
        int[] counts = new int[CASH_DENOMINATIONS_CENTS_DESC.length];
        tallyStacks(player.getInventory().items, counts, DollarBills::cashIndexForItem);
        tallyStacks(player.getInventory().offhand, counts, DollarBills::cashIndexForItem);
        return counts;
    }

    public static int[] findCashDepositPlan(int cents, int[] available) {
        int[] selected = new int[CASH_DENOMINATIONS_CENTS_DESC.length];
        return searchPlan(0, cents, available, selected, CASH_DENOMINATIONS_CENTS_DESC) ? selected : null;
    }

    public static int totalCashValueCents(int[] counts) {
        return totalValueForDenoms(counts, CASH_DENOMINATIONS_CENTS_DESC);
    }

    /**
     * Long-safe total for tender counts that may include money-stack inflation
     * (stacks tally as 100 bills each, so a full inventory of hundred-dollar
     * stacks exceeds Integer.MAX_VALUE cents). Clamped consumers should use
     * this instead of {@link #totalCashValueCents(int[])}.
     */
    public static long totalCashValueCentsLong(int[] counts) {
        long total = 0L;
        for (int i = 0; i < counts.length && i < CASH_DENOMINATIONS_CENTS_DESC.length; i++) {
            total += (long) counts[i] * CASH_DENOMINATIONS_CENTS_DESC[i];
        }
        return total;
    }

    public static void removeCash(ServerPlayer player, int[] plan) {
        int[] remaining = plan.clone();
        removeFromStacks(player.getInventory().items, remaining, DollarBills::cashIndexForItem);
        removeFromStacks(player.getInventory().offhand, remaining, DollarBills::cashIndexForItem);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    public static void giveCash(ServerPlayer player, int[] plan) {
        giveFromPlan(player, plan, CASH_DENOMINATIONS_CENTS_DESC, DollarBills::getCashItemForDenominationCents);
    }

    public static String formatCashPlan(int[] plan) {
        return formatPlanForDenoms(plan, CASH_DENOMINATIONS_CENTS_DESC);
    }

    public static boolean isCashTenderItem(Item item) {
        return cashIndexForItem(item) >= 0;
    }

    public static final int BILLS_PER_MONEY_STACK = 100;

    public static int billIndexForMoneyStackItem(Item item) {
        MoneyStackBlock.BillDenomination denomination = MoneyStackBlock.BillDenomination.fromStackItem(item);
        return denomination == null ? -1 : cashIndexForItem(denomination.billItem());
    }

    public static boolean isMoneyStackItem(Item item) {
        return billIndexForMoneyStackItem(item) >= 0;
    }

    public static boolean isPhysicalTenderItem(Item item) {
        return isCashTenderItem(item) || isMoneyStackItem(item);
    }

    public static long physicalTenderCents(Item item) {
        int cashIndex = cashIndexForItem(item);
        if (cashIndex >= 0) {
            return CASH_DENOMINATIONS_CENTS_DESC[cashIndex];
        }
        int billIndex = billIndexForMoneyStackItem(item);
        if (billIndex >= 0) {
            return (long) BILLS_PER_MONEY_STACK * CASH_DENOMINATIONS_CENTS_DESC[billIndex];
        }
        return 0L;
    }

    /**
     * Like {@link #getAvailableCashCounts(ServerPlayer)} but also counts carried money stacks
     * as {@value #BILLS_PER_MONEY_STACK} loose bills of their denomination. Still length 12.
     */
    public static int[] getAvailableTenderAsCashCounts(ServerPlayer player) {
        int[] counts = getAvailableCashCounts(player);
        tallyMoneyStacksAsBills(player.getInventory().items, counts);
        tallyMoneyStacksAsBills(player.getInventory().offhand, counts);
        return counts;
    }

    /**
     * Like {@link #removeCash(ServerPlayer, int[])} but may break money stacks: loose bills and
     * coins are consumed first; any bill shortfall breaks straps and returns the leftover of each
     * broken stack to the player as loose bills. Coins are always consumed purely loose.
     */
    public static void removeTender(ServerPlayer player, int[] plan) {
        int[] remaining = plan.clone();
        removeFromStacks(player.getInventory().items, remaining, DollarBills::cashIndexForItem);
        removeFromStacks(player.getInventory().offhand, remaining, DollarBills::cashIndexForItem);

        int[] residue = new int[CASH_DENOMINATIONS_CENTS_DESC.length];
        boolean hasResidue = false;
        for (int i = 0; i < remaining.length && i < CASH_DENOMINATIONS_CENTS_DESC.length; i++) {
            int shortfall = remaining[i];
            if (shortfall <= 0) {
                continue;
            }
            int neededStacks = (shortfall + BILLS_PER_MONEY_STACK - 1) / BILLS_PER_MONEY_STACK;
            int broken = breakMoneyStacks(player, i, neededStacks);
            int covered = Math.min(shortfall, broken * BILLS_PER_MONEY_STACK);
            remaining[i] -= covered;
            int leftover = broken * BILLS_PER_MONEY_STACK - covered;
            if (leftover > 0) {
                residue[i] += leftover;
                hasResidue = true;
            }
        }

        if (hasResidue) {
            giveCash(player, residue);
        } else {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    private static void tallyMoneyStacksAsBills(NonNullList<ItemStack> stacks, int[] counts) {
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int billIndex = billIndexForMoneyStackItem(stack.getItem());
            if (billIndex >= 0) {
                counts[billIndex] += BILLS_PER_MONEY_STACK * stack.getCount();
            }
        }
    }

    private static int breakMoneyStacks(ServerPlayer player, int billIndex, int needed) {
        int broken = breakMoneyStacksFrom(player.getInventory().items, billIndex, needed);
        if (broken < needed) {
            broken += breakMoneyStacksFrom(player.getInventory().offhand, billIndex, needed - broken);
        }
        return broken;
    }

    private static int breakMoneyStacksFrom(NonNullList<ItemStack> stacks, int billIndex, int needed) {
        int broken = 0;
        for (ItemStack stack : stacks) {
            if (broken >= needed) {
                break;
            }
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (billIndexForMoneyStackItem(stack.getItem()) != billIndex) {
                continue;
            }
            int take = Math.min(needed - broken, stack.getCount());
            stack.shrink(take);
            broken += take;
        }
        return broken;
    }

    public static int cashCentsForItem(Item item) {
        int idx = cashIndexForItem(item);
        return idx < 0 ? 0 : CASH_DENOMINATIONS_CENTS_DESC[idx];
    }

    public static int cashIndexForItem(Item item) {
        if (item == ModItems.HUNDRED_DOLLAR_BILL.get()) return 0;
        if (item == ModItems.FIFTY_DOLLAR_BILL.get()) return 1;
        if (item == ModItems.TWENTY_DOLLAR_BILL.get()) return 2;
        if (item == ModItems.TEN_DOLLAR_BILL.get()) return 3;
        if (item == ModItems.FIVE_DOLLAR_BILL.get()) return 4;
        if (item == ModItems.TWO_DOLLAR_BILL.get()) return 5;
        if (item == ModItems.ONE_DOLLAR_BILL.get()) return 6;
        if (item == ModItems.HALF_DOLLAR_COIN.get()) return 7;
        if (item == ModItems.QUARTER_COIN.get()) return 8;
        if (item == ModItems.DIME_COIN.get()) return 9;
        if (item == ModItems.NICKEL_COIN.get()) return 10;
        if (item == ModItems.PENNY_COIN.get()) return 11;
        return -1;
    }

    public static int cashDenominationCentsForIndex(int index) {
        if (index < 0 || index >= CASH_DENOMINATIONS_CENTS_DESC.length) {
            return 0;
        }
        return CASH_DENOMINATIONS_CENTS_DESC[index];
    }

    public static Item getCashItemForDenominationCents(int denominationCents) {
        return switch (denominationCents) {
            case 10_000 -> ModItems.HUNDRED_DOLLAR_BILL.get();
            case 5_000 -> ModItems.FIFTY_DOLLAR_BILL.get();
            case 2_000 -> ModItems.TWENTY_DOLLAR_BILL.get();
            case 1_000 -> ModItems.TEN_DOLLAR_BILL.get();
            case 500 -> ModItems.FIVE_DOLLAR_BILL.get();
            case 200 -> ModItems.TWO_DOLLAR_BILL.get();
            case 100 -> ModItems.ONE_DOLLAR_BILL.get();
            case 50 -> ModItems.HALF_DOLLAR_COIN.get();
            case 25 -> ModItems.QUARTER_COIN.get();
            case 10 -> ModItems.DIME_COIN.get();
            case 5 -> ModItems.NICKEL_COIN.get();
            case 1 -> ModItems.PENNY_COIN.get();
            default -> null;
        };
    }

    public static String formatCents(int cents) {
        if (cents % 100 == 0) {
            return Integer.toString(cents / 100);
        }
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    private static int[] buildWithdrawPlanForDenoms(int amount, int[] denominationsDesc) {
        if (amount <= 0) {
            return null;
        }
        int[] counts = new int[denominationsDesc.length];
        int remaining = amount;
        for (int i = 0; i < denominationsDesc.length; i++) {
            int denom = denominationsDesc[i];
            int count = remaining / denom;
            counts[i] = count;
            remaining -= count * denom;
        }
        return remaining == 0 ? counts : null;
    }

    private static int totalValueForDenoms(int[] counts, int[] denominationsDesc) {
        int total = 0;
        for (int i = 0; i < denominationsDesc.length && i < counts.length; i++) {
            total += denominationsDesc[i] * Math.max(0, counts[i]);
        }
        return total;
    }

    private static void giveFromPlan(ServerPlayer player,
                                     int[] plan,
                                     int[] denominationsDesc,
                                     IntFunction<Item> itemResolver) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < denominationsDesc.length && i < plan.length; i++) {
            int remaining = Math.max(0, plan[i]);
            if (remaining <= 0) {
                continue;
            }

            Item cashItem = itemResolver.apply(denominationsDesc[i]);
            if (cashItem == null) {
                continue;
            }

            int maxStack = new ItemStack(cashItem).getMaxStackSize();
            while (remaining > 0) {
                int giveCount = Math.min(maxStack, remaining);
                ItemStack stack = new ItemStack(cashItem, giveCount);
                if (!inventory.add(stack)) {
                    player.drop(stack, false);
                }
                remaining -= giveCount;
            }
        }

        inventory.setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static String formatPlanForDenoms(int[] plan, int[] denominationsDesc) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < denominationsDesc.length && i < plan.length; i++) {
            int count = plan[i];
            if (count <= 0) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append("$").append(formatCents(denominationsDesc[i])).append("x").append(count);
        }
        return out.isEmpty() ? "(none)" : out.toString();
    }

    private static boolean searchPlan(int index, int remaining, int[] available, int[] selected, int[] denominationsDesc) {
        if (remaining == 0) {
            return true;
        }
        if (index >= denominationsDesc.length) {
            return false;
        }

        int denom = denominationsDesc[index];
        int maxTake = Math.min(available[index], remaining / denom);
        for (int take = maxTake; take >= 0; take--) {
            selected[index] = take;
            if (searchPlan(index + 1, remaining - (take * denom), available, selected, denominationsDesc)) {
                return true;
            }
        }
        selected[index] = 0;
        return false;
    }

    private static void tallyStacks(NonNullList<ItemStack> stacks, int[] counts, ToIntFunction<Item> indexResolver) {
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int idx = indexResolver.applyAsInt(stack.getItem());
            if (idx >= 0) {
                counts[idx] += stack.getCount();
            }
        }
    }

    private static void removeFromStacks(NonNullList<ItemStack> stacks, int[] remaining, ToIntFunction<Item> indexResolver) {
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            int idx = indexResolver.applyAsInt(stack.getItem());
            if (idx < 0 || remaining[idx] <= 0) {
                continue;
            }

            int remove = Math.min(remaining[idx], stack.getCount());
            stack.shrink(remove);
            remaining[idx] -= remove;
        }
    }

    public static int indexForItem(Item item) {
        if (item == ModItems.HUNDRED_DOLLAR_BILL.get()) return 0;
        if (item == ModItems.FIFTY_DOLLAR_BILL.get()) return 1;
        if (item == ModItems.TWENTY_DOLLAR_BILL.get()) return 2;
        if (item == ModItems.TEN_DOLLAR_BILL.get()) return 3;
        if (item == ModItems.FIVE_DOLLAR_BILL.get()) return 4;
        if (item == ModItems.TWO_DOLLAR_BILL.get()) return 5;
        if (item == ModItems.ONE_DOLLAR_BILL.get()) return 6;
        return -1;
    }

    public static Item getItemForDenomination(int denomination) {
        return switch (denomination) {
            case 100 -> ModItems.HUNDRED_DOLLAR_BILL.get();
            case 50 -> ModItems.FIFTY_DOLLAR_BILL.get();
            case 20 -> ModItems.TWENTY_DOLLAR_BILL.get();
            case 10 -> ModItems.TEN_DOLLAR_BILL.get();
            case 5 -> ModItems.FIVE_DOLLAR_BILL.get();
            case 2 -> ModItems.TWO_DOLLAR_BILL.get();
            case 1 -> ModItems.ONE_DOLLAR_BILL.get();
            default -> null;
        };
    }

    public static Item getItemForIndex(int index) {
        if (index < 0 || index >= DENOMINATIONS_DESC.length) {
            return null;
        }
        return getItemForDenomination(DENOMINATIONS_DESC[index]);
    }
}
