package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Two-block thermal drill with one enclosing selection and collision volume.
 */
public final class ThermalDrillBlock extends HeistDrillBlock {
    private static final MapCodec<ThermalDrillBlock> CODEC = simpleCodec(ThermalDrillBlock::new);
    private static final VoxelShape SHAPE = Block.box(-8.0D, 0.0D, -8.0D, 24.0D, 32.0D, 24.0D);

    public ThermalDrillBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state,
                                        BlockGetter level,
                                        BlockPos pos,
                                        CollisionContext context) {
        return SHAPE;
    }
}
