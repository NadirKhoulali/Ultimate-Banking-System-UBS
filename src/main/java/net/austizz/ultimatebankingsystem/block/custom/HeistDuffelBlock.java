package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.HeistDuffelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class HeistDuffelBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<HeistDuffelBlock> CODEC = simpleCodec(HeistDuffelBlock::new);
    private static final VoxelShape NORTH_SOUTH = Block.box(2, 0, 3, 14, 10, 13);
    private static final VoxelShape EAST_WEST = Block.box(3, 0, 2, 13, 10, 14);

    public HeistDuffelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? EAST_WEST : NORTH_SOUTH;
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeistDuffelBlockEntity(pos, state);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                        BlockPos pos, Player player, InteractionHand hand,
                                                        BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HeistDuffelBlockEntity bag
                && player instanceof ServerPlayer serverPlayer) serverPlayer.openMenu(bag, pos);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HeistDuffelBlockEntity bag) bag.loadFromItem(stack);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof HeistDuffelBlockEntity bag) {
            ItemStack drop = new ItemStack(ModBlocks.HEIST_DUFFEL.get().asItem());
            bag.toItemStack(drop);
            Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .25, pos.getZ() + .5, drop);
        }
        super.onRemove(state, level, pos, next, moving);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
}
