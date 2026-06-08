package net.austizz.ultimatebankingsystem.shelf;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ShelfDisplayRules {
    private ShelfDisplayRules() {
    }

    public static boolean isAllowedDisplayItem(ItemStack stack) {
        return blockedReason(stack) == null;
    }

    public static String blockedReason(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "Select a valid inventory item.";
        }
        Item item = stack.getItem();
        if (item == null) {
            return "Select a valid inventory item.";
        }

        if (ShelfCartService.isBasketStack(stack)) {
            return "Shopping baskets cannot be displayed on shelves.";
        }
        if (item == ModItems.CHEQUE.get() || item == ModItems.BANK_NOTE.get()) {
            return "Cheques and bank notes cannot be sold through shelf displays.";
        }
        if (item == ModItems.CREDIT_CARD.get()) {
            return "Credit cards cannot be sold through shelf displays.";
        }
        if (DollarBills.isCashTenderItem(item)) {
            return "Cash bills and coins cannot be sold through shelf displays.";
        }
        if (item == ModItems.HANDHELD_PAYMENT_TERMINAL.get()) {
            return "Payment terminals cannot be sold through shelf displays.";
        }
        if (item == ModItems.BANK_TELLER_SPAWN_EGG.get() || item == ModItems.CASHIER_SPAWN_EGG.get()) {
            return "NPC spawn eggs cannot be sold through shelf displays.";
        }
        if (item == ModBlocks.TALL_WALL_SHELF.get().asItem()
                || item == ModBlocks.SHOP_SHELF.get().asItem()
                || item == ModBlocks.SHOP_SELLING_TABLE.get().asItem()
                || item == ModBlocks.CREATIVE_SHOP_SELLING_TABLE.get().asItem()
                || item == ModBlocks.SHOP_SELLING_TABLE_LARGE.get().asItem()
                || item == ModBlocks.CREATIVE_SHOP_SELLING_TABLE_LARGE.get().asItem()
                || item == ModBlocks.MODULAR_WALL_DISPLAY.get().asItem()
                || item == ModBlocks.CREATIVE_MODULAR_WALL_DISPLAY.get().asItem()
                || item == ModBlocks.GLASS_COUNTER_DISPLAY.get().asItem()
                || item == ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY.get().asItem()
                || item == ModBlocks.GLASS_COUNTER_DISPLAY_OPEN.get().asItem()
                || item == ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY_OPEN.get().asItem()
                || item == ModBlocks.SHOPPING_BASKET.get().asItem()
                || item == ModBlocks.SHOPPING_BASKET_HOLDER.get().asItem()) {
            return "Display fixtures and baskets cannot be sold through shelf displays.";
        }
        return null;
    }
}
