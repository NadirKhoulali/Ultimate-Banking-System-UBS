package net.austizz.ultimatebankingsystem.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Bridges API internals to the active ItemStack data APIs.
 */
public final class ItemStackDataCompat {
    private ItemStackDataCompat() {
    }

    public static CompoundTag getCustomData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    public static void setCustomData(ItemStack stack, CompoundTag tag) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag == null ? new CompoundTag() : tag.copy()));
    }

    public static void setCustomName(ItemStack stack, Component name) {
        if (stack == null || stack.isEmpty() || name == null) {
            return;
        }
        stack.set(DataComponents.CUSTOM_NAME, name);
    }
}
