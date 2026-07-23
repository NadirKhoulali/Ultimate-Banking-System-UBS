package net.austizz.ultimatebankingsystem.menu;

import net.austizz.ultimatebankingsystem.block.custom.SecureSafeBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.SecureSafeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class SecureSafeMenu extends AbstractContainerMenu {
    private static final int INVENTORY_X = 8;
    private static final int SLOT_SIZE = 18;
    private static final int UPGRADE_SLOT_COUNT = 1;

    private final SecureSafeBlockEntity safeEntity;
    private final IItemHandler itemHandler;
    private final ItemStackHandler upgradeHandler;
    private final boolean tall;
    private final boolean chestUpgradeInstalled;
    private final int safeSlotCount;
    private boolean consumingUpgrade;

    public static SecureSafeMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        BlockPos pos = data.readBlockPos();
        boolean tall = data.readBoolean();
        boolean chestUpgradeInstalled = data.readBoolean();
        SecureSafeBlockEntity safe = resolveSafe(playerInventory, pos);
        return new SecureSafeMenu(containerId, playerInventory, safe, tall, chestUpgradeInstalled);
    }

    public SecureSafeMenu(int containerId,
                          Inventory playerInventory,
                          SecureSafeBlockEntity safeEntity) {
        this(
                containerId,
                playerInventory,
                safeEntity,
                safeEntity == null || safeEntity.isTallSafe(),
                safeEntity != null && safeEntity.hasChestUpgrade()
        );
    }

    private SecureSafeMenu(int containerId,
                           Inventory playerInventory,
                           SecureSafeBlockEntity safeEntity,
                           boolean tall,
                           boolean chestUpgradeInstalled) {
        super(ModMenus.SECURE_SAFE.get(), containerId);
        this.safeEntity = safeEntity;
        this.tall = tall;
        this.chestUpgradeInstalled = chestUpgradeInstalled;
        this.safeSlotCount = chestUpgradeInstalled ? SecureSafeBlockEntity.CHEST_SLOT_COUNT : UPGRADE_SLOT_COUNT;
        this.itemHandler = safeEntity != null
                ? safeEntity.getItemHandler()
                : new ItemStackHandler(SecureSafeBlockEntity.TOTAL_SLOT_COUNT);
        this.upgradeHandler = createUpgradeHandler();

        addSafeSlots();
        addPlayerInventorySlots(playerInventory);
    }

    public boolean isTall() {
        return tall;
    }

    public boolean hasChestUpgrade() {
        return chestUpgradeInstalled;
    }

    public int getSafeSlotCount() {
        return safeSlotCount;
    }

    public int getInventoryStartY() {
        return 70;
    }

    private void addSafeSlots() {
        if (!chestUpgradeInstalled) {
            addSlot(new UpgradeSlot(upgradeHandler, 0, 79, 32));
            return;
        }

        int chestY = 32;
        for (int col = 0; col < SecureSafeBlockEntity.CHEST_SLOT_COUNT; col++) {
            int handlerSlot = SecureSafeBlockEntity.CHEST_SLOT_START + col;
            addSlot(new SafeSlot(itemHandler, handlerSlot, INVENTORY_X + col * SLOT_SIZE, chestY));
        }
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        int inventoryStartY = getInventoryStartY();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(
                        playerInventory,
                        col + row * 9 + 9,
                        INVENTORY_X + col * SLOT_SIZE,
                        inventoryStartY + row * SLOT_SIZE
                ));
            }
        }

        int hotbarY = inventoryStartY + 58;
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INVENTORY_X + col * SLOT_SIZE, hotbarY));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copied = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getItem();
        copied = original.copy();
        if (index < safeSlotCount) {
            if (!moveItemStackTo(original, safeSlotCount, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!chestUpgradeInstalled && SecureSafeBlockEntity.isChestUpgradeItem(original)) {
                if (!moveItemStackTo(original, 0, safeSlotCount, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!chestUpgradeInstalled || !moveItemStackTo(original, 0, safeSlotCount, false)) {
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

    private ItemStackHandler createUpgradeHandler() {
        return new ItemStackHandler(UPGRADE_SLOT_COUNT) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return !chestUpgradeInstalled && SecureSafeBlockEntity.isChestUpgradeItem(stack);
            }

            @Override
            protected void onContentsChanged(int slot) {
                if (consumingUpgrade || safeEntity == null || chestUpgradeInstalled) {
                    return;
                }
                ItemStack stack = getStackInSlot(slot);
                if (!SecureSafeBlockEntity.isChestUpgradeItem(stack)) {
                    return;
                }
                consumingUpgrade = true;
                try {
                    if (safeEntity.installChestUpgradeItem(stack)) {
                        setStackInSlot(slot, ItemStack.EMPTY);
                    }
                } finally {
                    consumingUpgrade = false;
                }
            }
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return safeEntity != null && safeEntity.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player != null && !player.level().isClientSide() && safeEntity != null) {
            safeEntity.onMenuClosed();
        }
    }

    private static SecureSafeBlockEntity resolveSafe(Inventory playerInventory, BlockPos pos) {
        if (playerInventory == null || playerInventory.player == null || playerInventory.player.level() == null) {
            return null;
        }
        var level = playerInventory.player.level();
        var state = level.getBlockState(pos);
        BlockPos masterPos = SecureSafeBlock.isSafeBlock(state) ? SecureSafeBlock.getMasterPos(state, pos) : pos;
        var blockEntity = level.getBlockEntity(masterPos);
        return blockEntity instanceof SecureSafeBlockEntity safe ? safe : null;
    }

    private static class SafeSlot extends SlotItemHandler {
        private SafeSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return true;
        }
    }

    private static class UpgradeSlot extends SlotItemHandler {
        private UpgradeSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return SecureSafeBlockEntity.isChestUpgradeItem(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
