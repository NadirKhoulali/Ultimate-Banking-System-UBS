package net.austizz.ultimatebankingsystem.item;

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

            int maxStack = cashItem.getMaxStackSize();
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
