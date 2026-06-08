package net.austizz.ultimatebankingsystem.menu;

import net.austizz.ultimatebankingsystem.block.entity.custom.CardboardBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class CardboardBoxMenu extends AbstractContainerMenu {
    public static final int BOX_ROWS = 2;
    private static final int BOX_COLUMNS = 9;
    public static final int BOX_SLOT_COUNT = BOX_ROWS * BOX_COLUMNS;

    private final CardboardBoxBlockEntity boxEntity;
    private final IItemHandler itemHandler;

    public CardboardBoxMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, resolveBox(playerInventory, pos));
    }

    public CardboardBoxMenu(int containerId,
                            Inventory playerInventory,
                            CardboardBoxBlockEntity boxEntity) {
        super(ModMenus.CARDBOARD_BOX.get(), containerId);
        this.boxEntity = boxEntity;
        this.itemHandler = boxEntity != null ? boxEntity.getItemHandler() : new ItemStackHandler(BOX_SLOT_COUNT);

        int slotStartX = 8;
        int slotStartY = 18;
        for (int row = 0; row < BOX_ROWS; row++) {
            for (int col = 0; col < BOX_COLUMNS; col++) {
                int slot = row * BOX_COLUMNS + col;
                this.addSlot(new SlotItemHandler(this.itemHandler, slot, slotStartX + col * 18, slotStartY + row * 18));
            }
        }

        int inventoryStartY = 32 + BOX_ROWS * 18;
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

    public CardboardBoxBlockEntity getBoxEntity() {
        return boxEntity;
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
        if (index < BOX_SLOT_COUNT) {
            if (!this.moveItemStackTo(original, BOX_SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(original, 0, BOX_SLOT_COUNT, false)) {
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
        return boxEntity != null && boxEntity.stillValid(player);
    }

    private static CardboardBoxBlockEntity resolveBox(Inventory playerInventory, BlockPos pos) {
        if (playerInventory == null || playerInventory.player == null || playerInventory.player.level() == null) {
            return null;
        }
        var blockEntity = playerInventory.player.level().getBlockEntity(pos);
        return blockEntity instanceof CardboardBoxBlockEntity box ? box : null;
    }
}
