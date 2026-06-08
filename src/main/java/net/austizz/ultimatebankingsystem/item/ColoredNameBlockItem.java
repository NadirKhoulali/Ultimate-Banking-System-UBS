package net.austizz.ultimatebankingsystem.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

public class ColoredNameBlockItem extends BlockItem {
    private final ChatFormatting color;

    public ColoredNameBlockItem(Block block, Properties properties, @Nullable ChatFormatting color) {
        super(block, properties);
        this.color = color;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component name = super.getName(stack);
        return color == null ? name : name.copy().withStyle(color);
    }
}
