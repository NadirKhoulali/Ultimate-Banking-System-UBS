package net.austizz.ultimatebankingsystem.block.custom;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.MetalPalletBlockEntity;
import net.austizz.ultimatebankingsystem.bank.safebox.SafeAccessAuditService;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.network.DeliveryAlertPayload;
import net.austizz.ultimatebankingsystem.network.ServerNotification;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MetalPalletBlock extends Block implements EntityBlock {
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 2);
    public static final IntegerProperty PART_Z = IntegerProperty.create("part_z", 0, 2);
    private static final double BASE_TOP = 5.0D;
    // Wide (bar/cash) columns step 4px per layer; money columns step 2px per
    // bundle. Both grids top out at the same 24px cargo height
    // (6 x 4px == 12 x 2px). Shape/hit math must match the chunk-baked content
    // offsets built in MetalPalletBlockEntity.buildContentsSnapshot().
    private static final double BAR_HEIGHT = 4.0D;
    private static final double MONEY_STACK_HEIGHT = 2.0D;
    private static final VoxelShape BASE_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, BASE_TOP, 16.0D);

    private static boolean internalRemoval;

    public MetalPalletBlock(Properties properties) {
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
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                              BlockState state,
                                              Level level,
                                              BlockPos pos,
                                              Player player,
                                              InteractionHand hand,
                                              BlockHitResult hit) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockPos masterPos = getMasterPos(state, pos);
        if (!(level.getBlockEntity(masterPos) instanceof MetalPalletBlockEntity pallet)) {
            return ItemInteractionResult.CONSUME;
        }

        HitTarget target = resolveHitTarget(masterPos, hit);
        ItemStack held = player.getItemInHand(hand);

        if (MetalPalletBlockEntity.isPalletStackable(held)) {
            boolean added = MetalPalletBlockEntity.isMoneyStack(held)
                    ? pallet.addMoneyStack(target.moneyX(), target.moneyZ(), held)
                    : pallet.addWideStack(target.wideX(), target.wideZ(), held);
            if (!added) {
                String message;
                if (MetalPalletBlockEntity.isMoneyStack(held)) {
                    message = pallet.isMoneyBlockedByWide(target.moneyX(), target.moneyZ())
                            ? "Money stacks cannot share pallet space with bars or cash."
                            : "This pallet money column is full (12 bundles max).";
                } else {
                    message = pallet.isWideBlockedByMoney(target.wideX(), target.wideZ())
                            ? "Bars and cash cannot share pallet space with money stacks."
                            : "This metal pallet column is full (6 stacks max).";
                }
                pushPalletFeedback(player, message, DeliveryAlertPayload.AlertTone.WARNING, 4200);
                return ItemInteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            pushPalletFeedback(player, "Stack added to metal pallet.", DeliveryAlertPayload.AlertTone.SUCCESS, 2200);
            return ItemInteractionResult.CONSUME;
        }

        if (held.isEmpty()) {
            ItemStack removed = removeByHit(pallet, target, hit, pos);
            if (removed.isEmpty()) {
                pushPalletFeedback(player,
                        "No stack in this pallet column.",
                        DeliveryAlertPayload.AlertTone.WARNING,
                        3600);
                return ItemInteractionResult.CONSUME;
            }
            if (!player.getInventory().add(removed)) {
                Containers.dropItemStack(level, player.getX(), player.getY() + 0.4D, player.getZ(), removed);
            }
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                    && player instanceof ServerPlayer serverPlayer) {
                SafeAccessAuditService.recordStorageRemoval(
                        serverLevel, masterPos, serverPlayer, "Metal Pallet", removed);
            }
            pushPalletFeedback(player, "Stack removed from metal pallet.", DeliveryAlertPayload.AlertTone.SUCCESS, 2200);
            return ItemInteractionResult.CONSUME;
        }

        pushPalletFeedback(
                player,
                "Hold a Gold Bar, Silver Bar, cash, or a money stack to stack it, or use empty hand to remove a stack.",
                DeliveryAlertPayload.AlertTone.INFO,
                4200
        );
        return ItemInteractionResult.CONSUME;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            removeStructure(level, getMasterPos(state, pos), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
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
        return new MetalPalletBlockEntity(pos, state);
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
            List<ItemStack> stackDrops = new ArrayList<>();
            BlockEntity blockEntity = level.getBlockEntity(masterPos);
            if (blockEntity instanceof MetalPalletBlockEntity pallet) {
                stackDrops.addAll(pallet.extractAllStacksForDrop());
            }

            for (BlockPos partPos : allParts(masterPos)) {
                BlockState partState = level.getBlockState(partPos);
                if (partState.is(ModBlocks.METAL_PALLET.get())) {
                    level.setBlock(partPos, Blocks.AIR.defaultBlockState(), 35);
                }
            }

            boolean dropPallet = breaker == null || !breaker.getAbilities().instabuild;
            if (dropPallet) {
                Containers.dropItemStack(
                        level,
                        masterPos.getX() + 0.5D,
                        masterPos.getY() + 0.25D,
                        masterPos.getZ() + 0.5D,
                        new ItemStack(ModBlocks.METAL_PALLET.get().asItem())
                );
            }

            for (ItemStack storedStack : stackDrops) {
                if (storedStack == null || storedStack.isEmpty()) {
                    continue;
                }
                Containers.dropItemStack(
                        level,
                        masterPos.getX() + 0.5D,
                        masterPos.getY() + 0.75D,
                        masterPos.getZ() + 0.5D,
                        storedStack
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
        if (!(level.getBlockEntity(masterPos) instanceof MetalPalletBlockEntity pallet)) {
            return shape;
        }
        // Content-aware cargo outline: take the max visual top across BOTH grids'
        // columns owned by this part (wide layers step 4px, money layers 2px) and
        // raise one full-width box to it. Full X/Z coverage is required because
        // narrow money columns can sit between the part blocks.
        int partX = state.getValue(PART_X);
        int partZ = state.getValue(PART_Z);
        double highestPx = BASE_TOP;
        for (int wz = 0; wz < MetalPalletBlockEntity.WIDE_GRID_Z; wz++) {
            for (int wx = 0; wx < MetalPalletBlockEntity.WIDE_GRID_X; wx++) {
                if (!widePositionBelongsToPart(wx, wz, partX, partZ)) {
                    continue;
                }
                int top = pallet.wideColumnTopLayer(wx, wz);
                if (top >= 0) {
                    highestPx = Math.max(highestPx, BASE_TOP + (top + 1) * BAR_HEIGHT);
                }
            }
        }
        for (int mz = 0; mz < MetalPalletBlockEntity.MONEY_GRID_Z; mz++) {
            for (int mx = 0; mx < MetalPalletBlockEntity.MONEY_GRID_X; mx++) {
                if (!moneyPositionBelongsToPart(mx, mz, partX, partZ)) {
                    continue;
                }
                int top = pallet.moneyColumnTopLayer(mx, mz);
                if (top >= 0) {
                    highestPx = Math.max(highestPx, BASE_TOP + (top + 1) * MONEY_STACK_HEIGHT);
                }
            }
        }
        if (highestPx > BASE_TOP) {
            shape = Shapes.or(shape, Block.box(0.0D, BASE_TOP, 0.75D, 16.0D, highestPx, 15.25D));
        }
        return shape;
    }

    /** Wide X columns map 0 -> west part, 1..2 -> center, 3 -> east; Z is 1:1. */
    private static boolean widePositionBelongsToPart(int wx, int wz, int partX, int partZ) {
        int mappedPartX = wx == 0 ? 0 : (wx == MetalPalletBlockEntity.WIDE_GRID_X - 1 ? 2 : 1);
        return mappedPartX == partX && wz == partZ;
    }

    /**
     * Money X columns: outer two per side -> edge parts, rest center (as today).
     * Money Z rows map by center: 0 -> part z0, 1 and 2 -> part z1, 3 -> part z2.
     */
    private static boolean moneyPositionBelongsToPart(int mx, int mz, int partX, int partZ) {
        int mappedPartX = mx <= 1 ? 0 : (mx >= MetalPalletBlockEntity.MONEY_GRID_X - 2 ? 2 : 1);
        int mappedPartZ = mz == 0 ? 0 : (mz == MetalPalletBlockEntity.MONEY_GRID_Z - 1 ? 2 : 1);
        return mappedPartX == partX && mappedPartZ == partZ;
    }

    /** Both candidate positions (money and wide grid) nearest to the hit point. */
    private record HitTarget(int wideX, int wideZ, int moneyX, int moneyZ,
                             double localX, double localZ) {
    }

    private static HitTarget resolveHitTarget(BlockPos masterPos, BlockHitResult hit) {
        // Offsets from the pallet center (the master block's cell center).
        double localX = hit.getLocation().x - (masterPos.getX() + 0.5D);
        double localZ = hit.getLocation().z - (masterPos.getZ() + 0.5D);
        int wx = nearestIndex(localX, MetalPalletBlockEntity.WIDE_PITCH_X, 1.5D,
                MetalPalletBlockEntity.WIDE_GRID_X);
        int wz = nearestIndex(localZ, MetalPalletBlockEntity.WIDE_PITCH_Z, 1.0D,
                MetalPalletBlockEntity.WIDE_GRID_Z);
        int mx = nearestIndex(localX, MetalPalletBlockEntity.MONEY_PITCH_X, 3.5D,
                MetalPalletBlockEntity.MONEY_GRID_X);
        int mz = nearestIndex(localZ, MetalPalletBlockEntity.MONEY_PITCH_Z, 1.5D,
                MetalPalletBlockEntity.MONEY_GRID_Z);
        return new HitTarget(wx, wz, mx, mz, localX, localZ);
    }

    /** Nearest grid index for centers at (index - centerBias) * pitch. */
    private static int nearestIndex(double local, double pitch, double centerBias, int count) {
        int index = (int) Math.floor(local / pitch + centerBias + 0.5D);
        return Math.max(0, Math.min(count - 1, index));
    }

    /**
     * Empty-hand removal: prefer whichever grid actually has content at the hit
     * column. If both do, use the grid whose occupied vertical interval contains
     * the hit Y; if that is ambiguous too, take the horizontally nearer position.
     */
    private static ItemStack removeByHit(MetalPalletBlockEntity pallet,
                                         HitTarget target,
                                         BlockHitResult hit,
                                         BlockPos pos) {
        int moneyTop = pallet.moneyColumnTopLayer(target.moneyX(), target.moneyZ());
        int wideTop = pallet.wideColumnTopLayer(target.wideX(), target.wideZ());
        if (moneyTop < 0 && wideTop < 0) {
            return ItemStack.EMPTY;
        }

        boolean useMoney;
        if (moneyTop >= 0 && wideTop >= 0) {
            double localYPx = (hit.getLocation().y - pos.getY()) * 16.0D;
            boolean inMoney = localYPx >= BASE_TOP
                    && localYPx < BASE_TOP + (moneyTop + 1) * MONEY_STACK_HEIGHT;
            boolean inWide = localYPx >= BASE_TOP
                    && localYPx < BASE_TOP + (wideTop + 1) * BAR_HEIGHT;
            if (inMoney != inWide) {
                useMoney = inMoney;
            } else {
                double moneyDx = target.localX() - MetalPalletBlockEntity.moneyCenterX(target.moneyX());
                double moneyDz = target.localZ() - MetalPalletBlockEntity.moneyCenterZ(target.moneyZ());
                double wideDx = target.localX() - MetalPalletBlockEntity.wideCenterX(target.wideX());
                double wideDz = target.localZ() - MetalPalletBlockEntity.wideCenterZ(target.wideZ());
                useMoney = moneyDx * moneyDx + moneyDz * moneyDz <= wideDx * wideDx + wideDz * wideDz;
            }
        } else {
            useMoney = moneyTop >= 0;
        }

        if (useMoney) {
            int layer = resolveLayerByHit(hit, pos, MONEY_STACK_HEIGHT, moneyTop);
            return layer >= 0
                    ? pallet.removeMoneyAtLayer(target.moneyX(), target.moneyZ(), layer)
                    : pallet.removeMoneyTop(target.moneyX(), target.moneyZ());
        }
        int layer = resolveLayerByHit(hit, pos, BAR_HEIGHT, wideTop);
        return layer >= 0
                ? pallet.removeWideAtLayer(target.wideX(), target.wideZ(), layer)
                : pallet.removeWideTop(target.wideX(), target.wideZ());
    }

    /**
     * Maps the hit Y onto the resolved grid's uniform layer steps (money 2px,
     * wide 4px) so the aimed bundle/bar is the one that gets removed. Returns -1
     * (remove top) when the hit is below the deck or above the occupied top.
     */
    private static int resolveLayerByHit(BlockHitResult hit, BlockPos pos, double stepPx, int topLayer) {
        if (hit == null || pos == null || topLayer < 0) {
            return -1;
        }
        double localY = (hit.getLocation().y - pos.getY()) * 16.0D;
        if (localY < BASE_TOP) {
            return -1;
        }
        int layer = (int) Math.floor((localY - BASE_TOP) / stepPx);
        return layer > topLayer ? -1 : layer;
    }

    private static void pushPalletFeedback(Player player,
                                           String message,
                                           DeliveryAlertPayload.AlertTone tone,
                                           int durationMs) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        String normalized = ServerNotification.stripLegacyFormatting(message);
        player.sendSystemMessage(UbsTranslations.literal(normalized));
        if (player instanceof ServerPlayer serverPlayer) {
            ServerNotification.sendLegacy(
                    serverPlayer,
                    "Metal Pallet",
                    normalized,
                    tone == null ? DeliveryAlertPayload.AlertTone.INFO : tone,
                    Math.max(1200, durationMs)
            );
        }
    }
}
