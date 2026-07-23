package net.austizz.ultimatebankingsystem.api.heist;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalLong;

@FunctionalInterface
public interface HeistLootValueProvider {
    OptionalLong valueCents(MinecraftServer server, ItemStack stack);
}
