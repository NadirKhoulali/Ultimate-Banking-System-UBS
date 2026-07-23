package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.block.entity.custom.SecureSafeBlockEntity;
import net.austizz.ultimatebankingsystem.heist.HeistService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SecureSafeBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final VoxelShape CLOSED_SHAPE = Block.box(0.5D, 0.0D, 0.5D, 15.5D, 16.0D, 15.5D);
    private static final VoxelShape OPEN_NORTH = Shapes.or(
            CLOSED_SHAPE,
            Block.box(14.0D, 0.0D, -3.0D, 16.0D, 16.0D, 13.0D)
    );
    private static final VoxelShape OPEN_SOUTH = Shapes.or(
            CLOSED_SHAPE,
            Block.box(0.0D, 0.0D, 3.0D, 2.0D, 16.0D, 19.0D)
    );
    private static final VoxelShape OPEN_WEST = Shapes.or(
            CLOSED_SHAPE,
            Block.box(-3.0D, 0.0D, 0.0D, 13.0D, 16.0D, 2.0D)
    );
    private static final VoxelShape OPEN_EAST = Shapes.or(
            CLOSED_SHAPE,
            Block.box(3.0D, 0.0D, 14.0D, 19.0D, 16.0D, 16.0D)
    );

    private static boolean internalRemoval;

    private final MapCodec<SecureSafeBlock> codec;
    private final boolean tall;

    public SecureSafeBlock(Properties properties, boolean tall) {
        super(properties);
        this.tall = tall;
        this.codec = simpleCodec(nextProperties -> new SecureSafeBlock(nextProperties, tall));
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(OPEN, false));
    }

    public boolean isTallSafe() {
        return tall;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos clicked = context.getClickedPos();
        Level level = context.getLevel();
        if (tall && clicked.getY() >= level.getMaxBuildHeight() - 1) {
            return null;
        }
        if (tall && !level.getBlockState(clicked.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(OPEN, false);
    }

    @Override
    public void setPlacedBy(Level level,
                            BlockPos pos,
                            BlockState state,
                            @Nullable LivingEntity placer,
                            ItemStack stack) {
        if (!level.isClientSide() && tall) {
            level.setBlock(
                    pos.above(),
                    state.setValue(HALF, DoubleBlockHalf.UPPER).setValue(OPEN, false),
                    3
            );
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                              BlockState state,
                                              Level level,
                                              BlockPos pos,
                                              Player player,
                                              InteractionHand hand,
                                              BlockHitResult hitResult) {
        return interact(level, state, pos, player, hand, stack, hitResult)
                ? ItemInteractionResult.sidedSuccess(level.isClientSide())
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hitResult) {
        return interact(level, state, pos, player, InteractionHand.MAIN_HAND, ItemStack.EMPTY, hitResult)
                ? InteractionResult.sidedSuccess(level.isClientSide())
                : InteractionResult.PASS;
    }

    private boolean interact(Level level,
                             BlockState state,
                             BlockPos pos,
                             Player player,
                             InteractionHand hand,
                             ItemStack stack,
                             BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return true;
        }
        BlockPos masterPos = getMasterPos(state, pos);
        BlockState masterState = level.getBlockState(masterPos);
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(masterPos) instanceof SecureSafeBlockEntity safe)) {
            return false;
        }

        var heist = HeistService.activeAt(serverPlayer.getServer(),
                level.dimension().location().toString(), masterPos);
        if (heist != null && !serverPlayer.hasPermissions(3)) {
            String message = HeistService.isCrew(serverPlayer.getServer(), serverPlayer.getUUID(), heist)
                    ? "Use the heist action key and the correct tool to access this safe."
                    : "This safe is locked during the active heist.";
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(message), true);
            return true;
        }

        if (!safe.isOpenForStorage()) {
            safe.openFor(serverPlayer);
            return true;
        }

        Direction facing = masterState.hasProperty(FACING) ? masterState.getValue(FACING) : Direction.NORTH;
        ItemStack held = stack == null ? serverPlayer.getItemInHand(hand) : stack;
        if (hitResult != null && safe.isChestCompartmentHit(facing, hitResult.getLocation())) {
            if (held.isEmpty()) {
                return safe.openChestCompartment(serverPlayer);
            }
            return safe.installChestUpgradeFromWorld(serverPlayer, held);
        }

        int shelfSlot = safe.resolveShelfSlotFromLook(serverPlayer, facing);
        if (shelfSlot >= 0) {
            if (held.isEmpty()) {
                return safe.takeShelfItem(serverPlayer, shelfSlot, serverPlayer.isShiftKeyDown());
            }
            return safe.placeShelfItem(serverPlayer, shelfSlot, held);
        }

        safe.openFor(serverPlayer);
        return true;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return null;
        }
        return new SecureSafeBlockEntity(pos, state, tall);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                            BlockState state,
                                                                            BlockEntityType<T> blockEntityType) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER || blockEntityType != ModBlockEntities.SECURE_SAFE.get()) {
            return null;
        }
        return (tickerLevel, tickerPos, tickerState, blockEntity) -> {
            if (blockEntity instanceof SecureSafeBlockEntity safe) {
                SecureSafeBlockEntity.tick(tickerLevel, tickerPos, tickerState, safe);
            }
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        if (tall && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return RenderShape.INVISIBLE;
        }
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
        if (!state.getValue(OPEN)) {
            return CLOSED_SHAPE;
        }
        return switch (state.getValue(FACING)) {
            case SOUTH -> OPEN_SOUTH;
            case WEST -> OPEN_WEST;
            case EAST -> OPEN_EAST;
            case NORTH, UP, DOWN -> OPEN_NORTH;
        };
    }

    @Override
    public BlockState updateShape(BlockState state,
                                  Direction direction,
                                  BlockState neighborState,
                                  LevelAccessor level,
                                  BlockPos pos,
                                  BlockPos neighborPos) {
        if (!tall) {
            return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        }
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y
                && ((half == DoubleBlockHalf.LOWER) == (direction == Direction.UP))) {
            return neighborState.is(this) && neighborState.getValue(HALF) != half
                    ? state.setValue(FACING, neighborState.getValue(FACING)).setValue(OPEN, neighborState.getValue(OPEN))
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !internalRemoval) {
            removeStructure(level, getMasterPos(state, pos), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() == newState.getBlock()) {
            super.onRemove(state, level, pos, newState, movedByPiston);
            return;
        }
        if (!level.isClientSide() && !internalRemoval) {
            removeStructure(level, getMasterPos(state, pos), null);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void removeStructure(Level level, BlockPos masterPos, @Nullable Player breaker) {
        if (level == null || masterPos == null || internalRemoval) {
            return;
        }
        internalRemoval = true;
        try {
            BlockState masterState = level.getBlockState(masterPos);
            Block block = masterState.getBlock();
            if (!(block instanceof SecureSafeBlock safeBlock)) {
                return;
            }

            if (level.getBlockEntity(masterPos) instanceof SecureSafeBlockEntity safe) {
                safe.dropContents();
            }

            if (safeBlock.tall) {
                BlockPos upper = masterPos.above();
                if (level.getBlockState(upper).is(this)) {
                    level.setBlock(upper, Blocks.AIR.defaultBlockState(), 35);
                }
            }
            if (level.getBlockState(masterPos).is(this)) {
                level.setBlock(masterPos, Blocks.AIR.defaultBlockState(), 35);
            }

            if (breaker == null || !breaker.getAbilities().instabuild) {
                Containers.dropItemStack(
                        level,
                        masterPos.getX() + 0.5D,
                        masterPos.getY() + 0.35D,
                        masterPos.getZ() + 0.5D,
                        new ItemStack(block.asItem())
                );
            }
        } finally {
            internalRemoval = false;
        }
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state == null || pos == null || !(state.getBlock() instanceof SecureSafeBlock)) {
            return pos;
        }
        if (state.hasProperty(HALF) && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos;
    }

    public static boolean isSafeBlock(BlockState state) {
        return state != null && (state.is(ModBlocks.STANDING_SAFE.get()) || state.is(ModBlocks.COMPACT_SAFE.get()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, OPEN);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
