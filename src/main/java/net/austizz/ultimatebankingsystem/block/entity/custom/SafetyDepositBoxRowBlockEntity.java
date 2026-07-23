package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.bank.safebox.LoadedSafeStructureIndex;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

public class SafetyDepositBoxRowBlockEntity extends BlockEntity {
    public static final int DOOR_COUNT = 4;
    public static final int OPEN_ANIMATION_TICKS = 34;
    public static final int CLOSE_ANIMATION_TICKS = 26;

    private final ModuleType[] moduleTypes = new ModuleType[DOOR_COUNT];
    private final float[] previousDoorProgress = new float[DOOR_COUNT];
    private final float[] doorProgress = new float[DOOR_COUNT];
    private final long[] openUntilGameTime = new long[DOOR_COUNT];
    private final UUID[] assignedAccountIds = new UUID[DOOR_COUNT];
    private final UUID[] viewingTransferIds = new UUID[DOOR_COUNT];
    private final UUID[] heistBreachIds = new UUID[DOOR_COUNT];
    private final String[] boxNumbers = new String[DOOR_COUNT];
    private LegacySafetyDepositRowData legacyDoorData = LegacySafetyDepositRowData.empty();

    public SafetyDepositBoxRowBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAFETY_DEPOSIT_BOX_ROW.get(), pos, state);
        Arrays.fill(moduleTypes, ModuleType.EMPTY);
        Arrays.fill(boxNumbers, "");
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LoadedSafeStructureIndex.register(level, worldPosition, LoadedSafeStructureIndex.Kind.ROW);
    }

    @Override
    public void setRemoved() {
        LoadedSafeStructureIndex.unregister(level, worldPosition, LoadedSafeStructureIndex.Kind.ROW);
        super.setRemoved();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SafetyDepositBoxRowBlockEntity row) {
        if (level == null || row == null) {
            return;
        }

        long now = level.getGameTime();
        boolean changed = false;
        for (int i = 0; i < DOOR_COUNT; i++) {
            row.previousDoorProgress[i] = row.doorProgress[i];
            boolean shouldOpen = row.isAssignableBoxStart(i)
                    && (row.openUntilGameTime[i] > now || row.viewingTransferIds[i] != null
                    || row.heistBreachIds[i] != null);
            float step = shouldOpen ? 1.0F / OPEN_ANIMATION_TICKS : 1.0F / CLOSE_ANIMATION_TICKS;
            float next = shouldOpen
                    ? Math.min(1.0F, row.doorProgress[i] + step)
                    : Math.max(0.0F, row.doorProgress[i] - step);
            if (next != row.doorProgress[i]) {
                row.doorProgress[i] = next;
                changed = true;
            }
            if (!shouldOpen && row.openUntilGameTime[i] != 0L && row.doorProgress[i] <= 0.0F) {
                row.openUntilGameTime[i] = 0L;
                changed = true;
            }
        }
        if (changed && !level.isClientSide()) {
            row.setChanged();
        }
    }

    public ModuleType getModuleType(int index) {
        return isValidRow(index) ? nonNullModule(moduleTypes[index]) : ModuleType.EMPTY;
    }

    public ModuleType[] getModuleTypesSnapshot() {
        return Arrays.copyOf(moduleTypes, moduleTypes.length);
    }

    public static boolean isFullyAssignableRow(ModuleType[] moduleTypes) {
        if (moduleTypes == null || moduleTypes.length < DOOR_COUNT) {
            return false;
        }
        boolean[] covered = new boolean[DOOR_COUNT];
        for (int start = 0; start < DOOR_COUNT; start++) {
            ModuleType type = nonNullModule(moduleTypes[start]);
            if (!type.assignable()) {
                continue;
            }
            if (!fits(type, start)) {
                return false;
            }
            for (int row = start; row < start + type.rowSpan(); row++) {
                if (covered[row]) {
                    return false;
                }
                covered[row] = true;
            }
        }
        for (boolean rowCovered : covered) {
            if (!rowCovered) {
                return false;
            }
        }
        return true;
    }

    public boolean isFullyAssignableRow() {
        return isFullyAssignableRow(moduleTypes);
    }

    public int getModuleStartForRow(int row) {
        if (!isValidRow(row)) {
            return -1;
        }
        for (int start = row; start >= 0; start--) {
            ModuleType type = nonNullModule(moduleTypes[start]);
            if (type.occupiesRows() && start + type.rowSpan() > row) {
                return start;
            }
        }
        return -1;
    }

    public boolean isAssignableBoxStart(int row) {
        ModuleType type = getModuleType(row);
        return type.assignable() && fits(type, row);
    }

    public boolean isAssigned(int row) {
        int start = getModuleStartForRow(row);
        return isValidRow(start) && assignedAccountIds[start] != null;
    }

    public UUID getAssignedAccountId(int row) {
        int start = getModuleStartForRow(row);
        return isValidRow(start) ? assignedAccountIds[start] : null;
    }

    public String getBoxNumber(int row) {
        int start = getModuleStartForRow(row);
        if (!isValidRow(start)) {
            return "";
        }
        String boxNumber = boxNumbers[start];
        return boxNumber == null ? "" : boxNumber;
    }

    public void assignDoor(int row, UUID accountId, String boxNumber) {
        int start = getModuleStartForRow(row);
        if (!isAssignableBoxStart(start) || viewingTransferIds[start] != null) {
            return;
        }
        assignedAccountIds[start] = accountId;
        boxNumbers[start] = boxNumber == null ? "" : boxNumber.trim();
        markUpdated();
    }

    public void clearDoorAssignment(int row) {
        int start = getModuleStartForRow(row);
        if (!isValidRow(start) || viewingTransferIds[start] != null) {
            return;
        }
        assignedAccountIds[start] = null;
        boxNumbers[start] = "";
        markUpdated();
    }

    public int firstAvailableStart(ModuleType type) {
        ModuleType cleanType = nonNullModule(type);
        if (!cleanType.occupiesRows()) {
            return -1;
        }
        for (int start = 0; start <= DOOR_COUNT - cleanType.rowSpan(); start++) {
            if (canInstallModule(start, cleanType)) {
                return start;
            }
        }
        return -1;
    }

    public boolean installModule(int start, ModuleType type) {
        ModuleType cleanType = nonNullModule(type);
        if (!canInstallModule(start, cleanType)) {
            return false;
        }
        moduleTypes[start] = cleanType;
        assignedAccountIds[start] = null;
        boxNumbers[start] = "";
        openUntilGameTime[start] = 0L;
        doorProgress[start] = 0.0F;
        previousDoorProgress[start] = 0.0F;
        for (int i = start + 1; i < start + cleanType.rowSpan(); i++) {
            moduleTypes[i] = ModuleType.EMPTY;
            assignedAccountIds[i] = null;
            boxNumbers[i] = "";
            openUntilGameTime[i] = 0L;
            doorProgress[i] = 0.0F;
            previousDoorProgress[i] = 0.0F;
        }
        markUpdated();
        return true;
    }

    public void openDoor(int row, long openUntil) {
        int start = getModuleStartForRow(row);
        if (!isAssignableBoxStart(start)) {
            return;
        }
        openUntilGameTime[start] = Math.max(openUntilGameTime[start], openUntil);
        markUpdated();
    }

    public void closeDoor(int row) {
        int start = getModuleStartForRow(row);
        if (!isValidRow(start) || viewingTransferIds[start] != null) {
            return;
        }
        openUntilGameTime[start] = 0L;
        markUpdated();
    }

    public boolean beginViewingTransfer(int row, UUID sessionId) {
        int start = getModuleStartForRow(row);
        if (!isAssignableBoxStart(start) || sessionId == null) {
            return false;
        }
        UUID current = viewingTransferIds[start];
        if (current != null && !current.equals(sessionId)) {
            return false;
        }
        viewingTransferIds[start] = sessionId;
        openUntilGameTime[start] = Long.MAX_VALUE;
        markUpdated();
        return true;
    }

    public boolean endViewingTransfer(int row, UUID sessionId) {
        int start = getModuleStartForRow(row);
        if (!isValidRow(start) || sessionId == null || !sessionId.equals(viewingTransferIds[start])) {
            return false;
        }
        viewingTransferIds[start] = null;
        openUntilGameTime[start] = 0L;
        markUpdated();
        return true;
    }

    public boolean isViewingTransferActive(int row) {
        int start = getModuleStartForRow(row);
        return isValidRow(start) && viewingTransferIds[start] != null;
    }

    public boolean isViewingTransferOwnedBy(int row, UUID sessionId) {
        int start = getModuleStartForRow(row);
        return isValidRow(start) && sessionId != null && sessionId.equals(viewingTransferIds[start]);
    }

    public boolean hasAnyViewingTransfer() {
        for (UUID transferId : viewingTransferIds) {
            if (transferId != null) {
                return true;
            }
        }
        return false;
    }

    public boolean beginHeistBreach(int row, UUID sessionId) {
        int start = getModuleStartForRow(row);
        if (!isAssignableBoxStart(start) || sessionId == null || viewingTransferIds[start] != null) {
            return false;
        }
        UUID current = heistBreachIds[start];
        if (current != null && !current.equals(sessionId)) {
            return false;
        }
        heistBreachIds[start] = sessionId;
        markUpdated();
        return true;
    }

    public boolean endHeistBreach(int row, UUID sessionId) {
        int start = getModuleStartForRow(row);
        if (!isValidRow(start) || sessionId == null || !sessionId.equals(heistBreachIds[start])) {
            return false;
        }
        heistBreachIds[start] = null;
        markUpdated();
        return true;
    }

    public boolean isHeistBreached(int row, UUID sessionId) {
        int start = getModuleStartForRow(row);
        return isValidRow(start) && sessionId != null && sessionId.equals(heistBreachIds[start]);
    }

    public boolean isHeistBreached(int row) {
        int start = getModuleStartForRow(row);
        return isValidRow(start) && heistBreachIds[start] != null;
    }

    public static UUID heistDisplayId(String dimension, BlockPos rowPos, int door) {
        String key = "ultimatebankingsystem:heist_box_contents:"
                + (dimension == null ? "" : dimension) + ":"
                + (rowPos == null ? 0L : rowPos.asLong()) + ":" + Math.max(0, door);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    public boolean[] getViewingTransferMask() {
        boolean[] mask = new boolean[DOOR_COUNT];
        for (int index = 0; index < DOOR_COUNT; index++) {
            mask[index] = viewingTransferIds[index] != null;
        }
        return mask;
    }

    public float getDoorProgress(int row, float partialTick) {
        int start = getModuleStartForRow(row);
        if (!isValidRow(start)) {
            return 0.0F;
        }
        return Mth.lerp(partialTick, previousDoorProgress[start], doorProgress[start]);
    }

    public float getCurrentDoorProgress(int row) {
        int start = getModuleStartForRow(row);
        return isValidRow(start) ? doorProgress[start] : 0.0F;
    }

    public AABB getRenderBoundingBox() {
        return new AABB(
                worldPosition.getX() - 1.0D,
                worldPosition.getY() - 1.0D,
                worldPosition.getZ() - 1.0D,
                worldPosition.getX() + 2.0D,
                worldPosition.getY() + 2.0D,
                worldPosition.getZ() + 2.0D
        );
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Arrays.fill(moduleTypes, ModuleType.EMPTY);
        Arrays.fill(assignedAccountIds, null);
        Arrays.fill(viewingTransferIds, null);
        Arrays.fill(heistBreachIds, null);
        Arrays.fill(boxNumbers, "");
        Arrays.fill(openUntilGameTime, 0L);
        Arrays.fill(doorProgress, 0.0F);
        Arrays.fill(previousDoorProgress, 0.0F);
        legacyDoorData = LegacySafetyDepositRowData.load(tag);

        for (int i = 0; i < DOOR_COUNT; i++) {
            moduleTypes[i] = ModuleType.byName(tag.getString("module_" + i));
            if (tag.hasUUID("account_" + i)) {
                assignedAccountIds[i] = tag.getUUID("account_" + i);
            }
            if (tag.hasUUID("viewing_transfer_" + i)) {
                viewingTransferIds[i] = tag.getUUID("viewing_transfer_" + i);
            }
            if (tag.hasUUID("heist_breach_" + i)) {
                heistBreachIds[i] = tag.getUUID("heist_breach_" + i);
            }
            boxNumbers[i] = tag.getString("box_number_" + i);
            openUntilGameTime[i] = tag.getLong("open_until_" + i);
            doorProgress[i] = Mth.clamp(tag.getFloat("door_progress_" + i), 0.0F, 1.0F);
            previousDoorProgress[i] = doorProgress[i];
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < DOOR_COUNT; i++) {
            tag.putString("module_" + i, nonNullModule(moduleTypes[i]).serializedName());
            UUID accountId = assignedAccountIds[i];
            if (accountId != null) {
                tag.putUUID("account_" + i, accountId);
            }
            UUID transferId = viewingTransferIds[i];
            if (transferId != null) {
                tag.putUUID("viewing_transfer_" + i, transferId);
            }
            UUID heistId = heistBreachIds[i];
            if (heistId != null) {
                tag.putUUID("heist_breach_" + i, heistId);
            }
            tag.putString("box_number_" + i, boxNumbers[i] == null ? "" : boxNumbers[i]);
            tag.putLong("open_until_" + i, openUntilGameTime[i]);
            tag.putFloat("door_progress_" + i, Mth.clamp(doorProgress[i], 0.0F, 1.0F));
        }
        legacyDoorData.saveTo(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private boolean canInstallModule(int start, ModuleType type) {
        if (!fits(type, start)) {
            return false;
        }
        for (int row = start; row < start + type.rowSpan(); row++) {
            if (isRowOccupied(row)) {
                return false;
            }
        }
        return true;
    }

    private boolean isRowOccupied(int row) {
        return getModuleStartForRow(row) >= 0;
    }

    private static boolean fits(ModuleType type, int start) {
        return type != null
                && type.occupiesRows()
                && start >= 0
                && start + type.rowSpan() <= DOOR_COUNT;
    }

    private static boolean isValidRow(int row) {
        return row >= 0 && row < DOOR_COUNT;
    }

    private static ModuleType nonNullModule(ModuleType type) {
        return type == null ? ModuleType.EMPTY : type;
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    public enum ModuleType {
        EMPTY("empty", "Empty Shell", 0, 0, false),
        SMALL("small", "Small Safety Deposit Box", 1, 9, true),
        MEDIUM("medium", "Medium Safety Deposit Box", 2, 18, true),
        LARGE("large", "Large Safety Deposit Box", 3, 27, true),
        EXTRA_LARGE("extra_large", "Extra Large Safety Deposit Box", 4, 54, true),
        COVER("cover", "Cover Plate", 1, 0, false);

        private final String serializedName;
        private final String displayName;
        private final int rowSpan;
        private final int inventorySlots;
        private final boolean assignable;

        ModuleType(String serializedName, String displayName, int rowSpan, int inventorySlots, boolean assignable) {
            this.serializedName = serializedName;
            this.displayName = displayName;
            this.rowSpan = Math.max(0, rowSpan);
            this.inventorySlots = Math.max(0, inventorySlots);
            this.assignable = assignable;
        }

        public String serializedName() {
            return serializedName;
        }

        public String displayName() {
            return displayName;
        }

        public int rowSpan() {
            return rowSpan;
        }

        public int inventorySlots() {
            return inventorySlots;
        }

        public boolean assignable() {
            return assignable;
        }

        public boolean occupiesRows() {
            return rowSpan > 0;
        }

        public static ModuleType byName(String name) {
            if (name == null || name.isBlank()) {
                return EMPTY;
            }
            String normalized = name.trim().toLowerCase(Locale.ROOT);
            for (ModuleType type : values()) {
                if (type.serializedName.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return type;
                }
            }
            return EMPTY;
        }
    }
}
