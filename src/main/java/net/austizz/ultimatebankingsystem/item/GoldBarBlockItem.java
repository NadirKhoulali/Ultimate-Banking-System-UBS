package net.austizz.ultimatebankingsystem.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class GoldBarBlockItem extends BlockItem {
    private static final int INGOT_BASIS = 6;
    private static final String SCARCITY_INDEX = "~37,000x Earth-crust availability";

    public GoldBarBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("[ UBS Bullion Reserve ]").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        tooltip.add(Component.literal("Contains: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(INGOT_BASIS + " Minecraft Gold Ingots").withStyle(ChatFormatting.YELLOW)));
        tooltip.add(Component.literal("Scarcity Index: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(SCARCITY_INDEX).withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("Market Value: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(INGOT_BASIS + "x Central Bank gold spot").withStyle(ChatFormatting.GREEN)));
        tooltip.add(Component.literal("Live quote: Smartphone > Spot Market").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Based on normal Overworld generation; badlands excluded.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
