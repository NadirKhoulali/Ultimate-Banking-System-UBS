package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.menu.ShoppingBagMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public class ShoppingBagItem extends BlockItem {
    public ShoppingBagItem(net.minecraft.world.level.block.Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModBlocks.SHOPPING_BAG.get().asItem())) {
            return InteractionResultHolder.pass(stack);
        }
        openFromHand(player, hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null
                && context.getPlayer().isShiftKeyDown()
                && context.getItemInHand().is(ModBlocks.SHOPPING_BAG.get().asItem())) {
            openFromHand(context.getPlayer(), context.getHand());
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }
        return super.useOn(context);
    }

    private static void openFromHand(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemStack stack = serverPlayer.getItemInHand(hand);
        if (stack == null || stack.isEmpty() || !stack.is(ModBlocks.SHOPPING_BAG.get().asItem())) {
            return;
        }

        NetworkHooks.openScreen(
                serverPlayer,
                new SimpleMenuProvider(
                        (containerId, playerInventory, menuPlayer) -> ShoppingBagMenu.forItem(containerId, playerInventory, hand),
                        Component.translatable("container.ultimatebankingsystem.shopping_bag")
                ),
                buffer -> buffer.writeEnum(hand)
        );
    }
}
