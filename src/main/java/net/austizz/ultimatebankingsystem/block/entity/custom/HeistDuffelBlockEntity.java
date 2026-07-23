package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.item.HeistDuffelData;
import net.austizz.ultimatebankingsystem.menu.HeistDuffelMenu;
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
import org.jetbrains.annotations.Nullable;

public final class HeistDuffelBlockEntity extends BlockEntity implements MenuProvider {
    private static final String STORAGE_KEY = "inventory";
    private final ItemStackHandler inventory = new ItemStackHandler(HeistDuffelData.SLOT_COUNT) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return HeistDuffelData.canInsert(stack); }
        @Override protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    };

    public HeistDuffelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEIST_DUFFEL.get(), pos, state);
    }
    public IItemHandler getItemHandler() { return inventory; }
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64.0;
    }
    public void loadFromItem(ItemStack stack) {
        if (level == null) return;
        ItemStackHandler source = HeistDuffelData.readInventory(stack, level.registryAccess());
        inventory.deserializeNBT(level.registryAccess(), source.serializeNBT(level.registryAccess()));
        setChanged();
    }
    public void toItemStack(ItemStack stack) {
        if (level != null) HeistDuffelData.writeInventory(stack, inventory, level.registryAccess());
    }
    @Override public Component getDisplayName() { return Component.translatable("container.ultimatebankingsystem.heist_duffel"); }
    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new HeistDuffelMenu(id, playerInventory, this);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.put(STORAGE_KEY, inventory.serializeNBT(registries));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CompoundTag data = tag.getCompound(STORAGE_KEY).copy(); data.putInt("Size", HeistDuffelData.SLOT_COUNT);
        inventory.deserializeNBT(registries, data);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
