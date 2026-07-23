package net.austizz.ultimatebankingsystem.block.custom;

import net.austizz.ultimatebankingsystem.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CashStackBlock extends Block {
    public static final int MAX_STACK_COUNT = 8;
    public static final EnumProperty<CashKind> KIND = EnumProperty.create("kind", CashKind.class);
    public static final IntegerProperty COUNT = IntegerProperty.create("count", 1, MAX_STACK_COUNT);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape[] BILL_NORTH_SOUTH_SHAPES = buildShapes(2.0D, 3.0D, 14.0D, 13.0D, 0.18D);
    private static final VoxelShape[] BILL_EAST_WEST_SHAPES = buildShapes(3.0D, 2.0D, 13.0D, 14.0D, 0.18D);
    private static final VoxelShape[] COIN_SHAPES = buildShapes(5.0D, 5.0D, 11.0D, 11.0D, 0.25D);

    public CashStackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(KIND, CashKind.ONE_DOLLAR_BILL)
                .setValue(COUNT, 1)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    public static BlockState stateForPlacement(CashKind kind, Direction facing) {
        Direction horizontal = facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing;
        return net.austizz.ultimatebankingsystem.block.ModBlocks.CASH_STACK.get()
                .defaultBlockState()
                .setValue(KIND, kind == null ? CashKind.ONE_DOLLAR_BILL : kind)
                .setValue(COUNT, 1)
                .setValue(FACING, horizontal);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                              BlockState state,
                                              Level level,
                                              BlockPos pos,
                                              Player player,
                                              net.minecraft.world.InteractionHand hand,
                                              net.minecraft.world.phys.BlockHitResult hitResult) {
        CashKind heldKind = CashKind.fromItem(stack.getItem());
        if (heldKind == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (tryAddCash(level, pos, state, player, stack, heldKind)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public static boolean tryAddCash(Level level,
                                     BlockPos pos,
                                     BlockState state,
                                     @Nullable Player player,
                                     ItemStack stack,
                                     CashKind heldKind) {
        if (!(state.getBlock() instanceof CashStackBlock) || heldKind == null || state.getValue(KIND) != heldKind) {
            return false;
        }

        int count = state.getValue(COUNT);
        if (count >= MAX_STACK_COUNT) {
            return true;
        }

        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(COUNT, count + 1), Block.UPDATE_ALL);
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return true;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return !level.getBlockState(pos.below()).isAir();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && (player == null || !player.getAbilities().instabuild)) {
            CashKind kind = state.getValue(KIND);
            Containers.dropItemStack(
                    level,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.15D,
                    pos.getZ() + 0.5D,
                    new ItemStack(kind.item(), state.getValue(COUNT))
            );
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND, COUNT, FACING);
    }

    private static VoxelShape shapeFor(BlockState state) {
        int index = Math.max(0, Math.min(MAX_STACK_COUNT - 1, state.getValue(COUNT) - 1));
        CashKind kind = state.getValue(KIND);
        if (kind.coin) {
            return COIN_SHAPES[index];
        }
        return state.getValue(FACING).getAxis() == Direction.Axis.X
                ? BILL_EAST_WEST_SHAPES[index]
                : BILL_NORTH_SOUTH_SHAPES[index];
    }

    private static VoxelShape[] buildShapes(double minX, double minZ, double maxX, double maxZ, double heightPerItem) {
        VoxelShape[] shapes = new VoxelShape[MAX_STACK_COUNT];
        for (int i = 0; i < MAX_STACK_COUNT; i++) {
            double height = Math.max(0.1D, (i + 1) * heightPerItem);
            shapes[i] = Block.box(minX, 0.0D, minZ, maxX, Math.min(3.0D, height), maxZ);
        }
        return shapes;
    }

    public enum CashKind implements StringRepresentable {
        HUNDRED_DOLLAR_BILL("hundred_dollar_bill", false),
        FIFTY_DOLLAR_BILL("fifty_dollar_bill", false),
        TWENTY_DOLLAR_BILL("twenty_dollar_bill", false),
        TEN_DOLLAR_BILL("ten_dollar_bill", false),
        FIVE_DOLLAR_BILL("five_dollar_bill", false),
        TWO_DOLLAR_BILL("two_dollar_bill", false),
        ONE_DOLLAR_BILL("one_dollar_bill", false),
        HALF_DOLLAR_COIN("half_dollar_coin", true),
        QUARTER_COIN("quarter_coin", true),
        DIME_COIN("dime_coin", true),
        NICKEL_COIN("nickel_coin", true),
        PENNY_COIN("penny_coin", true);

        private final String serializedName;
        private final boolean coin;

        CashKind(String serializedName, boolean coin) {
            this.serializedName = serializedName;
            this.coin = coin;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public boolean isCoin() {
            return coin;
        }

        public Item item() {
            return switch (this) {
                case HUNDRED_DOLLAR_BILL -> ModItems.HUNDRED_DOLLAR_BILL.get();
                case FIFTY_DOLLAR_BILL -> ModItems.FIFTY_DOLLAR_BILL.get();
                case TWENTY_DOLLAR_BILL -> ModItems.TWENTY_DOLLAR_BILL.get();
                case TEN_DOLLAR_BILL -> ModItems.TEN_DOLLAR_BILL.get();
                case FIVE_DOLLAR_BILL -> ModItems.FIVE_DOLLAR_BILL.get();
                case TWO_DOLLAR_BILL -> ModItems.TWO_DOLLAR_BILL.get();
                case ONE_DOLLAR_BILL -> ModItems.ONE_DOLLAR_BILL.get();
                case HALF_DOLLAR_COIN -> ModItems.HALF_DOLLAR_COIN.get();
                case QUARTER_COIN -> ModItems.QUARTER_COIN.get();
                case DIME_COIN -> ModItems.DIME_COIN.get();
                case NICKEL_COIN -> ModItems.NICKEL_COIN.get();
                case PENNY_COIN -> ModItems.PENNY_COIN.get();
            };
        }

        public static CashKind fromItem(Item item) {
            for (CashKind kind : values()) {
                if (item == kind.item()) {
                    return kind;
                }
            }
            return null;
        }
    }
}
