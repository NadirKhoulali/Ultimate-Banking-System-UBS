package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.util.ItemStackTagCompat;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModularWallDisplayBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements ShelfDisplayBlockEntity {
    // Internal capacity (2 rows x 2 columns). Active visible slot count depends on current layout mode.
    public static final int SLOT_COUNT = 4;
    private static final String TAG_PRICE_IN_CENTS = "slot_price_in_cents";
    private static final String TAG_STOCK_COUNT = "slot_stock_";
    private static final String TAG_SHOP_ID = "shop_id";
    private static final String TAG_SHOP_MODE = "shop_mode";
    private static final String TAG_LAYOUT_FOUR_SLOTS = "layout_four_slots";
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

    private final ItemStack[] displayItems = new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final long[] slotPrices = new long[]{0L, 0L, 0L, 0L};
    private final int[] slotStock = new int[]{0, 0, 0, 0};
    private final float[] slotOffsetX = new float[]{0.0F, 0.0F, 0.0F, 0.0F};
    private final float[] slotOffsetY = new float[]{0.0F, 0.0F, 0.0F, 0.0F};
    private final float[] slotOffsetZ = new float[]{0.0F, 0.0F, 0.0F, 0.0F};
    private final float[] slotRotationX = new float[]{0.0F, 0.0F, 0.0F, 0.0F};
    private final float[] slotRotationY = new float[]{0.0F, 0.0F, 0.0F, 0.0F};
    private final float[] slotRotationZ = new float[]{0.0F, 0.0F, 0.0F, 0.0F};
    private final float[] slotScaleX = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
    private final float[] slotScaleY = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
    private final float[] slotScaleZ = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
    private boolean fourSlotLayoutEnabled;

    private UUID ownerUuid;
    private UUID shopId;
    private String ownerName = "";
    private boolean shopMode = true;

    public ModularWallDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MODULAR_WALL_DISPLAY.get(), pos, state);
    }

    @Override
    public int getSlotCount() {
        // Compact layout: 2 centered items (one per row). Expanded layout: 4 items (2 per row).
        return fourSlotLayoutEnabled ? SLOT_COUNT : 2;
    }

    public boolean isFourSlotLayoutEnabled() {
        return fourSlotLayoutEnabled;
    }

    public void setFourSlotLayoutEnabled(boolean enabled) {
        if (this.fourSlotLayoutEnabled == enabled) {
            return;
        }
        this.fourSlotLayoutEnabled = enabled;
        markUpdated();
    }

    @Override
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Override
    public String getOwnerName() {
        return ownerName == null ? "" : ownerName;
    }

    @Override
    public UUID getShopId() {
        return shopId;
    }

    @Override
    public void setShopId(UUID shopId) {
        this.shopId = shopId;
        markUpdated();
    }

    @Override
    public void setOwner(UUID ownerUuid, String ownerName) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName == null ? "" : ownerName.trim();
        markUpdated();
    }

    @Override
    public boolean isCreativeShelf() {
        BlockState state = getBlockState();
        return state != null && state.is(ModBlocks.CREATIVE_MODULAR_WALL_DISPLAY.get());
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

    @Override
    public ItemStack getDisplayItem(int slot) {
        int idx = toInternalSlot(slot);
        return displayItems[idx].copy();
    }

    @Override
    public long getSlotPrice(int slot) {
        int idx = toInternalSlot(slot);
        return Math.max(0L, slotPrices[idx]);
    }

    @Override
    public int getSlotStock(int slot) {
        if (isCreativeShelf()) {
            return Integer.MAX_VALUE;
        }
        int idx = toInternalSlot(slot);
        return Math.max(0, slotStock[idx]);
    }

    @Override
    public void setSlot(int slot, ItemStack item, long priceCents, int stockCount) {
        int idx = toInternalSlot(slot);
        if (item == null || item.isEmpty()) {
            displayItems[idx] = ItemStack.EMPTY;
            slotPrices[idx] = 0L;
            slotStock[idx] = 0;
            resetTransform(idx);
        } else {
            ItemStack copy = item.copy();
            copy.setCount(1);
            displayItems[idx] = copy;
            slotPrices[idx] = isShopMode() ? Math.max(0L, priceCents) : 0L;
            slotStock[idx] = isCreativeShelf() ? Integer.MAX_VALUE : Math.max(0, stockCount);
        }
        markUpdated();
    }

    @Override
    public void setPriceOnly(int slot, long priceCents) {
        int idx = toInternalSlot(slot);
        if (displayItems[idx].isEmpty()) {
            slotPrices[idx] = 0L;
        } else {
            slotPrices[idx] = isShopMode() ? Math.max(0L, priceCents) : 0L;
        }
        markUpdated();
    }

    @Override
    public void clearSlot(int slot) {
        int idx = toInternalSlot(slot);
        displayItems[idx] = ItemStack.EMPTY;
        slotPrices[idx] = 0L;
        slotStock[idx] = 0;
        resetTransform(idx);
        markUpdated();
    }

    @Override
    public void setStockOnly(int slot, int stockCount) {
        int idx = toInternalSlot(slot);
        if (isCreativeShelf() || displayItems[idx].isEmpty()) {
            slotStock[idx] = isCreativeShelf() ? Integer.MAX_VALUE : 0;
        } else {
            slotStock[idx] = Math.max(0, stockCount);
        }
        markUpdated();
    }

    @Override
    public boolean consumeOneStock(int slot) {
        if (isCreativeShelf()) {
            return true;
        }
        int idx = toInternalSlot(slot);
        if (slotStock[idx] <= 0) {
            return false;
        }
        slotStock[idx] -= 1;
        markUpdated();
        return true;
    }

    @Override
    public void addStock(int slot, int amount) {
        if (isCreativeShelf() || amount <= 0) {
            return;
        }
        int idx = toInternalSlot(slot);
        if (displayItems[idx].isEmpty()) {
            return;
        }
        slotStock[idx] = Math.max(0, slotStock[idx]) + amount;
        markUpdated();
    }

    @Override
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
        int idx = toInternalSlot(slot);
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
        int idx = toInternalSlot(slot);
        ItemDisplayTransform clamped = ShelfTransformBounds.forType(ShelfDisplayType.MODULAR_WALL).clamp(
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        if (ownerUuid != null) {
            tag.putUUID("owner_uuid", ownerUuid);
        }
        if (shopId != null) {
            tag.putUUID(TAG_SHOP_ID, shopId);
        }
        tag.putBoolean(TAG_SHOP_MODE, shopMode);
        tag.putBoolean(TAG_LAYOUT_FOUR_SLOTS, fourSlotLayoutEnabled);
        tag.putString("owner_name", getOwnerName());

        for (int i = 0; i < SLOT_COUNT; i++) {
            tag.put("slot_item_" + i, ItemStackTagCompat.save(displayItems[i], registries));
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
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        ownerUuid = tag.contains("owner_uuid") ? tag.getUUID("owner_uuid") : null;
        shopId = tag.contains(TAG_SHOP_ID) ? tag.getUUID(TAG_SHOP_ID) : null;
        ownerName = tag.getString("owner_name");
        if (tag.contains(TAG_SHOP_MODE, Tag.TAG_BYTE)) {
            shopMode = tag.getBoolean(TAG_SHOP_MODE);
        } else {
            shopMode = true;
        }

        boolean inCents = tag.getBoolean(TAG_PRICE_IN_CENTS);
        boolean explicitLayoutFlag = tag.contains(TAG_LAYOUT_FOUR_SLOTS, Tag.TAG_BYTE);
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (tag.contains("slot_item_" + i, Tag.TAG_COMPOUND)) {
                displayItems[i] = ItemStackTagCompat.parse(tag.getCompound("slot_item_" + i), registries);
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
                slotStock[i] = isCreativeShelf() ? Integer.MAX_VALUE : stock;
                float legacyScale = tag.contains(TAG_SCALE + i, Tag.TAG_FLOAT) ? tag.getFloat(TAG_SCALE + i) : 1.0F;
                ItemDisplayTransform clamped = ShelfTransformBounds.forType(ShelfDisplayType.MODULAR_WALL).clamp(
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
        if (explicitLayoutFlag) {
            fourSlotLayoutEnabled = tag.getBoolean(TAG_LAYOUT_FOUR_SLOTS);
        } else {
            // Backward-compatibility: legacy shelves with configured side slots open in expanded mode.
            fourSlotLayoutEnabled = !displayItems[2].isEmpty() || !displayItems[3].isEmpty();
        }
        if (!shopMode) {
            for (int i = 0; i < SLOT_COUNT; i++) {
                slotPrices[i] = 0L;
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
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

    private int toInternalSlot(int visibleSlot) {
        int clampedVisible = Mth.clamp(visibleSlot, 0, Math.max(0, getSlotCount() - 1));
        if (fourSlotLayoutEnabled) {
            return Mth.clamp(clampedVisible, 0, SLOT_COUNT - 1);
        }
        // Compact mode uses one centered item per row; map row0->slot0 and row1->slot2.
        return clampedVisible <= 0 ? 0 : 2;
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

    private static float clampValue(float value, float min, float max) {
        return Mth.clamp(value, min, max);
    }
}
