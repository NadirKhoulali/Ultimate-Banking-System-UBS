package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.menu.ShoppingBagMenu;
import net.minecraft.core.BlockPos;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
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
    private LazyOptional<IItemHandler> itemHandlerCapability = LazyOptional.of(() -> itemHandler);

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
        if (stack.isEmpty()) {
            return stack;
        }
        CompoundTag inventoryTag = itemHandler.serializeNBT();
        if (inventoryTag.isEmpty()) {
            stack.removeTagKey(ShoppingBagDataKeys.BAG_DATA_KEY);
        } else {
            stack.getOrCreateTag().put(ShoppingBagDataKeys.BAG_DATA_KEY, inventoryTag);
        }
        return stack;
    }

    public void loadFromItem(ItemStack stack) {
        CompoundTag empty = new CompoundTag();
        empty.putInt("Size", SLOT_COUNT);
        itemHandler.deserializeNBT(empty);
        if (stack == null || stack.isEmpty()) {
            setChanged();
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(ShoppingBagDataKeys.BAG_DATA_KEY)) {
            CompoundTag inventoryTag = tag.getCompound(ShoppingBagDataKeys.BAG_DATA_KEY).copy();
            inventoryTag.putInt("Size", SLOT_COUNT);
            itemHandler.deserializeNBT(inventoryTag);
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
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(STORAGE_KEY, itemHandler.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(STORAGE_KEY)) {
            CompoundTag inventoryTag = tag.getCompound(STORAGE_KEY).copy();
            inventoryTag.putInt("Size", SLOT_COUNT);
            itemHandler.deserializeNBT(inventoryTag);
        } else {
            CompoundTag empty = new CompoundTag();
            empty.putInt("Size", SLOT_COUNT);
            itemHandler.deserializeNBT(empty);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap,
                                                       @Nullable net.minecraft.core.Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandlerCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandlerCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemHandlerCapability = LazyOptional.of(() -> itemHandler);
    }
}
