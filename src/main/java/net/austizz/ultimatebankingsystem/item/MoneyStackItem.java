package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.block.custom.MoneyStackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * A strapped bundle of 100 bills of a single denomination. Right-click places it in the
 * world as a {@code MoneyStackBlock} pile; crouch-use unwraps it back into 100 loose
 * bills (the strap is consumed).
 */
public class MoneyStackItem extends Item {
    private final MoneyStackBlock.BillDenomination denomination;

    public MoneyStackItem(MoneyStackBlock.BillDenomination denomination, Properties properties) {
        super(properties);
        this.denomination = denomination;
    }

    public MoneyStackBlock.BillDenomination getDenomination() {
        return denomination;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();

        if (player != null && player.isShiftKeyDown()) {
            return unwrap(level, player, stack);
        }

        BlockState clickedState = level.getBlockState(clickedPos);
        if (MoneyStackBlock.tryAddStack(level, clickedPos, clickedState, stack, player)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        BlockPos placePos = clickedState.canBeReplaced()
                ? clickedPos
                : clickedPos.relative(context.getClickedFace());
        BlockState targetState = level.getBlockState(placePos);
        if (MoneyStackBlock.tryAddStack(level, placePos, targetState, stack, player)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!targetState.canBeReplaced() || !level.isInWorldBounds(placePos)) {
            return InteractionResult.FAIL;
        }

        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockState placedState = MoneyStackBlock.stateForPlacement(denomination, facing);
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

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            InteractionResult result = unwrap(level, player, stack);
            return new InteractionResultHolder<>(result, stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    private InteractionResult unwrap(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        MoneyBundles.giveItem(serverPlayer, denomination.billItem(), MoneyBundles.BILLS_PER_STACK);
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.SHEEP_SHEAR,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
        return InteractionResult.CONSUME;
    }
}
