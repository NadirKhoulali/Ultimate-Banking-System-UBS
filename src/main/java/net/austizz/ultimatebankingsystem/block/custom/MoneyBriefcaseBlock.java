package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.block.entity.custom.MoneyBriefcaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Flat black-leather money briefcase. The block model is empty; the whole
 * case (shell, lid, latches, stored bundles) is drawn by the block entity
 * renderer. Interaction happens entirely in-world (no screen).
 */
public class MoneyBriefcaseBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    // Closed case bounds [0.5,0,2.5]->[15.5,5.5,13.5]; the box is symmetric
    // around block center, so north/south and east/west share one shape each.
    private static final VoxelShape SHAPE_NORTH_SOUTH = Block.box(0.5D, 0.0D, 2.5D, 15.5D, 5.5D, 13.5D);
    private static final VoxelShape SHAPE_EAST_WEST = Block.box(2.5D, 0.0D, 0.5D, 13.5D, 5.5D, 15.5D);

    private final MapCodec<MoneyBriefcaseBlock> codec;

    public MoneyBriefcaseBlock(Properties properties) {
        super(properties);
        this.codec = simpleCodec(MoneyBriefcaseBlock::new);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // Latches face the placer (repo convention).
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(OPEN, false);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state,
                                  Direction direction,
                                  BlockState neighborState,
                                  LevelAccessor level,
                                  BlockPos pos,
                                  BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    private static VoxelShape shapeFor(BlockState state) {
        Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
        return facing.getAxis() == Direction.Axis.X ? SHAPE_EAST_WEST : SHAPE_NORTH_SOUTH;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                              BlockState state,
                                              Level level,
                                              BlockPos pos,
                                              Player player,
                                              InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (!MoneyBriefcaseBlockEntity.isMoneyStackItem(stack)) {
            // Non-money items fall through to the empty-hand toggle handling.
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof MoneyBriefcaseBlockEntity briefcase)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!briefcase.isOpenForStorage()) {
            showStatus(serverPlayer, "Open the briefcase first.");
            return ItemInteractionResult.sidedSuccess(false);
        }
        if (!briefcase.insertMoneyStack(serverPlayer, stack)) {
            showStatus(serverPlayer, "The briefcase is full.");
        }
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof MoneyBriefcaseBlockEntity briefcase)) {
            return InteractionResult.PASS;
        }
        if (serverPlayer.isShiftKeyDown() && briefcase.isOpenForStorage()) {
            briefcase.withdrawLast(serverPlayer);
            return InteractionResult.sidedSuccess(false);
        }
        briefcase.setTargetOpen(!briefcase.isTargetOpen());
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof MoneyBriefcaseBlockEntity briefcase) {
            boolean hasContents = briefcase.hasStoredMoney();
            // Shulker-style: contents ride along inside the dropped item, never
            // spill. Creative players only get a drop when it carries money.
            if (hasContents || player == null || !player.getAbilities().instabuild) {
                Containers.dropItemStack(
                        level,
                        pos.getX() + 0.5D,
                        pos.getY() + 0.25D,
                        pos.getZ() + 0.5D,
                        briefcase.createDropStack()
                );
            }
            // Mark this removal as handled (even for the intentional no-drop
            // creative-empty case) so onRemove does not spawn a second item.
            briefcase.setDropHandled();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() == newState.getBlock()) {
            super.onRemove(state, level, pos, newState, movedByPiston);
            return;
        }
        // Safety net for every non-player removal path (support block broken ->
        // updateShape returns air, explosions, ...): always yield exactly ONE
        // briefcase item carrying the full inventory. Player mining is already
        // handled (and flagged) in playerWillDestroy above. Pistons cannot move
        // block-entity blocks, so movedByPiston never dupes this drop.
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof MoneyBriefcaseBlockEntity briefcase
                && !briefcase.isDropHandled()) {
            briefcase.setDropHandled();
            Containers.dropItemStack(
                    level,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.25D,
                    pos.getZ() + 0.5D,
                    briefcase.createDropStack()
            );
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MoneyBriefcaseBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                            BlockState state,
                                                                            BlockEntityType<T> blockEntityType) {
        if (blockEntityType != ModBlockEntities.MONEY_BRIEFCASE.get()) {
            return null;
        }
        return (tickerLevel, tickerPos, tickerState, blockEntity) -> {
            if (blockEntity instanceof MoneyBriefcaseBlockEntity briefcase) {
                MoneyBriefcaseBlockEntity.tick(tickerLevel, tickerPos, tickerState, briefcase);
            }
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    private static void showStatus(ServerPlayer player, String message) {
        if (player != null && message != null && !message.isBlank()) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }
}
