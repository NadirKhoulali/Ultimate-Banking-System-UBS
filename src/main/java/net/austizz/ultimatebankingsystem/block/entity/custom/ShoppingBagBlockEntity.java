package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.menu.ShoppingBagMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShoppingBagBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_COUNT = 9;
    private static final String STORAGE_KEY = "inventory";

    private final ItemStackHandler itemHandler = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null) {
                BlockState state = getBlockState();
                level.sendBlockUpdated(worldPosition, state, state, 3);
            }
        }
    };

    public ShoppingBagBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHOPPING_BAG.get(), pos, state);
    }

    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    public boolean stillValid(Player player) {
        if (level == null || player == null) {
            return false;
        }
        if (level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D
        ) <= 64.0D;
    }

    public ItemStack toItemStack(ItemStack stack) {
        if (stack.isEmpty() || level == null) {
            return stack;
        }
        CompoundTag inventoryTag = itemHandler.serializeNBT(level.registryAccess());
        if (inventoryTag.isEmpty()) {
            ItemStackDataCompat.removeCustomData(ShoppingBagDataKeys.BAG_DATA_KEY, stack);
        } else {
            ItemStackDataCompat.putCustomData(ShoppingBagDataKeys.BAG_DATA_KEY, stack, inventoryTag);
        }
        return stack;
    }

    public void loadFromItem(ItemStack stack) {
        if (level == null) {
            return;
        }
        CompoundTag empty = new CompoundTag();
        empty.putInt("Size", SLOT_COUNT);
        itemHandler.deserializeNBT(level.registryAccess(), empty);
        if (stack == null || stack.isEmpty()) {
            setChanged();
            return;
        }
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        if (tag.contains(ShoppingBagDataKeys.BAG_DATA_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag inventoryTag = tag.getCompound(ShoppingBagDataKeys.BAG_DATA_KEY).copy();
            inventoryTag.putInt("Size", SLOT_COUNT);
            itemHandler.deserializeNBT(level.registryAccess(), inventoryTag);
        }
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ultimatebankingsystem.shopping_bag");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ShoppingBagMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(STORAGE_KEY, itemHandler.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(STORAGE_KEY)) {
            CompoundTag inventoryTag = tag.getCompound(STORAGE_KEY).copy();
            inventoryTag.putInt("Size", SLOT_COUNT);
            itemHandler.deserializeNBT(registries, inventoryTag);
        } else {
            CompoundTag empty = new CompoundTag();
            empty.putInt("Size", SLOT_COUNT);
            itemHandler.deserializeNBT(registries, empty);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

}
