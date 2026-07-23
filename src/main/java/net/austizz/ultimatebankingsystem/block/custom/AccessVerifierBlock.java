package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.austizz.ultimatebankingsystem.block.entity.custom.AccessVerifierBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class AccessVerifierBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<AccessVerifierBlock> CODEC = simpleCodec(AccessVerifierBlock::new);

    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(2.0, 2.0, -3.0, 14.0, 15.0, 1.0)
    );
    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(2.0, 2.0, 15.0, 14.0, 15.0, 19.0)
    );
    private static final VoxelShape SHAPE_WEST = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(-3.0, 2.0, 2.0, 1.0, 15.0, 14.0)
    );
    private static final VoxelShape SHAPE_EAST = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(15.0, 2.0, 2.0, 19.0, 15.0, 14.0)
    );

    public AccessVerifierBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // Opposite of the player's look direction so the keypad face points at
        // the player who placed it.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                              BlockState state,
                                              Level level,
                                              BlockPos pos,
                                              Player player,
                                              InteractionHand hand,
                                              BlockHitResult hitResult) {
        return interact(level, pos, player)
                ? ItemInteractionResult.sidedSuccess(level.isClientSide())
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hitResult) {
        return interact(level, pos, player)
                ? InteractionResult.sidedSuccess(level.isClientSide())
                : InteractionResult.PASS;
    }

    private static boolean interact(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof AccessVerifierBlockEntity verifier) {
            verifier.openFor(serverPlayer);
            return true;
        }
        return false;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AccessVerifierBlockEntity(pos, state);
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
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            case NORTH, UP, DOWN -> SHAPE_NORTH;
        };
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof AccessVerifierBlockEntity verifier) {
            return verifier.currentSignal();
        }
        return 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean hadSignal = level.getBlockEntity(pos) instanceof AccessVerifierBlockEntity verifier
                && verifier.currentSignal() > 0;
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide() && hadSignal) {
            level.updateNeighborsAt(pos, this);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
