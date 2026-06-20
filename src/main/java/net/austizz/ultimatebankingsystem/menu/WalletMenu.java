package net.austizz.ultimatebankingsystem.menu;

import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.item.WalletData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.UUID;

public class WalletMenu extends AbstractContainerMenu {
    public static final int CASH_SLOT_COUNT = WalletData.CASH_SLOT_COUNT;
    public static final int CARD_SLOT_COUNT = WalletData.CARD_SLOT_COUNT;
    public static final int BUTTON_TOGGLE_MODE = 1;
    public static final int BUTTON_TOGGLE_CARD_FALLBACK = 2;
    public static final int BUTTON_CASH_BASE = 100;
    public static final int CASH_BUTTONS_PER_DENOM = 6;

    public static final int IMAGE_WIDTH = 404;
    public static final int IMAGE_HEIGHT = 320;

    public static final int BILLS_X = 24;
    public static final int COINS_X = 106;
    public static final int CASH_Y = 82;
    public static final int CARD_X = 224;
    public static final int CARD_Y = 84;
    public static final int PLAYER_INV_X = 114;
    public static final int PLAYER_INV_Y = 232;

    private final InteractionHand openedHand;
    private final UUID openedWalletReference;
    private final HolderLookup.Provider registries;
    private final ItemStackHandler cardHandler;
    private final SimpleContainer cashContainer = new SimpleContainer(CASH_SLOT_COUNT);

    public static WalletMenu forItem(int containerId, Inventory playerInventory, InteractionHand hand) {
        return new WalletMenu(ModMenus.WALLET.get(), containerId, playerInventory, hand);
    }

    private WalletMenu(MenuType<?> menuType, int containerId, Inventory playerInventory, InteractionHand hand) {
        super(menuType, containerId);
        this.openedHand = hand == null ? InteractionHand.MAIN_HAND : hand;
        this.registries = playerInventory.player.level().registryAccess();

        ItemStack openedStack = resolveWalletStack(playerInventory, this.openedHand);
        WalletData.ensureOwner(openedStack, playerInventory.player);
        this.openedWalletReference = WalletData.ensureOpenReference(openedStack);
        this.cardHandler = createCardHandler(playerInventory.player);
        WalletData.loadCards(openedStack, this.cardHandler, this.registries);

        addSlots(playerInventory);
    }

    public WalletData.PaymentMode getMode() {
        return WalletData.getMode(currentWalletStack());
    }

    public boolean isCardFallbackEnabled() {
        return WalletData.isCardFallbackEnabled(currentWalletStack());
    }

    public int getCashCount(int cashIndex) {
        return WalletData.getCashCount(currentWalletStack(), cashIndex);
    }

    public long getTotalCashCents() {
        return WalletData.totalCashCents(currentWalletStack());
    }

    public int getCardCount() {
        int count = 0;
        for (int i = 0; i < CARD_SLOT_COUNT; i++) {
            if (WalletData.isCreditCard(cardHandler.getStackInSlot(i))) {
                count++;
            }
        }
        return count;
    }

    public boolean hasCard(int slot) {
        return slot >= 0 && slot < CARD_SLOT_COUNT && WalletData.isCreditCard(cardHandler.getStackInSlot(slot));
    }

    public String getOwnerName() {
        return WalletData.getOwnerName(currentWalletStack());
    }

