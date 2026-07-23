package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.item.SafetyDepositBoxInsertItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SafetyDepositBoxRowBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<SafetyDepositBoxRowBlock> CODEC = simpleCodec(SafetyDepositBoxRowBlock::new);
    private static final int MAX_VERTICAL_STACK_ROWS = 4;
    private static final VoxelShape WALL_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public SafetyDepositBoxRowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (wouldExceedVerticalStackLimit(context)) {
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("Safety deposit box rows can only be stacked up to "
                        + MAX_VERTICAL_STACK_ROWS + " high."));
            }
            return null;
        }
        // Opposite of the player's look direction so the door side (model north /
        // entrance) faces the player who placed it.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return WALL_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return WALL_SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                              BlockState state,
                                              Level level,
                                              BlockPos pos,
                                              Player player,
                                              net.minecraft.world.InteractionHand hand,
                                              BlockHitResult hit) {
        if (stack.getItem() instanceof SafetyDepositBoxInsertItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return interact(state, level, pos, player, hit)
                ? ItemInteractionResult.sidedSuccess(level.isClientSide())
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hit) {
        return interact(state, level, pos, player, hit)
                ? InteractionResult.sidedSuccess(level.isClientSide())
                : InteractionResult.PASS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SafetyDepositBoxRowBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
                                                                             BlockState state,
                                                                             BlockEntityType<T> type) {
        if (type != ModBlockEntities.SAFETY_DEPOSIT_BOX_ROW.get()) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof SafetyDepositBoxRowBlockEntity row) {
                SafetyDepositBoxRowBlockEntity.tick(tickLevel, tickPos, tickState, row);
            }
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private static boolean interact(BlockState state,
                                    Level level,
                                    BlockPos pos,
                                    Player player,
                                    BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SafetyDepositBoxRowBlockEntity row)) {
            return false;
        }
        int doorIndex = doorIndexForHit(state, pos, hit);
        if (level.isClientSide()) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            SafetyDepositBoxService.openDoorForPlayer(serverPlayer, row, doorIndex);
        }
        return true;
    }

    public static int doorIndexForHit(BlockState state, BlockPos pos, BlockHitResult hit) {
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        double v = 1.0D - local.y;
        int index = (int) Math.floor(Math.max(0.0D, Math.min(0.999D, v)) * SafetyDepositBoxRowBlockEntity.DOOR_COUNT);
        return Math.max(0, Math.min(SafetyDepositBoxRowBlockEntity.DOOR_COUNT - 1, index));
    }

    private static boolean wouldExceedVerticalStackLimit(BlockPlaceContext context) {
        if (context == null || context.getLevel() == null) {
            return false;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).canBeReplaced(context)) {
            pos = pos.relative(context.getClickedFace());
        }
        int rows = 1
                + countStackedRows(level, pos, Direction.UP)
                + countStackedRows(level, pos, Direction.DOWN);
        return rows > MAX_VERTICAL_STACK_ROWS;
    }

    private static int countStackedRows(Level level, BlockPos origin, Direction direction) {
        int count = 0;
        BlockPos.MutableBlockPos cursor = origin.mutable();
        while (count < MAX_VERTICAL_STACK_ROWS) {
            cursor.move(direction);
            if (!(level.getBlockState(cursor).getBlock() instanceof SafetyDepositBoxRowBlock)) {
                break;
            }
            count++;
        }
        return count;
    }
}
