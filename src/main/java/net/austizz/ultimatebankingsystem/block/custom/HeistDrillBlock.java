package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Creative heist prop that can stand on a floor or mount against a target surface.
 */
public class HeistDrillBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<Mount> MOUNT = EnumProperty.create("mount", Mount.class);

    private static final VoxelShape SHAPE_NORTH = Block.box(3.5D, 1.0D, 0.5D, 12.5D, 11.75D, 14.5D);
    private static final VoxelShape SHAPE_SOUTH = Block.box(3.5D, 1.0D, 1.5D, 12.5D, 11.75D, 15.5D);
    private static final VoxelShape SHAPE_EAST = Block.box(1.5D, 1.0D, 3.5D, 15.5D, 11.75D, 12.5D);
    private static final VoxelShape SHAPE_WEST = Block.box(0.5D, 1.0D, 3.5D, 14.5D, 11.75D, 12.5D);

    private final MapCodec<HeistDrillBlock> codec;

    public HeistDrillBlock(Properties properties) {
        super(properties);
        codec = simpleCodec(HeistDrillBlock::new);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(MOUNT, Mount.FLOOR));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (clickedFace == Direction.DOWN) {
            return null;
        }

        BlockState placedState = clickedFace.getAxis().isHorizontal()
                ? defaultBlockState()
                        .setValue(FACING, clickedFace.getOpposite())
                        .setValue(MOUNT, Mount.TARGET)
                : defaultBlockState()
                        .setValue(FACING, context.getHorizontalDirection())
                        .setValue(MOUNT, Mount.FLOOR);
        return placedState.canSurvive(context.getLevel(), context.getClickedPos()) ? placedState : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(MOUNT) == Mount.FLOOR) {
            BlockPos supportPos = pos.below();
            return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, Direction.UP);
        }

        BlockPos targetPos = pos.relative(state.getValue(FACING));
        BlockState targetState = level.getBlockState(targetPos);
        return !targetState.isAir() && !targetState.canBeReplaced();
    }

    @Override
    public BlockState updateShape(BlockState state,
                                  Direction direction,
                                  BlockState neighborState,
                                  LevelAccessor level,
                                  BlockPos pos,
                                  BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state,
                                        BlockGetter level,
                                        BlockPos pos,
                                        CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT);
    }

    public enum Mount implements StringRepresentable {
        FLOOR("floor"),
        TARGET("target");

        private final String serializedName;

        Mount(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
