package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TallWallShelfBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements ShelfDisplayBlockEntity {
    public static final int SLOT_COUNT = 3;
    private static final String TAG_PRICE_IN_CENTS = "slot_price_in_cents";
    private static final String TAG_STOCK_COUNT = "slot_stock_";
    private static final String TAG_CREATIVE_SHELF = "creative_shelf";
    private static final String TAG_SHOP_MODE = "shop_mode";
    private static final String TAG_SHOP_ID = "shop_id";
    private static final String TAG_OFFSET_X = "slot_offset_x_";
    private static final String TAG_OFFSET_Y = "slot_offset_y_";
    private static final String TAG_OFFSET_Z = "slot_offset_z_";
    private static final String TAG_ROT_X = "slot_rot_x_";
    private static final String TAG_ROT_Y = "slot_rot_y_";
    private static final String TAG_ROT_Z = "slot_rot_z_";
    private static final String TAG_SCALE = "slot_scale_";
    private static final String TAG_SCALE_X = "slot_scale_x_";
    private static final String TAG_SCALE_Y = "slot_scale_y_";
    private static final String TAG_SCALE_Z = "slot_scale_z_";

    private final ItemStack[] displayItems = new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final long[] slotPrices = new long[]{0L, 0L, 0L};
    private final int[] slotStock = new int[]{0, 0, 0};
    private final float[] slotOffsetX = new float[]{0.0F, 0.0F, 0.0F};
    private final float[] slotOffsetY = new float[]{0.0F, 0.0F, 0.0F};
    private final float[] slotOffsetZ = new float[]{0.0F, 0.0F, 0.0F};
    private final float[] slotRotationX = new float[]{0.0F, 0.0F, 0.0F};
    private final float[] slotRotationY = new float[]{0.0F, 0.0F, 0.0F};
    private final float[] slotRotationZ = new float[]{0.0F, 0.0F, 0.0F};
    private final float[] slotScaleX = new float[]{1.0F, 1.0F, 1.0F};
    private final float[] slotScaleY = new float[]{1.0F, 1.0F, 1.0F};
    private final float[] slotScaleZ = new float[]{1.0F, 1.0F, 1.0F};

    private UUID ownerUuid;
    private UUID shopId;
    private String ownerName = "";
    private boolean creativeShelf;
    private boolean shopMode = true;

    public TallWallShelfBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TALL_WALL_SHELF.get(), pos, state);
    }

    @Override
    public int getSlotCount() {
        return SLOT_COUNT;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName == null ? "" : ownerName;
    }

    public UUID getShopId() {
        return shopId;
    }

    public void setShopId(UUID shopId) {
        this.shopId = shopId;
        markUpdated();
    }

    public void setOwner(UUID ownerUuid, String ownerName) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName == null ? "" : ownerName.trim();
        markUpdated();
    }

    public boolean isCreativeShelf() {
        return creativeShelf;
    }

    @Override
    public boolean isShopMode() {
        return shopMode;
    }

    @Override
    public void setShopMode(boolean shopMode) {
        if (this.shopMode == shopMode) {
            return;
        }
        this.shopMode = shopMode;
        if (!shopMode) {
            // Regular display mode is intentionally non-purchasable; keep every slot at $0.
            for (int i = 0; i < SLOT_COUNT; i++) {
                slotPrices[i] = 0L;
            }
        }
        markUpdated();
    }

    public void setCreativeShelf(boolean creativeShelf) {
        this.creativeShelf = creativeShelf;
        markUpdated();
    }

    public ItemStack getDisplayItem(int slot) {
        int idx = clampSlot(slot);
        return displayItems[idx].copy();
    }

    public long getSlotPrice(int slot) {
        int idx = clampSlot(slot);
        return Math.max(0L, slotPrices[idx]);
    }

    public int getSlotStock(int slot) {
        if (isCreativeShelf()) {
            return Integer.MAX_VALUE;
        }
        int idx = clampSlot(slot);
        return Math.max(0, slotStock[idx]);
    }

    public void setSlot(int slot, ItemStack item, long priceDollars) {
        setSlot(slot, item, priceDollars, 1);
    }

    public void setSlot(int slot, ItemStack item, long priceDollars, int stockCount) {
        int idx = clampSlot(slot);
        if (item == null || item.isEmpty()) {
            displayItems[idx] = ItemStack.EMPTY;
            slotPrices[idx] = 0L;
            slotStock[idx] = 0;
            resetTransform(idx);
        } else {
            ItemStack copy = item.copy();
            copy.setCount(1);
            displayItems[idx] = copy;
            slotPrices[idx] = isShopMode() ? Math.max(0L, priceDollars) : 0L;
            slotStock[idx] = isCreativeShelf() ? Integer.MAX_VALUE : Math.max(0, stockCount);
        }
        markUpdated();
    }

    public void setPriceOnly(int slot, long priceDollars) {
        int idx = clampSlot(slot);
        if (displayItems[idx].isEmpty()) {
            slotPrices[idx] = 0L;
        } else {
            slotPrices[idx] = isShopMode() ? Math.max(0L, priceDollars) : 0L;
        }
        markUpdated();
    }

    public void clearSlot(int slot) {
        int idx = clampSlot(slot);
        displayItems[idx] = ItemStack.EMPTY;
        slotPrices[idx] = 0L;
        slotStock[idx] = 0;
        resetTransform(idx);
        markUpdated();
    }

    public void setStockOnly(int slot, int stockCount) {
        int idx = clampSlot(slot);
        if (isCreativeShelf() || displayItems[idx].isEmpty()) {
            slotStock[idx] = isCreativeShelf() ? Integer.MAX_VALUE : 0;
        } else {
            slotStock[idx] = Math.max(0, stockCount);
        }
        markUpdated();
    }

    public boolean consumeOneStock(int slot) {
        if (isCreativeShelf()) {
            return true;
        }
        int idx = clampSlot(slot);
        if (slotStock[idx] <= 0) {
            return false;
        }
        slotStock[idx] -= 1;
        markUpdated();
        return true;
    }

    public void addStock(int slot, int amount) {
        if (isCreativeShelf() || amount <= 0) {
            return;
        }
        int idx = clampSlot(slot);
        if (displayItems[idx].isEmpty()) {
            return;
        }
        slotStock[idx] = Math.max(0, slotStock[idx]) + amount;
        markUpdated();
    }

    public List<ItemStack> extractDisplayItemsForDrop() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack display = displayItems[i];
            if (display.isEmpty()) {
                continue;
            }
            int toDrop = isCreativeShelf() ? 1 : Math.max(1, slotStock[i]);
            int maxStack = Math.max(1, display.getMaxStackSize());
            while (toDrop > 0) {
                int amount = Math.min(maxStack, toDrop);
                ItemStack drop = display.copy();
                drop.setCount(amount);
                out.add(drop);
                toDrop -= amount;
            }
            displayItems[i] = ItemStack.EMPTY;
            slotPrices[i] = 0L;
            slotStock[i] = 0;
            resetTransform(i);
        }
        if (!out.isEmpty()) {
            markUpdated();
        }
        return out;
    }

    @Override
    public ItemDisplayTransform getSlotTransform(int slot) {
        int idx = clampSlot(slot);
        return new ItemDisplayTransform(
                slotOffsetX[idx],
                slotOffsetY[idx],
                slotOffsetZ[idx],
                slotRotationX[idx],
                slotRotationY[idx],
                slotRotationZ[idx],
                slotScaleX[idx],
                slotScaleY[idx],
                slotScaleZ[idx]
        );
    }

    @Override
    public void setSlotTransform(int slot,
                                 float offsetX,
                                 float offsetY,
                                 float offsetZ,
                                 float rotationX,
                                 float rotationY,
                                 float rotationZ,
                                 float scaleX,
                                 float scaleY,
                                 float scaleZ) {
        int idx = clampSlot(slot);
        ItemDisplayTransform clamped = ShelfTransformBounds.forType(ShelfDisplayType.TALL_WALL).clamp(
                new ItemDisplayTransform(
                        offsetX,
                        offsetY,
                        offsetZ,
                        rotationX,
                        rotationY,
                        rotationZ,
                        scaleX,
                        scaleY,
                        scaleZ
                )
        );
        slotOffsetX[idx] = clamped.offsetX();
        slotOffsetY[idx] = clamped.offsetY();
        slotOffsetZ[idx] = clamped.offsetZ();
        slotRotationX[idx] = clamped.rotationX();
        slotRotationY[idx] = clamped.rotationY();
        slotRotationZ[idx] = clamped.rotationZ();
        slotScaleX[idx] = clamped.scaleX();
        slotScaleY[idx] = clamped.scaleY();
        slotScaleZ[idx] = clamped.scaleZ();
        markUpdated();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        if (ownerUuid != null) {
            tag.putUUID("owner_uuid", ownerUuid);
        }
        if (shopId != null) {
            tag.putUUID(TAG_SHOP_ID, shopId);
        }
        tag.putString("owner_name", getOwnerName());
        tag.putBoolean(TAG_CREATIVE_SHELF, creativeShelf);
        tag.putBoolean(TAG_SHOP_MODE, shopMode);

        for (int i = 0; i < SLOT_COUNT; i++) {
            tag.put("slot_item_" + i, displayItems[i].save(new CompoundTag()));
            tag.putLong("slot_price_" + i, Math.max(0L, slotPrices[i]));
            tag.putInt(TAG_STOCK_COUNT + i, Math.max(0, slotStock[i]));
            tag.putFloat(TAG_OFFSET_X + i, slotOffsetX[i]);
            tag.putFloat(TAG_OFFSET_Y + i, slotOffsetY[i]);
            tag.putFloat(TAG_OFFSET_Z + i, slotOffsetZ[i]);
            tag.putFloat(TAG_ROT_X + i, slotRotationX[i]);
            tag.putFloat(TAG_ROT_Y + i, slotRotationY[i]);
            tag.putFloat(TAG_ROT_Z + i, slotRotationZ[i]);
            tag.putFloat(TAG_SCALE + i, slotScaleX[i]);
            tag.putFloat(TAG_SCALE_X + i, slotScaleX[i]);
            tag.putFloat(TAG_SCALE_Y + i, slotScaleY[i]);
            tag.putFloat(TAG_SCALE_Z + i, slotScaleZ[i]);
        }
        tag.putBoolean(TAG_PRICE_IN_CENTS, true);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        ownerUuid = tag.contains("owner_uuid") ? tag.getUUID("owner_uuid") : null;
        shopId = tag.contains(TAG_SHOP_ID) ? tag.getUUID(TAG_SHOP_ID) : null;
        ownerName = tag.getString("owner_name");
        if (tag.contains(TAG_CREATIVE_SHELF, Tag.TAG_BYTE)) {
            creativeShelf = tag.getBoolean(TAG_CREATIVE_SHELF);
        } else {
            creativeShelf = getBlockState().is(ModBlocks.TALL_WALL_SHELF.get());
        }
        if (tag.contains(TAG_SHOP_MODE, Tag.TAG_BYTE)) {
            shopMode = tag.getBoolean(TAG_SHOP_MODE);
        } else {
            shopMode = true;
        }

        boolean inCents = tag.getBoolean(TAG_PRICE_IN_CENTS);
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (tag.contains("slot_item_" + i, Tag.TAG_COMPOUND)) {
                displayItems[i] = ItemStack.of(tag.getCompound("slot_item_" + i));
                if (!displayItems[i].isEmpty()) {
                    displayItems[i].setCount(1);
                }
            } else {
                displayItems[i] = ItemStack.EMPTY;
            }

            long price = Math.max(0L, tag.getLong("slot_price_" + i));
            if (!inCents) {
                try {
                    price = Math.multiplyExact(price, 100L);
                } catch (ArithmeticException ignored) {
                    price = Long.MAX_VALUE;
                }
            }
            slotPrices[i] = displayItems[i].isEmpty() ? 0L : price;
            int stock = Math.max(0, tag.getInt(TAG_STOCK_COUNT + i));
            if (displayItems[i].isEmpty()) {
                slotStock[i] = 0;
                resetTransform(i);
            } else {
                slotStock[i] = creativeShelf ? Integer.MAX_VALUE : stock;
                float legacyScale = tag.contains(TAG_SCALE + i, Tag.TAG_FLOAT) ? tag.getFloat(TAG_SCALE + i) : 1.0F;
                ItemDisplayTransform clamped = ShelfTransformBounds.forType(ShelfDisplayType.TALL_WALL).clamp(
                        new ItemDisplayTransform(
                                tag.getFloat(TAG_OFFSET_X + i),
                                tag.getFloat(TAG_OFFSET_Y + i),
                                tag.getFloat(TAG_OFFSET_Z + i),
                                tag.contains(TAG_ROT_X + i, Tag.TAG_FLOAT) ? tag.getFloat(TAG_ROT_X + i) : 0.0F,
                                tag.getFloat(TAG_ROT_Y + i),
                                tag.contains(TAG_ROT_Z + i, Tag.TAG_FLOAT) ? tag.getFloat(TAG_ROT_Z + i) : 0.0F,
                                tag.contains(TAG_SCALE_X + i, Tag.TAG_FLOAT) ? tag.getFloat(TAG_SCALE_X + i) : legacyScale,
                                tag.contains(TAG_SCALE_Y + i, Tag.TAG_FLOAT) ? tag.getFloat(TAG_SCALE_Y + i) : legacyScale,
                                tag.contains(TAG_SCALE_Z + i, Tag.TAG_FLOAT) ? tag.getFloat(TAG_SCALE_Z + i) : legacyScale
                        )
                );
                slotOffsetX[i] = clamped.offsetX();
                slotOffsetY[i] = clamped.offsetY();
                slotOffsetZ[i] = clamped.offsetZ();
                slotRotationX[i] = clamped.rotationX();
                slotRotationY[i] = clamped.rotationY();
                slotRotationZ[i] = clamped.rotationZ();
                slotScaleX[i] = clamped.scaleX();
                slotScaleY[i] = clamped.scaleY();
                slotScaleZ[i] = clamped.scaleZ();
            }
        }
        if (!shopMode) {
            for (int i = 0; i < SLOT_COUNT; i++) {
                slotPrices[i] = 0L;
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
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

    private static int clampSlot(int slot) {
        return Mth.clamp(slot, 0, SLOT_COUNT - 1);
    }

    private void resetTransform(int slot) {
        slotOffsetX[slot] = 0.0F;
        slotOffsetY[slot] = 0.0F;
        slotOffsetZ[slot] = 0.0F;
        slotRotationX[slot] = 0.0F;
        slotRotationY[slot] = 0.0F;
        slotRotationZ[slot] = 0.0F;
        slotScaleX[slot] = 1.0F;
        slotScaleY[slot] = 1.0F;
        slotScaleZ[slot] = 1.0F;
    }
}
