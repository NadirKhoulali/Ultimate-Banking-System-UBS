package net.austizz.ultimatebankingsystem.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class ItemStackTagCompat {
    private ItemStackTagCompat() {}
    public static CompoundTag get(ItemStack stack) { return ItemStackDataCompat.getCustomData(stack); }
    public static void set(ItemStack stack, CompoundTag tag) { ItemStackDataCompat.setCustomData(stack, tag); }
    public static void put(String key, ItemStack stack, Tag value) { ItemStackDataCompat.putCustomData(key, stack, value); }
    public static void putString(String key, ItemStack stack, String value) { ItemStackDataCompat.putCustomString(key, stack, value); }
    public static void remove(String key, ItemStack stack) { ItemStackDataCompat.removeCustomData(key, stack); }
    public static void setName(ItemStack stack, Component name) { ItemStackDataCompat.setCustomName(stack, name); }
    public static CompoundTag save(ItemStack stack, HolderLookup.Provider registries) { return ItemStackDataCompat.saveStack(stack, registries); }
    public static ItemStack parse(CompoundTag tag, HolderLookup.Provider registries) { return ItemStackDataCompat.parseStack(tag, registries); }
    public static CompoundTag serialize(ItemStackHandler handler, HolderLookup.Provider registries) { return ItemStackDataCompat.serializeHandler(handler, registries); }
    public static void deserialize(ItemStackHandler handler, CompoundTag tag, HolderLookup.Provider registries) { ItemStackDataCompat.deserializeHandler(handler, tag, registries); }
    public static boolean same(ItemStack left, ItemStack right) { return ItemStackDataCompat.sameItemSameComponents(left, right); }
}
