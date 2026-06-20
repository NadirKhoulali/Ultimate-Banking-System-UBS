package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WalletData {
    public static final String WALLET_DATA_KEY = "ubs_wallet";
    public static final String OPEN_ID_KEY = "open_id";
    private static final String OWNER_ID_KEY = "owner_id";
    private static final String OWNER_NAME_KEY = "owner_name";
    private static final String MODE_KEY = "mode";
    private static final String CARD_FALLBACK_KEY = "card_fallback";
    private static final String CASH_COUNTS_KEY = "cash_counts";
    private static final String CARD_SLOTS_KEY = "card_slots";

    public static final int CARD_SLOT_COUNT = 5;
    public static final int CASH_SLOT_COUNT = DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length;

    public enum PaymentMode {
        CASH,
        CARD
    }

    public record WalletCardSlot(int slot, ItemStack stack) {
    }

    private WalletData() {
    }

    public static boolean isWallet(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.WALLET.get());
    }

    public static CompoundTag readData(ItemStack stack) {
        CompoundTag root = ItemStackDataCompat.getCustomData(stack);
        if (root.contains(WALLET_DATA_KEY, Tag.TAG_COMPOUND)) {
            return root.getCompound(WALLET_DATA_KEY).copy();
        }
        CompoundTag data = new CompoundTag();
        data.putString(MODE_KEY, PaymentMode.CASH.name());
        data.putBoolean(CARD_FALLBACK_KEY, true);
        return data;
    }

    public static void writeData(ItemStack stack, CompoundTag data) {
        if (!isWallet(stack)) {
            return;
        }
        CompoundTag clean = data == null ? new CompoundTag() : data.copy();
        if (!clean.contains(MODE_KEY)) {
            clean.putString(MODE_KEY, PaymentMode.CASH.name());
        }
        if (!clean.contains(CARD_FALLBACK_KEY)) {
            clean.putBoolean(CARD_FALLBACK_KEY, true);
        }
        ItemStackDataCompat.putCustomData(WALLET_DATA_KEY, stack, clean);
    }

    public static UUID ensureOpenReference(ItemStack stack) {
        if (!isWallet(stack)) {
            return null;
        }
        CompoundTag data = readData(stack);
        if (!data.hasUUID(OPEN_ID_KEY)) {
            data.putUUID(OPEN_ID_KEY, UUID.randomUUID());
            writeData(stack, data);
        }
        return data.getUUID(OPEN_ID_KEY);
    }

    public static UUID getOpenReference(ItemStack stack) {
        if (!isWallet(stack)) {
            return null;
        }
        CompoundTag data = readData(stack);
        return data.hasUUID(OPEN_ID_KEY) ? data.getUUID(OPEN_ID_KEY) : null;
    }

    public static void ensureOwner(ItemStack stack, Player player) {
        if (!isWallet(stack) || player == null) {
            return;
        }
        CompoundTag data = readData(stack);
        if (data.hasUUID(OWNER_ID_KEY)) {
            return;
        }
        data.putUUID(OWNER_ID_KEY, player.getUUID());
        data.putString(OWNER_NAME_KEY, player.getName().getString());
        writeData(stack, data);
    }

    public static UUID getOwnerId(ItemStack stack) {
        if (!isWallet(stack)) {
            return null;
        }
        CompoundTag data = readData(stack);
        return data.hasUUID(OWNER_ID_KEY) ? data.getUUID(OWNER_ID_KEY) : null;
    }

    public static String getOwnerName(ItemStack stack) {
        if (!isWallet(stack)) {
            return "Unknown";
        }
        CompoundTag data = readData(stack);
        String owner = data.contains(OWNER_NAME_KEY) ? data.getString(OWNER_NAME_KEY).trim() : "";
        return owner.isBlank() ? "Unassigned" : owner;
    }

    public static boolean isOwner(ItemStack stack, Player player) {
        if (!isWallet(stack) || player == null) {
            return false;
        }
        UUID owner = getOwnerId(stack);
        return owner == null || owner.equals(player.getUUID());
    }

    public static PaymentMode getMode(ItemStack stack) {
        if (!isWallet(stack)) {
            return PaymentMode.CASH;
        }
        String raw = readData(stack).getString(MODE_KEY);
        try {
            return PaymentMode.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return PaymentMode.CASH;
        }
    }

    public static void setMode(ItemStack stack, PaymentMode mode) {
        if (!isWallet(stack)) {
            return;
        }
        CompoundTag data = readData(stack);
        data.putString(MODE_KEY, (mode == null ? PaymentMode.CASH : mode).name());
        writeData(stack, data);
    }

    public static void toggleMode(ItemStack stack) {
        setMode(stack, getMode(stack) == PaymentMode.CASH ? PaymentMode.CARD : PaymentMode.CASH);
    }

    public static boolean isCardFallbackEnabled(ItemStack stack) {
        if (!isWallet(stack)) {
            return true;
        }
        CompoundTag data = readData(stack);
        return !data.contains(CARD_FALLBACK_KEY) || data.getBoolean(CARD_FALLBACK_KEY);
    }

    public static void toggleCardFallback(ItemStack stack) {
        if (!isWallet(stack)) {
            return;
        }
        CompoundTag data = readData(stack);
        data.putBoolean(CARD_FALLBACK_KEY, !isCardFallbackEnabled(stack));
        writeData(stack, data);
    }

    public static int[] getCashCounts(ItemStack stack) {
        int[] counts = new int[CASH_SLOT_COUNT];
        if (!isWallet(stack)) {
            return counts;
        }
        CompoundTag data = readData(stack);
        if (!data.contains(CASH_COUNTS_KEY, Tag.TAG_INT_ARRAY)) {
            return counts;
        }
        int[] stored = data.getIntArray(CASH_COUNTS_KEY);
        for (int i = 0; i < counts.length && i < stored.length; i++) {
            counts[i] = Math.max(0, stored[i]);
        }
        return counts;
    }

    public static int getCashCount(ItemStack stack, int cashIndex) {
        if (cashIndex < 0 || cashIndex >= CASH_SLOT_COUNT) {
            return 0;
        }
        return getCashCounts(stack)[cashIndex];
    }

    public static void setCashCounts(ItemStack stack, int[] counts) {
        if (!isWallet(stack)) {
            return;
        }
        int[] clean = new int[CASH_SLOT_COUNT];
        if (counts != null) {
            for (int i = 0; i < clean.length && i < counts.length; i++) {
                clean[i] = Math.max(0, counts[i]);
            }
        }
        CompoundTag data = readData(stack);
        data.putIntArray(CASH_COUNTS_KEY, clean);
        writeData(stack, data);
    }

    public static int addCash(ItemStack stack, int cashIndex, int count) {
        if (!isWallet(stack) || cashIndex < 0 || cashIndex >= CASH_SLOT_COUNT || count <= 0) {
            return 0;
        }
        int[] counts = getCashCounts(stack);
        long next = (long) counts[cashIndex] + count;
        int added = next > Integer.MAX_VALUE ? Integer.MAX_VALUE - counts[cashIndex] : count;
        if (added <= 0) {
            return 0;
        }
        counts[cashIndex] += added;
        setCashCounts(stack, counts);
        return added;
    }

    public static int removeCash(ItemStack stack, int cashIndex, int count) {
        if (!isWallet(stack) || cashIndex < 0 || cashIndex >= CASH_SLOT_COUNT || count <= 0) {
            return 0;
        }
        int[] counts = getCashCounts(stack);
        int removed = Math.min(counts[cashIndex], count);
        if (removed <= 0) {
            return 0;
        }
        counts[cashIndex] -= removed;
        setCashCounts(stack, counts);
        return removed;
    }

    public static void addCashPlan(ItemStack stack, int[] plan) {
        if (!isWallet(stack) || plan == null) {
            return;
        }
        int[] counts = getCashCounts(stack);
        for (int i = 0; i < counts.length && i < plan.length; i++) {
            if (plan[i] <= 0) {
                continue;
            }
            long next = (long) counts[i] + plan[i];
            counts[i] = next > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) next;
        }
        setCashCounts(stack, counts);
    }

    public static boolean removeCashPlan(ItemStack stack, int[] plan) {
        if (!isWallet(stack) || plan == null) {
            return false;
        }
        int[] counts = getCashCounts(stack);
        for (int i = 0; i < counts.length && i < plan.length; i++) {
            if (Math.max(0, plan[i]) > counts[i]) {
                return false;
            }
        }
        for (int i = 0; i < counts.length && i < plan.length; i++) {
            counts[i] -= Math.max(0, plan[i]);
        }
        setCashCounts(stack, counts);
        return true;
    }

    public static long totalCashCents(ItemStack stack) {
        int[] counts = getCashCounts(stack);
        long total = 0L;
        for (int i = 0; i < counts.length; i++) {
            total += (long) counts[i] * DollarBills.CASH_DENOMINATIONS_CENTS_DESC[i];
        }
        return Math.max(0L, total);
    }

    public static String formatTotalCash(ItemStack stack) {
        return MoneyText.abbreviate(BigDecimal.valueOf(totalCashCents(stack), 2));
    }

    public static void loadCards(ItemStack wallet, ItemStackHandler handler, HolderLookup.Provider registries) {
        if (handler == null) {
            return;
        }
        CompoundTag empty = new CompoundTag();
        empty.putInt("Size", CARD_SLOT_COUNT);
        handler.deserializeNBT(registries, empty);
        if (!isWallet(wallet)) {
            return;
        }
        CompoundTag data = readData(wallet);
        if (!data.contains(CARD_SLOTS_KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag cards = data.getCompound(CARD_SLOTS_KEY).copy();
        cards.putInt("Size", CARD_SLOT_COUNT);
        handler.deserializeNBT(registries, cards);
    }

    public static void saveCards(ItemStack wallet, ItemStackHandler handler, HolderLookup.Provider registries) {
        if (!isWallet(wallet) || handler == null) {
            return;
        }
        CompoundTag data = readData(wallet);
        data.put(CARD_SLOTS_KEY, handler.serializeNBT(registries));
        writeData(wallet, data);
    }

    public static int getCardCount(ItemStack wallet, HolderLookup.Provider registries) {
        ItemStackHandler handler = new ItemStackHandler(CARD_SLOT_COUNT);
        loadCards(wallet, handler, registries);
        int count = 0;
        for (int i = 0; i < CARD_SLOT_COUNT; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (isCreditCard(stack)) {
                count++;
            }
        }
        return count;
    }

    public static List<WalletCardSlot> getPaymentCards(ItemStack wallet, HolderLookup.Provider registries) {
        List<WalletCardSlot> cards = new ArrayList<>();
        if (!isWallet(wallet)) {
            return cards;
        }
        ItemStackHandler handler = new ItemStackHandler(CARD_SLOT_COUNT);
        loadCards(wallet, handler, registries);
        int max = isCardFallbackEnabled(wallet) ? CARD_SLOT_COUNT : 1;
        for (int i = 0; i < max; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (isCreditCard(stack)) {
                cards.add(new WalletCardSlot(i, stack.copy()));
            }
        }
        return cards;
    }

    public static boolean isCreditCard(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.CREDIT_CARD.get());
    }

    public static ItemStack findHeldWallet(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getMainHandItem();
        if (isWallet(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (isWallet(off)) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack findWalletByOpenId(Player player, UUID openId) {
        if (player == null || openId == null) {
            return ItemStack.EMPTY;
        }
        ItemStack held = findHeldWallet(player);
        if (openId.equals(getOpenReference(held))) {
            return held;
        }
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isWallet(stack) && openId.equals(getOpenReference(stack))) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static int pullPhysicalCashIntoWallet(ServerPlayer player, ItemStack wallet, int cashIndex, int requestedCount) {
        if (player == null || !isWallet(wallet) || cashIndex < 0 || cashIndex >= CASH_SLOT_COUNT || requestedCount <= 0) {
            return 0;
        }
        int capacity = Integer.MAX_VALUE - getCashCount(wallet, cashIndex);
        if (capacity <= 0) {
            return 0;
        }
        int targetCount = Math.min(requestedCount, capacity);
        int remaining = targetCount;
        remaining = pullFromList(player.getInventory().items, wallet, cashIndex, remaining);
        remaining = pullFromList(player.getInventory().offhand, wallet, cashIndex, remaining);
        int pulled = targetCount - remaining;
        if (pulled > 0) {
            addCash(wallet, cashIndex, pulled);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        return pulled;
    }

    public static int pullAllPhysicalCashIntoWallet(ServerPlayer player, ItemStack wallet, int cashIndex) {
        return pullPhysicalCashIntoWallet(player, wallet, cashIndex, Integer.MAX_VALUE);
    }

    public static void giveCashPlanToPlayer(ServerPlayer player, int[] plan) {
        DollarBills.giveCash(player, plan);
    }

    public static Item cashItemForIndex(int cashIndex) {
        int cents = DollarBills.cashDenominationCentsForIndex(cashIndex);
        return DollarBills.getCashItemForDenominationCents(cents);
    }

    private static int pullFromList(List<ItemStack> stacks, ItemStack wallet, int cashIndex, int remaining) {
        if (remaining <= 0) {
            return 0;
        }
        for (ItemStack stack : stacks) {
            if (remaining <= 0) {
                break;
            }
            if (stack == null || stack.isEmpty() || stack == wallet) {
                continue;
            }
            if (DollarBills.cashIndexForItem(stack.getItem()) != cashIndex) {
                continue;
            }
            int take = Math.min(stack.getCount(), remaining);
            stack.shrink(take);
            remaining -= take;
        }
        return remaining;
    }
}
