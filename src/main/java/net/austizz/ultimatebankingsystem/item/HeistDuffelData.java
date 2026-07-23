package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.UUID;

public final class HeistDuffelData {
    public static final int SLOT_COUNT = 27;
    public static final String ROOT_KEY = "heistDuffel";
    private static final String INVENTORY_KEY = "inventory";
    private static final String BAG_ID_KEY = "bagId";
    private static final String SESSION_ID_KEY = "sessionId";
    private static final String OWNER_ID_KEY = "ownerId";
    private static final String ACTIVE_KEY = "activeHeistBag";
    private static final String OPEN_ID_KEY = "openId";

    private HeistDuffelData() {}

    public static ItemStack createActive(ItemStack stack, UUID sessionId, UUID bagId, UUID ownerId) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        CompoundTag root = ItemStackDataCompat.getCustomData(stack);
        CompoundTag data = new CompoundTag();
        data.putUUID(BAG_ID_KEY, bagId == null ? UUID.randomUUID() : bagId);
        if (sessionId != null) data.putUUID(SESSION_ID_KEY, sessionId);
        if (ownerId != null) data.putUUID(OWNER_ID_KEY, ownerId);
        data.putBoolean(ACTIVE_KEY, true);
        data.putUUID(OPEN_ID_KEY, UUID.randomUUID());
        root.put(ROOT_KEY, data);
        ItemStackDataCompat.setCustomData(stack, root);
        return stack;
    }

    public static boolean isActive(ItemStack stack) {
        CompoundTag data = data(stack);
        return data != null && data.getBoolean(ACTIVE_KEY) && data.hasUUID(SESSION_ID_KEY);
    }

    public static UUID bagId(ItemStack stack) {
        CompoundTag data = data(stack);
        return data != null && data.hasUUID(BAG_ID_KEY) ? data.getUUID(BAG_ID_KEY) : null;
    }

    public static UUID sessionId(ItemStack stack) {
        CompoundTag data = data(stack);
        return data != null && data.hasUUID(SESSION_ID_KEY) ? data.getUUID(SESSION_ID_KEY) : null;
    }

    public static UUID ownerId(ItemStack stack) {
        CompoundTag data = data(stack);
        return data != null && data.hasUUID(OWNER_ID_KEY) ? data.getUUID(OWNER_ID_KEY) : null;
    }

    public static UUID ensureOpenId(ItemStack stack) {
        CompoundTag root = ItemStackDataCompat.getCustomData(stack);
        CompoundTag data = root.contains(ROOT_KEY, Tag.TAG_COMPOUND)
                ? root.getCompound(ROOT_KEY).copy() : new CompoundTag();
        if (!data.hasUUID(BAG_ID_KEY)) data.putUUID(BAG_ID_KEY, UUID.randomUUID());
        if (!data.hasUUID(OPEN_ID_KEY)) data.putUUID(OPEN_ID_KEY, UUID.randomUUID());
        root.put(ROOT_KEY, data);
        ItemStackDataCompat.setCustomData(stack, root);
        return data.getUUID(OPEN_ID_KEY);
    }

    public static UUID openId(ItemStack stack) {
        CompoundTag data = data(stack);
        return data != null && data.hasUUID(OPEN_ID_KEY) ? data.getUUID(OPEN_ID_KEY) : null;
    }

    public static void makePermanent(ItemStack stack) {
        CompoundTag root = ItemStackDataCompat.getCustomData(stack);
        if (!root.contains(ROOT_KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag data = root.getCompound(ROOT_KEY).copy();
        data.remove(SESSION_ID_KEY);
        data.remove(OWNER_ID_KEY);
        data.putBoolean(ACTIVE_KEY, false);
        root.put(ROOT_KEY, data);
        ItemStackDataCompat.setCustomData(stack, root);
    }

    public static ItemStackHandler readInventory(ItemStack stack, HolderLookup.Provider registries) {
        ItemStackHandler handler = new ItemStackHandler(SLOT_COUNT);
        CompoundTag data = data(stack);
        if (data != null && data.contains(INVENTORY_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag inventory = data.getCompound(INVENTORY_KEY).copy();
            inventory.putInt("Size", SLOT_COUNT);
            handler.deserializeNBT(registries, inventory);
        }
        return handler;
    }

    public static void writeInventory(ItemStack stack, ItemStackHandler handler, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty() || handler == null) return;
        CompoundTag root = ItemStackDataCompat.getCustomData(stack);
        CompoundTag data = root.contains(ROOT_KEY, Tag.TAG_COMPOUND)
                ? root.getCompound(ROOT_KEY).copy() : new CompoundTag();
        data.put(INVENTORY_KEY, handler.serializeNBT(registries));
        if (!data.hasUUID(BAG_ID_KEY)) data.putUUID(BAG_ID_KEY, UUID.randomUUID());
        if (!data.hasUUID(OPEN_ID_KEY)) data.putUUID(OPEN_ID_KEY, UUID.randomUUID());
        root.put(ROOT_KEY, data);
        ItemStackDataCompat.setCustomData(stack, root);
    }

    public static boolean canInsert(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !(stack.getItem() instanceof HeistDuffelItem);
    }

    public static boolean insert(ItemStack bag, ItemStack loot, HolderLookup.Provider registries) {
        if (!canInsert(loot)) return false;
        ItemStackHandler handler = readInventory(bag, registries);
        ItemStack remainder = loot.copy();
        for (int slot = 0; slot < SLOT_COUNT && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, false);
        }
        if (!remainder.isEmpty()) return false;
        writeInventory(bag, handler, registries);
        return true;
    }

    public static int occupiedSlots(ItemStack bag, HolderLookup.Provider registries) {
        ItemStackHandler handler = readInventory(bag, registries);
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) if (!handler.getStackInSlot(i).isEmpty()) count++;
        return count;
    }

    private static CompoundTag data(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag root = ItemStackDataCompat.getCustomData(stack);
        return root.contains(ROOT_KEY, Tag.TAG_COMPOUND) ? root.getCompound(ROOT_KEY) : null;
    }
}
