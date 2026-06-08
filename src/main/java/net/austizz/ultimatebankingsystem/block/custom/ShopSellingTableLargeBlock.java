package net.austizz.ultimatebankingsystem.block.custom;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShopSellingTableBlockEntity;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.network.ShelfUsePayload;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShopSellingTableLargeBlock extends Block implements EntityBlock {
    public static final EnumProperty<TablePart> PART = EnumProperty.create("part", TablePart.class);
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public ShopSellingTableLargeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(PART, TablePart.MASTER));
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
        BlockPos master = context.getClickedPos();
        Set<BlockPos> footprint = footprint(master);
        Level level = context.getLevel();

        for (BlockPos pos : footprint) {
            if (!level.getBlockState(pos).canBeReplaced(context)) {
                return null;
            }
        }
        if (!ShopSellingTableBlock.hasPlacementClearance(level, footprint)) {
            return null;
        }
        return this.defaultBlockState().setValue(PART, TablePart.MASTER);
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
        BlockState base = this.defaultBlockState();
        level.setBlock(pos.east(), base.setValue(PART, TablePart.EAST), 3);
        level.setBlock(pos.north(), base.setValue(PART, TablePart.NORTH), 3);
        level.setBlock(pos.north().east(), base.setValue(PART, TablePart.NORTH_EAST), 3);

        if (!(placer instanceof Player player)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof ShopSellingTableBlockEntity tableEntity)) {
            return;
        }

        tableEntity.setOwner(player.getUUID(), player.getName().getString());
        tableEntity.setSpinEnabled(false);

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
            removeAllParts(level, pos, false);

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
        if (level.isClientSide()) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }

        BlockPos master = getMasterPos(state, pos);
        if (pos.equals(master)) {
            ejectTableContents(level, master);
        }
        removeAllParts(level, master, false);
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static void removeAllParts(Level level, BlockPos master, boolean dropBlockItems) {
        if (level == null || master == null) {
            return;
        }
        Set<BlockPos> footprint = footprint(master);
        for (BlockPos partPos : footprint) {
            BlockState partState = level.getBlockState(partPos);
            if (!isLargeTableBlock(partState)) {
                continue;
            }
            level.setBlock(partPos, Blocks.AIR.defaultBlockState(), 35);
            if (dropBlockItems) {
                Containers.dropItemStack(level, partPos.getX() + 0.5D, partPos.getY() + 0.7D, partPos.getZ() + 0.5D,
                        new ItemStack(ModBlocks.SHOP_SELLING_TABLE_LARGE.get().asItem()));
            }
        }
    }

    private static void ejectTableContents(Level level, BlockPos master) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(master);
        if (!(blockEntity instanceof ShopSellingTableBlockEntity table)) {
            return;
        }
        List<ItemStack> drops = table.extractDisplayItemsForDrop();
        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty()) {
                continue;
            }
            Containers.dropItemStack(level, master.getX() + 0.5D, master.getY() + 1.0D, master.getZ() + 0.5D, drop);
        }
    }

    public static Set<BlockPos> footprint(BlockPos master) {
        Set<BlockPos> out = new HashSet<>();
        out.add(master);
        out.add(master.east());
        out.add(master.north());
        out.add(master.north().east());
        return out;
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state == null || pos == null || !state.hasProperty(PART)) {
            return pos;
        }
        return switch (state.getValue(PART)) {
            case MASTER -> pos;
            case EAST -> pos.west();
            case NORTH -> pos.south();
            case NORTH_EAST -> pos.south().west();
        };
    }

    public static boolean isLargeTableBlock(BlockState state) {
        return state != null
                && (state.is(ModBlocks.SHOP_SELLING_TABLE_LARGE.get())
                || state.is(ModBlocks.CREATIVE_SHOP_SELLING_TABLE_LARGE.get()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART) != TablePart.MASTER) {
            return null;
        }
        return new ShopSellingTableBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                   BlockState state,
                                                                   BlockEntityType<T> blockEntityType) {
        return null;
    }

    public enum TablePart implements StringRepresentable {
        MASTER("master"),
        EAST("east"),
        NORTH("north"),
        NORTH_EAST("north_east");

        private final String id;

        TablePart(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }
}
