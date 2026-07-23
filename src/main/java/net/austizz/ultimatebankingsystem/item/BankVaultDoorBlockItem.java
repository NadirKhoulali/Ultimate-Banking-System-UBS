package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.block.custom.BankVaultDoorBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public final class BankVaultDoorBlockItem extends BlockItem {
    public BankVaultDoorBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        if (result.consumesAction() || context.getLevel().isClientSide()) {
            return result;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return result;
        }
        if (getBlock() instanceof BankVaultDoorBlock door) {
            BankVaultDoorBlock.PlacementIssue issue = door.findPlacementIssue(context);
            if (issue != null) {
                player.sendSystemMessage(Component.literal(issue.message()));
                return result;
            }
        }
        player.sendSystemMessage(Component.literal(
                "Vault door placement was rejected. Verify that you can manage every safe area touched by its 5 x 4 x 4 footprint."));
        return result;
    }
}
