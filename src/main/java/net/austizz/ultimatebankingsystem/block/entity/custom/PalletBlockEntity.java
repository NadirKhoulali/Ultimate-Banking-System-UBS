package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PalletBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
    public static final int COLUMNS = 9;
    public static final int LAYERS = 3;
    public static final int SLOT_COUNT = COLUMNS * LAYERS;
    public static final String DELIVERY_PALLET_ID_ITEM_KEY = "ubs_delivery_pallet_id";
    private static final String STORAGE_KEY = "boxes";
    private static final String DELIVERY_LABEL_ENABLED_KEY = "delivery_label_enabled";
    private static final String DELIVERY_LABEL_SHOP_KEY = "delivery_label_shop";
    private static final String DELIVERY_PALLET_ID_KEY = "delivery_pallet_id";

    private final ItemStackHandler boxes = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            markUpdated();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return !stack.isEmpty() && stack.is(ModBlocks.CARDBOARD_BOX.get().asItem());
        }
    };

    private boolean deliveryLabelEnabled;
    private String deliveryLabelShopName = "";
    private String deliveryPalletId = "";

    public PalletBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PALLET.get(), pos, state);
    }

    public IItemHandler getItemHandler() {
        return boxes;
    }

    public ItemStack getBox(int column, int layer) {
        int slot = toSlotIndex(column, layer);
        if (slot < 0 || slot >= boxes.getSlots()) {
            return ItemStack.EMPTY;
        }
        return boxes.getStackInSlot(slot);
    }

    public List<Integer> getNonEmptyLayersTopFirst(int column) {
        List<Integer> layers = new ArrayList<>();
        if (column < 0 || column >= COLUMNS) {
            return layers;
        }
        int layerCapacity = getLayerCapacity();
        for (int layer = layerCapacity - 1; layer >= 0; layer--) {
            int slot = toSlotIndex(column, layer);
            if (slot >= 0 && slot < boxes.getSlots() && !boxes.getStackInSlot(slot).isEmpty()) {
                layers.add(layer);
            }
        }
        return layers;
    }

    public boolean addBoxToColumn(int column, ItemStack boxStack) {
        if (boxStack == null || boxStack.isEmpty() || !boxStack.is(ModBlocks.CARDBOARD_BOX.get().asItem())) {
            return false;
        }
        if (column < 0 || column >= COLUMNS) {
            return false;
        }
        int layerCapacity = getLayerCapacity();
        for (int layer = 0; layer < layerCapacity; layer++) {
            int slot = toSlotIndex(column, layer);
            if (slot < 0 || slot >= boxes.getSlots()) {
                continue;
            }
            if (!boxes.getStackInSlot(slot).isEmpty()) {
                continue;
            }
            boxes.setStackInSlot(slot, copySingle(boxStack));
            return true;
        }
        return false;
    }

    public ItemStack removeBoxFromColumn(int column, boolean preferLowerLayer) {
        if (column < 0 || column >= COLUMNS) {
            return ItemStack.EMPTY;
        }
        int layerCapacity = getLayerCapacity();
        if (layerCapacity <= 0) {
            return ItemStack.EMPTY;
        }
        if (preferLowerLayer) {
            int lowestLayer = -1;
            for (int layer = 0; layer < layerCapacity; layer++) {
                int slot = toSlotIndex(column, layer);
                if (slot >= 0 && slot < boxes.getSlots() && !boxes.getStackInSlot(slot).isEmpty()) {
                    lowestLayer = layer;
                    break;
                }
            }
            if (lowestLayer < 0) {
                return ItemStack.EMPTY;
            }

            int removeSlot = toSlotIndex(column, lowestLayer);
            ItemStack removed = boxes.getStackInSlot(removeSlot).copy();
            boxes.setStackInSlot(removeSlot, ItemStack.EMPTY);

            for (int layer = lowestLayer + 1; layer < layerCapacity; layer++) {
                int fromSlot = toSlotIndex(column, layer);
                if (fromSlot < 0 || fromSlot >= boxes.getSlots()) {
                    continue;
                }
                ItemStack from = boxes.getStackInSlot(fromSlot);
                if (from.isEmpty()) {
                    break;
                }
                boxes.setStackInSlot(toSlotIndex(column, layer - 1), from.copy());
                boxes.setStackInSlot(fromSlot, ItemStack.EMPTY);
            }
            return removed;
        }

        for (int layer = layerCapacity - 1; layer >= 0; layer--) {
            int slot = toSlotIndex(column, layer);
            if (slot < 0 || slot >= boxes.getSlots()) {
                continue;
            }
            ItemStack stack = boxes.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack removed = stack.copy();
            boxes.setStackInSlot(slot, ItemStack.EMPTY);
            return removed;
        }
        return ItemStack.EMPTY;
    }

    public ItemStack removeBoxFromColumnAtLayer(int column, int layer) {
        if (column < 0 || column >= COLUMNS) {
            return ItemStack.EMPTY;
        }
        int layerCapacity = getLayerCapacity();
        if (layer < 0 || layer >= layerCapacity) {
            return ItemStack.EMPTY;
        }

        int removeSlot = toSlotIndex(column, layer);
        if (removeSlot < 0 || removeSlot >= boxes.getSlots()) {
            return ItemStack.EMPTY;
        }

        ItemStack removed = boxes.getStackInSlot(removeSlot);
        if (removed == null || removed.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = removed.copy();

        // Collapse any boxes above this layer downward so no stack can float.
        for (int l = layer; l < layerCapacity - 1; l++) {
            int toSlot = toSlotIndex(column, l);
            int fromSlot = toSlotIndex(column, l + 1);
            ItemStack from = boxes.getStackInSlot(fromSlot);
            boxes.setStackInSlot(toSlot, from == null ? ItemStack.EMPTY : from.copy());
        }
        boxes.setStackInSlot(toSlotIndex(column, layerCapacity - 1), ItemStack.EMPTY);
        return out;
    }

    public List<ItemStack> extractAllBoxesForDrop() {
        List<ItemStack> drops = new ArrayList<>();
        for (int slot = 0; slot < boxes.getSlots(); slot++) {
            ItemStack stack = boxes.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            drops.add(stack.copy());
            boxes.setStackInSlot(slot, ItemStack.EMPTY);
        }
        return drops;
    }

    public boolean hasDeliveryLabel() {
        return deliveryLabelEnabled;
    }

    public String getDeliveryPalletId() {
        return deliveryPalletId == null ? "" : deliveryPalletId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean hasDeliveryPalletId() {
        return !getDeliveryPalletId().isBlank();
    }

    public String ensureDeliveryPalletId() {
        String current = getDeliveryPalletId();
        if (!current.isBlank()) {
            return current;
        }
        String generated = UUID.randomUUID().toString().toLowerCase(java.util.Locale.ROOT);
        setDeliveryPalletId(generated);
        return generated;
    }

    public void setDeliveryPalletId(String palletId) {
        String safe = palletId == null ? "" : palletId.trim().toLowerCase(java.util.Locale.ROOT);
        if (!safe.equals(getDeliveryPalletId())) {
            deliveryPalletId = safe;
            markUpdated();
        } else {
            deliveryPalletId = safe;
        }
    }

    public String getDeliveryLabelShopName() {
        return deliveryLabelShopName == null ? "" : deliveryLabelShopName;
    }

    public void setDeliveryLabel(String shopName) {
        String safe = shopName == null ? "" : shopName.trim();
        if (!safe.equals(deliveryLabelShopName) || !deliveryLabelEnabled) {
            deliveryLabelShopName = safe;
            deliveryLabelEnabled = true;
            markUpdated();
        }
    }

    public void clearDeliveryLabel() {
        if (deliveryLabelEnabled || (deliveryLabelShopName != null && !deliveryLabelShopName.isBlank())) {
            deliveryLabelEnabled = false;
            deliveryLabelShopName = "";
            markUpdated();
        }
    }

    public AABB getRenderBoundingBox() {
        return new AABB(
                worldPosition.getX() - 1.0D,
                worldPosition.getY(),
                worldPosition.getZ() - 1.0D,
                worldPosition.getX() + 2.0D,
                worldPosition.getY() + 4.0D,
                worldPosition.getZ() + 2.0D
        );
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(STORAGE_KEY, boxes.serializeNBT(registries));
        tag.putBoolean(DELIVERY_LABEL_ENABLED_KEY, deliveryLabelEnabled);
        tag.putString(DELIVERY_LABEL_SHOP_KEY, getDeliveryLabelShopName());
        if (!getDeliveryPalletId().isBlank()) {
            tag.putString(DELIVERY_PALLET_ID_KEY, getDeliveryPalletId());
        } else {
            tag.remove(DELIVERY_PALLET_ID_KEY);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(STORAGE_KEY)) {
            CompoundTag storageTag = tag.getCompound(STORAGE_KEY).copy();
            // Backward-compat: old pallet saves may contain only 18 slots (2 layers).
            // Force current size so old worlds auto-upgrade and avoid out-of-range access.
            storageTag.putInt("Size", SLOT_COUNT);
            boxes.deserializeNBT(registries, storageTag);
        } else {
            CompoundTag storageTag = new CompoundTag();
            storageTag.putInt("Size", SLOT_COUNT);
            boxes.deserializeNBT(registries, storageTag);
        }
        deliveryLabelEnabled = tag.getBoolean(DELIVERY_LABEL_ENABLED_KEY);
        deliveryLabelShopName = tag.contains(DELIVERY_LABEL_SHOP_KEY) ? tag.getString(DELIVERY_LABEL_SHOP_KEY) : "";
        deliveryPalletId = tag.contains(DELIVERY_PALLET_ID_KEY) ? tag.getString(DELIVERY_PALLET_ID_KEY) : "";
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }


    private int toSlotIndex(int column, int layer) {
        if (column < 0 || column >= COLUMNS || layer < 0 || layer >= LAYERS) {
            return -1;
        }
        return layer * COLUMNS + column;
    }

    private int getLayerCapacity() {
        return Math.min(LAYERS, Math.max(0, boxes.getSlots() / COLUMNS));
    }

    private static ItemStack copySingle(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }
}
