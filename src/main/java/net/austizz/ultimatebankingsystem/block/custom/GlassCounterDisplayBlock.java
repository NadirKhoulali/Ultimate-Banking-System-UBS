package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.entity.custom.GlassCounterDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.network.ShelfUsePayload;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GlassCounterDisplayBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<GlassCounterDisplayBlock> CODEC = simpleCodec(GlassCounterDisplayBlock::new);

    // Keep the footprint exactly 1x1 while extending only vertical height to match the model.
    private static final VoxelShape SHAPE_BASE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape SHAPE_TOP = Block.box(0.0D, 16.0D, 0.0D, 16.0D, 21.0D, 16.0D);
    private static final VoxelShape SHAPE = Shapes.or(SHAPE_BASE, SHAPE_TOP);


    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public GlassCounterDisplayBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level,
                            BlockPos pos,
                            BlockState state,
                            @Nullable LivingEntity placer,
                            ItemStack stack) {
        if (level.isClientSide() || !(placer instanceof Player player)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof GlassCounterDisplayBlockEntity display)) {
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
            level.removeBlock(pos, false);

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
        ShelfUsePayload.sendToServer(level, pos, hit, player, hand);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hit) {
        ShelfUsePayload.sendToServer(level, pos, hit, player, true);
        return InteractionResult.SUCCESS;
    }

    public static LocalXZ toCanonicalLocal(double localX, double localZ, Direction facing) {
        return switch (facing) {
            case SOUTH -> new LocalXZ(1.0D - localX, 1.0D - localZ);
            case EAST -> new LocalXZ(localZ, 1.0D - localX);
            case WEST -> new LocalXZ(1.0D - localZ, localX);
            default -> new LocalXZ(localX, localZ);
        };
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() == newState.getBlock()) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }

        if (!level.isClientSide()) {
            ejectDisplayContents(level, pos);
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static void ejectDisplayContents(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof GlassCounterDisplayBlockEntity display)) {
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

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GlassCounterDisplayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                   BlockState state,
                                                                   BlockEntityType<T> blockEntityType) {
        return null;
    }

    public record LocalXZ(double x, double z) {
    }
}
