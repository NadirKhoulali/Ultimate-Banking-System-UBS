package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.Codec;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * In-world pile of strapped 100-bill money stacks (1-8 bundles per block, criss-cross layers).
 * Placed via {@code MoneyStackItem}; item-less block registered with registerBlockOnly.
 */
public class MoneyStackBlock extends Block {
    public static final int MAX_STACK_COUNT = 8;
    public static final EnumProperty<BillDenomination> KIND = EnumProperty.create("kind", BillDenomination.class);
    public static final IntegerProperty COUNT = IntegerProperty.create("count", 1, MAX_STACK_COUNT);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape[] NORTH_SOUTH_SHAPES = buildShapes(2.0D, 5.5D, 14.0D, 10.5D, 2.0D);
    private static final VoxelShape[] EAST_WEST_SHAPES = buildShapes(5.5D, 2.0D, 10.5D, 14.0D, 2.0D);

    public MoneyStackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(KIND, BillDenomination.ONE)
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

    public static BlockState stateForPlacement(BillDenomination denomination, Direction facing) {
        Direction horizontal = facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing;
        return net.austizz.ultimatebankingsystem.block.ModBlocks.MONEY_STACK.get()
                .defaultBlockState()
                .setValue(KIND, denomination == null ? BillDenomination.ONE : denomination)
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
        BillDenomination heldDenomination = BillDenomination.fromStackItem(stack.getItem());
        if (heldDenomination == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (tryAddStack(level, pos, state, stack, player)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public static boolean tryAddStack(Level level,
                                      BlockPos pos,
                                      BlockState state,
                                      ItemStack stack,
                                      @Nullable Player player) {
        BillDenomination heldDenomination = BillDenomination.fromStackItem(stack.getItem());
        if (!(state.getBlock() instanceof MoneyStackBlock)
                || heldDenomination == null
                || state.getValue(KIND) != heldDenomination) {
            return false;
        }

        int count = state.getValue(COUNT);
        if (count >= MAX_STACK_COUNT) {
            return true;
        }

        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(COUNT, count + 1), Block.UPDATE_ALL);
            SoundType sound = state.getSoundType(level, pos, player);
            level.playSound(
                    player,
                    pos,
                    sound.getPlaceSound(),
                    SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 2.0F,
                    sound.getPitch() * 0.8F
            );
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
            BillDenomination denomination = state.getValue(KIND);
            Containers.dropItemStack(
                    level,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.15D,
                    pos.getZ() + 0.5D,
                    new ItemStack(denomination.stackItem(), state.getValue(COUNT))
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
        return state.getValue(FACING).getAxis() == Direction.Axis.X
                ? EAST_WEST_SHAPES[index]
                : NORTH_SOUTH_SHAPES[index];
    }

    private static VoxelShape[] buildShapes(double minX, double minZ, double maxX, double maxZ, double heightPerItem) {
        VoxelShape[] shapes = new VoxelShape[MAX_STACK_COUNT];
        for (int i = 0; i < MAX_STACK_COUNT; i++) {
            double height = Math.max(0.1D, (i + 1) * heightPerItem);
            double cappedHeight = Math.min(16.0D, height);
            // Count 1 hugs the single bundle; higher counts criss-cross 90 degrees
            // per layer, so their outline is the square union of both orientations.
            shapes[i] = i == 0
                    ? Block.box(minX, 0.0D, minZ, maxX, cappedHeight, maxZ)
                    : Block.box(2.0D, 0.0D, 2.0D, 14.0D, cappedHeight, 14.0D);
        }
        return shapes;
    }

    public enum BillDenomination implements StringRepresentable {
        ONE(1, "one_dollar"),
        TWO(2, "two_dollar"),
        FIVE(5, "five_dollar"),
        TEN(10, "ten_dollar"),
        TWENTY(20, "twenty_dollar"),
        FIFTY(50, "fifty_dollar"),
        HUNDRED(100, "hundred_dollar");

        public static final Codec<BillDenomination> CODEC = StringRepresentable.fromEnum(BillDenomination::values);

        private final int value;
        private final String serializedName;

        BillDenomination(int value, String serializedName) {
            this.value = value;
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public int value() {
            return value;
        }

        public Item billItem() {
            return DollarBills.getItemForDenomination(value);
        }

        public Item strapItem() {
            return switch (this) {
                case ONE -> ModItems.ONE_DOLLAR_STRAP.get();
                case TWO -> ModItems.TWO_DOLLAR_STRAP.get();
                case FIVE -> ModItems.FIVE_DOLLAR_STRAP.get();
                case TEN -> ModItems.TEN_DOLLAR_STRAP.get();
                case TWENTY -> ModItems.TWENTY_DOLLAR_STRAP.get();
                case FIFTY -> ModItems.FIFTY_DOLLAR_STRAP.get();
                case HUNDRED -> ModItems.HUNDRED_DOLLAR_STRAP.get();
            };
        }

        public Item stackItem() {
            return switch (this) {
                case ONE -> ModItems.ONE_DOLLAR_MONEY_STACK.get();
                case TWO -> ModItems.TWO_DOLLAR_MONEY_STACK.get();
                case FIVE -> ModItems.FIVE_DOLLAR_MONEY_STACK.get();
                case TEN -> ModItems.TEN_DOLLAR_MONEY_STACK.get();
                case TWENTY -> ModItems.TWENTY_DOLLAR_MONEY_STACK.get();
                case FIFTY -> ModItems.FIFTY_DOLLAR_MONEY_STACK.get();
                case HUNDRED -> ModItems.HUNDRED_DOLLAR_MONEY_STACK.get();
            };
        }

        public static @Nullable BillDenomination fromStrapItem(Item item) {
            for (BillDenomination denomination : values()) {
                if (item == denomination.strapItem()) {
                    return denomination;
                }
            }
            return null;
        }

        public static @Nullable BillDenomination fromStackItem(Item item) {
            for (BillDenomination denomination : values()) {
                if (item == denomination.stackItem()) {
                    return denomination;
                }
            }
            return null;
        }

        public static @Nullable BillDenomination fromBillItem(Item item) {
            for (BillDenomination denomination : values()) {
                if (item == denomination.billItem()) {
                    return denomination;
                }
            }
            return null;
        }
    }
}
