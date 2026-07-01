package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.block.entity.custom.BankVaultDoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BankVaultDoorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<BankVaultDoorBlock> CODEC = simpleCodec(BankVaultDoorBlock::new);
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 4);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 3);
    public static final IntegerProperty PART_D = IntegerProperty.create("part_d", 0, 3);
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    private static final int MASTER_PART_X = 2;
    private static final int MASTER_PART_Y = 0;
    private static final int MASTER_PART_D = 0;
    private static final int MAX_PART_D = 3;
    private static final int REMOVE_MAX_PART_D = 3;
    private static final int PART_X_COUNT = 5;
    private static final int PART_Y_COUNT = 4;
    private static final int PART_D_COUNT = 4;
    private static final int COLLISION_FRAME_COUNT = 17;
    private static final int DOOR_VERTICAL_SEGMENTS = 10;
    private static final int DOOR_LENGTH_SEGMENTS = 8;
    private static final double DEG_TO_RAD = Math.PI / 180.0D;
    private static final double DOOR_OPEN_DEGREES = 108.0D;
    private static final double DOOR_OVERSHOOT_DEGREES = 114.0D;
    private static final double DOOR_HINGE_U = 1.03D;
    private static final double DOOR_CENTER_U = 2.50D;
    private static final double DOOR_CENTER_Y = 2.02D;
    private static final double DOOR_RADIUS_U = 1.48D;
    private static final double DOOR_RADIUS_Y = 1.50D;
    private static final double DOOR_CLOSED_DEPTH = 0.50D;
    private static final double DOOR_THICKNESS = 0.34D;
    private static final double COLLISION_EPSILON = 0.01D;
    private static final double ENTITY_PUSH_EPSILON = 0.02D;
    private static final double ENTITY_PUSH_MAX_PER_TICK = 0.45D;
    private static final VoxelShape FULL_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape BOTTOM_SLAB_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    private static final VoxelShape EMPTY_SHAPE = Shapes.empty();
    private static final VoxelShape[][][][][] DOOR_COLLISION_SHAPES = buildDoorCollisionShapes();
    private static final DoorCollisionFrame[][] DOOR_COLLISION_FRAMES = buildDoorCollisionFrames();
    private static boolean internalRemoval;

    public BankVaultDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART_X, MASTER_PART_X)
                .setValue(PART_Y, MASTER_PART_Y)
                .setValue(PART_D, MASTER_PART_D)
                .setValue(POWERED, false)
                .setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos masterPos = context.getClickedPos();
        Level level = context.getLevel();
        for (int partD = 0; partD <= MAX_PART_D; partD++) {
            for (int partY = 0; partY <= 3; partY++) {
                if (masterPos.getY() + partY >= level.getMaxBuildHeight()) {
                    return null;
                }
                for (int partX = 0; partX <= 4; partX++) {
                    BlockPos partPos = getPartPos(masterPos, facing, partX, partY, partD);
                    if (!level.getBlockState(partPos).canBeReplaced(context)) {
                        return null;
                    }
                }
            }
        }
        boolean powered = hasAnyPartNeighborSignal(level, masterPos, facing);
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(PART_X, MASTER_PART_X)
                .setValue(PART_Y, MASTER_PART_Y)
                .setValue(PART_D, MASTER_PART_D)
                .setValue(POWERED, powered);
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
        boolean powered = hasAnyPartNeighborSignal(level, pos, facing);
        BlockState baseState = defaultBlockState()
                .setValue(FACING, facing)
                .setValue(POWERED, powered)
                .setValue(OPEN, false);

        for (int partD = 0; partD <= MAX_PART_D; partD++) {
            for (int partY = 0; partY <= 3; partY++) {
                for (int partX = 0; partX <= 4; partX++) {
                    BlockState partState = baseState
                            .setValue(PART_X, partX)
                            .setValue(PART_Y, partY)
                            .setValue(PART_D, partD);
                    if (partX == MASTER_PART_X && partY == MASTER_PART_Y && partD == MASTER_PART_D) {
                        level.setBlock(pos, partState, Block.UPDATE_ALL);
                        continue;
                    }
                    level.setBlock(
                            getPartPos(pos, facing, partX, partY, partD),
                            partState,
                            Block.UPDATE_ALL
                    );
                }
            }
        }
        if (level.getBlockEntity(pos) instanceof BankVaultDoorBlockEntity vault) {
            vault.setTargetOpen(powered);
        }
    }

    @Override
    protected void neighborChanged(BlockState state,
                                   Level level,
                                   BlockPos pos,
                                   Block neighborBlock,
                                   BlockPos neighborPos,
                                   boolean movedByPiston) {
        if (!level.isClientSide()) {
            syncPoweredState(level, getMasterPos(state, pos));
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    public BlockState updateShape(BlockState state,
                                  Direction direction,
                                  BlockState neighborState,
                                  LevelAccessor level,
                                  BlockPos pos,
                                  BlockPos neighborPos) {
        if (internalRemoval || !(level instanceof Level readableLevel)) {
            return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        }
        BlockPos masterPos = getMasterPos(state, pos);
        BlockState masterState = readableLevel.getBlockState(masterPos);
        if (!masterState.is(this)
                || masterState.getValue(PART_X) != MASTER_PART_X
                || masterState.getValue(PART_Y) != MASTER_PART_Y
                || masterState.getValue(PART_D) != MASTER_PART_D) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getVaultShape(state, level, pos);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getVaultCollisionShape(state, level, pos);
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
        if (state.getValue(PART_X) != MASTER_PART_X
                || state.getValue(PART_Y) != MASTER_PART_Y
                || state.getValue(PART_D) != MASTER_PART_D) {
            return null;
        }
        return new BankVaultDoorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
                                                                             BlockState state,
                                                                             BlockEntityType<T> type) {
        if (type != ModBlockEntities.BANK_VAULT_DOOR.get()) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof BankVaultDoorBlockEntity vault) {
                BankVaultDoorBlockEntity.tick(tickLevel, tickPos, tickState, vault);
            }
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_X, PART_Y, PART_D, POWERED, OPEN);
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state == null || pos == null || !state.hasProperty(FACING)
                || !state.hasProperty(PART_X) || !state.hasProperty(PART_Y) || !state.hasProperty(PART_D)) {
            return pos;
        }
        Direction facing = state.getValue(FACING);
        int partX = state.getValue(PART_X);
        int partY = state.getValue(PART_Y);
        int partD = state.getValue(PART_D);
        return pos.relative(getRightDirection(facing), MASTER_PART_X - partX)
                .below(partY - MASTER_PART_Y)
                .relative(facing, partD - MASTER_PART_D);
    }

    private static VoxelShape getVaultShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (state == null || !state.hasProperty(PART_X) || !state.hasProperty(PART_Y)
                || !state.hasProperty(PART_D) || !state.hasProperty(POWERED) || !state.hasProperty(OPEN)) {
            return FULL_SHAPE;
        }
        VoxelShape staticFrame = getStaticFrameShape(state);
        VoxelShape doorLeaf = getDoorLeafShape(state, level, pos);
        return doorLeaf.isEmpty() ? staticFrame : Shapes.or(staticFrame, doorLeaf);
    }

    private static VoxelShape getVaultCollisionShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (state == null || !state.hasProperty(PART_X) || !state.hasProperty(PART_Y)
                || !state.hasProperty(PART_D) || !state.hasProperty(POWERED) || !state.hasProperty(OPEN)) {
            return FULL_SHAPE;
        }
        VoxelShape staticFrame = getStaticFrameShape(state);
        VoxelShape doorLeaf = getDoorLeafShapeAtProgress(state, getCollisionAnimationProgress(state, level, pos));
        return doorLeaf.isEmpty() ? staticFrame : Shapes.or(staticFrame, doorLeaf);
    }

    private static VoxelShape getStaticFrameShape(BlockState state) {
        int partX = state.getValue(PART_X);
        int partY = state.getValue(PART_Y);
        int partD = state.getValue(PART_D);
        if (partD != MASTER_PART_D) {
            return EMPTY_SHAPE;
        }
        if (partY == 0 && partX >= 1 && partX <= 3) {
            return BOTTOM_SLAB_SHAPE;
        }
        boolean centralWalkThrough = partX >= 1 && partX <= 3 && partY <= 2;
        return centralWalkThrough ? EMPTY_SHAPE : FULL_SHAPE;
    }

    private static VoxelShape getDoorLeafShape(BlockState state, BlockGetter level, BlockPos pos) {
        float progress = getAnimationProgress(state, level, pos);
        int partX = state.getValue(PART_X);
        int partY = state.getValue(PART_Y);
        int partD = state.getValue(PART_D);
        return getDoorLeafShapeAtProgress(state, progress, partX, partY, partD);
    }

    private static VoxelShape getDoorLeafShapeAtProgress(BlockState state, float progress) {
        int partX = state.getValue(PART_X);
        int partY = state.getValue(PART_Y);
        int partD = state.getValue(PART_D);
        return getDoorLeafShapeAtProgress(state, progress, partX, partY, partD);
    }

    private static VoxelShape getDoorLeafShapeAtProgress(BlockState state, float progress, int partX, int partY, int partD) {
        if (partX < 0 || partX >= PART_X_COUNT || partY < 0 || partY >= PART_Y_COUNT
                || partD < 0 || partD >= PART_D_COUNT) {
            return EMPTY_SHAPE;
        }
        return DOOR_COLLISION_SHAPES[getFacingIndex(state.getValue(FACING))]
                [getCollisionFrame(progress)][partX][partY][partD];
    }

    public static void pushEntitiesForAnimatedDoor(Level level,
                                                   BlockPos masterPos,
                                                   BlockState masterState,
                                                   float previousProgress,
                                                   float nextProgress) {
        if (level == null || level.isClientSide() || masterPos == null || masterState == null
                || !masterState.is(ModBlocks.BANK_VAULT_DOOR.get())
                || previousProgress == nextProgress) {
            return;
        }
        Direction facing = masterState.hasProperty(FACING) ? masterState.getValue(FACING) : Direction.NORTH;
        DoorCollisionFrame previousFrame = getDoorCollisionFrame(facing, previousProgress);
        DoorCollisionFrame nextFrame = getDoorCollisionFrame(facing, nextProgress);
        if (nextFrame.isEmpty()) {
            return;
        }
        AABB nextBounds = nextFrame.bounds().move(masterPos.getX(), masterPos.getY(), masterPos.getZ());
        AABB queryBounds = previousFrame.isEmpty()
                ? nextBounds
                : union(
                        previousFrame.bounds().move(masterPos.getX(), masterPos.getY(), masterPos.getZ()),
                        nextBounds
                );
        queryBounds = queryBounds.inflate(0.35D);

        List<Entity> entities = level.getEntities(
                (Entity) null,
                queryBounds,
                entity -> entity != null && entity.isAlive() && !entity.isSpectator()
        );
        for (Entity entity : entities) {
            Vec3 push = nextFrame.collisionResponse(entity.getBoundingBox().move(
                    -masterPos.getX(),
                    -masterPos.getY(),
                    -masterPos.getZ()
            ));
            if (push.lengthSqr() > 1.0E-8D) {
                entity.move(MoverType.PISTON, push);
            }
        }
    }

    public static void setOpenStateIfNeeded(Level level, BlockPos masterPos, BlockState masterState, boolean open) {
        if (level == null || level.isClientSide() || masterPos == null || masterState == null
                || !masterState.is(ModBlocks.BANK_VAULT_DOOR.get())) {
            return;
        }
        BlockState currentState = level.getBlockState(masterPos);
        if (!currentState.is(ModBlocks.BANK_VAULT_DOOR.get()) || !currentState.hasProperty(OPEN)
                || currentState.getValue(OPEN) == open) {
            return;
        }
        Direction facing = currentState.hasProperty(FACING) ? currentState.getValue(FACING) : Direction.NORTH;
        setAllPartsOpen(level, masterPos, facing, open);
    }

    private static AABB union(AABB first, AABB second) {
        return new AABB(
                Math.min(first.minX, second.minX),
                Math.min(first.minY, second.minY),
                Math.min(first.minZ, second.minZ),
                Math.max(first.maxX, second.maxX),
                Math.max(first.maxY, second.maxY),
                Math.max(first.maxZ, second.maxZ)
        );
    }

    private record DoorCollisionFrame(List<AABB> boxes, AABB bounds) {
        private static final DoorCollisionFrame EMPTY =
                new DoorCollisionFrame(List.of(), new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D));

        private static DoorCollisionFrame of(List<AABB> boxes) {
            if (boxes.isEmpty()) {
                return EMPTY;
            }
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (AABB box : boxes) {
                minX = Math.min(minX, box.minX);
                minY = Math.min(minY, box.minY);
                minZ = Math.min(minZ, box.minZ);
                maxX = Math.max(maxX, box.maxX);
                maxY = Math.max(maxY, box.maxY);
                maxZ = Math.max(maxZ, box.maxZ);
            }
            return new DoorCollisionFrame(
                    List.copyOf(boxes),
                    new AABB(minX, minY, minZ, maxX, maxY, maxZ)
            );
        }

        private boolean isEmpty() {
            return boxes.isEmpty();
        }

        private Vec3 collisionResponse(AABB entityBounds) {
            if (isEmpty() || !entityBounds.intersects(bounds)) {
                return Vec3.ZERO;
            }
            AABB workingBounds = entityBounds;
            Vec3 totalPush = Vec3.ZERO;
            for (int iteration = 0; iteration < 4; iteration++) {
                Vec3 push = findSmallestHorizontalPush(workingBounds);
                if (push.lengthSqr() <= 1.0E-8D) {
                    break;
                }
                Vec3 nextPush = totalPush.add(push);
                double horizontalLength = Math.sqrt(nextPush.x * nextPush.x + nextPush.z * nextPush.z);
                if (horizontalLength > ENTITY_PUSH_MAX_PER_TICK) {
                    double scale = ENTITY_PUSH_MAX_PER_TICK / horizontalLength;
                    return new Vec3(nextPush.x * scale, 0.0D, nextPush.z * scale);
                }
                totalPush = nextPush;
                workingBounds = workingBounds.move(push);
            }
            return totalPush;
        }

        private Vec3 findSmallestHorizontalPush(AABB entityBounds) {
            Vec3 bestPush = Vec3.ZERO;
            double bestDistance = Double.MAX_VALUE;
            for (AABB doorBox : boxes) {
                if (!entityBounds.intersects(doorBox)) {
                    continue;
                }
                double negativeX = doorBox.minX - entityBounds.maxX - ENTITY_PUSH_EPSILON;
                double positiveX = doorBox.maxX - entityBounds.minX + ENTITY_PUSH_EPSILON;
                double negativeZ = doorBox.minZ - entityBounds.maxZ - ENTITY_PUSH_EPSILON;
                double positiveZ = doorBox.maxZ - entityBounds.minZ + ENTITY_PUSH_EPSILON;
                double pushX = Math.abs(negativeX) < Math.abs(positiveX) ? negativeX : positiveX;
                double pushZ = Math.abs(negativeZ) < Math.abs(positiveZ) ? negativeZ : positiveZ;
                Vec3 candidate = Math.abs(pushX) < Math.abs(pushZ)
                        ? new Vec3(pushX, 0.0D, 0.0D)
                        : new Vec3(0.0D, 0.0D, pushZ);
                double candidateDistance = Math.abs(candidate.x) + Math.abs(candidate.z);
                if (candidateDistance < bestDistance) {
                    bestDistance = candidateDistance;
                    bestPush = candidate;
                }
            }
            return bestPush;
        }
    }

    private static float getAnimationProgress(BlockState state, BlockGetter level, BlockPos pos) {
        if (level != null && pos != null) {
            BlockPos masterPos = getMasterPos(state, pos);
            if (level.getBlockEntity(masterPos) instanceof BankVaultDoorBlockEntity vault) {
                return vault.getCurrentAnimationProgress();
            }
        }
        return state.getValue(POWERED) ? 1.0F : 0.0F;
    }

    private static float getCollisionAnimationProgress(BlockState state, BlockGetter level, BlockPos pos) {
        if (level != null && pos != null) {
            BlockPos masterPos = getMasterPos(state, pos);
            if (level.getBlockEntity(masterPos) instanceof BankVaultDoorBlockEntity vault) {
                return vault.getCurrentAnimationProgress();
            }
        }
        return state.getValue(OPEN) ? 1.0F : 0.0F;
    }

    private static VoxelShape[][][][][] buildDoorCollisionShapes() {
        VoxelShape[][][][][] shapes =
                new VoxelShape[4][COLLISION_FRAME_COUNT][PART_X_COUNT][PART_Y_COUNT][PART_D_COUNT];
        Direction[] facings = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction facing : facings) {
            int facingIndex = getFacingIndex(facing);
            for (int frame = 0; frame < COLLISION_FRAME_COUNT; frame++) {
                float progress = (float) frame / (COLLISION_FRAME_COUNT - 1);
                double angle = getDoorAngleRadians(progress);
                double sin = Math.sin(angle);
                double cos = Math.cos(angle);
                for (int partX = 0; partX < PART_X_COUNT; partX++) {
                    for (int partY = 0; partY < PART_Y_COUNT; partY++) {
                        for (int partD = 0; partD < PART_D_COUNT; partD++) {
                            shapes[facingIndex][frame][partX][partY][partD] =
                                    buildDoorPartShape(facing, partX, partY, partD, sin, cos);
                        }
                    }
                }
            }
        }
        return shapes;
    }

    private static DoorCollisionFrame[][] buildDoorCollisionFrames() {
        DoorCollisionFrame[][] frames = new DoorCollisionFrame[4][COLLISION_FRAME_COUNT];
        Direction[] facings = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction facing : facings) {
            int facingIndex = getFacingIndex(facing);
            for (int frame = 0; frame < COLLISION_FRAME_COUNT; frame++) {
                frames[facingIndex][frame] = buildDoorCollisionFrame(facing, facingIndex, frame);
            }
        }
        return frames;
    }

    private static DoorCollisionFrame buildDoorCollisionFrame(Direction facing, int facingIndex, int frame) {
        List<AABB> boxes = new ArrayList<>();
        for (int partD = 0; partD < PART_D_COUNT; partD++) {
            for (int partY = 0; partY < PART_Y_COUNT; partY++) {
                for (int partX = 0; partX < PART_X_COUNT; partX++) {
                    VoxelShape shape = DOOR_COLLISION_SHAPES[facingIndex][frame][partX][partY][partD];
                    if (shape.isEmpty()) {
                        continue;
                    }
                    BlockPos partOffset = getPartPos(BlockPos.ZERO, facing, partX, partY, partD);
                    shape.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                            boxes.add(new AABB(x1, y1, z1, x2, y2, z2)
                                    .move(partOffset.getX(), partOffset.getY(), partOffset.getZ())));
                }
            }
        }
        return DoorCollisionFrame.of(boxes);
    }

    private static DoorCollisionFrame getDoorCollisionFrame(Direction facing, float progress) {
        return DOOR_COLLISION_FRAMES[getFacingIndex(facing)][getCollisionFrame(progress)];
    }

    private static VoxelShape buildDoorPartShape(Direction facing,
                                                 int partX,
                                                 int partY,
                                                 int partD,
                                                 double sin,
                                                 double cos) {
        VoxelShape shape = EMPTY_SHAPE;
        for (int verticalIndex = 0; verticalIndex < DOOR_VERTICAL_SEGMENTS; verticalIndex++) {
            double y0 = DOOR_CENTER_Y - DOOR_RADIUS_Y
                    + (2.0D * DOOR_RADIUS_Y * verticalIndex) / DOOR_VERTICAL_SEGMENTS;
            double y1 = DOOR_CENTER_Y - DOOR_RADIUS_Y
                    + (2.0D * DOOR_RADIUS_Y * (verticalIndex + 1)) / DOOR_VERTICAL_SEGMENTS;
            double yMid = (y0 + y1) * 0.5D;
            double normalizedY = (yMid - DOOR_CENTER_Y) / DOOR_RADIUS_Y;
            double chordScale = Math.sqrt(Math.max(0.0D, 1.0D - normalizedY * normalizedY));
            double u0 = DOOR_CENTER_U - DOOR_RADIUS_U * chordScale;
            double u1 = DOOR_CENTER_U + DOOR_RADIUS_U * chordScale;
            double t0 = u0 - DOOR_HINGE_U;
            double t1 = u1 - DOOR_HINGE_U;

            for (int lengthIndex = 0; lengthIndex < DOOR_LENGTH_SEGMENTS; lengthIndex++) {
                double segmentT0 = lerp((double) lengthIndex / DOOR_LENGTH_SEGMENTS, t0, t1);
                double segmentT1 = lerp((double) (lengthIndex + 1) / DOOR_LENGTH_SEGMENTS, t0, t1);
                VoxelShape segment = makeDoorSegmentShape(
                        facing,
                        partX,
                        partY,
                        partD,
                        y0,
                        y1,
                        segmentT0,
                        segmentT1,
                        sin,
                        cos
                );
                if (!segment.isEmpty()) {
                    shape = Shapes.or(shape, segment);
                }
            }
        }
        return shape.isEmpty() ? EMPTY_SHAPE : shape.optimize();
    }

    private static VoxelShape makeDoorSegmentShape(Direction facing,
                                                   int partX,
                                                   int partY,
                                                   int partD,
                                                   double y0,
                                                   double y1,
                                                   double t0,
                                                   double t1,
                                                   double sin,
                                                   double cos) {
        double halfThickness = DOOR_THICKNESS * 0.5D;
        double minU = Double.POSITIVE_INFINITY;
        double minD = Double.POSITIVE_INFINITY;
        double maxU = Double.NEGATIVE_INFINITY;
        double maxD = Double.NEGATIVE_INFINITY;
        double[] tValues = {t0, t1};
        double[] depthValues = {-halfThickness, halfThickness};
        for (double t : tValues) {
            for (double depthOffset : depthValues) {
                double u = DOOR_HINGE_U + t * cos - depthOffset * sin;
                double depth = DOOR_CLOSED_DEPTH + t * sin + depthOffset * cos;
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minD = Math.min(minD, depth);
                maxD = Math.max(maxD, depth);
            }
        }

        minU -= COLLISION_EPSILON;
        maxU += COLLISION_EPSILON;
        minD -= COLLISION_EPSILON;
        maxD += COLLISION_EPSILON;
        y0 -= COLLISION_EPSILON;
        y1 += COLLISION_EPSILON;

        double clippedU0 = Math.max(minU, partX);
        double clippedU1 = Math.min(maxU, partX + 1.0D);
        double clippedY0 = Math.max(y0, partY);
        double clippedY1 = Math.min(y1, partY + 1.0D);
        double clippedD0 = Math.max(minD, partD);
        double clippedD1 = Math.min(maxD, partD + 1.0D);
        if (clippedU1 <= clippedU0 || clippedY1 <= clippedY0 || clippedD1 <= clippedD0) {
            return EMPTY_SHAPE;
        }

        return boxFromVaultCoordinates(
                facing,
                clippedU0 - partX,
                clippedU1 - partX,
                clippedY0 - partY,
                clippedY1 - partY,
                clippedD0 - partD,
                clippedD1 - partD
        );
    }

    private static VoxelShape boxFromVaultCoordinates(Direction facing,
                                                      double u0,
                                                      double u1,
                                                      double y0,
                                                      double y1,
                                                      double d0,
                                                      double d1) {
        double x0;
        double x1;
        double z0;
        double z1;
        switch (facing) {
            case SOUTH -> {
                x0 = 1.0D - u1;
                x1 = 1.0D - u0;
                z0 = d0;
                z1 = d1;
            }
            case EAST -> {
                x0 = d0;
                x1 = d1;
                z0 = u0;
                z1 = u1;
            }
            case WEST -> {
                x0 = 1.0D - d1;
                x1 = 1.0D - d0;
                z0 = 1.0D - u1;
                z1 = 1.0D - u0;
            }
            case NORTH -> {
                x0 = u0;
                x1 = u1;
                z0 = 1.0D - d1;
                z1 = 1.0D - d0;
            }
            default -> {
                x0 = u0;
                x1 = u1;
                z0 = d0;
                z1 = d1;
            }
        }
        return Block.box(
                x0 * 16.0D,
                y0 * 16.0D,
                z0 * 16.0D,
                x1 * 16.0D,
                y1 * 16.0D,
                z1 * 16.0D
        );
    }

    private static int getCollisionFrame(float progress) {
        int frame = Math.round(clamp(progress, 0.0F, 1.0F) * (COLLISION_FRAME_COUNT - 1));
        return Math.max(0, Math.min(COLLISION_FRAME_COUNT - 1, frame));
    }

    private static double getDoorAngleRadians(float progress) {
        float clamped = clamp(progress, 0.0F, 1.0F);
        double swing = smooth(clamp((clamped - 0.18F) / 0.82F, 0.0F, 1.0F));
        double overshoot = Math.sin(clamp((clamped - 0.78F) / 0.22F, 0.0F, 1.0F) * Math.PI);
        return (DOOR_OPEN_DEGREES * swing + (DOOR_OVERSHOOT_DEGREES - DOOR_OPEN_DEGREES) * overshoot) * DEG_TO_RAD;
    }

    private static double smooth(double value) {
        double clamped = clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int getFacingIndex(Direction facing) {
        return switch (facing) {
            case SOUTH -> 1;
            case WEST -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    private static void syncPoweredState(Level level, BlockPos masterPos) {
        if (level == null || masterPos == null || level.isClientSide()) {
            return;
        }
        BlockState masterState = level.getBlockState(masterPos);
        if (!masterState.is(ModBlocks.BANK_VAULT_DOOR.get())) {
            return;
        }
        Direction facing = masterState.getValue(FACING);
        boolean powered = hasAnyPartNeighborSignal(level, masterPos, facing);
        setAllPartsPowered(level, masterPos, facing, powered);
        if (level.getBlockEntity(masterPos) instanceof BankVaultDoorBlockEntity vault) {
            vault.setTargetOpen(powered);
        }
    }

    private static void setAllPartsPowered(Level level, BlockPos masterPos, Direction facing, boolean powered) {
        for (int partD = 0; partD <= MAX_PART_D; partD++) {
            for (int partY = 0; partY <= 3; partY++) {
                for (int partX = 0; partX <= 4; partX++) {
                    BlockPos partPos = getPartPos(masterPos, facing, partX, partY, partD);
                    BlockState partState = level.getBlockState(partPos);
                    if (!partState.is(ModBlocks.BANK_VAULT_DOOR.get())
                            || !partState.hasProperty(POWERED)
                            || partState.getValue(POWERED) == powered) {
                        continue;
                    }
                    level.setBlock(partPos, partState.setValue(POWERED, powered), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void setAllPartsOpen(Level level, BlockPos masterPos, Direction facing, boolean open) {
        for (int partD = 0; partD <= MAX_PART_D; partD++) {
            for (int partY = 0; partY <= 3; partY++) {
                for (int partX = 0; partX <= 4; partX++) {
                    BlockPos partPos = getPartPos(masterPos, facing, partX, partY, partD);
                    BlockState partState = level.getBlockState(partPos);
                    if (!partState.is(ModBlocks.BANK_VAULT_DOOR.get())
                            || !partState.hasProperty(OPEN)
                            || partState.getValue(OPEN) == open) {
                        continue;
                    }
                    level.setBlock(partPos, partState.setValue(OPEN, open), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static boolean hasAnyPartNeighborSignal(Level level, BlockPos masterPos, Direction facing) {
        if (level == null || masterPos == null || facing == null) {
            return false;
        }
        for (int partD = 0; partD <= MAX_PART_D; partD++) {
            for (int partY = 0; partY <= 3; partY++) {
                for (int partX = 0; partX <= 4; partX++) {
                    if (level.hasNeighborSignal(getPartPos(masterPos, facing, partX, partY, partD))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static BlockPos getPartPos(BlockPos masterPos, Direction facing, int partX, int partY, int partD) {
        return masterPos.relative(getRightDirection(facing), partX - MASTER_PART_X)
                .above(partY - MASTER_PART_Y)
                .relative(facing, MASTER_PART_D - partD);
    }

    private static BlockPos getLegacyFrontPartPos(BlockPos masterPos, Direction facing, int partX, int partY, int partD) {
        return masterPos.relative(getRightDirection(facing), partX - MASTER_PART_X)
                .above(partY - MASTER_PART_Y)
                .relative(facing, partD - MASTER_PART_D);
    }

    private static Direction getRightDirection(Direction facing) {
        return facing.getClockWise();
    }

    private void removeStructure(Level level, BlockPos masterPos, @Nullable Player breaker) {
        if (internalRemoval || level == null || masterPos == null) {
            return;
        }
        BlockState masterState = level.getBlockState(masterPos);
        Direction facing = masterState.hasProperty(FACING) ? masterState.getValue(FACING) : Direction.NORTH;
        internalRemoval = true;
        try {
            for (int partD = 0; partD <= REMOVE_MAX_PART_D; partD++) {
                for (int partY = 0; partY <= 3; partY++) {
                    for (int partX = 0; partX <= 4; partX++) {
                        removePart(level, getPartPos(masterPos, facing, partX, partY, partD));
                        removePart(level, getLegacyFrontPartPos(masterPos, facing, partX, partY, partD));
                    }
                }
            }
            boolean shouldDrop = breaker == null || !breaker.getAbilities().instabuild;
            if (shouldDrop) {
                Containers.dropItemStack(
                        level,
                        masterPos.getX() + 0.5D,
                        masterPos.getY() + 0.5D,
                        masterPos.getZ() + 0.5D,
                        new ItemStack(ModBlocks.BANK_VAULT_DOOR.get().asItem())
                );
            }
        } finally {
            internalRemoval = false;
        }
    }

    private static void removePart(Level level, BlockPos partPos) {
        BlockState partState = level.getBlockState(partPos);
        if (partState.is(ModBlocks.BANK_VAULT_DOOR.get())) {
            level.setBlock(partPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
