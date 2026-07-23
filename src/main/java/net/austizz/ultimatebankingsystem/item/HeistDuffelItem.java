package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.menu.HeistDuffelMenu;
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

public final class HeistDuffelItem extends BlockItem {
    public HeistDuffelItem(net.minecraft.world.level.block.Block block, Properties properties) {
        super(block, properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (HeistDuffelData.isActive(stack)) {
            if (!level.isClientSide()) player.displayClientMessage(
                    Component.literal("Hold the heist action key to load this duffel."), true);
            return InteractionResultHolder.fail(stack);
        }
        open(player, hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (HeistDuffelData.isActive(stack)) {
            if (!context.getLevel().isClientSide() && context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.literal(
                        "Active heist duffels cannot be placed or opened."), true);
            }
            return InteractionResult.FAIL;
        }
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) return super.useOn(context);
        open(context.getPlayer(), context.getHand());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    private static void open(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ItemStack stack = serverPlayer.getItemInHand(hand);
        if (!stack.is(ModBlocks.HEIST_DUFFEL.get().asItem()) || HeistDuffelData.isActive(stack)) return;
        HeistDuffelData.ensureOpenId(stack);
        serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inventory, ignored) -> HeistDuffelMenu.forItem(id, inventory, hand),
                Component.translatable("container.ultimatebankingsystem.heist_duffel")),
                buffer -> buffer.writeEnum(hand));
    }
}
