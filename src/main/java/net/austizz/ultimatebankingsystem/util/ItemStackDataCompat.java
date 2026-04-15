package net.austizz.ultimatebankingsystem.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Bridges 1.21 DataComponents usage to 1.20.1 ItemStack NBT/custom-name APIs.
 */
public final class ItemStackDataCompat {
    private static final String CUSTOM_MODEL_DATA_KEY = "CustomModelData";

    private ItemStackDataCompat() {
    }

    public static CompoundTag getCustomData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        CompoundTag tag = stack.getTag();
        return tag == null ? new CompoundTag() : tag.copy();
    }

    public static void setCustomData(ItemStack stack, CompoundTag tag) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.setTag(tag == null ? new CompoundTag() : tag.copy());
    }

    public static void setCustomName(ItemStack stack, Component name) {
        if (stack == null || stack.isEmpty() || name == null) {
            return;
        }
        stack.setHoverName(name);
    }

    public static void setCustomModelData(ItemStack stack, int modelData) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.getOrCreateTag().putInt(CUSTOM_MODEL_DATA_KEY, modelData);
    }

    public static boolean hasCustomModelData(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.hasTag()
                && stack.getTag().contains(CUSTOM_MODEL_DATA_KEY, Tag.TAG_INT);
    }

    public static int getCustomModelData(ItemStack stack, int defaultValue) {
        if (!hasCustomModelData(stack)) {
            return defaultValue;
        }
        return stack.getTag().getInt(CUSTOM_MODEL_DATA_KEY);
    }
}
