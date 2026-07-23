package net.austizz.ultimatebankingsystem.shelf;

import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ShelfCartService {
    private static final String TAG_ENTRIES = "ubs_basket_entries";
    private static final String TAG_STACK = "stack";
    private static final String TAG_QTY = "qty";
    private static final String TAG_PRICE = "unit_price";
    private static final String TAG_PRICE_IN_CENTS = "unit_price_in_cents";
    private static final String TAG_SOURCE = "source";

    private ShelfCartService() {
    }

    public record BasketEntryView(ItemStack stack,
                                  int quantity,
                                  long unitPriceCents,
                                  String source) {}

    public static boolean isBasketStack(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.is(ModBlocks.SHOPPING_BASKET.get().asItem());
    }

    public static int addItem(ItemStack basket, ItemStack displayItem, long unitPriceCents) {
        return addItem(basket, displayItem, unitPriceCents, "");
    }

    public static int addItem(ItemStack basket, ItemStack displayItem, long unitPriceCents, String source) {
        if (!isBasketStack(basket) || displayItem == null || displayItem.isEmpty() || unitPriceCents < 0L) {
            return 0;
        }
        String normalizedSource = source == null ? "" : source.trim();

        ItemStack normalized = normalizeStack(displayItem);
        List<BasketEntry> entries = readEntries(basket);
        for (BasketEntry entry : entries) {
            if (entry.unitPriceCents == unitPriceCents
                    && entry.source.equalsIgnoreCase(normalizedSource)
                    && ItemStackDataCompat.sameItemSameComponents(entry.stack, normalized)) {
                entry.quantity += 1;
                writeEntries(basket, entries);
                return entry.quantity;
            }
        }

        BasketEntry entry = new BasketEntry();
        entry.stack = normalized;
        entry.unitPriceCents = unitPriceCents;
        entry.quantity = 1;
        entry.source = normalizedSource;
        entries.add(entry);
        writeEntries(basket, entries);
        return 1;
    }

    public static int removeItem(ItemStack basket, ItemStack displayItem, long unitPriceCents) {
        return removeItem(basket, displayItem, unitPriceCents, "");
    }

    public static int removeItem(ItemStack basket, ItemStack displayItem, long unitPriceCents, String source) {
        if (!isBasketStack(basket) || displayItem == null || displayItem.isEmpty() || unitPriceCents < 0L) {
            return -1;
        }
        String normalizedSource = source == null ? "" : source.trim();

        ItemStack normalized = normalizeStack(displayItem);
        List<BasketEntry> entries = readEntries(basket);

        if (!normalizedSource.isBlank()) {
            for (int i = 0; i < entries.size(); i++) {
                BasketEntry entry = entries.get(i);
                if (entry.unitPriceCents != unitPriceCents
                        || !entry.source.equalsIgnoreCase(normalizedSource)
                        || !ItemStackDataCompat.sameItemSameComponents(entry.stack, normalized)) {
                    continue;
                }
                entry.quantity -= 1;
                int remaining = Math.max(0, entry.quantity);
                if (entry.quantity <= 0) {
                    entries.remove(i);
                }
                writeEntries(basket, entries);
                return remaining;
            }
        }

        for (int i = 0; i < entries.size(); i++) {
            BasketEntry entry = entries.get(i);
            if (entry.unitPriceCents != unitPriceCents
                    || !ItemStackDataCompat.sameItemSameComponents(entry.stack, normalized)) {
                continue;
            }
            entry.quantity -= 1;
            int remaining = Math.max(0, entry.quantity);
            if (entry.quantity <= 0) {
                entries.remove(i);
            }
            writeEntries(basket, entries);
            return remaining;
        }
        return -1;
    }

    public static List<BasketEntryView> getEntries(ItemStack basket) {
        List<BasketEntryView> out = new ArrayList<>();
        for (BasketEntry entry : readEntries(basket)) {
            if (entry == null || entry.stack == null || entry.stack.isEmpty() || entry.quantity <= 0 || entry.unitPriceCents < 0L) {
                continue;
            }
            out.add(new BasketEntryView(
                    normalizeStack(entry.stack),
                    Math.max(0, entry.quantity),
                    Math.max(0L, entry.unitPriceCents),
                    entry.source == null ? "" : entry.source.trim()
            ));
        }
        return out;
    }

    public static long getTotalPriceDollars(ItemStack basket) {
        long total = 0L;
        for (BasketEntry entry : readEntries(basket)) {
            if (entry.quantity <= 0 || entry.unitPriceCents < 0L) {
                continue;
            }
            long line;
            try {
                line = Math.multiplyExact(entry.unitPriceCents, entry.quantity);
            } catch (ArithmeticException overflow) {
                line = Long.MAX_VALUE;
            }
            try {
                total = Math.addExact(total, line);
            } catch (ArithmeticException overflow) {
                total = Long.MAX_VALUE;
            }
        }
        return Math.max(0L, total);
    }

    public static int getTotalUnits(ItemStack basket) {
        int total = 0;
        for (BasketEntry entry : readEntries(basket)) {
            total += Math.max(0, entry.quantity);
        }
        return Math.max(0, total);
    }

    public static List<Component> buildTooltip(ItemStack basket) {
        List<BasketEntry> entries = readEntries(basket);
        List<Component> lines = new ArrayList<>();
        lines.add(UbsTranslations.literal("Shopping Basket").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));

        if (entries.isEmpty()) {
            lines.add(UbsTranslations.literal("No items in basket.").withStyle(ChatFormatting.GRAY));
            return lines;
        }

        entries.sort(Comparator.comparing(entry -> entry.stack.getHoverName().getString().toLowerCase()));

        int visible = Math.min(8, entries.size());
        for (int i = 0; i < visible; i++) {
            BasketEntry entry = entries.get(i);
            long lineTotal;
            try {
                lineTotal = Math.multiplyExact(entry.unitPriceCents, entry.quantity);
            } catch (ArithmeticException overflow) {
                lineTotal = Long.MAX_VALUE;
            }
            String itemName = entry.stack.getHoverName().getString();
            String unitLabel = entry.unitPriceCents == 0L
                    ? "Free"
                    : "$" + ShelfPrice.abbreviateFromCents(entry.unitPriceCents);
            String lineLabel = lineTotal == 0L
                    ? "Free"
                    : "$" + ShelfPrice.abbreviateFromCents(lineTotal);
            lines.add(Component.literal("- ")
                    .append(entry.stack.getHoverName())
                    .withStyle(ChatFormatting.WHITE)
                    .append(UbsTranslations.literal(" x").append(Component.literal(String.valueOf(entry.quantity))).withStyle(ChatFormatting.YELLOW))
                    .append(UbsTranslations.literal(" @ ").append(UbsTranslations.literal(unitLabel))
                            .withStyle(ChatFormatting.GRAY))
                    .append(UbsTranslations.literal(" = ").append(UbsTranslations.literal(lineLabel))
                            .withStyle(ChatFormatting.GREEN)));
        }

        if (entries.size() > visible) {
            lines.add(UbsTranslations.literal("...and ")
                    .append(Component.literal(String.valueOf(entries.size() - visible)))
                    .append(UbsTranslations.literal(" more"))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        lines.add(UbsTranslations.literal("Total: $")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(ShelfPrice.abbreviateFromCents(getTotalPriceDollars(basket)))
                        .withStyle(ChatFormatting.GOLD)));
        lines.add(UbsTranslations.literal("Right-click: +1 | Shift+Right-click: +stack")
                .withStyle(ChatFormatting.DARK_GRAY));
        lines.add(UbsTranslations.literal("Left-click: -1 | Shift+Left-click: -stack")
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    public static CompoundTag extractBasketData(ItemStack basket) {
        CompoundTag out = new CompoundTag();
        if (!isBasketStack(basket)) {
            return out;
        }
        CompoundTag tag = ItemStackDataCompat.getCustomData(basket);
        if (tag == null || !tag.contains(TAG_ENTRIES, Tag.TAG_LIST)) {
            return out;
        }
        out.put(TAG_ENTRIES, tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND).copy());
        return out;
    }

    public static void applyBasketData(ItemStack basket, CompoundTag basketData) {
        if (!isBasketStack(basket)) {
            return;
        }
        if (basketData == null || !basketData.contains(TAG_ENTRIES, Tag.TAG_LIST)) {
            CompoundTag existing = ItemStackDataCompat.getCustomData(basket);
            if (existing != null) {
                existing.remove(TAG_ENTRIES);
                if (existing.isEmpty()) {
                    ItemStackDataCompat.setCustomData(basket, null);
                }
            }
            return;
        }
        CompoundTag tag = ItemStackDataCompat.getCustomData(basket);
        tag.put(TAG_ENTRIES, basketData.getList(TAG_ENTRIES, Tag.TAG_COMPOUND).copy());
        ItemStackDataCompat.setCustomData(basket, tag);
    }

    public static List<ItemStack> getVisualStacksFromData(CompoundTag basketData, int maxStacks) {
        List<ItemStack> out = new ArrayList<>();
        if (basketData == null || !basketData.contains(TAG_ENTRIES, Tag.TAG_LIST) || maxStacks <= 0) {
            return out;
        }
        List<BasketEntry> entries = readEntriesFromData(basketData);
        for (BasketEntry entry : entries) {
            if (entry == null || entry.stack == null || entry.stack.isEmpty() || entry.quantity <= 0) {
                continue;
            }
            int repeats = Math.max(1, Math.min(entry.quantity, maxStacks - out.size()));
            for (int i = 0; i < repeats; i++) {
                ItemStack copy = entry.stack.copy();
                copy.setCount(1);
                out.add(copy);
                if (out.size() >= maxStacks) {
                    return out;
                }
            }
        }
        return out;
    }

    private static List<BasketEntry> readEntries(ItemStack basket) {
        if (!isBasketStack(basket)) {
            return new ArrayList<>();
        }
        return readEntriesFromData(ItemStackDataCompat.getCustomData(basket));
    }

    private static List<BasketEntry> readEntriesFromData(CompoundTag tag) {
        List<BasketEntry> entries = new ArrayList<>();
        if (tag == null || !tag.contains(TAG_ENTRIES, Tag.TAG_LIST)) {
            return entries;
        }
        ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            if (!entryTag.contains(TAG_STACK, Tag.TAG_COMPOUND)) {
                continue;
            }

            ItemStack stack = ItemStackDataCompat.parseStack(entryTag.getCompound(TAG_STACK));
            if (stack.isEmpty()) {
                continue;
            }

            int qty = Math.max(0, entryTag.getInt(TAG_QTY));
            boolean inCents = entryTag.getBoolean(TAG_PRICE_IN_CENTS);
            long unitPrice = Math.max(0L, entryTag.getLong(TAG_PRICE));
            if (!inCents) {
                try {
                    unitPrice = Math.multiplyExact(unitPrice, 100L);
                } catch (ArithmeticException ignored) {
                    unitPrice = Long.MAX_VALUE;
                }
            }
            if (qty <= 0 || unitPrice < 0L) {
                continue;
            }

            BasketEntry entry = new BasketEntry();
            entry.stack = normalizeStack(stack);
            entry.quantity = qty;
            entry.unitPriceCents = unitPrice;
            entry.source = entryTag.contains(TAG_SOURCE, Tag.TAG_STRING) ? entryTag.getString(TAG_SOURCE).trim() : "";
            entries.add(entry);
        }
        return entries;
    }

    private static void writeEntries(ItemStack basket, List<BasketEntry> entries) {
        if (!isBasketStack(basket)) {
            return;
        }

        ListTag list = new ListTag();
        for (BasketEntry entry : entries) {
            if (entry == null || entry.stack == null || entry.stack.isEmpty() || entry.quantity <= 0 || entry.unitPriceCents < 0L) {
                continue;
            }

            CompoundTag entryTag = new CompoundTag();
            entryTag.put(TAG_STACK, ItemStackDataCompat.saveStack(normalizeStack(entry.stack)));
            entryTag.putInt(TAG_QTY, entry.quantity);
            entryTag.putLong(TAG_PRICE, entry.unitPriceCents);
            entryTag.putBoolean(TAG_PRICE_IN_CENTS, true);
            if (entry.source != null && !entry.source.isBlank()) {
                entryTag.putString(TAG_SOURCE, entry.source);
            }
            list.add(entryTag);
        }

        if (list.isEmpty()) {
            CompoundTag existing = ItemStackDataCompat.getCustomData(basket);
            if (existing != null) {
                existing.remove(TAG_ENTRIES);
                if (existing.isEmpty()) {
                    ItemStackDataCompat.setCustomData(basket, null);
                }
            }
            return;
        }

        CompoundTag tag = ItemStackDataCompat.getCustomData(basket);
        tag.put(TAG_ENTRIES, list);
        ItemStackDataCompat.setCustomData(basket, tag);
    }

    private static ItemStack normalizeStack(ItemStack input) {
        ItemStack copy = input.copy();
        copy.setCount(1);
        return copy;
    }

    private static final class BasketEntry {
        private ItemStack stack = ItemStack.EMPTY;
        private int quantity;
        private long unitPriceCents;
        private String source = "";
    }
}
