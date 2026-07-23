package net.austizz.ultimatebankingsystem.item;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Utility methods for money strap bundling: counting, removing and giving a single
 * item type across a player's main inventory and offhand.
 */
public final class MoneyBundles {

    public static final int BILLS_PER_STACK = 100;

    private MoneyBundles() {}

    public static int countItem(Player player, Item item) {
        int total = 0;
        total += tallyStacks(player.getInventory().items, item);
        total += tallyStacks(player.getInventory().offhand, item);
        return total;
    }

    public static boolean removeItem(Player player, Item item, int count) {
        if (count <= 0) {
            return true;
        }
        if (countItem(player, item) < count) {
            return false;
        }

        int remaining = count;
        remaining = removeFromStacks(player.getInventory().items, item, remaining);
        remaining = removeFromStacks(player.getInventory().offhand, item, remaining);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return remaining == 0;
    }

    public static void giveItem(ServerPlayer player, Item item, int count) {
        if (item == null || count <= 0) {
            return;
        }

        Inventory inventory = player.getInventory();
        int maxStack = new ItemStack(item).getMaxStackSize();
        int remaining = count;
        while (remaining > 0) {
            int giveCount = Math.min(maxStack, remaining);
            ItemStack stack = new ItemStack(item, giveCount);
            if (!inventory.add(stack)) {
                player.drop(stack, false);
            }
            remaining -= giveCount;
        }

        inventory.setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static int tallyStacks(NonNullList<ItemStack> stacks, Item item) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty() || stack.getItem() != item) {
                continue;
            }
            total += stack.getCount();
        }
        return total;
    }

    private static int removeFromStacks(NonNullList<ItemStack> stacks, Item item, int remaining) {
        for (ItemStack stack : stacks) {
            if (remaining <= 0) {
                break;
            }
            if (stack == null || stack.isEmpty() || stack.getItem() != item) {
                continue;
            }

            int remove = Math.min(remaining, stack.getCount());
            stack.shrink(remove);
            remaining -= remove;
        }
        return remaining;
    }
}
