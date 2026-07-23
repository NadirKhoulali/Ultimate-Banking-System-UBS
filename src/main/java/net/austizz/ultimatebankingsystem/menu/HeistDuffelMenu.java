package net.austizz.ultimatebankingsystem.menu;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.HeistDuffelBlockEntity;
import net.austizz.ultimatebankingsystem.item.HeistDuffelData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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

public final class HeistDuffelMenu extends AbstractContainerMenu {
    public static final int ROWS = 3;
    private final HeistDuffelBlockEntity blockEntity;
    private final IItemHandler inventory;
    private final boolean itemMode;
    private final InteractionHand openedHand;
    private final UUID openedId;
    private final HolderLookup.Provider registries;

    public HeistDuffelMenu(int id, Inventory playerInventory, BlockPos pos) {
        this(id, playerInventory, playerInventory.player.level().getBlockEntity(pos) instanceof HeistDuffelBlockEntity bag ? bag : null);
    }
    public HeistDuffelMenu(int id, Inventory playerInventory, HeistDuffelBlockEntity bag) {
        super(ModMenus.HEIST_DUFFEL.get(), id);
        blockEntity = bag;
        inventory = bag == null ? new ItemStackHandler(HeistDuffelData.SLOT_COUNT) : bag.getItemHandler();
        itemMode = false;
        openedHand = InteractionHand.MAIN_HAND;
        openedId = null;
        registries = playerInventory.player.registryAccess();
        addSlots(playerInventory);
    }
    public static HeistDuffelMenu forItem(int id, Inventory playerInventory, InteractionHand hand) {
        return new HeistDuffelMenu(ModMenus.HEIST_DUFFEL_ITEM.get(), id, playerInventory, hand);
    }
    private HeistDuffelMenu(MenuType<?> type, int id, Inventory playerInventory, InteractionHand hand) {
        super(type, id);
        blockEntity = null;
        itemMode = true;
        openedHand = hand == null ? InteractionHand.MAIN_HAND : hand;
        registries = playerInventory.player.registryAccess();
        ItemStack stack = playerInventory.player.getItemInHand(openedHand);
        openedId = HeistDuffelData.ensureOpenId(stack);
        inventory = HeistDuffelData.readInventory(stack, registries);
        addSlots(playerInventory);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < HeistDuffelData.SLOT_COUNT) {
            if (!moveItemStackTo(source, HeistDuffelData.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!HeistDuffelData.canInsert(source)
                || !moveItemStackTo(source, 0, HeistDuffelData.SLOT_COUNT, false)) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }
    @Override public boolean stillValid(Player player) {
        return itemMode ? !findStack(player).isEmpty() : blockEntity != null && blockEntity.stillValid(player);
    }
    @Override public void removed(Player player) {
        super.removed(player);
        if (itemMode && !player.level().isClientSide() && inventory instanceof ItemStackHandler handler) {
            ItemStack stack = findStack(player);
            if (!stack.isEmpty()) HeistDuffelData.writeInventory(stack, handler, registries);
        }
    }
    private void addSlots(Inventory playerInventory) {
        for (int row = 0; row < ROWS; row++) for (int col = 0; col < 9; col++) {
            int slot = row * 9 + col;
            addSlot(new SlotItemHandler(inventory, slot, 8 + col * 18, 18 + row * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return HeistDuffelData.canInsert(stack) && super.mayPlace(stack); }
            });
        }
        int y = 85;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new net.minecraft.world.inventory.Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new net.minecraft.world.inventory.Slot(playerInventory, col, 8 + col * 18, y + 58));
    }
    private ItemStack findStack(Player player) {
        ItemStack held = player.getItemInHand(openedHand);
        if (matches(held)) return held;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (matches(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }
    private boolean matches(ItemStack stack) {
        return stack != null && stack.is(ModBlocks.HEIST_DUFFEL.get().asItem()) && !HeistDuffelData.isActive(stack)
                && (openedId == null || openedId.equals(HeistDuffelData.openId(stack)));
    }
}
