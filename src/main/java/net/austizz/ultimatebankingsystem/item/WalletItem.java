package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.menu.WalletMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class WalletItem extends Item {
    public WalletItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            WalletData.ensureOwner(stack, serverPlayer);
            WalletData.ensureOpenReference(stack);
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (containerId, inventory, ignored) -> WalletMenu.forItem(containerId, inventory, hand),
                            Component.literal("Wallet")
                    ),
                    buffer -> buffer.writeEnum(hand)
            );
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Owner: " + WalletData.getOwnerName(stack)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Mode: " + displayMode(WalletData.getMode(stack))).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Card fallback: " + (WalletData.isCardFallbackEnabled(stack) ? "On" : "Off"))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Cash: $" + WalletData.formatTotalCash(stack)).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("Cards: " + WalletData.getCardCount(stack, context.registries()))
                .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.literal("Right-click to open.").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String displayMode(WalletData.PaymentMode mode) {
        return mode == WalletData.PaymentMode.CARD ? "Card" : "Cash";
    }
}
