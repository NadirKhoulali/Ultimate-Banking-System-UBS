package net.austizz.ultimatebankingsystem.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible single-item display that reuses the shelf/table interaction stack.
 * The block remains selectable/clickable but has no world model and no collision.
 */
public class InvisibleDisplayBlock extends ShopSellingTableBlock {
    private static final VoxelShape CLICKABLE_SHAPE = box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private final SizePreset sizePreset;

    public InvisibleDisplayBlock(Properties properties, SizePreset sizePreset) {
        super(properties);
        this.sizePreset = sizePreset == null ? SizePreset.MEDIUM : sizePreset;
    }

    public SizePreset sizePreset() {
        return sizePreset;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Keep a normal interaction footprint so right-click/shift-right-click targeting stays reliable.
        return CLICKABLE_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Invisible displays are pass-through by design.
        return Shapes.empty();
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    public enum SizePreset {
        SMALL,
        MEDIUM,
        LARGE
    }
}
