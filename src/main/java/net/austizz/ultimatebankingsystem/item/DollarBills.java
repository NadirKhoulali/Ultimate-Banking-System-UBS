package net.austizz.ultimatebankingsystem.item;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;

/**
 * Utility methods for handling physical USD bill items.
 */
public final class DollarBills {

    public static final int[] DENOMINATIONS_DESC = {100, 50, 20, 10, 5, 2, 1};

    private DollarBills() {}

    public static int[] buildWithdrawPlan(int dollars) {
        int[] counts = new int[DENOMINATIONS_DESC.length];
        int remaining = dollars;
        for (int i = 0; i < DENOMINATIONS_DESC.length; i++) {
            int denom = DENOMINATIONS_DESC[i];
            int count = remaining / denom;
            counts[i] = count;
            remaining -= count * denom;
        }
        return remaining == 0 ? counts : null;
    }

    public static int[] getAvailableBillCounts(ServerPlayer player) {
        int[] counts = new int[DENOMINATIONS_DESC.length];
        tallyStacks(player.getInventory().items, counts);
        tallyStacks(player.getInventory().offhand, counts);
        return counts;
    }

    public static int[] findDepositPlan(int dollars, int[] available) {
        int[] selected = new int[DENOMINATIONS_DESC.length];
        return searchPlan(0, dollars, available, selected) ? selected : null;
    }

    public static int totalValue(int[] counts) {
        int total = 0;
        for (int i = 0; i < DENOMINATIONS_DESC.length && i < counts.length; i++) {
            total += DENOMINATIONS_DESC[i] * Math.max(0, counts[i]);
        }
        return total;
    }

    public static void removeBills(ServerPlayer player, int[] plan) {
        int[] remaining = plan.clone();
        removeFromStacks(player.getInventory().items, remaining);
        removeFromStacks(player.getInventory().offhand, remaining);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    public static void giveBills(ServerPlayer player, int[] plan) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < DENOMINATIONS_DESC.length && i < plan.length; i++) {
            int remaining = Math.max(0, plan[i]);
            if (remaining <= 0) {
                continue;
            }

            Item billItem = getItemForDenomination(DENOMINATIONS_DESC[i]);
            if (billItem == null) {
                continue;
            }

            int maxStack = billItem.getDefaultMaxStackSize();
            while (remaining > 0) {
                int giveCount = Math.min(maxStack, remaining);
                ItemStack stack = new ItemStack(billItem, giveCount);
                if (!inventory.add(stack)) {
                    player.drop(stack, false);
                }
                remaining -= giveCount;
            }
        }

        inventory.setChanged();
        player.containerMenu.broadcastChanges();
    }

    public static String formatPlan(int[] plan) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < DENOMINATIONS_DESC.length && i < plan.length; i++) {
            int count = plan[i];
            if (count <= 0) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append("$").append(DENOMINATIONS_DESC[i]).append("x").append(count);
        }
        return out.isEmpty() ? "(none)" : out.toString();
    }

    private static boolean searchPlan(int index, int remaining, int[] available, int[] selected) {
        if (remaining == 0) {
            return true;
        }
        if (index >= DENOMINATIONS_DESC.length) {
            return false;
        }

        int denom = DENOMINATIONS_DESC[index];
        int maxTake = Math.min(available[index], remaining / denom);
        for (int take = maxTake; take >= 0; take--) {
            selected[index] = take;
            if (searchPlan(index + 1, remaining - (take * denom), available, selected)) {
                return true;
            }
        }
        selected[index] = 0;
        return false;
    }

    private static void tallyStacks(NonNullList<ItemStack> stacks, int[] counts) {
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int idx = indexForItem(stack.getItem());
            if (idx >= 0) {
                counts[idx] += stack.getCount();
            }
        }
    }

    private static void removeFromStacks(NonNullList<ItemStack> stacks, int[] remaining) {
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            int idx = indexForItem(stack.getItem());
            if (idx < 0 || remaining[idx] <= 0) {
                continue;
            }

            int remove = Math.min(remaining[idx], stack.getCount());
            stack.shrink(remove);
            remaining[idx] -= remove;
        }
    }

    private static int indexForItem(Item item) {
        if (item == ModItems.HUNDRED_DOLLAR_BILL.get()) return 0;
        if (item == ModItems.FIFTY_DOLLAR_BILL.get()) return 1;
        if (item == ModItems.TWENTY_DOLLAR_BILL.get()) return 2;
        if (item == ModItems.TEN_DOLLAR_BILL.get()) return 3;
        if (item == ModItems.FIVE_DOLLAR_BILL.get()) return 4;
        if (item == ModItems.TWO_DOLLAR_BILL.get()) return 5;
        if (item == ModItems.ONE_DOLLAR_BILL.get()) return 6;
        return -1;
    }

    private static Item getItemForDenomination(int denomination) {
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
}
