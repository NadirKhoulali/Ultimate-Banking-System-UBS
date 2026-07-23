package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShopSellingTableBlockEntity;
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
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShopSellingTableBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<ShopSellingTableBlock> CODEC = simpleCodec(ShopSellingTableBlock::new);

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);


    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public ShopSellingTableBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
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
        // Prevent manual mining in all modes. Table is removed via Shelf UI action.
        return 0.0F;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (!hasPlacementClearance(level, Set.of(pos))) {
            return null;
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        if (!(level.getBlockEntity(pos) instanceof ShopSellingTableBlockEntity tableEntity)) {
            return;
        }

        tableEntity.setOwner(player.getUUID(), player.getName().getString());

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
            tableEntity.setShopId(ShopService.resolveShopAtPosForActor(
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

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() == newState.getBlock()) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }

        if (!level.isClientSide()) {
            ejectTableContents(level, pos);
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    protected static void ejectTableContents(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ShopSellingTableBlockEntity table)) {
            return;
        }
        List<ItemStack> drops = table.extractDisplayItemsForDrop();
        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty()) {
                continue;
            }
            Containers.dropItemStack(
                    level,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.7D,
                    pos.getZ() + 0.5D,
                    drop
            );
        }
    }

    public static boolean hasPlacementClearance(LevelAccessor level, Set<BlockPos> footprint) {
        if (level == null || footprint == null || footprint.isEmpty()) {
            return false;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : footprint) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        Set<BlockPos> allowed = new HashSet<>(footprint);
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                for (int y = minY; y <= maxY + 2; y++) {
                    BlockPos check = new BlockPos(x, y, z);
                    if (allowed.contains(check)) {
                        continue;
                    }
                    if (!level.getBlockState(check).canBeReplaced()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShopSellingTableBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                   BlockState state,
                                                                   BlockEntityType<T> blockEntityType) {
        return null;
    }
}