    public ItemStack currentWalletStack() {
        return findOpenedWalletStack(null);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player == null) {
            return false;
        }
        ItemStack wallet = findOpenedWalletStack(player);
        if (wallet.isEmpty()) {
            return false;
        }
        WalletData.ensureOwner(wallet, player);
        if (id == BUTTON_TOGGLE_MODE) {
            WalletData.toggleMode(wallet);
            player.getInventory().setChanged();
            broadcastChanges();
            return true;
        }
        if (id == BUTTON_TOGGLE_CARD_FALLBACK) {
            WalletData.toggleCardFallback(wallet);
            player.getInventory().setChanged();
            broadcastChanges();
            return true;
        }
        if (id >= BUTTON_CASH_BASE) {
            int encoded = id - BUTTON_CASH_BASE;
            int cashIndex = encoded / CASH_BUTTONS_PER_DENOM;
            int action = encoded % CASH_BUTTONS_PER_DENOM;
            if (cashIndex < 0 || cashIndex >= CASH_SLOT_COUNT) {
                return false;
            }
            handleCashButton(player, wallet, cashIndex, action);
            player.getInventory().setChanged();
            broadcastChanges();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (player == null || index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getItem();
        ItemStack copied = original.copy();
        int cashSlotEnd = CASH_SLOT_COUNT;
        int cardSlotEnd = cashSlotEnd + CARD_SLOT_COUNT;

        if (index < CASH_SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        if (index < cardSlotEnd) {
            if (!this.moveItemStackTo(original, cardSlotEnd, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            int cashIndex = DollarBills.cashIndexForItem(original.getItem());
            if (cashIndex >= 0) {
                ItemStack wallet = findOpenedWalletStack(player);
                if (!wallet.isEmpty()) {
                    int count = original.getCount();
                    int added = WalletData.addCash(wallet, cashIndex, count);
                    original.shrink(added);
                    player.getInventory().setChanged();
                    broadcastChanges();
                    return copied;
                }
            }
            if (original.is(ModItems.CREDIT_CARD.get())) {
                if (!this.moveItemStackTo(original, CASH_SLOT_COUNT, cardSlotEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copied;
    }

    @Override
    public boolean stillValid(Player player) {
        return !findOpenedWalletStack(player).isEmpty();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player == null || player.level().isClientSide()) {
            return;
        }
        saveCards(player);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private void addSlots(Inventory playerInventory) {
        for (int i = 0; i < 7; i++) {
            this.addSlot(new WalletCashSlot(this, this.cashContainer, i, i, BILLS_X, CASH_Y + i * 18));
        }
        for (int i = 7; i < CASH_SLOT_COUNT; i++) {
            this.addSlot(new WalletCashSlot(this, this.cashContainer, i, i, COINS_X, CASH_Y + (i - 7) * 18));
        }

        for (int i = 0; i < CARD_SLOT_COUNT; i++) {
            this.addSlot(new WalletCardSlot(this.cardHandler, i, CARD_X + i * 23, CARD_Y));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(
                        playerInventory,
                        col + row * 9 + 9,
                        PLAYER_INV_X + col * 18,
                        PLAYER_INV_Y + row * 18
                ));
            }
        }

        int hotbarY = PLAYER_INV_Y + 58;
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, PLAYER_INV_X + col * 18, hotbarY));
        }
    }

    private void handleCashButton(Player player, ItemStack wallet, int cashIndex, int action) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (action == 0) {
            WalletData.pullPhysicalCashIntoWallet(serverPlayer, wallet, cashIndex, 1);
            return;
        }
        if (action == 1) {
            WalletData.pullPhysicalCashIntoWallet(serverPlayer, wallet, cashIndex, 64);
            return;
        }
        if (action == 2) {
            WalletData.pullAllPhysicalCashIntoWallet(serverPlayer, wallet, cashIndex);
            return;
        }

        int withdraw = switch (action) {
            case 3 -> 1;
            case 4 -> 64;
            case 5 -> Integer.MAX_VALUE;
            default -> 0;
        };
        if (withdraw <= 0) {
            return;
        }
        int removed = WalletData.removeCash(wallet, cashIndex, withdraw);
        if (removed <= 0) {
            return;
        }
        int[] plan = new int[CASH_SLOT_COUNT];
        plan[cashIndex] = removed;
        WalletData.giveCashPlanToPlayer(serverPlayer, plan);
    }

    private boolean depositCashStack(Player player, int cashIndex, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return false;
        }
        if (DollarBills.cashIndexForItem(stack.getItem()) != cashIndex) {
            return false;
        }
        ItemStack wallet = findOpenedWalletStack(player);
        if (wallet.isEmpty()) {
            return false;
        }
        int added = WalletData.addCash(wallet, cashIndex, stack.getCount());
        stack.shrink(added);
        player.getInventory().setChanged();
        broadcastChanges();
        return added > 0;
    }

    private ItemStackHandler createCardHandler(Player player) {
        return new ItemStackHandler(CARD_SLOT_COUNT) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return WalletData.isCreditCard(stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                saveCards(player);
            }
        };
    }

    private void saveCards(Player player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        ItemStack wallet = findOpenedWalletStack(player);
        if (!wallet.isEmpty()) {
            WalletData.saveCards(wallet, this.cardHandler, this.registries);
        }
    }

    private static ItemStack resolveWalletStack(Inventory playerInventory, InteractionHand hand) {
        if (playerInventory == null || playerInventory.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = playerInventory.player.getItemInHand(hand == null ? InteractionHand.MAIN_HAND : hand);
        return WalletData.isWallet(stack) ? stack : ItemStack.EMPTY;
    }

    private ItemStack findOpenedWalletStack(Player explicitPlayer) {
        Player player = explicitPlayer;
        if (player == null && !this.slots.isEmpty() && this.slots.get(CASH_SLOT_COUNT + CARD_SLOT_COUNT) instanceof Slot slot) {
            if (slot.container instanceof Inventory inventory) {
                player = inventory.player;
            }
        }
        if (player == null) {
            return ItemStack.EMPTY;
        }

        ItemStack held = player.getItemInHand(openedHand == null ? InteractionHand.MAIN_HAND : openedHand);
        if (matchesOpenedWallet(held)) {
            return held;
        }

        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (matchesOpenedWallet(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private boolean matchesOpenedWallet(ItemStack stack) {
        if (!WalletData.isWallet(stack)) {
            return false;
        }
        if (openedWalletReference == null) {
            return true;
        }
        CompoundTag data = WalletData.readData(stack);
        return data.hasUUID(WalletData.OPEN_ID_KEY) && openedWalletReference.equals(data.getUUID(WalletData.OPEN_ID_KEY));
    }

    private static final class WalletCashSlot extends Slot {
        private final WalletMenu menu;
        private final int cashIndex;

        private WalletCashSlot(WalletMenu menu, SimpleContainer container, int containerSlot, int cashIndex, int x, int y) {
            super(container, containerSlot, x, y);
            this.menu = menu;
            this.cashIndex = cashIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack != null && !stack.isEmpty() && DollarBills.cashIndexForItem(stack.getItem()) == this.cashIndex;
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public ItemStack getItem() {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean hasItem() {
            return false;
        }

        @Override
        public void set(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                super.set(ItemStack.EMPTY);
                return;
            }
            Player player = null;
            if (this.menu.slots.size() > CASH_SLOT_COUNT + CARD_SLOT_COUNT
                    && this.menu.slots.get(CASH_SLOT_COUNT + CARD_SLOT_COUNT).container instanceof Inventory inventory) {
                player = inventory.player;
            }
            this.menu.depositCashStack(player, this.cashIndex, stack);
            super.set(ItemStack.EMPTY);
        }
    }

    private static final class WalletCardSlot extends SlotItemHandler {
        private WalletCardSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return WalletData.isCreditCard(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
