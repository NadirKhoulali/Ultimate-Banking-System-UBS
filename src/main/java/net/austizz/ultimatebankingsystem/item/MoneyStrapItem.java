package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.block.custom.MoneyStackBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Federal-Reserve-style currency strap for one bill denomination. Combine one strap
 * with exactly 100 matching bills in a crafting grid to bundle them into a money stack.
 */
public class MoneyStrapItem extends Item {
    private final MoneyStackBlock.BillDenomination denomination;

    public MoneyStrapItem(MoneyStackBlock.BillDenomination denomination, Properties properties) {
        super(properties);
        this.denomination = denomination;
    }

    public MoneyStackBlock.BillDenomination getDenomination() {
        return denomination;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        "item.ultimatebankingsystem." + denomination.getSerializedName() + "_strap.description")
                .withStyle(ChatFormatting.GRAY));
    }
}
