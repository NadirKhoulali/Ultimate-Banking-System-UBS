package net.austizz.ultimatebankingsystem.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class SilverBarBlockItem extends BlockItem {
    private static final int INGOT_BASIS = 6;

    public SilverBarBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("[ UBS Bullion Reserve ]").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));
        tooltip.add(Component.literal("Contains: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(INGOT_BASIS + " compatible Silver Ingots").withStyle(ChatFormatting.WHITE)));
        tooltip.add(Component.literal("Real-World Basis: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Silver is ~18.75x more common than gold in Earth's crust").withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.literal("Minecraft Scarcity: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Depends on the mod providing silver ore").withStyle(ChatFormatting.YELLOW)));
        tooltip.add(Component.literal("Market Value: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(INGOT_BASIS + "x Central Bank silver spot").withStyle(ChatFormatting.GREEN)));
        tooltip.add(Component.literal("Live quote: Smartphone > Spot Market").withStyle(ChatFormatting.AQUA));
    }
}
