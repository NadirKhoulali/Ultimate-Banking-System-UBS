package net.austizz.ultimatebankingsystem.heist;

import net.austizz.ultimatebankingsystem.api.heist.HeistDoorAdapterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Canonicalizes two-block doors so every interaction addresses the same heist target. */
public final class HeistDoorSupport {
    private static final TagKey<Block> BREACHABLE_DOORS = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath("ultimatebankingsystem", "heist_breachable_doors"));

    private HeistDoorSupport() {}

    public static boolean isBreachable(BlockState state) {
        return state != null && (state.getBlock() instanceof DoorBlock
                || state.is(BREACHABLE_DOORS)
                || HeistDoorAdapterRegistry.matches(state));
    }

    public static BlockPos canonicalPos(BlockGetter level, BlockPos pos) {
        if (level == null || pos == null) return pos;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock
                && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lower = pos.below();
            if (level.getBlockState(lower).getBlock() == state.getBlock()) return lower;
        }
        return pos;
    }

    public static String targetKey(ServerLevel level, BlockPos pos) {
        BlockPos canonical = canonicalPos(level, pos);
        return "door|" + level.dimension().location() + "|" + canonical.asLong();
    }

    public static boolean setBreached(ServerLevel level, BlockPos pos, boolean breached) {
        if (level == null || pos == null) return false;
        BlockPos canonical = canonicalPos(level, pos);
        BlockState state = level.getBlockState(canonical);
        if (!isBreachable(state)) return false;
        if (state.hasProperty(BlockStateProperties.OPEN)) {
            setOpen(level, canonical, state, breached);
            if (state.getBlock() instanceof DoorBlock) {
                BlockPos upperPos = canonical.above();
                BlockState upper = level.getBlockState(upperPos);
                if (upper.getBlock() == state.getBlock() && upper.hasProperty(BlockStateProperties.OPEN)) {
                    setOpen(level, upperPos, upper, breached);
                }
            }
            return true;
        }
        return HeistDoorAdapterRegistry.setBreached(level, canonical, state, breached);
    }

    private static void setOpen(ServerLevel level, BlockPos pos, BlockState state, boolean open) {
        if (state.getValue(BlockStateProperties.OPEN) != open) {
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
        }
    }
}
