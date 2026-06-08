package net.austizz.ultimatebankingsystem.menu;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShoppingBagBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShoppingBagDataKeys;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.UUID;

public class ShoppingBagMenu extends AbstractContainerMenu {
    public static final int BAG_ROWS = 1;
    private static final int BAG_COLUMNS = 9;
    public static final int BAG_SLOT_COUNT = BAG_ROWS * BAG_COLUMNS;

    private final ShoppingBagBlockEntity bagEntity;
    private final IItemHandler itemHandler;
    private final boolean itemMode;
    private final InteractionHand openedHand;
    private final UUID openedBagReference;
    private final HolderLookup.Provider registries;

    public ShoppingBagMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, resolveBag(playerInventory, pos));
    }

    public ShoppingBagMenu(int containerId,
                           Inventory playerInventory,
                           ShoppingBagBlockEntity bagEntity) {
        super(ModMenus.SHOPPING_BAG.get(), containerId);
        this.bagEntity = bagEntity;
        this.itemMode = false;
        this.openedHand = InteractionHand.MAIN_HAND;
        this.openedBagReference = null;
        this.registries = playerInventory.player.level().registryAccess();
        this.itemHandler = bagEntity != null ? bagEntity.getItemHandler() : new ItemStackHandler(BAG_SLOT_COUNT);
        addSlots(playerInventory);
    }

    public static ShoppingBagMenu forItem(int containerId, Inventory playerInventory, InteractionHand hand) {
        return new ShoppingBagMenu(ModMenus.SHOPPING_BAG_ITEM.get(), containerId, playerInventory, hand);
    }

    private ShoppingBagMenu(MenuType<?> menuType,
                            int containerId,
                            Inventory playerInventory,
                            InteractionHand hand) {
        super(menuType, containerId);
        this.bagEntity = null;
        this.itemMode = true;
        this.openedHand = hand == null ? InteractionHand.MAIN_HAND : hand;
        this.registries = playerInventory.player.level().registryAccess();

        ItemStack openedStack = resolveBagStack(playerInventory, this.openedHand);
        ItemStackHandler handler = new ItemStackHandler(BAG_SLOT_COUNT);
        this.openedBagReference = ensureOpenReference(openedStack);
        loadFromBagItem(openedStack, handler);
        this.itemHandler = handler;

        addSlots(playerInventory);
    }

    public ShoppingBagBlockEntity getBagEntity() {
        return bagEntity;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copied = ItemStack.EMPTY;
        var slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getItem();
        copied = original.copy();
        if (index < BAG_SLOT_COUNT) {
            if (!this.moveItemStackTo(original, BAG_SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(original, 0, BAG_SLOT_COUNT, false)) {
            return ItemStack.EMPTY;
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
        if (itemMode) {
            return !findOpenedBagStack(player).isEmpty();
        }
        return bagEntity != null && bagEntity.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!itemMode || player == null || player.level().isClientSide()) {
            return;
        }

        ItemStack target = findOpenedBagStack(player);
        if (!target.isEmpty()) {
            saveToBagItem(target);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    private void addSlots(Inventory playerInventory) {
        int slotStartX = 8;
        int slotStartY = 18;
        for (int row = 0; row < BAG_ROWS; row++) {
            for (int col = 0; col < BAG_COLUMNS; col++) {
                int slot = row * BAG_COLUMNS + col;
                this.addSlot(new SlotItemHandler(this.itemHandler, slot, slotStartX + col * 18, slotStartY + row * 18));
            }
        }

        int inventoryStartY = 32 + BAG_ROWS * 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new net.minecraft.world.inventory.Slot(
                        playerInventory,
                        col + row * 9 + 9,
                        8 + col * 18,
                        inventoryStartY + row * 18
                ));
            }
        }

        int hotbarY = inventoryStartY + 58;
        for (int col = 0; col < 9; col++) {
            this.addSlot(new net.minecraft.world.inventory.Slot(
                    playerInventory,
                    col,
                    8 + col * 18,
                    hotbarY
            ));
        }
    }

    private static ShoppingBagBlockEntity resolveBag(Inventory playerInventory, BlockPos pos) {
        if (playerInventory == null || playerInventory.player == null || playerInventory.player.level() == null) {
            return null;
        }
        var blockEntity = playerInventory.player.level().getBlockEntity(pos);
        return blockEntity instanceof ShoppingBagBlockEntity bag ? bag : null;
    }

    private static ItemStack resolveBagStack(Inventory playerInventory, InteractionHand hand) {
        if (playerInventory == null || playerInventory.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = playerInventory.player.getItemInHand(hand == null ? InteractionHand.MAIN_HAND : hand);
        if (stack != null && !stack.isEmpty() && stack.is(ModBlocks.SHOPPING_BAG.get().asItem())) {
            return stack;
        }
        return ItemStack.EMPTY;
    }

    private void loadFromBagItem(ItemStack bagStack, ItemStackHandler handler) {
        CompoundTag empty = new CompoundTag();
        empty.putInt("Size", BAG_SLOT_COUNT);
        handler.deserializeNBT(registries, empty);

        if (bagStack == null || bagStack.isEmpty()) {
            return;
        }
        CompoundTag root = ItemStackDataCompat.getCustomData(bagStack);
        if (!root.contains(ShoppingBagDataKeys.BAG_DATA_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag bagData = root.getCompound(ShoppingBagDataKeys.BAG_DATA_KEY).copy();
        bagData.putInt("Size", BAG_SLOT_COUNT);
        handler.deserializeNBT(registries, bagData);
    }

    private void saveToBagItem(ItemStack bagStack) {
        if (bagStack == null || bagStack.isEmpty()) {
            return;
        }
        CompoundTag bagData = ((ItemStackHandler) this.itemHandler).serializeNBT(registries);
        if (bagData.isEmpty()) {
            ItemStackDataCompat.removeCustomData(ShoppingBagDataKeys.BAG_DATA_KEY, bagStack);
            return;
        }
        ItemStackDataCompat.putCustomData(ShoppingBagDataKeys.BAG_DATA_KEY, bagStack, bagData);
    }

    private ItemStack findOpenedBagStack(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }

        // Prefer the original hand first so saving is deterministic for normal usage.
        ItemStack held = player.getItemInHand(openedHand == null ? InteractionHand.MAIN_HAND : openedHand);
        if (matchesOpenedBag(held)) {
            return held;
        }

        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (matchesOpenedBag(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private boolean matchesOpenedBag(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(ModBlocks.SHOPPING_BAG.get().asItem())) {
            return false;
        }
        if (openedBagReference == null) {
            return true;
        }
        CompoundTag root = ItemStackDataCompat.getCustomData(stack);
        return root != null
                && root.hasUUID(ShoppingBagDataKeys.BAG_OPEN_ID_KEY)
                && openedBagReference.equals(root.getUUID(ShoppingBagDataKeys.BAG_OPEN_ID_KEY));
    }

    private static UUID ensureOpenReference(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CompoundTag root = ItemStackDataCompat.getCustomData(stack);
        if (!root.hasUUID(ShoppingBagDataKeys.BAG_OPEN_ID_KEY)) {
            root.putUUID(ShoppingBagDataKeys.BAG_OPEN_ID_KEY, UUID.randomUUID());
        }
        return root.getUUID(ShoppingBagDataKeys.BAG_OPEN_ID_KEY);
    }
}
