package net.austizz.ultimatebankingsystem.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class ItemStackDataCompat {
    public static final HolderLookup.Provider DEFAULT_REGISTRIES = RegistryAccess.EMPTY;
    private static final String CUSTOM_MODEL_DATA_KEY = "CustomModelData";

    private ItemStackDataCompat() {
    }

    public static CompoundTag getCustomData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static void setCustomData(ItemStack stack, CompoundTag tag) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag.copy()));
    }

    public static void putCustomData(String key, ItemStack stack, Tag value) {
        if (stack == null || stack.isEmpty() || key == null || key.isBlank() || value == null) {
            return;
        }
        CompoundTag tag = getCustomData(stack);
        tag.put(key, value);
        setCustomData(stack, tag);
    }

    public static void putCustomString(String key, ItemStack stack, String value) {
        if (stack == null || stack.isEmpty() || key == null || key.isBlank()) {
            return;
        }
        CompoundTag tag = getCustomData(stack);
        tag.putString(key, value == null ? "" : value);
        setCustomData(stack, tag);
    }

    public static void removeCustomData(String key, ItemStack stack) {
        if (stack == null || stack.isEmpty() || key == null || key.isBlank()) {
            return;
        }
        CompoundTag tag = getCustomData(stack);
        tag.remove(key);
        setCustomData(stack, tag);
    }

    public static void setCustomName(ItemStack stack, Component name) {
        if (stack == null || stack.isEmpty() || name == null) {
            return;
        }
        stack.set(DataComponents.CUSTOM_NAME, name);
    }

    public static void setCustomModelData(ItemStack stack, int modelData) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(modelData));
    }

    public static boolean hasCustomModelData(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(DataComponents.CUSTOM_MODEL_DATA);
    }

    public static int getCustomModelData(ItemStack stack, int defaultValue) {
        if (!hasCustomModelData(stack)) {
            return defaultValue;
        }
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return data == null ? defaultValue : data.value();
    }

    public static String getCustomString(String key, ItemStack stack) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return getCustomData(stack).getString(key);
    }

    public static boolean hasCustomKey(String key, ItemStack stack) {
        return key != null && !key.isBlank() && getCustomData(stack).contains(key);
    }

    public static CompoundTag saveStack(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        Tag saved = stack.save(registries == null ? DEFAULT_REGISTRIES : registries, new CompoundTag());
        if (saved instanceof CompoundTag compoundTag) {
            return compoundTag;
        }
        return new CompoundTag();
    }

    public static CompoundTag saveStack(ItemStack stack) {
        return saveStack(stack, DEFAULT_REGISTRIES);
    }

    public static ItemStack parseStack(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag == null || tag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            return ItemStack.parseOptional(registries == null ? DEFAULT_REGISTRIES : registries, tag);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static ItemStack parseStack(CompoundTag tag) {
        return parseStack(tag, DEFAULT_REGISTRIES);
    }

    public static CompoundTag serializeHandler(ItemStackHandler handler, HolderLookup.Provider registries) {
        return handler == null ? new CompoundTag() : handler.serializeNBT(registries == null ? DEFAULT_REGISTRIES : registries);
    }

    public static CompoundTag serializeHandler(ItemStackHandler handler) {
        return serializeHandler(handler, DEFAULT_REGISTRIES);
    }

    public static void deserializeHandler(ItemStackHandler handler, CompoundTag tag, HolderLookup.Provider registries) {
        if (handler != null) {
            handler.deserializeNBT(registries == null ? DEFAULT_REGISTRIES : registries, tag == null ? new CompoundTag() : tag);
        }
    }

    public static void deserializeHandler(ItemStackHandler handler, CompoundTag tag) {
        deserializeHandler(handler, tag, DEFAULT_REGISTRIES);
    }

    public static boolean sameItemSameComponents(ItemStack left, ItemStack right) {
        return ItemStack.isSameItemSameComponents(left, right);
    }

}
