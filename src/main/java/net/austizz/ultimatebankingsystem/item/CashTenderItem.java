package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.block.custom.CashStackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class CashTenderItem extends Item {
    private final CashStackBlock.CashKind cashKind;

    public CashTenderItem(CashStackBlock.CashKind cashKind, Properties properties) {
        super(properties);
        this.cashKind = cashKind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        BlockState clickedState = level.getBlockState(clickedPos);

        if (CashStackBlock.tryAddCash(level, clickedPos, clickedState, player, stack, cashKind)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        BlockPos placePos = clickedState.canBeReplaced()
                ? clickedPos
                : clickedPos.relative(context.getClickedFace());
        BlockState targetState = level.getBlockState(placePos);
        if (CashStackBlock.tryAddCash(level, placePos, targetState, player, stack, cashKind)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!targetState.canBeReplaced() || !level.isInWorldBounds(placePos)) {
            return InteractionResult.FAIL;
        }

        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockState placedState = CashStackBlock.stateForPlacement(cashKind, facing);
        if (!placedState.canSurvive(level, placePos)) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide()) {
            level.setBlock(placePos, placedState, Block.UPDATE_ALL);
            SoundType sound = placedState.getSoundType(level, placePos, player);
            level.playSound(
                    player,
                    placePos,
                    sound.getPlaceSound(),
                    SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 2.0F,
                    sound.getPitch() * 0.8F
            );
            level.gameEvent(GameEvent.BLOCK_PLACE, placePos, GameEvent.Context.of(player, placedState));
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
