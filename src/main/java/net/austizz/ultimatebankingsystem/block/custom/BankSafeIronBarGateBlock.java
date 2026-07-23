package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.block.entity.custom.BankSafeIronBarGateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BankSafeIronBarGateBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<BankSafeIronBarGateBlock> CODEC = simpleCodec(BankSafeIronBarGateBlock::new);
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 4);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 3);
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    private static final int MASTER_PART_X = 2;
    private static final int MASTER_PART_Y = 0;
    private static final int MAX_PART_X = 4;
    private static final int MAX_PART_Y = 3;
    private static final float PASSABLE_PROGRESS = 0.92F;
    private static final VoxelShape FULL_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape BOTTOM_THRESHOLD_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    private static final VoxelShape EMPTY_SHAPE = Shapes.empty();
    private static boolean internalRemoval;

    public BankSafeIronBarGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART_X, MASTER_PART_X)
                .setValue(PART_Y, MASTER_PART_Y)
                .setValue(POWERED, false)
                .setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos masterPos = context.getClickedPos();
        Level level = context.getLevel();
        for (int partY = 0; partY <= MAX_PART_Y; partY++) {
            if (masterPos.getY() + partY >= level.getMaxBuildHeight()) {
                return null;
            }
            for (int partX = 0; partX <= MAX_PART_X; partX++) {
                BlockPos partPos = getPartPos(masterPos, facing, partX, partY);
                if (!level.getBlockState(partPos).canBeReplaced(context)) {
                    return null;
                }
            }
        }
        boolean powered = hasAnyPartNeighborSignal(level, masterPos, facing);
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(PART_X, MASTER_PART_X)
                .setValue(PART_Y, MASTER_PART_Y)
                .setValue(POWERED, powered);
    }

    @Override
    public void setPlacedBy(Level level,
                            BlockPos pos,
                            BlockState state,
                            @Nullable LivingEntity placer,
                            ItemStack stack) {
        if (level.isClientSide()) {
            return;
        }
        Direction facing = state.getValue(FACING);
        boolean powered = hasAnyPartNeighborSignal(level, pos, facing);
        BlockState baseState = defaultBlockState()
                .setValue(FACING, facing)
                .setValue(POWERED, powered)
                .setValue(OPEN, false);

        for (int partY = 0; partY <= MAX_PART_Y; partY++) {
            for (int partX = 0; partX <= MAX_PART_X; partX++) {
                BlockState partState = baseState
                        .setValue(PART_X, partX)
                        .setValue(PART_Y, partY);
                BlockPos partPos = getPartPos(pos, facing, partX, partY);
                level.setBlock(partPos, partState, Block.UPDATE_ALL);
            }
        }
        if (level.getBlockEntity(pos) instanceof BankSafeIronBarGateBlockEntity gate) {
            gate.setTargetOpen(powered);
        }
    }

    @Override
    protected void neighborChanged(BlockState state,
                                   Level level,
                                   BlockPos pos,
                                   Block neighborBlock,
                                   BlockPos neighborPos,
                                   boolean movedByPiston) {
        if (!level.isClientSide()) {
            syncPoweredState(level, getMasterPos(state, pos));
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    public BlockState updateShape(BlockState state,
                                  Direction direction,
                                  BlockState neighborState,
                                  LevelAccessor level,
                                  BlockPos pos,
                                  BlockPos neighborPos) {
        if (internalRemoval || !(level instanceof Level readableLevel)) {
            return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        }
        BlockPos masterPos = getMasterPos(state, pos);
        BlockState masterState = readableLevel.getBlockState(masterPos);
        if (!masterState.is(this)
                || masterState.getValue(PART_X) != MASTER_PART_X
                || masterState.getValue(PART_Y) != MASTER_PART_Y) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getGateShape(state, level, pos);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getGateShape(state, level, pos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            removeStructure(level, getMasterPos(state, pos), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() == newState.getBlock()) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }
        if (!level.isClientSide() && !internalRemoval) {
            removeStructure(level, getMasterPos(state, pos), null);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART_X) != MASTER_PART_X || state.getValue(PART_Y) != MASTER_PART_Y) {
            return null;
        }
        return new BankSafeIronBarGateBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
                                                                             BlockState state,
                                                                             BlockEntityType<T> type) {
        if (type != ModBlockEntities.BANK_SAFE_IRON_BAR_GATE.get()) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof BankSafeIronBarGateBlockEntity gate) {
                BankSafeIronBarGateBlockEntity.tick(tickLevel, tickPos, tickState, gate);
            }
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_X, PART_Y, POWERED, OPEN);
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state == null || pos == null || !state.hasProperty(FACING)
                || !state.hasProperty(PART_X) || !state.hasProperty(PART_Y)) {
            return pos;
        }
        Direction facing = state.getValue(FACING);
        int partX = state.getValue(PART_X);
        int partY = state.getValue(PART_Y);
        return pos.relative(getRightDirection(facing), MASTER_PART_X - partX)
                .below(partY - MASTER_PART_Y);
    }

    public static void setOpenStateIfNeeded(Level level, BlockPos masterPos, BlockState masterState, boolean open) {
        if (level == null || level.isClientSide() || masterPos == null || masterState == null
                || !masterState.is(ModBlocks.BANK_SAFE_IRON_BAR_GATE.get())) {
            return;
        }
        BlockState currentState = level.getBlockState(masterPos);
        if (!currentState.is(ModBlocks.BANK_SAFE_IRON_BAR_GATE.get()) || !currentState.hasProperty(OPEN)
                || currentState.getValue(OPEN) == open) {
            return;
        }
        Direction facing = currentState.hasProperty(FACING) ? currentState.getValue(FACING) : Direction.NORTH;
        setAllPartsOpen(level, masterPos, facing, open);
    }

    private static VoxelShape getGateShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (state == null || !state.hasProperty(PART_X) || !state.hasProperty(PART_Y)
                || !state.hasProperty(POWERED) || !state.hasProperty(OPEN)) {
            return FULL_SHAPE;
        }
        VoxelShape staticFrame = getStaticFrameShape(state);
        VoxelShape bars = getBarsShape(state, level, pos);
        return bars.isEmpty() ? staticFrame : Shapes.or(staticFrame, bars);
    }

    private static VoxelShape getStaticFrameShape(BlockState state) {
        int partX = state.getValue(PART_X);
        int partY = state.getValue(PART_Y);
        if (partX == 0 || partX == MAX_PART_X || partY == MAX_PART_Y) {
            return FULL_SHAPE;
        }
        if (partY == 0) {
            return BOTTOM_THRESHOLD_SHAPE;
        }
        return EMPTY_SHAPE;
    }

    private static VoxelShape getBarsShape(BlockState state, BlockGetter level, BlockPos pos) {
        int partX = state.getValue(PART_X);
        int partY = state.getValue(PART_Y);
        if (partX <= 0 || partX >= MAX_PART_X || partY <= 0 || partY >= MAX_PART_Y) {
            return EMPTY_SHAPE;
        }
        return getAnimationProgress(state, level, pos) >= PASSABLE_PROGRESS ? EMPTY_SHAPE : FULL_SHAPE;
    }

    private static float getAnimationProgress(BlockState state, BlockGetter level, BlockPos pos) {
        if (level != null && pos != null) {
            BlockPos masterPos = getMasterPos(state, pos);
            if (level.getBlockEntity(masterPos) instanceof BankSafeIronBarGateBlockEntity gate) {
                return gate.getCurrentAnimationProgress();
            }
        }
        return state.getValue(OPEN) ? 1.0F : 0.0F;
    }

    private static void syncPoweredState(Level level, BlockPos masterPos) {
        if (level == null || masterPos == null || level.isClientSide()) {
            return;
        }
        BlockState masterState = level.getBlockState(masterPos);
        if (!masterState.is(ModBlocks.BANK_SAFE_IRON_BAR_GATE.get())) {
            return;
        }
        Direction facing = masterState.getValue(FACING);
        boolean powered = hasAnyPartNeighborSignal(level, masterPos, facing);
        setAllPartsPowered(level, masterPos, facing, powered);
        if (level.getBlockEntity(masterPos) instanceof BankSafeIronBarGateBlockEntity gate) {
            gate.setTargetOpen(powered);
        }
    }

    private static void setAllPartsPowered(Level level, BlockPos masterPos, Direction facing, boolean powered) {
        for (int partY = 0; partY <= MAX_PART_Y; partY++) {
            for (int partX = 0; partX <= MAX_PART_X; partX++) {
                BlockPos partPos = getPartPos(masterPos, facing, partX, partY);
                BlockState partState = level.getBlockState(partPos);
                if (!partState.is(ModBlocks.BANK_SAFE_IRON_BAR_GATE.get())
                        || !partState.hasProperty(POWERED)
                        || partState.getValue(POWERED) == powered) {
                    continue;
                }
                level.setBlock(partPos, partState.setValue(POWERED, powered), Block.UPDATE_ALL);
            }
        }
    }

    private static void setAllPartsOpen(Level level, BlockPos masterPos, Direction facing, boolean open) {
        for (int partY = 0; partY <= MAX_PART_Y; partY++) {
            for (int partX = 0; partX <= MAX_PART_X; partX++) {
                BlockPos partPos = getPartPos(masterPos, facing, partX, partY);
                BlockState partState = level.getBlockState(partPos);
                if (!partState.is(ModBlocks.BANK_SAFE_IRON_BAR_GATE.get())
                        || !partState.hasProperty(OPEN)
                        || partState.getValue(OPEN) == open) {
                    continue;
                }
                level.setBlock(partPos, partState.setValue(OPEN, open), Block.UPDATE_ALL);
            }
        }
    }

    private static boolean hasAnyPartNeighborSignal(Level level, BlockPos masterPos, Direction facing) {
        if (level == null || masterPos == null || facing == null) {
            return false;
        }
        for (int partY = 0; partY <= MAX_PART_Y; partY++) {
            for (int partX = 0; partX <= MAX_PART_X; partX++) {
                if (level.hasNeighborSignal(getPartPos(masterPos, facing, partX, partY))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BlockPos getPartPos(BlockPos masterPos, Direction facing, int partX, int partY) {
        return masterPos.relative(getRightDirection(facing), partX - MASTER_PART_X)
                .above(partY - MASTER_PART_Y);
    }

    private static Direction getRightDirection(Direction facing) {
        return facing.getClockWise();
    }

    private void removeStructure(Level level, BlockPos masterPos, @Nullable Player breaker) {
        if (internalRemoval || level == null || masterPos == null) {
            return;
        }
        BlockState masterState = level.getBlockState(masterPos);
        Direction facing = masterState.hasProperty(FACING) ? masterState.getValue(FACING) : Direction.NORTH;
        internalRemoval = true;
        try {
            for (int partY = 0; partY <= MAX_PART_Y; partY++) {
                for (int partX = 0; partX <= MAX_PART_X; partX++) {
                    removePart(level, getPartPos(masterPos, facing, partX, partY));
                }
            }
            boolean shouldDrop = breaker == null || !breaker.getAbilities().instabuild;
            if (shouldDrop) {
                Containers.dropItemStack(
                        level,
                        masterPos.getX() + 0.5D,
                        masterPos.getY() + 0.5D,
                        masterPos.getZ() + 0.5D,
                        new ItemStack(ModBlocks.BANK_SAFE_IRON_BAR_GATE.get().asItem())
                );
            }
        } finally {
            internalRemoval = false;
        }
    }

    private static void removePart(Level level, BlockPos partPos) {
        BlockState partState = level.getBlockState(partPos);
        if (partState.is(ModBlocks.BANK_SAFE_IRON_BAR_GATE.get())) {
            level.setBlock(partPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
