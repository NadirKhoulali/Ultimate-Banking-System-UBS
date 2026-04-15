package net.austizz.ultimatebankingsystem.block.custom;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShopTerminalBlockEntity;
import net.austizz.ultimatebankingsystem.network.ShopTerminalUsePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.austizz.ultimatebankingsystem.compat.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public class ShopTerminalBlock extends Block implements EntityBlock {
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    public static final IntegerProperty POWER_LEVEL = BlockStateProperties.POWER;
    public static final IntegerProperty RESULT = IntegerProperty.create("result", 0, 2);
    private static final int PULSE_DURATION_TICKS = 10;

    // Simple rectangular hitbox that cleanly contains the full terminal.
    private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 3.0, 12.0, 10.0, 13.0);

    public ShopTerminalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ROTATION, 0)
                .setValue(POWER_LEVEL, 0)
                .setValue(RESULT, 0));
    }

    @Override
    public InteractionResult use(BlockState state,
                                 Level level,
                                 BlockPos pos,
                                 Player player,
                                 InteractionHand hand,
                                 BlockHitResult hitResult) {
        if (state.hasProperty(RESULT) && state.getValue(RESULT) != 0) {
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            PacketDistributor.sendToServer(new ShopTerminalUsePayload(
                    level.dimension().location().toString(),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    player.isShiftKeyDown()
            ));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShopTerminalBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level,
                            BlockPos pos,
                            BlockState state,
                            @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player player)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ShopTerminalBlockEntity terminal) {
            terminal.setOwner(player.getUUID(), player.getName().getString());
        }
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // Snap to standard 90-degree directions only and face the placing player.
        Direction facing = context.getHorizontalDirection();
        int snapped90Segment = RotationSegment.convertToSegment(facing.toYRot()) & 15;
        return this.defaultBlockState()
                .setValue(ROTATION, snapped90Segment)
                .setValue(POWER_LEVEL, 0)
                .setValue(RESULT, 0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION, POWER_LEVEL, RESULT);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), 16));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), 16));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                   BlockState state,
                                                                   BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return null;
        }
        if (blockEntityType != ModBlockEntities.PAYMENT_TERMINAL.get()) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof ShopTerminalBlockEntity terminal) {
                ShopTerminalBlockEntity.serverTick(tickLevel, tickPos, tickState, terminal);
            }
        };
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER_LEVEL);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER_LEVEL);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWER_LEVEL) <= 0) {
            return;
        }
        level.setBlock(pos, state.setValue(POWER_LEVEL, 0), Block.UPDATE_ALL);
        level.updateNeighborsAt(pos, this);
    }

    public static void startPulse(Level level, BlockPos pos, int strength) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState current = level.getBlockState(pos);
        if (!current.is(ModBlocks.PAYMENT_TERMINAL.get())) {
            return;
        }

        int clampedStrength = Math.max(1, Math.min(15, strength));
        if (current.getValue(POWER_LEVEL) != clampedStrength) {
            level.setBlock(pos, current.setValue(POWER_LEVEL, clampedStrength), Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, current.getBlock(), PULSE_DURATION_TICKS);
        level.updateNeighborsAt(pos, current.getBlock());
    }

    public static void setPowerLevel(Level level, BlockPos pos, int strength) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState current = level.getBlockState(pos);
        if (!current.is(ModBlocks.PAYMENT_TERMINAL.get()) || !current.hasProperty(POWER_LEVEL)) {
            return;
        }
        int clampedStrength = Math.max(0, Math.min(15, strength));
        if (current.getValue(POWER_LEVEL) != clampedStrength) {
            level.setBlock(pos, current.setValue(POWER_LEVEL, clampedStrength), Block.UPDATE_ALL);
            level.updateNeighborsAt(pos, current.getBlock());
        }
    }

}
