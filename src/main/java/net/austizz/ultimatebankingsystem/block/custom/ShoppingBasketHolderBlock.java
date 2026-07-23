package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShoppingBasketHolderBlockEntity;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.shelf.ShelfBasketSessionService;
import net.austizz.ultimatebankingsystem.shelf.ShelfService;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ShoppingBasketHolderBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<ShoppingBasketHolderBlock> CODEC = simpleCodec(ShoppingBasketHolderBlock::new);

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape SHAPE_LOWER = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape SHAPE_UPPER = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);


    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public ShoppingBasketHolderBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? SHAPE_LOWER : SHAPE_UPPER;
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
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level,
                            BlockPos pos,
                            BlockState state,
                            @Nullable LivingEntity placer,
                            ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        if (level.isClientSide()) {
            return;
        }
        if (!(placer instanceof Player player)) {
            level.removeBlock(pos.above(), false);
            level.removeBlock(pos, false);
            return;
        }
        if (level.getServer() == null) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(level.getServer());
        UUID shopId = centralBank == null
                ? null
                : ShopService.resolveShopAtPosForActor(
                centralBank,
                player.getUUID(),
                level.dimension().location().toString(),
                pos,
                true
        );
        if (shopId == null) {
            level.removeBlock(pos.above(), false);
            level.removeBlock(pos, false);
            ItemStack refund = new ItemStack(this.asItem());
            if (!player.getInventory().add(refund)) {
                player.drop(refund, false);
            }
            player.sendSystemMessage(UbsTranslations.literal("Claim shop land first. Basket holder must be placed inside your shop plot."));
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ShoppingBasketHolderBlockEntity holderEntity) {
            holderEntity.setOwnerAndShop(player.getUUID(), player.getName().getString(), shopId);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                         BlockState state,
                                 Level level,
                                 BlockPos pos,
                                 Player player,
                                 InteractionHand hand,
                                 BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return ItemInteractionResult.CONSUME;
        }
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return ItemInteractionResult.CONSUME;
        }

        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockEntity blockEntity = level.getBlockEntity(lowerPos);
        if (!(blockEntity instanceof ShoppingBasketHolderBlockEntity holderEntity)) {
            serverPlayer.sendSystemMessage(UbsTranslations.literal("Basket holder data is unavailable."));
            return ItemInteractionResult.CONSUME;
        }

        if (ShelfBasketSessionService.hasActiveSession(serverPlayer.getUUID())) {
            ShelfBasketSessionService.ActionResult result =
                    ShelfBasketSessionService.tryReturnBasket(serverPlayer, holderEntity);
            serverPlayer.sendSystemMessage(UbsTranslations.literal((result.success() ? "§a" : "§c") + result.message()));
            return ItemInteractionResult.CONSUME;
        }

        // Closed shops must not issue new shopping baskets.
        if (!ShelfService.ensureShopOpenForShopping(serverPlayer, holderEntity.getShopId())) {
            return ItemInteractionResult.CONSUME;
        }

        if (!serverPlayer.getMainHandItem().isEmpty() || !serverPlayer.getOffhandItem().isEmpty()) {
            serverPlayer.sendSystemMessage(UbsTranslations.literal("Use an empty hand to take a shopping basket."));
            return ItemInteractionResult.CONSUME;
        }

        ShelfBasketSessionService.ActionResult result =
                ShelfBasketSessionService.startSessionFromHolder(serverPlayer, holderEntity);
        serverPlayer.sendSystemMessage(UbsTranslations.literal((result.success() ? "§a" : "§c") + result.message()));
        return ItemInteractionResult.CONSUME;
    }

    @Override
    public BlockState updateShape(BlockState state,
                                  Direction direction,
                                  BlockState neighborState,
                                  LevelAccessor level,
                                  BlockPos pos,
                                  BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y) {
            if (half == DoubleBlockHalf.LOWER && direction == Direction.UP) {
                if (!neighborState.is(this) || neighborState.getValue(HALF) != DoubleBlockHalf.UPPER) {
                    return Blocks.AIR.defaultBlockState();
                }
            } else if (half == DoubleBlockHalf.UPPER && direction == Direction.DOWN) {
                if (!neighborState.is(this) || neighborState.getValue(HALF) != DoubleBlockHalf.LOWER) {
                    return Blocks.AIR.defaultBlockState();
                }
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.is(this) && otherState.getValue(HALF) != half) {
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return null;
        }
        return new ShoppingBasketHolderBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                   BlockState state,
                                                                   BlockEntityType<T> type) {
        return null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }
}
