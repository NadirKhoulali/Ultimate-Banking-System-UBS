package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.austizz.ultimatebankingsystem.npc.escort.TemporaryRelayLeaseState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class RfidSignalRelayBlock extends Block {
    public static final MapCodec<RfidSignalRelayBlock> CODEC = simpleCodec(RfidSignalRelayBlock::new);
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);
    public static final DirectionProperty SIGNAL_SIDE = DirectionProperty.create("signal_side");
    public static final BooleanProperty TEMPORARY = BooleanProperty.create("temporary");

    public RfidSignalRelayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(POWER, 0)
                .setValue(SIGNAL_SIDE, Direction.NORTH)
                .setValue(TEMPORARY, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(POWER, 0)
                .setValue(SIGNAL_SIDE, context.getClickedFace())
                .setValue(TEMPORARY, false);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == state.getValue(SIGNAL_SIDE) ? state.getValue(POWER) : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(TEMPORARY)) {
            return;
        }
        int fallbackDelay = TemporaryRelayLeaseState.fallbackDelay(level, pos);
        if (fallbackDelay > 0) {
            level.scheduleTick(pos, this, fallbackDelay);
            return;
        }
        Direction signalSide = state.getValue(SIGNAL_SIDE);
        BlockPos targetPos = pos.relative(signalSide.getOpposite());
        if (level.removeBlock(pos, false)) {
            level.updateNeighborsAt(targetPos, this);
        }
    }

    public static void setPower(Level level, BlockPos pos, Direction signalSide, int power) {
        int clamped = Math.max(0, Math.min(15, power));
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RfidSignalRelayBlock)) {
            return;
        }
        Direction configuredSide = signalSide == null ? Direction.NORTH : signalSide;
        BlockState next = state
                .setValue(SIGNAL_SIDE, configuredSide)
                .setValue(POWER, clamped);
        if (!state.equals(next)) {
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        }
        BlockPos targetPos = pos.relative(configuredSide.getOpposite());
        level.neighborChanged(targetPos, next.getBlock(), pos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER, SIGNAL_SIDE, TEMPORARY);
    }
}
