package net.austizz.ultimatebankingsystem.block.custom;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.PalletBlockEntity;
import net.austizz.ultimatebankingsystem.network.DeliveryAlertPayload;
import net.austizz.ultimatebankingsystem.network.ServerActionAlert;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PalletBlock extends Block implements EntityBlock {
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 2);
    public static final IntegerProperty PART_Z = IntegerProperty.create("part_z", 0, 2);
    private static final VoxelShape BASE_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 9.0D, 16.0D);
    private static final VoxelShape[] BOX_LAYER_SHAPES = new VoxelShape[]{
            Block.box(2.0D, 9.0D, 0.0D, 14.0D, 21.0D, 16.0D),
            Block.box(2.0D, 21.0D, 0.0D, 14.0D, 33.0D, 16.0D),
            Block.box(2.0D, 33.0D, 0.0D, 14.0D, 45.0D, 16.0D)
    };

    private static boolean internalRemoval;

    public PalletBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(PART_X, 1)
                .setValue(PART_Z, 1));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return buildColumnShape(state, level, pos);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return buildColumnShape(state, level, pos);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos center = context.getClickedPos();
        Level level = context.getLevel();
        for (BlockPos partPos : allParts(center)) {
            if (!level.getBlockState(partPos).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState().setValue(PART_X, 1).setValue(PART_Z, 1);
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
        String deliveryPalletId = "";
        CompoundTag stackTag = stack == null ? null : stack.getTag();
        if (stackTag != null) {
            deliveryPalletId = stackTag.getString(PalletBlockEntity.DELIVERY_PALLET_ID_ITEM_KEY).trim().toLowerCase(java.util.Locale.ROOT);
        }
        BlockState baseState = defaultBlockState();
        for (int z = 0; z < 3; z++) {
            for (int x = 0; x < 3; x++) {
                if (x == 1 && z == 1) {
                    continue;
                }
                BlockPos partPos = pos.offset(x - 1, 0, z - 1);
                level.setBlock(partPos, baseState.setValue(PART_X, x).setValue(PART_Z, z), 3);
            }
        }
        if (!deliveryPalletId.isBlank() && level.getBlockEntity(pos) instanceof PalletBlockEntity pallet) {
            // Persist delivery pallet identity across break/place so assignment follows the item.
            pallet.setDeliveryPalletId(deliveryPalletId);
        }
        if (level instanceof ServerLevel serverLevel && !deliveryPalletId.isBlank()) {
            ShopService.ShopActionResult result = ShopService.validateDeliveryPalletPlacement(
                    serverLevel.getServer(),
                    serverLevel,
                    pos,
                    deliveryPalletId
            );
            if (!result.success()) {
                removeStructure(level, pos, null);
                if (placer instanceof Player player && result.message() != null && !result.message().isBlank()) {
                    pushPalletFeedback(player, result.message(), DeliveryAlertPayload.AlertTone.ERROR, 5200);
                }
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state,
                                 Level level,
                                 BlockPos pos,
                                 Player player,
                                 InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos masterPos = getMasterPos(state, pos);
        if (!(level.getBlockEntity(masterPos) instanceof PalletBlockEntity pallet)) {
            return InteractionResult.CONSUME;
        }

        int column = state.getValue(PART_Z) * 3 + state.getValue(PART_X);
        ItemStack held = player.getItemInHand(hand);

        if (held.is(ModBlocks.CARDBOARD_BOX.get().asItem())) {
            ItemStack singlePlaced = held.copy();
            singlePlaced.setCount(1);

            // Delivery-labeled pallets enforce strict order-box validation before placement.
            if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
                ShopService.ShopActionResult canPlace = ShopService.validateDeliveryPalletBoxPlacement(
                        serverLevel.getServer(),
                        serverPlayer,
                        serverLevel,
                        masterPos,
                        singlePlaced
                );
                if (!canPlace.success()) {
                    if (canPlace.message() != null && !canPlace.message().isBlank()) {
                        pushPalletFeedback(serverPlayer, canPlace.message(), DeliveryAlertPayload.AlertTone.ERROR, 5200);
                    }
                    return InteractionResult.CONSUME;
                }
            }

            if (!pallet.addBoxToColumn(column, held)) {
                pushPalletFeedback(player,
                        "This pallet column is already full (3 boxes max).",
                        DeliveryAlertPayload.AlertTone.WARNING,
                        4200);
                return InteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }

            if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
                CentralBank centralBank = BankManager.getCentralBank(serverLevel.getServer());
                if (ShopService.isDeliveryPallet(
                        centralBank,
                        serverLevel.dimension().location().toString(),
                        masterPos
                )) {
                    ShopService.ShopActionResult delivery = ShopService.handlePalletDelivery(
                            serverLevel.getServer(),
                            serverPlayer,
                            serverLevel,
                            masterPos,
                            singlePlaced
                    );
                    if (!delivery.success()) {
                        // Delivery pallets must behave atomically: if payout/order matching fails,
                        // the placed box is immediately rolled back and returned to the player.
                        ItemStack rollback = pallet.removeBoxFromColumn(column, false);
                        if (rollback == null || rollback.isEmpty()) {
                            rollback = singlePlaced.copy();
                        }
                        if (!player.getAbilities().instabuild) {
                            if (!player.getInventory().add(rollback)) {
                                Containers.dropItemStack(level, player.getX(), player.getY() + 0.4D, player.getZ(), rollback);
                            }
                        }
                    }
                    if (!delivery.success() && delivery.message() != null && !delivery.message().isBlank()) {
                        pushPalletFeedback(serverPlayer, delivery.message(), DeliveryAlertPayload.AlertTone.ERROR, 5200);
                    }
                }
            }
            return InteractionResult.CONSUME;
        }

        if (held.isEmpty()) {
            int selectedLayer = resolveLayerByHit(hit, pos);
            if (selectedLayer < 0 || selectedLayer >= PalletBlockEntity.LAYERS) {
                pushPalletFeedback(player,
                        "Aim at a cardboard box to pick it up.",
                        DeliveryAlertPayload.AlertTone.INFO,
                        3600);
                return InteractionResult.CONSUME;
            }
            ItemStack removed = pallet.removeBoxFromColumnAtLayer(column, selectedLayer);
            if (removed.isEmpty()) {
                pushPalletFeedback(player,
                        "No cardboard box at that layer in this column.",
                        DeliveryAlertPayload.AlertTone.WARNING,
                        4000);
                return InteractionResult.CONSUME;
            }
            if (!player.getInventory().add(removed)) {
                Containers.dropItemStack(level, player.getX(), player.getY() + 0.4D, player.getZ(), removed);
            }
            pushPalletFeedback(player, "Box removed from pallet.", DeliveryAlertPayload.AlertTone.SUCCESS, 2400);
            return InteractionResult.CONSUME;
        }

        pushPalletFeedback(
                player,
                "Hold a Cardboard Box to place, or use empty hand while aiming at a specific box to pick it up.",
                DeliveryAlertPayload.AlertTone.INFO,
                4200
        );
        return InteractionResult.CONSUME;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            removeStructure(level, getMasterPos(state, pos), player);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() == newState.getBlock()) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }
        if (!level.isClientSide() && !internalRemoval) {
            removeStructure(level, getMasterPos(state, pos), null);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART_X) != 1 || state.getValue(PART_Z) != 1) {
            return null;
        }
        return new PalletBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
                                                                             BlockState state,
                                                                             BlockEntityType<T> blockEntityType) {
        return null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART_X, PART_Z);
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state == null || pos == null || !state.hasProperty(PART_X) || !state.hasProperty(PART_Z)) {
            return pos;
        }
        return pos.offset(1 - state.getValue(PART_X), 0, 1 - state.getValue(PART_Z));
    }

    private static List<BlockPos> allParts(BlockPos masterPos) {
        List<BlockPos> parts = new ArrayList<>(9);
        for (int z = 0; z < 3; z++) {
            for (int x = 0; x < 3; x++) {
                parts.add(masterPos.offset(x - 1, 0, z - 1));
            }
        }
        return parts;
    }

    private void removeStructure(Level level, BlockPos masterPos, @Nullable Player breaker) {
        if (internalRemoval || level == null || masterPos == null) {
            return;
        }
        internalRemoval = true;
        try {
            List<ItemStack> boxDrops = new ArrayList<>();
            String deliveryPalletId = "";
            BlockEntity blockEntity = level.getBlockEntity(masterPos);
            if (blockEntity instanceof PalletBlockEntity pallet) {
                boxDrops.addAll(pallet.extractAllBoxesForDrop());
                deliveryPalletId = pallet.getDeliveryPalletId();
            }

            for (BlockPos partPos : allParts(masterPos)) {
                BlockState partState = level.getBlockState(partPos);
                if (partState.is(ModBlocks.PALLET.get())) {
                    level.setBlock(partPos, Blocks.AIR.defaultBlockState(), 35);
                }
            }

            if (!deliveryPalletId.isBlank() && level instanceof ServerLevel serverLevel) {
                // Keep shop registry in sync: once this pallet no longer exists in world,
                // remove stale delivery assignment immediately.
                ShopService.handleDeliveryPalletRemoved(serverLevel.getServer(), deliveryPalletId);
            }

            boolean dropPallet = breaker == null || !breaker.getAbilities().instabuild;
            if (dropPallet) {
                ItemStack palletDrop = new ItemStack(ModBlocks.PALLET.get().asItem());
                if (!deliveryPalletId.isBlank()) {
                    palletDrop.getOrCreateTag().putString(PalletBlockEntity.DELIVERY_PALLET_ID_ITEM_KEY, deliveryPalletId);
                }
                Containers.dropItemStack(
                        level,
                        masterPos.getX() + 0.5D,
                        masterPos.getY() + 0.25D,
                        masterPos.getZ() + 0.5D,
                        palletDrop
                );
            }

            for (ItemStack box : boxDrops) {
                if (box == null || box.isEmpty()) {
                    continue;
                }
                Containers.dropItemStack(
                        level,
                        masterPos.getX() + 0.5D,
                        masterPos.getY() + 0.85D,
                        masterPos.getZ() + 0.5D,
                        box
                );
            }
        } finally {
            internalRemoval = false;
        }
    }

    private static VoxelShape buildColumnShape(BlockState state, BlockGetter level, BlockPos pos) {
        VoxelShape shape = BASE_SHAPE;
        if (state == null || level == null || pos == null) {
            return shape;
        }
        BlockPos masterPos = getMasterPos(state, pos);
        if (!(level.getBlockEntity(masterPos) instanceof PalletBlockEntity pallet)) {
            return shape;
        }
        int column = state.getValue(PART_Z) * 3 + state.getValue(PART_X);
        for (int layer = 0; layer < PalletBlockEntity.LAYERS; layer++) {
            ItemStack stack = pallet.getBox(column, layer);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            shape = Shapes.or(shape, BOX_LAYER_SHAPES[layer]);
        }
        return shape;
    }

    private static int resolveLayerByHit(BlockHitResult hit, BlockPos pos) {
        if (hit == null || pos == null) {
            return -1;
        }
        double localY = hit.getLocation().y - pos.getY();
        if (localY < (9.0D / 16.0D)) {
            return -1;
        }
        int layer = (int) Math.floor((localY - (9.0D / 16.0D)) / 0.75D);
        if (layer < 0 || layer >= PalletBlockEntity.LAYERS) {
            return -1;
        }
        return layer;
    }

    private static void pushPalletFeedback(Player player,
                                           String message,
                                           DeliveryAlertPayload.AlertTone tone,
                                           int durationMs) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        // Keep chat output for compatibility while also pushing the shared HUD alert card.
        String normalized = ServerActionAlert.stripLegacyFormatting(message);
        player.sendSystemMessage(Component.literal(normalized));
        if (player instanceof ServerPlayer serverPlayer) {
            ServerActionAlert.sendLegacy(
                    serverPlayer,
                    "Pallet",
                    normalized,
                    tone == null ? DeliveryAlertPayload.AlertTone.INFO : tone,
                    Math.max(1200, durationMs)
            );
        }
    }
}
