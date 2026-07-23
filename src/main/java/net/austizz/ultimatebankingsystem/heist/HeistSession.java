package net.austizz.ultimatebankingsystem.heist;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HeistSession {
    private final UUID id;
    private UUID leaderId;
    private UUID bankId;
    private String bankName = "";
    private String premiseId = "";
    private SafeBlockBounds premiseBounds;
    private SafeExitSnapshot exit;
    private HeistPhase phase = HeistPhase.PLANNING;
    private final LinkedHashMap<UUID, HeistMember> members = new LinkedHashMap<>();
    private final ArrayList<HeistLootJournalEntry> lootJournal = new ArrayList<>();
    private final LinkedHashSet<UUID> cancelVotes = new LinkedHashSet<>();
    private final LinkedHashSet<String> breachedTargets = new LinkedHashSet<>();
    private long createdAtMillis;
    private long countdownEndsAtTick;
    private long startedAtMillis;
    private long startedAtTick;
    private long deadlineTick;
    private long extractionStartedTick;
    private long totalLootCents;
    private boolean lootArmed;
    private boolean alarmed;
    private String alarmReason = "";
    private UUID drillOwnerId;
    private CompoundTag drillStackTag = new CompoundTag();
    private boolean drillReturned;
    private final LinkedHashMap<String, DrillState> vaultDrills = new LinkedHashMap<>();
    private final LinkedHashMap<String, DrillState> safeDrills = new LinkedHashMap<>();
    private UUID hackedAccountId;
    private UUID hackedPlayerId;
    private BigDecimal hackedAmount = BigDecimal.ZERO;
    private String hackedBankName = "";
    private boolean hackCompleted;
    private final LinkedHashMap<String, HackState> activeHacks = new LinkedHashMap<>();
    private final LinkedHashSet<String> hackedComputers = new LinkedHashSet<>();
    private final ArrayList<HackTransfer> hackTransfers = new ArrayList<>();

    public HeistSession(UUID id, UUID leaderId, String leaderName) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.leaderId = leaderId;
        this.createdAtMillis = System.currentTimeMillis();
        if (leaderId != null) {
            HeistMember leader = new HeistMember(leaderId, leaderName, true);
            members.put(leaderId, leader);
        }
    }

    public UUID id() { return id; }
    public UUID leaderId() { return leaderId; }
    public UUID bankId() { return bankId; }
    public String bankName() { return bankName; }
    public String premiseId() { return premiseId; }
    public SafeBlockBounds premiseBounds() { return premiseBounds; }
    public SafeExitSnapshot exit() { return exit; }
    public HeistPhase phase() { return phase; }
    public Map<UUID, HeistMember> members() { return Map.copyOf(members); }
    public HeistMember member(UUID playerId) { return members.get(playerId); }
    public List<HeistLootJournalEntry> lootJournal() { return List.copyOf(lootJournal); }
    public Set<UUID> cancelVotes() { return Set.copyOf(cancelVotes); }
    public Set<String> breachedTargets() { return Set.copyOf(breachedTargets); }
    public long createdAtMillis() { return createdAtMillis; }
    public long countdownEndsAtTick() { return countdownEndsAtTick; }
    public long startedAtMillis() { return startedAtMillis; }
    public long startedAtTick() { return startedAtTick; }
    public long deadlineTick() { return deadlineTick; }
    public long extractionStartedTick() { return extractionStartedTick; }
    public long totalLootCents() { return totalLootCents; }
    public boolean lootArmed() { return lootArmed; }
    public boolean alarmed() { return alarmed; }
    public String alarmReason() { return alarmReason; }
    public UUID drillOwnerId() { return drillOwnerId; }
    public CompoundTag drillStackTag() { return drillStackTag.copy(); }
    public boolean drillReturned() { return drillReturned; }
    public List<DrillState> vaultDrills() { return List.copyOf(vaultDrills.values()); }
    public List<DrillState> safeDrills() { return List.copyOf(safeDrills.values()); }
    public DrillState vaultDrill(String dimension, BlockPos pos) { return vaultDrills.get(targetKey(dimension, pos)); }
    public DrillState safeDrill(String dimension, BlockPos pos) { return safeDrills.get(targetKey(dimension, pos)); }
    public UUID hackedAccountId() { return hackedAccountId; }
    public UUID hackedPlayerId() { return hackedPlayerId; }
    public BigDecimal hackedAmount() { return hackedAmount; }
    public String hackedBankName() { return hackedBankName; }
    public boolean hackCompleted() { return hackCompleted; }
    public List<HackState> activeHacks() { return List.copyOf(activeHacks.values()); }
    public int hackedComputerCount() { return hackedComputers.size(); }
    public HackState activeHack(String dimension, BlockPos pos) { return activeHacks.get(targetKey(dimension, pos)); }
    public List<HackTransfer> hackTransfers() { return List.copyOf(hackTransfers); }
    public Set<UUID> hackedAccountIds() {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (HackTransfer transfer : hackTransfers) {
            if (transfer.accountId() != null) ids.add(transfer.accountId());
        }
        return Set.copyOf(ids);
    }
    public boolean isComputerHacked(String dimension, BlockPos pos) {
        return pos != null && hackedComputers.contains(hackKey(dimension, pos));
    }

    public void selectTarget(UUID bankId, String bankName, String premiseId,
                             SafeBlockBounds bounds, SafeExitSnapshot exit) {
        this.bankId = bankId;
        this.bankName = clean(bankName, 80);
        this.premiseId = clean(premiseId, 80);
        this.premiseBounds = bounds;
        this.exit = exit;
        members.values().forEach(member -> member.setReady(false));
    }
    public void setPhase(HeistPhase phase) { this.phase = phase == null ? HeistPhase.PLANNING : phase; }
    public void putMember(HeistMember member) { if (member != null) members.put(member.playerId(), member); }
    public void removeMember(UUID playerId) {
        if (playerId == null || members.remove(playerId) == null) return;
        cancelVotes.remove(playerId);
        if (playerId.equals(leaderId)) {
            leaderId = HeistCrewLeadership.chooseSuccessor(members.values().stream()
                    .map(member -> new HeistCrewLeadership.Candidate(
                            member.playerId(), member.accepted()))
                    .toList());
        }
    }
    public void addJournal(HeistLootJournalEntry entry) {
        if (entry != null) {
            lootJournal.add(entry);
            totalLootCents = Math.max(0L, totalLootCents + entry.valueCents());
            lootArmed = true;
        }
    }
    public void rollbackJournal(UUID entryId) {
        if (entryId == null) return;
        for (int index = lootJournal.size() - 1; index >= 0; index--) {
            HeistLootJournalEntry entry = lootJournal.get(index);
            if (!entryId.equals(entry.entryId())) continue;
            lootJournal.remove(index);
            totalLootCents = Math.max(0L, totalLootCents - Math.max(0L, entry.valueCents()));
            lootArmed = hackCompleted || !lootJournal.isEmpty();
            return;
        }
    }
    public void addCancelVote(UUID playerId) { if (playerId != null) cancelVotes.add(playerId); }
    public void clearCancelVotes() { cancelVotes.clear(); }
    public void breach(String targetKey) { if (targetKey != null && !targetKey.isBlank()) breachedTargets.add(targetKey); }
    public boolean isBreached(String targetKey) { return breachedTargets.contains(targetKey); }
    public void startCountdown(long endTick) { phase = HeistPhase.COUNTDOWN; countdownEndsAtTick = endTick; }
    public void start(long tick, long durationTicks) {
        phase = HeistPhase.CASING;
        startedAtMillis = System.currentTimeMillis();
        startedAtTick = tick;
        deadlineTick = tick + Math.max(20L, durationTicks);
        countdownEndsAtTick = 0L;
    }
    public void setExtractionStartedTick(long tick) { extractionStartedTick = Math.max(0L, tick); }
    public void armLoot() { lootArmed = true; }
    public void addLootValue(long cents) { totalLootCents = Math.max(0L, totalLootCents + Math.max(0L, cents)); }
    public void alarm(String reason) {
        alarmed = true;
        if (alarmReason.isBlank()) alarmReason = clean(reason, 160);
        if (phase == HeistPhase.CASING) phase = HeistPhase.ACTIVE;
    }
    public void escrowDrill(UUID owner, CompoundTag stack) {
        drillOwnerId = owner;
        drillStackTag = stack == null ? new CompoundTag() : stack.copy();
        drillReturned = false;
    }
    public boolean hasEscrowedDrill() {
        return !drillReturned && drillOwnerId != null && !drillStackTag.isEmpty();
    }
    public DrillEscrow takeEscrowedDrill() {
        if (!hasEscrowedDrill()) return null;
        DrillEscrow escrow = new DrillEscrow(drillOwnerId, drillStackTag);
        drillOwnerId = null;
        drillStackTag = new CompoundTag();
        drillReturned = true;
        return escrow;
    }
    public boolean deployDrill(UUID owner, CompoundTag stack, String dimension, BlockPos pos,
                               long finishTick, int jams) {
        if (pos == null) return false;
        return vaultDrills.putIfAbsent(targetKey(dimension, pos),
                new DrillState(owner, stack, dimension, pos, finishTick, jams, 0L, false)) == null;
    }
    public DrillState removeDrill(String dimension, BlockPos pos) {
        return vaultDrills.remove(targetKey(dimension, pos));
    }
    public boolean deploySafeDrill(UUID owner, CompoundTag stack, String dimension, BlockPos pos,
                                   long finishTick, int jams) {
        if (pos == null) return false;
        return safeDrills.putIfAbsent(targetKey(dimension, pos),
                new DrillState(owner, stack, dimension, pos, finishTick, jams, 0L, false)) == null;
    }
    public DrillState removeSafeDrill(String dimension, BlockPos pos) {
        return safeDrills.remove(targetKey(dimension, pos));
    }
    public void clearDrills() {
        vaultDrills.clear();
        safeDrills.clear();
    }
    public boolean beginHack(String dimension, BlockPos pos, long finishTick, long pausedUntilTick) {
        if (pos == null) return false;
        return activeHacks.putIfAbsent(targetKey(dimension, pos),
                new HackState(dimension, pos, finishTick, pausedUntilTick)) == null;
    }
    public void resumeHack(String dimension, BlockPos pos, long finishTick) {
        HackState state = activeHack(dimension, pos);
        if (state != null) state.resume(finishTick);
    }
    public void completeHack(String dimension, BlockPos pos, UUID accountId, UUID playerId,
                             BigDecimal amount, String bankName) {
        hackedAccountId = accountId;
        hackedPlayerId = playerId;
        hackedAmount = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
        hackedBankName = clean(bankName, 80);
        hackCompleted = hackedAmount.signum() > 0;
        if (hackCompleted) {
            hackTransfers.add(new HackTransfer(accountId, playerId, hackedAmount, hackedBankName));
            lootArmed = true;
            addLootValue(hackedAmount.movePointRight(2).longValue());
        }
        completeActiveComputer(dimension, pos);
    }
    public void completeHackWithoutTransfer(String dimension, BlockPos pos) {
        completeActiveComputer(dimension, pos);
    }
    private void completeActiveComputer(String dimension, BlockPos pos) {
        if (pos != null) hackedComputers.add(hackKey(dimension, pos));
        activeHacks.remove(targetKey(dimension, pos));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        if (leaderId != null) tag.putUUID("leader", leaderId);
        if (bankId != null) tag.putUUID("bank", bankId);
        tag.putString("bank_name", bankName);
        tag.putString("premise", premiseId);
        if (premiseBounds != null) tag.put("bounds", saveBounds(premiseBounds));
        if (exit != null) tag.put("exit", saveExit(exit));
        tag.putString("phase", phase.name());
        tag.putLong("created_at", createdAtMillis);
        tag.putLong("countdown_end", countdownEndsAtTick);
        tag.putLong("started_at_millis", startedAtMillis);
        tag.putLong("started_at_tick", startedAtTick);
        tag.putLong("deadline", deadlineTick);
        tag.putLong("extraction_started", extractionStartedTick);
        tag.putLong("loot_cents", totalLootCents);
        tag.putBoolean("loot_armed", lootArmed);
        tag.putBoolean("alarmed", alarmed);
        tag.putString("alarm_reason", alarmReason);
        ListTag memberTags = new ListTag();
        members.values().forEach(member -> memberTags.add(member.save()));
        tag.put("members", memberTags);
        ListTag journalTags = new ListTag();
        lootJournal.forEach(entry -> journalTags.add(entry.save()));
        tag.put("journal", journalTags);
        tag.put("cancel_votes", saveUuids(cancelVotes));
        ListTag breached = new ListTag();
        for (String value : breachedTargets) {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", value);
            breached.add(entry);
        }
        tag.put("breached", breached);
        if (drillOwnerId != null) tag.putUUID("drill_owner", drillOwnerId);
        if (!drillStackTag.isEmpty()) tag.put("drill_stack", drillStackTag.copy());
        tag.putBoolean("drill_returned", drillReturned);
        ListTag vaultDrillTags = new ListTag();
        vaultDrills.values().forEach(state -> vaultDrillTags.add(state.save()));
        tag.put("vault_drills", vaultDrillTags);
        ListTag safeDrillTags = new ListTag();
        safeDrills.values().forEach(state -> safeDrillTags.add(state.save()));
        tag.put("safe_drills", safeDrillTags);
        if (hackedAccountId != null) tag.putUUID("hack_account", hackedAccountId);
        if (hackedPlayerId != null) tag.putUUID("hack_player", hackedPlayerId);
        tag.putString("hack_amount", hackedAmount.toPlainString());
        tag.putString("hack_bank", hackedBankName);
        tag.putBoolean("hack_complete", hackCompleted);
        ListTag activeHackTags = new ListTag();
        activeHacks.values().forEach(state -> activeHackTags.add(state.save()));
        tag.put("active_hacks", activeHackTags);
        ListTag hackedPcTags = new ListTag();
        for (String key : hackedComputers) {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", key);
            hackedPcTags.add(entry);
        }
        tag.put("hacked_computers", hackedPcTags);
        ListTag transferTags = new ListTag();
        for (HackTransfer transfer : hackTransfers) transferTags.add(transfer.save());
        tag.put("hack_transfers", transferTags);
        return tag;
    }

    public static HeistSession load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("id") || !tag.hasUUID("leader")) return null;
        HeistSession session = new HeistSession(tag.getUUID("id"), tag.getUUID("leader"), "");
        session.bankId = tag.hasUUID("bank") ? tag.getUUID("bank") : null;
        session.bankName = tag.getString("bank_name");
        session.premiseId = tag.getString("premise");
        session.premiseBounds = tag.contains("bounds", Tag.TAG_COMPOUND) ? loadBounds(tag.getCompound("bounds")) : null;
        session.exit = tag.contains("exit", Tag.TAG_COMPOUND) ? loadExit(tag.getCompound("exit")) : null;
        session.phase = HeistPhase.byName(tag.getString("phase"));
        session.createdAtMillis = tag.getLong("created_at");
        session.countdownEndsAtTick = tag.getLong("countdown_end");
        session.startedAtMillis = tag.getLong("started_at_millis");
        session.startedAtTick = tag.getLong("started_at_tick");
        session.deadlineTick = tag.getLong("deadline");
        session.extractionStartedTick = tag.getLong("extraction_started");
        session.totalLootCents = Math.max(0L, tag.getLong("loot_cents"));
        session.lootArmed = tag.getBoolean("loot_armed");
        session.alarmed = tag.getBoolean("alarmed");
        session.alarmReason = tag.getString("alarm_reason");
        session.members.clear();
        ListTag members = tag.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            HeistMember member = HeistMember.load(members.getCompound(i));
            if (member != null) session.members.put(member.playerId(), member);
        }
        ListTag journal = tag.getList("journal", Tag.TAG_COMPOUND);
        for (int i = 0; i < journal.size(); i++) {
            HeistLootJournalEntry entry = HeistLootJournalEntry.load(journal.getCompound(i));
            if (entry != null) session.lootJournal.add(entry);
        }
        loadUuids(tag.getList("cancel_votes", Tag.TAG_COMPOUND), session.cancelVotes);
        ListTag breached = tag.getList("breached", Tag.TAG_COMPOUND);
        for (int i = 0; i < breached.size(); i++) {
            String key = breached.getCompound(i).getString("key");
            if (!key.isBlank()) session.breachedTargets.add(key);
        }
        session.drillOwnerId = tag.hasUUID("drill_owner") ? tag.getUUID("drill_owner") : null;
        session.drillStackTag = tag.getCompound("drill_stack").copy();
        session.drillReturned = tag.getBoolean("drill_returned");
        ListTag vaultDrillTags = tag.getList("vault_drills", Tag.TAG_COMPOUND);
        for (int i = 0; i < vaultDrillTags.size(); i++) {
            DrillState state = DrillState.load(vaultDrillTags.getCompound(i));
            if (state != null) session.vaultDrills.put(targetKey(state.dimension(), state.pos()), state);
        }
        ListTag safeDrillTags = tag.getList("safe_drills", Tag.TAG_COMPOUND);
        for (int i = 0; i < safeDrillTags.size(); i++) {
            DrillState state = DrillState.load(safeDrillTags.getCompound(i));
            if (state != null) session.safeDrills.put(targetKey(state.dimension(), state.pos()), state);
        }
        if (session.vaultDrills.isEmpty() && tag.contains("drill_pos") && !tag.getBoolean("drill_recovered")) {
            String dimension = tag.getString("drill_dimension");
            BlockPos pos = tag.contains("vault_pos")
                    ? BlockPos.of(tag.getLong("vault_pos")) : BlockPos.of(tag.getLong("drill_pos"));
            DrillState state = new DrillState(session.drillOwnerId, session.drillStackTag, dimension, pos,
                    tag.getLong("drill_finish"), tag.getInt("drill_jams"),
                    tag.getLong("drill_jammed"), tag.getBoolean("drill_complete"));
            session.vaultDrills.put(targetKey(dimension, pos), state);
            session.drillOwnerId = null;
            session.drillStackTag = new CompoundTag();
            session.drillReturned = true;
        }
        if (session.safeDrills.isEmpty() && tag.contains("safe_drill_pos")) {
            String dimension = tag.getString("safe_drill_dimension");
            BlockPos pos = BlockPos.of(tag.getLong("safe_drill_pos"));
            DrillState state = new DrillState(
                    tag.hasUUID("safe_drill_owner") ? tag.getUUID("safe_drill_owner") : null,
                    tag.getCompound("safe_drill_stack"), dimension, pos,
                    tag.getLong("safe_drill_finish"), tag.getInt("safe_drill_jams"),
                    tag.getLong("safe_drill_jammed"), tag.getBoolean("safe_drill_complete"));
            session.safeDrills.put(targetKey(dimension, pos), state);
        }
        session.hackedAccountId = tag.hasUUID("hack_account") ? tag.getUUID("hack_account") : null;
        session.hackedPlayerId = tag.hasUUID("hack_player") ? tag.getUUID("hack_player") : null;
        session.hackedAmount = decimal(tag.getString("hack_amount"));
        session.hackedBankName = tag.getString("hack_bank");
        session.hackCompleted = tag.getBoolean("hack_complete");
        ListTag activeHackTags = tag.getList("active_hacks", Tag.TAG_COMPOUND);
        for (int i = 0; i < activeHackTags.size(); i++) {
            HackState state = HackState.load(activeHackTags.getCompound(i));
            if (state != null) session.activeHacks.put(targetKey(state.dimension(), state.pos()), state);
        }
        if (session.activeHacks.isEmpty() && tag.contains("hack_pos")) {
            String dimension = tag.getString("hack_dimension");
            BlockPos pos = BlockPos.of(tag.getLong("hack_pos"));
            HackState state = new HackState(dimension, pos,
                    tag.getLong("hack_finish"), tag.getLong("hack_paused"));
            session.activeHacks.put(targetKey(dimension, pos), state);
        }
        ListTag hackedPcTags = tag.getList("hacked_computers", Tag.TAG_COMPOUND);
        for (int i = 0; i < hackedPcTags.size(); i++) {
            String key = hackedPcTags.getCompound(i).getString("key");
            if (!key.isBlank()) session.hackedComputers.add(key);
        }
        ListTag transferTags = tag.getList("hack_transfers", Tag.TAG_COMPOUND);
        for (int i = 0; i < transferTags.size(); i++) {
            HackTransfer transfer = HackTransfer.load(transferTags.getCompound(i));
            if (transfer != null) session.hackTransfers.add(transfer);
        }
        if (session.hackTransfers.isEmpty() && session.hackCompleted
                && session.hackedAccountId != null && session.hackedAmount.signum() > 0) {
            session.hackTransfers.add(new HackTransfer(session.hackedAccountId, session.hackedPlayerId,
                    session.hackedAmount, session.hackedBankName));
        }
        return session;
    }

    public record DrillEscrow(UUID ownerId, CompoundTag stackTag) {
        public DrillEscrow {
            stackTag = stackTag == null ? new CompoundTag() : stackTag.copy();
        }

        @Override
        public CompoundTag stackTag() {
            return stackTag.copy();
        }
    }

    public static final class DrillState {
        private final UUID ownerId;
        private final CompoundTag stackTag;
        private final String dimension;
        private final BlockPos pos;
        private long finishesTick;
        private int jamsRemaining;
        private long jammedAtTick;
        private boolean completed;

        DrillState(UUID ownerId, CompoundTag stackTag, String dimension, BlockPos pos,
                   long finishesTick, int jamsRemaining, long jammedAtTick, boolean completed) {
            this.ownerId = ownerId;
            this.stackTag = stackTag == null ? new CompoundTag() : stackTag.copy();
            this.dimension = clean(dimension, 128);
            this.pos = pos == null ? BlockPos.ZERO : pos.immutable();
            this.finishesTick = Math.max(0L, finishesTick);
            this.jamsRemaining = Math.max(0, jamsRemaining);
            this.jammedAtTick = Math.max(0L, jammedAtTick);
            this.completed = completed;
        }

        public UUID ownerId() { return ownerId; }
        public CompoundTag stackTag() { return stackTag.copy(); }
        public String dimension() { return dimension; }
        public BlockPos pos() { return pos; }
        public long finishesTick() { return finishesTick; }
        public int jamsRemaining() { return jamsRemaining; }
        public long jammedAtTick() { return jammedAtTick; }
        public boolean completed() { return completed; }
        public void setJammed(long tick) { jammedAtTick = Math.max(0L, tick); }
        public void restart(long finishTick) {
            jammedAtTick = 0L;
            jamsRemaining = Math.max(0, jamsRemaining - 1);
            finishesTick = Math.max(0L, finishTick);
        }
        public void complete() {
            completed = true;
            jammedAtTick = 0L;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (ownerId != null) tag.putUUID("owner", ownerId);
            if (!stackTag.isEmpty()) tag.put("stack", stackTag.copy());
            tag.putString("dimension", dimension);
            tag.putLong("pos", pos.asLong());
            tag.putLong("finish", finishesTick);
            tag.putInt("jams", jamsRemaining);
            tag.putLong("jammed", jammedAtTick);
            tag.putBoolean("complete", completed);
            return tag;
        }

        static DrillState load(CompoundTag tag) {
            if (tag == null || !tag.contains("pos")) return null;
            return new DrillState(tag.hasUUID("owner") ? tag.getUUID("owner") : null,
                    tag.getCompound("stack"), tag.getString("dimension"), BlockPos.of(tag.getLong("pos")),
                    tag.getLong("finish"), tag.getInt("jams"), tag.getLong("jammed"),
                    tag.getBoolean("complete"));
        }
    }

    public static final class HackState {
        private final String dimension;
        private final BlockPos pos;
        private long finishesTick;
        private long pausedUntilTick;

        HackState(String dimension, BlockPos pos, long finishesTick, long pausedUntilTick) {
            this.dimension = clean(dimension, 128);
            this.pos = pos == null ? BlockPos.ZERO : pos.immutable();
            this.finishesTick = Math.max(0L, finishesTick);
            this.pausedUntilTick = Math.max(0L, pausedUntilTick);
        }

        public String dimension() { return dimension; }
        public BlockPos pos() { return pos; }
        public long finishesTick() { return finishesTick; }
        public long pausedUntilTick() { return pausedUntilTick; }
        public void pauseForRestart() { pausedUntilTick = Long.MAX_VALUE; }
        public void resume(long finishTick) {
            pausedUntilTick = 0L;
            finishesTick = Math.max(0L, finishTick);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", dimension);
            tag.putLong("pos", pos.asLong());
            tag.putLong("finish", finishesTick);
            tag.putLong("paused", pausedUntilTick);
            return tag;
        }

        static HackState load(CompoundTag tag) {
            if (tag == null || !tag.contains("pos")) return null;
            return new HackState(tag.getString("dimension"), BlockPos.of(tag.getLong("pos")),
                    tag.getLong("finish"), tag.getLong("paused"));
        }
    }

    public record HackTransfer(UUID accountId, UUID playerId, BigDecimal amount, String bankName) {
        public HackTransfer {
            amount = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
            bankName = clean(bankName, 80);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (accountId != null) tag.putUUID("account", accountId);
            if (playerId != null) tag.putUUID("player", playerId);
            tag.putString("amount", amount.toPlainString());
            tag.putString("bank", bankName);
            return tag;
        }

        static HackTransfer load(CompoundTag tag) {
            if (tag == null || !tag.hasUUID("account")) return null;
            return new HackTransfer(tag.getUUID("account"),
                    tag.hasUUID("player") ? tag.getUUID("player") : null,
                    decimal(tag.getString("amount")), tag.getString("bank"));
        }
    }

    private static String hackKey(String dimension, BlockPos pos) {
        return clean(dimension, 128) + "|" + (pos == null ? 0L : pos.asLong());
    }

    private static String targetKey(String dimension, BlockPos pos) {
        return hackKey(dimension, pos);
    }

    private static CompoundTag saveBounds(SafeBlockBounds bounds) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", bounds.dimension());
        tag.putInt("min_x", bounds.minX()); tag.putInt("min_y", bounds.minY()); tag.putInt("min_z", bounds.minZ());
        tag.putInt("max_x", bounds.maxX()); tag.putInt("max_y", bounds.maxY()); tag.putInt("max_z", bounds.maxZ());
        return tag;
    }
    private static SafeBlockBounds loadBounds(CompoundTag tag) {
        return new SafeBlockBounds(tag.getString("dimension"), tag.getInt("min_x"), tag.getInt("min_y"),
                tag.getInt("min_z"), tag.getInt("max_x"), tag.getInt("max_y"), tag.getInt("max_z"));
    }
    private static CompoundTag saveExit(SafeExitSnapshot exit) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", exit.dimension()); tag.putInt("x", exit.x()); tag.putInt("y", exit.y());
        tag.putInt("z", exit.z()); tag.putFloat("yaw", exit.yaw()); return tag;
    }
    private static SafeExitSnapshot loadExit(CompoundTag tag) {
        return new SafeExitSnapshot(tag.getString("dimension"), tag.getInt("x"), tag.getInt("y"),
                tag.getInt("z"), tag.getFloat("yaw"));
    }
    private static ListTag saveUuids(Set<UUID> values) {
        ListTag list = new ListTag();
        for (UUID value : values) { CompoundTag tag = new CompoundTag(); tag.putUUID("id", value); list.add(tag); }
        return list;
    }
    private static void loadUuids(ListTag list, Set<UUID> target) {
        for (int i = 0; i < list.size(); i++) if (list.getCompound(i).hasUUID("id")) target.add(list.getCompound(i).getUUID("id"));
    }
    private static BigDecimal decimal(String value) {
        try { return new BigDecimal(value == null || value.isBlank() ? "0" : value).max(BigDecimal.ZERO); }
        catch (NumberFormatException ignored) { return BigDecimal.ZERO; }
    }
    private static String clean(String value, int max) {
        String safe = value == null ? "" : value.trim(); return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
