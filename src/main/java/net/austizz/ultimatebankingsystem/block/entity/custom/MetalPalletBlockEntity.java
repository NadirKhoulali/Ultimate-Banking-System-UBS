package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.CashStackBlock;
import net.austizz.ultimatebankingsystem.block.custom.MoneyStackBlock;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MetalPalletBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
    // Two logical grids share one handler, wide grid first:
    //  - WIDE grid (bars/cash): 4 across (X) x 3 deep (Z) x 6 layers, slots 0..71,
    //    slot = layer * 12 + (wz * 4 + wx). This is EXACTLY the v1 layout, so v1
    //    saves (Size == 72) load into the wide grid with zero slot remapping.
    //  - MONEY grid (bill bundles): 8 across (X) x 4 deep (Z) x 12 layers,
    //    slots 72..455, slot = 72 + layer * 32 + (mz * 8 + mx).
    // Both column types top out at the same height: 6 x 4px = 12 x 2px = 24px.
    public static final int WIDE_GRID_X = 4;
    public static final int WIDE_GRID_Z = 3;
    public static final int WIDE_LAYERS = 6;
    public static final int WIDE_POSITIONS = WIDE_GRID_X * WIDE_GRID_Z;
    public static final int WIDE_SLOT_COUNT = WIDE_POSITIONS * WIDE_LAYERS;

    public static final int MONEY_GRID_X = 8;
    public static final int MONEY_GRID_Z = 4;
    public static final int MONEY_LAYERS = 12;
    public static final int MONEY_POSITIONS = MONEY_GRID_X * MONEY_GRID_Z;
    public static final int MONEY_SLOT_COUNT = MONEY_POSITIONS * MONEY_LAYERS;

    public static final int SLOT_COUNT = WIDE_SLOT_COUNT + MONEY_SLOT_COUNT;

    // Geometry: position centers as block offsets from the pallet center.
    public static final double WIDE_PITCH_X = 0.64D;
    public static final double WIDE_PITCH_Z = 1.0D;
    public static final double MONEY_PITCH_X = 0.32D;
    // 12.12px pitch: bundles are 12px long along Z; keep the same 0.12px
    // micro-gap used between X sub-columns. Four rows span 48.36px on the 48px
    // pallet; the 0.18px overhang per side over the rim is accepted.
    public static final double MONEY_PITCH_Z = 0.7575D;

    // Legacy save formats (see loadAdditional).
    private static final int LEGACY_V1_SLOT_COUNT = 72;
    private static final int LEGACY_V2_SLOT_COUNT = 144;
    private static final int LEGACY_V2_COLUMNS = 24;
    private static final int LEGACY_V2_GRID_X = 8;
    private static final String STORAGE_KEY = "bars";

    // Real content footprints in px, taken from the blocks' voxel shapes/models:
    //  - money bundle rotated EAST on pallets: 5px wide (X) x 12px long (Z)
    //  - gold/silver bar (NORTH): 6.5px (X, 4.75..11.25) x 14.5px (Z, 0.75..15.25)
    //  - cash bill stack (NORTH): 12px (X, 2..14) x 10px (Z, 3..13); coins 6x6px
    // The wide envelope uses the widest wide content per axis:
    // X = 12px (cash bills), Z = 14.5px (bars).
    private static final double MONEY_HALF_X = 2.5D / 16.0D;
    private static final double MONEY_HALF_Z = 6.0D / 16.0D;
    private static final double WIDE_HALF_X = 6.0D / 16.0D;
    private static final double WIDE_HALF_Z = 7.25D / 16.0D;
    // Touching footprints do not conflict; only real overlap does.
    private static final double CONFLICT_EPSILON = 1.0E-4D;

    // Static cross-grid conflict table, [widePosition][moneyPosition]. Symmetric
    // by construction: both lookup directions read the same entries, which are
    // all derived from the single footprintsConflict predicate.
    private static final boolean[][] WIDE_TO_MONEY_CONFLICTS = buildConflictTable();

    private final ItemStackHandler bars = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            markUpdated();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (!isPalletStackable(stack)) {
                return false;
            }
            if (slot < WIDE_SLOT_COUNT) {
                if (!isWideContent(stack)) {
                    return false;
                }
                int position = slot % WIDE_POSITIONS;
                return !isWideBlockedByMoney(position % WIDE_GRID_X, position / WIDE_GRID_X);
            }
            if (!isMoneyStack(stack)) {
                return false;
            }
            int position = (slot - WIDE_SLOT_COUNT) % MONEY_POSITIONS;
            return !isMoneyBlockedByWide(position % MONEY_GRID_X, position / MONEY_GRID_X);
        }
    };

    public MetalPalletBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.METAL_PALLET.get(), pos, state);
    }

    public IItemHandler getItemHandler() {
        return bars;
    }

    public static boolean isMetalBar(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (stack.is(ModBlocks.GOLD_BAR.get().asItem()) || stack.is(ModBlocks.SILVER_BAR.get().asItem()));
    }

    public static boolean isCashTender(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && CashStackBlock.CashKind.fromItem(stack.getItem()) != null;
    }

    public static boolean isMoneyStack(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && MoneyStackBlock.BillDenomination.fromStackItem(stack.getItem()) != null;
    }

    public static boolean isPalletStackable(ItemStack stack) {
        return isMetalBar(stack) || isCashTender(stack) || isMoneyStack(stack);
    }

    public static boolean isWideContent(ItemStack stack) {
        return isMetalBar(stack) || isCashTender(stack);
    }

    public static int wideSlot(int wx, int wz, int layer) {
        if (wx < 0 || wx >= WIDE_GRID_X || wz < 0 || wz >= WIDE_GRID_Z || layer < 0 || layer >= WIDE_LAYERS) {
            return -1;
        }
        return layer * WIDE_POSITIONS + wz * WIDE_GRID_X + wx;
    }

    public static int moneySlot(int mx, int mz, int layer) {
        if (mx < 0 || mx >= MONEY_GRID_X || mz < 0 || mz >= MONEY_GRID_Z || layer < 0 || layer >= MONEY_LAYERS) {
            return -1;
        }
        return WIDE_SLOT_COUNT + layer * MONEY_POSITIONS + mz * MONEY_GRID_X + mx;
    }

    public static double wideCenterX(int wx) {
        return (wx - 1.5D) * WIDE_PITCH_X;
    }

    public static double wideCenterZ(int wz) {
        return (wz - 1.0D) * WIDE_PITCH_Z;
    }

    public static double moneyCenterX(int mx) {
        return (mx - 3.5D) * MONEY_PITCH_X;
    }

    public static double moneyCenterZ(int mz) {
        return (mz - 1.5D) * MONEY_PITCH_Z;
    }

    private static boolean footprintsConflict(int wx, int wz, int mx, int mz) {
        double dx = Math.abs(wideCenterX(wx) - moneyCenterX(mx));
        double dz = Math.abs(wideCenterZ(wz) - moneyCenterZ(mz));
        return dx < WIDE_HALF_X + MONEY_HALF_X - CONFLICT_EPSILON
                && dz < WIDE_HALF_Z + MONEY_HALF_Z - CONFLICT_EPSILON;
    }

    private static boolean[][] buildConflictTable() {
        boolean[][] table = new boolean[WIDE_POSITIONS][MONEY_POSITIONS];
        for (int wz = 0; wz < WIDE_GRID_Z; wz++) {
            for (int wx = 0; wx < WIDE_GRID_X; wx++) {
                for (int mz = 0; mz < MONEY_GRID_Z; mz++) {
                    for (int mx = 0; mx < MONEY_GRID_X; mx++) {
                        table[wz * WIDE_GRID_X + wx][mz * MONEY_GRID_X + mx] = footprintsConflict(wx, wz, mx, mz);
                    }
                }
            }
        }
        return table;
    }

    public ItemStack getWideStack(int wx, int wz, int layer) {
        int slot = wideSlot(wx, wz, layer);
        return slot < 0 ? ItemStack.EMPTY : bars.getStackInSlot(slot);
    }

    public ItemStack getMoneyStack(int mx, int mz, int layer) {
        int slot = moneySlot(mx, mz, layer);
        return slot < 0 ? ItemStack.EMPTY : bars.getStackInSlot(slot);
    }

    public boolean wideColumnHasContent(int wx, int wz) {
        return wideColumnTopLayer(wx, wz) >= 0;
    }

    public boolean moneyColumnHasContent(int mx, int mz) {
        return moneyColumnTopLayer(mx, mz) >= 0;
    }

    /** Highest occupied layer index of the wide column, or -1 when empty. */
    public int wideColumnTopLayer(int wx, int wz) {
        for (int layer = WIDE_LAYERS - 1; layer >= 0; layer--) {
            if (!getWideStack(wx, wz, layer).isEmpty()) {
                return layer;
            }
        }
        return -1;
    }

    /** Highest occupied layer index of the money column, or -1 when empty. */
    public int moneyColumnTopLayer(int mx, int mz) {
        for (int layer = MONEY_LAYERS - 1; layer >= 0; layer--) {
            if (!getMoneyStack(mx, mz, layer).isEmpty()) {
                return layer;
            }
        }
        return -1;
    }

    /**
     * Cross-grid anti-collision: a wide position is blocked while any money
     * position whose real footprint overlaps it holds at least one bundle.
     */
    public boolean isWideBlockedByMoney(int wx, int wz) {
        if (wx < 0 || wx >= WIDE_GRID_X || wz < 0 || wz >= WIDE_GRID_Z) {
            return false;
        }
        boolean[] row = WIDE_TO_MONEY_CONFLICTS[wz * WIDE_GRID_X + wx];
        for (int m = 0; m < MONEY_POSITIONS; m++) {
            if (row[m] && moneyColumnHasContent(m % MONEY_GRID_X, m / MONEY_GRID_X)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cross-grid anti-collision: a money position is blocked while any wide
     * position whose real footprint overlaps it holds at least one bar/cash pack.
     */
    public boolean isMoneyBlockedByWide(int mx, int mz) {
        if (mx < 0 || mx >= MONEY_GRID_X || mz < 0 || mz >= MONEY_GRID_Z) {
            return false;
        }
        int m = mz * MONEY_GRID_X + mx;
        for (int w = 0; w < WIDE_POSITIONS; w++) {
            if (WIDE_TO_MONEY_CONFLICTS[w][m] && wideColumnHasContent(w % WIDE_GRID_X, w / WIDE_GRID_X)) {
                return true;
            }
        }
        return false;
    }

    public boolean addWideStack(int wx, int wz, ItemStack stackToAdd) {
        if (!isWideContent(stackToAdd) || wideSlot(wx, wz, 0) < 0) {
            return false;
        }
        if (isWideBlockedByMoney(wx, wz)) {
            return false;
        }
        boolean addingCash = isCashTender(stackToAdd);
        for (int layer = 0; layer < WIDE_LAYERS; layer++) {
            int slot = wideSlot(wx, wz, layer);
            ItemStack existing = bars.getStackInSlot(slot);
            if (addingCash && isSameCashTender(existing, stackToAdd)
                    && existing.getCount() < CashStackBlock.MAX_STACK_COUNT) {
                ItemStack grown = existing.copy();
                grown.grow(1);
                bars.setStackInSlot(slot, grown);
                return true;
            }
            if (!existing.isEmpty()) {
                continue;
            }
            bars.setStackInSlot(slot, copySingle(stackToAdd));
            return true;
        }
        return false;
    }

    public boolean addMoneyStack(int mx, int mz, ItemStack stackToAdd) {
        if (!isMoneyStack(stackToAdd) || moneySlot(mx, mz, 0) < 0) {
            return false;
        }
        if (isMoneyBlockedByWide(mx, mz)) {
            return false;
        }
        for (int layer = 0; layer < MONEY_LAYERS; layer++) {
            int slot = moneySlot(mx, mz, layer);
            if (bars.getStackInSlot(slot).isEmpty()) {
                bars.setStackInSlot(slot, copySingle(stackToAdd));
                return true;
            }
        }
        return false;
    }

    public ItemStack removeWideTop(int wx, int wz) {
        int top = wideColumnTopLayer(wx, wz);
        return top < 0 ? ItemStack.EMPTY : takeAndCollapse(wideColumnSlots(wx, wz), top);
    }

    public ItemStack removeMoneyTop(int mx, int mz) {
        int top = moneyColumnTopLayer(mx, mz);
        return top < 0 ? ItemStack.EMPTY : takeAndCollapse(moneyColumnSlots(mx, mz), top);
    }

    public ItemStack removeWideAtLayer(int wx, int wz, int layer) {
        if (layer < 0 || layer >= WIDE_LAYERS || wideSlot(wx, wz, 0) < 0) {
            return ItemStack.EMPTY;
        }
        return takeAndCollapse(wideColumnSlots(wx, wz), layer);
    }

    public ItemStack removeMoneyAtLayer(int mx, int mz, int layer) {
        if (layer < 0 || layer >= MONEY_LAYERS || moneySlot(mx, mz, 0) < 0) {
            return ItemStack.EMPTY;
        }
        return takeAndCollapse(moneyColumnSlots(mx, mz), layer);
    }

    private int[] wideColumnSlots(int wx, int wz) {
        int[] slots = new int[WIDE_LAYERS];
        for (int layer = 0; layer < WIDE_LAYERS; layer++) {
            slots[layer] = wideSlot(wx, wz, layer);
        }
        return slots;
    }

    private int[] moneyColumnSlots(int mx, int mz) {
        int[] slots = new int[MONEY_LAYERS];
        for (int layer = 0; layer < MONEY_LAYERS; layer++) {
            slots[layer] = moneySlot(mx, mz, layer);
        }
        return slots;
    }

    /**
     * Removes the stack at the given layer and collapses everything above it one
     * layer down so the column never floats after a middle removal.
     */
    private ItemStack takeAndCollapse(int[] columnSlots, int layer) {
        ItemStack removed = bars.getStackInSlot(columnSlots[layer]);
        if (removed == null || removed.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = removed.copy();
        for (int l = layer; l < columnSlots.length - 1; l++) {
            ItemStack from = bars.getStackInSlot(columnSlots[l + 1]);
            bars.setStackInSlot(columnSlots[l], from == null ? ItemStack.EMPTY : from.copy());
        }
        bars.setStackInSlot(columnSlots[columnSlots.length - 1], ItemStack.EMPTY);
        return out;
    }

    public List<ItemStack> extractAllStacksForDrop() {
        List<ItemStack> drops = new ArrayList<>();
        for (int slot = 0; slot < bars.getSlots(); slot++) {
            ItemStack stack = bars.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            drops.add(stack.copy());
            bars.setStackInSlot(slot, ItemStack.EMPTY);
        }
        return drops;
    }

    /**
     * Builds a metal pallet ITEM pre-filled with {@code count} single-count
     * copies of {@code content} in ONE grid: the money grid (slots 72..455)
     * when {@code moneyGrid} is true, the wide bars/cash grid (slots 0..71)
     * otherwise. Slots are filled sequentially from the grid start, which is
     * layer-major: every full layer is completed bottom-up before the next
     * one starts. Because only a single grid is ever populated, the
     * cross-grid anti-collision invariant cannot be violated here, so no
     * conflict handling is needed. The handler serializes with
     * Size == SLOT_COUNT, so placement restores it without any legacy
     * migration (vanilla BlockItem.place -> updateCustomBlockEntityTag ->
     * CustomData.loadInto -> loadAdditional).
     */
    public static ItemStack createFilledPalletItem(ItemStack content,
                                                   boolean moneyGrid,
                                                   int count,
                                                   HolderLookup.Provider registries) {
        ItemStackHandler handler = new ItemStackHandler(SLOT_COUNT);
        int start = moneyGrid ? WIDE_SLOT_COUNT : 0;
        int capacity = moneyGrid ? MONEY_SLOT_COUNT : WIDE_SLOT_COUNT;
        int filled = Math.max(0, Math.min(count, capacity));
        for (int i = 0; i < filled; i++) {
            handler.setStackInSlot(start + i, copySingle(content));
        }
        CompoundTag tag = new CompoundTag();
        tag.put(STORAGE_KEY, handler.serializeNBT(registries));
        ItemStack palletStack = new ItemStack(ModBlocks.METAL_PALLET.get());
        BlockItem.setBlockEntityData(palletStack, ModBlockEntities.METAL_PALLET.get(), tag);
        return palletStack;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(STORAGE_KEY, bars.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.contains(STORAGE_KEY)) {
            CompoundTag storageTag = new CompoundTag();
            storageTag.putInt("Size", SLOT_COUNT);
            bars.deserializeNBT(registries, storageTag);
            return;
        }
        CompoundTag storageTag = tag.getCompound(STORAGE_KEY).copy();
        int savedSize = storageTag.getInt("Size");
        if (savedSize == LEGACY_V1_SLOT_COUNT || savedSize == LEGACY_V2_SLOT_COUNT) {
            // Migration chain, run exactly once: the migrated handler is saved
            // with Size == SLOT_COUNT on the next save. Deserialize the legacy
            // tag at its own size first so item identity (money vs wide) can
            // drive the remap. No item can be lost: the legacy formats hold at
            // most 144 occupied slots, the new handler has 456, and every remap
            // falls back to "first free slot anywhere" as a last resort.
            ItemStackHandler legacy = new ItemStackHandler(savedSize);
            legacy.deserializeNBT(registries, storageTag);
            CompoundTag fresh = new CompoundTag();
            fresh.putInt("Size", SLOT_COUNT);
            bars.deserializeNBT(registries, fresh);
            if (savedSize == LEGACY_V1_SLOT_COUNT) {
                migrateFromV1(legacy);
            } else {
                migrateFromV2(legacy);
            }
        } else {
            storageTag.putInt("Size", SLOT_COUNT);
            bars.deserializeNBT(registries, storageTag);
        }
    }

    /**
     * v1 layout (Size == 72): slot = layer * 12 + (z * 4 + x), identical to the
     * new wide grid, so wide content keeps its exact slot. Money-stack items in
     * v1 slots should not exist (pre-money era) but v2-era worlds may hold odd
     * data; they are moved to the nearest money position, layer-compacted.
     */
    private void migrateFromV1(ItemStackHandler legacy) {
        // Two passes (wide first) so money placement can see every migrated
        // wide column and relocate away from cross-grid conflicts.
        for (int pass = 0; pass < 2; pass++) {
            boolean moneyPass = pass == 1;
            for (int slot = 0; slot < legacy.getSlots(); slot++) {
                ItemStack stack = legacy.getStackInSlot(slot);
                if (stack.isEmpty() || isMoneyStack(stack) != moneyPass) {
                    continue;
                }
                int layer = slot / WIDE_POSITIONS;
                int position = slot % WIDE_POSITIONS;
                int wx = position % WIDE_GRID_X;
                int wz = position / WIDE_GRID_X;
                if (moneyPass) {
                    placeMoneyMigrated(wx * 2, legacyZToMoneyRow(wz), layer, stack);
                } else {
                    placeWideMigrated(wx, wz, layer, stack);
                }
            }
        }
    }

    /**
     * v2 layout (Size == 144): slot = layer * 24 + column, column = cz * 8 + cx,
     * with 8 X sub-columns (pairs sharing a wide position) and 3 Z rows.
     * Wide items -> wideSlot(cx / 2, cz, layer); money items -> the nearest new
     * money row via legacyZToMoneyRow, keeping the sub-column and layer.
     */
    private void migrateFromV2(ItemStackHandler legacy) {
        // Two passes (wide first) so money placement can see every migrated
        // wide column and relocate away from cross-grid conflicts (v2's pair
        // rule allowed near-overlaps between neighboring pairs).
        for (int pass = 0; pass < 2; pass++) {
            boolean moneyPass = pass == 1;
            for (int slot = 0; slot < legacy.getSlots(); slot++) {
                ItemStack stack = legacy.getStackInSlot(slot);
                if (stack.isEmpty() || isMoneyStack(stack) != moneyPass) {
                    continue;
                }
                int layer = slot / LEGACY_V2_COLUMNS;
                int column = slot % LEGACY_V2_COLUMNS;
                int cx = column % LEGACY_V2_GRID_X;
                int cz = column / LEGACY_V2_GRID_X;
                if (moneyPass) {
                    placeMoneyMigrated(cx, legacyZToMoneyRow(cz), layer, stack);
                } else {
                    placeWideMigrated(cx / 2, cz, layer, stack);
                }
            }
        }
    }

    /**
     * Legacy Z rows sat at -1.0 / 0.0 / +1.0 blocks; the new money rows sit at
     * -1.13625 / -0.37875 / +0.37875 / +1.13625. Nearest mapping: 0->0, 1->1
     * (0.0 is equidistant to rows 1 and 2; row 1 is the stable pick), 2->3.
     */
    private static int legacyZToMoneyRow(int legacyZ) {
        return legacyZ <= 0 ? 0 : (legacyZ == 1 ? 1 : 3);
    }

    private void placeMoneyMigrated(int mx, int mz, int preferredLayer, ItemStack stack) {
        // Runs after the wide pass: prefer the mapped position only when it does
        // not conflict with migrated wide content, otherwise relocate to the
        // nearest conflict-free money position so the cross-grid invariant holds
        // immediately on load. Item preservation still outranks the invariant.
        if (!isMoneyBlockedByWide(mx, mz)) {
            int preferred = moneySlot(mx, mz, preferredLayer);
            if (preferred >= 0 && bars.getStackInSlot(preferred).isEmpty()) {
                bars.setStackInSlot(preferred, stack.copy());
                return;
            }
            for (int layer = 0; layer < MONEY_LAYERS; layer++) {
                int slot = moneySlot(mx, mz, layer);
                if (slot >= 0 && bars.getStackInSlot(slot).isEmpty()) {
                    bars.setStackInSlot(slot, stack.copy());
                    return;
                }
            }
        }
        int bestX = -1;
        int bestZ = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int pz = 0; pz < MONEY_GRID_Z; pz++) {
            for (int px = 0; px < MONEY_GRID_X; px++) {
                if ((px == mx && pz == mz) || isMoneyBlockedByWide(px, pz)) {
                    continue;
                }
                boolean hasRoom = false;
                for (int layer = 0; layer < MONEY_LAYERS; layer++) {
                    int slot = moneySlot(px, pz, layer);
                    if (slot >= 0 && bars.getStackInSlot(slot).isEmpty()) {
                        hasRoom = true;
                        break;
                    }
                }
                if (!hasRoom) {
                    continue;
                }
                int distance = Math.abs(px - mx) + Math.abs(pz - mz);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestX = px;
                    bestZ = pz;
                }
            }
        }
        if (bestX >= 0) {
            for (int layer = 0; layer < MONEY_LAYERS; layer++) {
                int slot = moneySlot(bestX, bestZ, layer);
                if (slot >= 0 && bars.getStackInSlot(slot).isEmpty()) {
                    bars.setStackInSlot(slot, stack.copy());
                    return;
                }
            }
        }
        placeInRangeOrAnywhere(stack, WIDE_SLOT_COUNT, SLOT_COUNT);
    }

    private void placeWideMigrated(int wx, int wz, int preferredLayer, ItemStack stack) {
        int preferred = wideSlot(wx, wz, preferredLayer);
        if (preferred >= 0 && bars.getStackInSlot(preferred).isEmpty()) {
            bars.setStackInSlot(preferred, stack.copy());
            return;
        }
        for (int layer = 0; layer < WIDE_LAYERS; layer++) {
            int slot = wideSlot(wx, wz, layer);
            if (slot >= 0 && bars.getStackInSlot(slot).isEmpty()) {
                bars.setStackInSlot(slot, stack.copy());
                return;
            }
        }
        placeInRangeOrAnywhere(stack, 0, WIDE_SLOT_COUNT);
    }

    private void placeInRangeOrAnywhere(ItemStack stack, int rangeStart, int rangeEnd) {
        for (int slot = rangeStart; slot < rangeEnd; slot++) {
            if (bars.getStackInSlot(slot).isEmpty()) {
                bars.setStackInSlot(slot, stack.copy());
                return;
            }
        }
        for (int slot = 0; slot < bars.getSlots(); slot++) {
            if (bars.getStackInSlot(slot).isEmpty()) {
                bars.setStackInSlot(slot, stack.copy());
                return;
            }
        }
        // Unreachable: legacy formats hold at most 144 stacks, we have 456 slots.
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static ItemStack copySingle(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private static boolean isSameCashTender(ItemStack left, ItemStack right) {
        return isCashTender(left)
                && isCashTender(right)
                && ItemStack.isSameItemSameComponents(left, right);
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    // ------------------------------------------------------------------
    // Chunk-baked content rendering (client model data)
    // ------------------------------------------------------------------

    // Content render geometry: cargo sits on the 5px deck; wide (bar/cash)
    // layers step 4px, money bundles 2px. Empty layers still advance the step
    // (pure columns, exactly like the old per-frame renderer did). Must stay
    // in sync with MetalPalletBlock's shape/hit math.
    public static final float CONTENT_BASE_Y = 5.0F / 16.0F;
    public static final float WIDE_STEP_Y = 4.0F / 16.0F;
    public static final float MONEY_STEP_Y = 2.0F / 16.0F;

    /**
     * Maps a stored pallet stack to the {@link BlockState} rendered for it.
     * Common-side safe (pure BlockState math); shared by the client content
     * snapshot below and any future users.
     */
    public static @Nullable BlockState renderStateFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.is(ModBlocks.SILVER_BAR.get().asItem())) {
            return ModBlocks.SILVER_BAR.get().defaultBlockState();
        }
        if (stack.is(ModBlocks.GOLD_BAR.get().asItem())) {
            return ModBlocks.GOLD_BAR.get().defaultBlockState();
        }
        CashStackBlock.CashKind cashKind = CashStackBlock.CashKind.fromItem(stack.getItem());
        if (cashKind != null) {
            int count = Math.max(1, Math.min(CashStackBlock.MAX_STACK_COUNT, stack.getCount()));
            return CashStackBlock.stateForPlacement(cashKind, Direction.NORTH)
                    .setValue(CashStackBlock.COUNT, count);
        }
        MoneyStackBlock.BillDenomination denomination =
                MoneyStackBlock.BillDenomination.fromStackItem(stack.getItem());
        if (denomination != null) {
            // One bundle per pallet slot (bar-style); COUNT stays 1 from stateForPlacement.
            // EAST = 90-degree rotation on pallets so bundles run along the pallet depth.
            return MoneyStackBlock.stateForPlacement(denomination, Direction.EAST);
        }
        return null;
    }

    /**
     * Client hook (never invoked on the dedicated server): feeds the content
     * snapshot to MetalPalletContentsBakedModel so the cargo is baked into the
     * static chunk mesh instead of being re-tessellated every frame.
     */
    @Override
    public ModelData getModelData() {
        return ModelData.of(MetalPalletModelData.CONTENTS, buildContentsSnapshot());
    }

    private MetalPalletModelData.ContentsSnapshot buildContentsSnapshot() {
        List<MetalPalletModelData.ContentEntry> entries = new ArrayList<>();
        for (int wz = 0; wz < WIDE_GRID_Z; wz++) {
            for (int wx = 0; wx < WIDE_GRID_X; wx++) {
                float xOff = (float) wideCenterX(wx);
                float zOff = (float) wideCenterZ(wz);
                float yOff = CONTENT_BASE_Y;
                for (int layer = 0; layer < WIDE_LAYERS; layer++) {
                    addContentEntry(entries, getWideStack(wx, wz, layer), xOff, yOff, zOff);
                    yOff += WIDE_STEP_Y;
                }
            }
        }
        for (int mz = 0; mz < MONEY_GRID_Z; mz++) {
            for (int mx = 0; mx < MONEY_GRID_X; mx++) {
                float xOff = (float) moneyCenterX(mx);
                float zOff = (float) moneyCenterZ(mz);
                float yOff = CONTENT_BASE_Y;
                for (int layer = 0; layer < MONEY_LAYERS; layer++) {
                    addContentEntry(entries, getMoneyStack(mx, mz, layer), xOff, yOff, zOff);
                    yOff += MONEY_STEP_Y;
                }
            }
        }
        return new MetalPalletModelData.ContentsSnapshot(entries);
    }

    private static void addContentEntry(List<MetalPalletModelData.ContentEntry> entries,
                                        ItemStack stack,
                                        float xOff,
                                        float yOff,
                                        float zOff) {
        BlockState renderState = renderStateFor(stack);
        if (renderState != null) {
            entries.add(new MetalPalletModelData.ContentEntry(xOff, yOff, zOff, renderState));
        }
    }

    /**
     * Live content updates reach the client through
     * ClientPacketListener.handleBlockEntityData -> onDataPacket (default impl
     * loads the tag via loadWithComponents).
     */
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        super.onDataPacket(net, pkt, lookupProvider);
        refreshClientContentsModel();
    }

    /**
     * Initial chunk-load update tags reach the client through
     * LevelChunk.replaceWithPacketData's BlockEntityTagOutput consumer ->
     * handleUpdateTag (default impl loads the tag via loadWithComponents).
     */
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        super.handleUpdateTag(tag, lookupProvider);
        refreshClientContentsModel();
    }

    /**
     * Client re-mesh pitfall: after content data lands client-side the model
     * data must be refreshed AND the render section dirtied, otherwise pallets
     * only update on F3+A. ClientLevel.sendBlockUpdated ignores the flags and
     * forwards to LevelRenderer.blockChanged, which marks the section dirty.
     */
    private void refreshClientContentsModel() {
        if (level == null || !level.isClientSide()) {
            return;
        }
        requestModelDataUpdate();
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, 8);
    }
}
