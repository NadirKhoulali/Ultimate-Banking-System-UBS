package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.shelf.ShelfCartService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ShoppingBasketBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
    private CompoundTag basketData = new CompoundTag();
    private boolean suppressDropOnce;

    public ShoppingBasketBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHOPPING_BASKET.get(), pos, state);
    }

    public void setBasketData(CompoundTag data) {
        basketData = data == null ? new CompoundTag() : data.copy();
        markUpdated();
    }

    public CompoundTag getBasketDataCopy() {
        return basketData == null ? new CompoundTag() : basketData.copy();
    }

    public void setFromItem(ItemStack basketItem) {
        setBasketData(ShelfCartService.extractBasketData(basketItem));
    }

    public void writeToItem(ItemStack basketItem) {
        ShelfCartService.applyBasketData(basketItem, getBasketDataCopy());
    }

    public List<ItemStack> getRenderStacks(int maxStacks) {
        return ShelfCartService.getVisualStacksFromData(getBasketDataCopy(), maxStacks);
    }

    public void suppressNextDrop() {
        this.suppressDropOnce = true;
    }

    public boolean consumeDropSuppression() {
        boolean value = suppressDropOnce;
        suppressDropOnce = false;
        return value;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("basket_data", getBasketDataCopy());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        basketData = tag.contains("basket_data", Tag.TAG_COMPOUND)
                ? tag.getCompound("basket_data").copy()
                : new CompoundTag();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }
}
