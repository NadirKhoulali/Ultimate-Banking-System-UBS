package net.austizz.ultimatebankingsystem.heist;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.UUID;

public final class HeistLootJournalEntry {
    public enum SourceType {
        METAL_PALLET,
        SECURE_SAFE,
        SAFE_BOX_ACCOUNT,
        WORLD_BLOCK;

        static SourceType byName(String value) {
            try {
                return valueOf(value == null ? "" : value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return WORLD_BLOCK;
            }
        }
    }

    private final UUID entryId;
    private final SourceType sourceType;
    private final String dimension;
    private final BlockPos sourcePos;
    private final int sourceSlot;
    private final UUID accountId;
    private final UUID bagId;
    private final long valueCents;
    private final CompoundTag stackTag;
    private final CompoundTag blockStateTag;
    private boolean restored;
    private boolean committed;

    public HeistLootJournalEntry(UUID entryId,
                                 SourceType sourceType,
                                 String dimension,
                                 BlockPos sourcePos,
                                 int sourceSlot,
                                 UUID accountId,
                                 UUID bagId,
                                 long valueCents,
                                 CompoundTag stackTag,
                                 CompoundTag blockStateTag) {
        this.entryId = entryId == null ? UUID.randomUUID() : entryId;
        this.sourceType = sourceType == null ? SourceType.WORLD_BLOCK : sourceType;
        this.dimension = dimension == null ? "" : dimension.trim().toLowerCase(Locale.ROOT);
        this.sourcePos = sourcePos == null ? BlockPos.ZERO : sourcePos.immutable();
        this.sourceSlot = sourceSlot;
        this.accountId = accountId;
        this.bagId = bagId;
        this.valueCents = Math.max(0L, valueCents);
        this.stackTag = stackTag == null ? new CompoundTag() : stackTag.copy();
        this.blockStateTag = blockStateTag == null ? new CompoundTag() : blockStateTag.copy();
    }

    public UUID entryId() { return entryId; }
    public SourceType sourceType() { return sourceType; }
    public String dimension() { return dimension; }
    public BlockPos sourcePos() { return sourcePos; }
    public int sourceSlot() { return sourceSlot; }
    public UUID accountId() { return accountId; }
    public UUID bagId() { return bagId; }
    public long valueCents() { return valueCents; }
    public CompoundTag stackTag() { return stackTag.copy(); }
    public CompoundTag blockStateTag() { return blockStateTag.copy(); }
    public boolean restored() { return restored; }
    public boolean committed() { return committed; }
    public void markRestored() { restored = true; }
    public void markCommitted() { committed = true; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", entryId);
        tag.putString("type", sourceType.name());
        tag.putString("dimension", dimension);
        tag.putLong("pos", sourcePos.asLong());
        tag.putInt("slot", sourceSlot);
        if (accountId != null) tag.putUUID("account", accountId);
        if (bagId != null) tag.putUUID("bag", bagId);
        tag.putLong("value_cents", valueCents);
        tag.put("stack", stackTag.copy());
        if (!blockStateTag.isEmpty()) tag.put("block_state", blockStateTag.copy());
        tag.putBoolean("restored", restored);
        tag.putBoolean("committed", committed);
        return tag;
    }

    public static HeistLootJournalEntry load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("id")) {
            return null;
        }
        HeistLootJournalEntry entry = new HeistLootJournalEntry(
                tag.getUUID("id"),
                SourceType.byName(tag.getString("type")),
                tag.getString("dimension"),
                BlockPos.of(tag.getLong("pos")),
                tag.getInt("slot"),
                tag.hasUUID("account") ? tag.getUUID("account") : null,
                tag.hasUUID("bag") ? tag.getUUID("bag") : null,
                tag.getLong("value_cents"),
                tag.getCompound("stack"),
                tag.getCompound("block_state")
        );
        entry.restored = tag.getBoolean("restored");
        entry.committed = tag.getBoolean("committed");
        return entry;
    }
}
