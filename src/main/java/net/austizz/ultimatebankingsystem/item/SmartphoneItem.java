package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.network.SmartphoneOpenRequestPayload;
import net.austizz.ultimatebankingsystem.phone.SmartphoneService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class SmartphoneItem extends Item {
    public SmartphoneItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            SmartphoneService.openPhone(serverPlayer, true);
        } else if (level.isClientSide()) {
            PacketDistributor.sendToServer(new SmartphoneOpenRequestPayload(true));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Owner: " + SmartphoneData.getOwnerName(stack)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Theme: " + SmartphoneData.getAccent(stack) + " / " + SmartphoneData.getWallpaper(stack))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Right-click or press P with it in inventory.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
