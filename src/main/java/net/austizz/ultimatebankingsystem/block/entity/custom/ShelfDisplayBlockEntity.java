package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public interface ShelfDisplayBlockEntity {
    int getSlotCount();

    UUID getOwnerUuid();

    String getOwnerName();

    UUID getShopId();

    void setShopId(UUID shopId);

    void setOwner(UUID ownerUuid, String ownerName);

    boolean isCreativeShelf();

    /**
     * Shop mode enables shelf shopping features (basket flow, pricing, stock actions).
     * Regular mode keeps the display as a simple hand pickup/return surface.
     */
    boolean isShopMode();

    /**
     * Toggles between shop mode and regular display mode.
     */
    void setShopMode(boolean shopMode);

    ItemStack getDisplayItem(int slot);

    long getSlotPrice(int slot);

    int getSlotStock(int slot);

    void setSlot(int slot, ItemStack item, long priceCents, int stockCount);

    void setPriceOnly(int slot, long priceCents);

    void clearSlot(int slot);

    void setStockOnly(int slot, int stockCount);

    boolean consumeOneStock(int slot);

    void addStock(int slot, int amount);

    List<ItemStack> extractDisplayItemsForDrop();

    ItemDisplayTransform getSlotTransform(int slot);

    void setSlotTransform(int slot,
                          float offsetX,
                          float offsetY,
                          float offsetZ,
                          float rotationX,
                          float rotationY,
                          float rotationZ,
                          float scaleX,
                          float scaleY,
                          float scaleZ);
}
