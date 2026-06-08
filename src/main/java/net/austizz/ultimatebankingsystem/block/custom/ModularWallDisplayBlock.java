package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.entity.custom.ModularWallDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.network.ShelfUsePayload;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ModularWallDisplayBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<ModularWallDisplayBlock> CODEC = simpleCodec(ModularWallDisplayBlock::new);

    public static final EnumProperty<DisplayPart> PART = EnumProperty.create("part", DisplayPart.class);
    // Exact per-part thickness for the modular wall body (matches model depth).
    private static final VoxelShape SHAPE_NORTH = Block.box(0.0D, 0.0D, 14.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape SHAPE_SOUTH = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 2.0D);
    private static final VoxelShape SHAPE_EAST = Block.box(0.0D, 0.0D, 0.0D, 2.0D, 16.0D, 16.0D);
    private static final VoxelShape SHAPE_WEST = Block.box(14.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);


    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public ModularWallDisplayBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, DisplayPart.MASTER));
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
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // Prevent manual mining in all modes. Display is removed via Shelf UI action.
        return 0.0F;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos masterPos = context.getClickedPos();
        BlockPos extensionPos = masterPos.relative(extensionDirectionFromFacing(facing));
        Level level = context.getLevel();

        if (!level.isInWorldBounds(extensionPos)) {
            return null;
        }
        if (!level.getBlockState(extensionPos).canBeReplaced(context)) {
            return null;
        }
        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(PART, DisplayPart.MASTER);
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
        BlockPos extensionPos = pos.relative(extensionDirectionFromFacing(facing));
        level.setBlock(extensionPos, state.setValue(PART, DisplayPart.EXTENSION), 3);

        if (!(placer instanceof Player player)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof ModularWallDisplayBlockEntity display)) {
            return;
        }

        display.setOwner(player.getUUID(), player.getName().getString());

        CentralBank centralBank = BankManager.getCentralBank(level.getServer());
        String dimensionId = level.dimension().location().toString();
        ShopService.ShopActionResult placementCheck = ShopService.validateShopShelfPlacement(
                centralBank,
                player.getUUID(),
                dimensionId,
                pos
        );

        if (!player.hasPermissions(3)
                && !placementCheck.success()) {
            removeBothParts(level, pos, state);

            ItemStack refund = new ItemStack(this.asItem());
            if (!player.getInventory().add(refund)) {
                player.drop(refund, false);
            }
            player.sendSystemMessage(UbsTranslations.literal(placementCheck.message()));
            return;
        }

        if (centralBank != null) {
            display.setShopId(ShopService.resolveShopAtPosForActor(
                    centralBank,
                    player.getUUID(),
                    dimensionId,
                    pos,
                    true
            ));
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                         BlockState state,
                                 Level level,
                                 BlockPos pos,
                                 Player player,
                                 InteractionHand hand,
                                 BlockHitResult hit) {
        ShelfUsePayload.sendToServer(level, getMasterPos(state, pos), hit, player, hand);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hit) {
        ShelfUsePayload.sendToServer(level, getMasterPos(state, pos), hit, player, true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() == newState.getBlock()) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }

        if (!level.isClientSide()) {
            BlockPos masterPos = getMasterPos(state, pos);
            if (masterPos.equals(pos)) {
                ejectDisplayContents(level, masterPos);
            }
            removeBothParts(level, masterPos, state.setValue(PART, DisplayPart.MASTER));
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState updateShape(BlockState state,
                                  Direction direction,
                                  BlockState neighborState,
                                  LevelAccessor level,
                                  BlockPos pos,
                                  BlockPos neighborPos) {
        Direction counterpartDirection = state.getValue(PART) == DisplayPart.MASTER
                ? extensionDirectionFromFacing(state.getValue(FACING))
                : extensionDirectionFromFacing(state.getValue(FACING)).getOpposite();
        if (direction == counterpartDirection
                && !isMatchingPair(state, neighborState)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    private static void ejectDisplayContents(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ModularWallDisplayBlockEntity display)) {
            return;
        }
        List<ItemStack> drops = display.extractDisplayItemsForDrop();
        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty()) {
                continue;
            }
            Containers.dropItemStack(
                    level,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.6D,
                    pos.getZ() + 0.5D,
                    drop
            );
        }
    }

    private static boolean isMatchingPair(BlockState state, BlockState neighborState) {
        if (state == null || neighborState == null) {
            return false;
        }
        if (state.getBlock() != neighborState.getBlock()) {
            return false;
        }
        if (!state.hasProperty(FACING) || !neighborState.hasProperty(FACING)
                || !state.hasProperty(PART) || !neighborState.hasProperty(PART)) {
            return false;
        }
        return state.getValue(FACING) == neighborState.getValue(FACING)
                && state.getValue(PART) != neighborState.getValue(PART);
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state == null || pos == null || !state.hasProperty(PART) || !state.hasProperty(FACING)) {
            return pos;
        }
        if (state.getValue(PART) == DisplayPart.MASTER) {
            return pos;
        }
        return pos.relative(extensionDirectionFromFacing(state.getValue(FACING)).getOpposite());
    }

    private static Direction extensionDirectionFromFacing(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static void removeBothParts(Level level, BlockPos masterPos, BlockState masterState) {
        if (level == null || masterPos == null || masterState == null || !masterState.hasProperty(FACING)) {
            return;
        }
        Direction extensionDirection = extensionDirectionFromFacing(masterState.getValue(FACING));
        BlockPos extensionPos = masterPos.relative(extensionDirection);
        BlockState extensionState = level.getBlockState(extensionPos);
        if (extensionState.getBlock() == masterState.getBlock()) {
            level.setBlock(extensionPos, Blocks.AIR.defaultBlockState(), 35);
        }
        BlockState centerState = level.getBlockState(masterPos);
        if (centerState.getBlock() == masterState.getBlock()) {
            level.setBlock(masterPos, Blocks.AIR.defaultBlockState(), 35);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART) != DisplayPart.MASTER) {
            return null;
        }
        return new ModularWallDisplayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                   BlockState state,
                                                                   BlockEntityType<T> blockEntityType) {
        return null;
    }

    public enum DisplayPart implements StringRepresentable {
        MASTER("master"),
        EXTENSION("extension");

        private final String id;

        DisplayPart(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }
}
