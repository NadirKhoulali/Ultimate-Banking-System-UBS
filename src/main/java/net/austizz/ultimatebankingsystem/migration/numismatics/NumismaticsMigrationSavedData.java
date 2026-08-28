package net.austizz.ultimatebankingsystem.migration.numismatics;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent migration journal. All mutations are keyed by the source fingerprint. */
public final class NumismaticsMigrationSavedData extends SavedData {
    private static final String DATA_NAME = "ultimate_banking_system_numismatics_migration";
    private static final int MAX_MESSAGES = 2_048;

    private UUID migrationId;
    private UUID ownerId;
    private NumismaticsMigrationPhase phase = NumismaticsMigrationPhase.IDLE;
    private SourceKind sourceKind = SourceKind.WORLD;
    private String sourcePath = "";
    private String sourceHash = "";
    private NumismaticsMigrationOptions options = NumismaticsMigrationOptions.defaults();
    private NumismaticsPreflightResult preflight = NumismaticsPreflightResult.empty();
    private String statusMessage = "No Numismatics migration has been configured.";
    private String failureMessage = "";
    private String backupDirectory = "";
    private long createdAt;
    private long updatedAt;
    private long sourceAccountSpurs;
    private int sourceAccountCount;
    private int progressCurrent;
    private int progressTotal;
    private boolean maintenanceLocked;
    private boolean authoritativeScanComplete;
    private boolean sourceBalancesConsumed;
    private final LinkedHashMap<UUID, UUID> accountMappings = new LinkedHashMap<>();
    private final LinkedHashSet<UUID> appliedSourceAccounts = new LinkedHashSet<>();
    private final LinkedHashSet<String> completedPlayerFiles = new LinkedHashSet<>();
    private final LinkedHashSet<String> completedChunks = new LinkedHashSet<>();
    private final ArrayList<CompoundTag> recoveryItems = new ArrayList<>();
    private final ArrayList<String> audit = new ArrayList<>();

    public static NumismaticsMigrationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static SavedData.Factory<NumismaticsMigrationSavedData> factory() {
        return new SavedData.Factory<>(NumismaticsMigrationSavedData::new,
                NumismaticsMigrationSavedData::load, null);
    }

    public synchronized void begin(UUID ownerId, SourceKind sourceKind, String sourcePath,
                                   NumismaticsSourceSnapshot source) {
        this.migrationId = UUID.randomUUID();
        this.ownerId = ownerId;
        this.phase = NumismaticsMigrationPhase.SOURCE_READY;
        this.sourceKind = sourceKind == null ? SourceKind.WORLD : sourceKind;
        this.sourcePath = safe(sourcePath, 4_096);
        this.sourceHash = source == null ? "" : safe(source.sha256(), 128);
        this.sourceAccountCount = source == null ? 0 : source.accounts().size();
        this.sourceAccountSpurs = source == null ? 0L : source.totalSpurs();
        this.options = NumismaticsMigrationOptions.defaults();
        this.preflight = NumismaticsPreflightResult.empty();
        this.statusMessage = "Source validated. Configure the migration and run preflight.";
        this.failureMessage = "";
        this.backupDirectory = "";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = createdAt;
        this.progressCurrent = 0;
        this.progressTotal = 0;
        this.maintenanceLocked = false;
        this.authoritativeScanComplete = false;
        this.sourceBalancesConsumed = false;
        this.accountMappings.clear();
        this.appliedSourceAccounts.clear();
        this.completedPlayerFiles.clear();
        this.completedChunks.clear();
        this.recoveryItems.clear();
        this.audit.clear();
        audit("SOURCE", this.sourceKind + " " + this.sourceHash);
        touch();
    }

    public synchronized void reset() {
        migrationId = null;
        ownerId = null;
        phase = NumismaticsMigrationPhase.IDLE;
        sourceKind = SourceKind.WORLD;
        sourcePath = "";
        sourceHash = "";
        options = NumismaticsMigrationOptions.defaults();
        preflight = NumismaticsPreflightResult.empty();
        statusMessage = "No Numismatics migration has been configured.";
        failureMessage = "";
        backupDirectory = "";
        sourceAccountSpurs = 0L;
        sourceAccountCount = 0;
        progressCurrent = 0;
        progressTotal = 0;
        maintenanceLocked = false;
        authoritativeScanComplete = false;
        sourceBalancesConsumed = false;
        accountMappings.clear();
        appliedSourceAccounts.clear();
        completedPlayerFiles.clear();
        completedChunks.clear();
        recoveryItems.clear();
        audit.clear();
        touch();
    }

    public synchronized void setOptions(NumismaticsMigrationOptions options) {
        if (phase.running()) throw new IllegalStateException("Migration is already running.");
        this.options = options == null ? NumismaticsMigrationOptions.defaults() : options;
        this.preflight = NumismaticsPreflightResult.empty();
        this.authoritativeScanComplete = false;
        this.phase = NumismaticsMigrationPhase.SOURCE_READY;
        this.statusMessage = "Options saved. Run preflight to calculate the migration.";
        touch();
    }

    public synchronized void setPreflight(NumismaticsPreflightResult result, boolean authoritative) {
        this.preflight = result == null ? NumismaticsPreflightResult.empty() : result;
        this.authoritativeScanComplete = authoritative;
        this.phase = NumismaticsMigrationPhase.READY;
        this.statusMessage = this.preflight.blockers().isEmpty()
                ? "Preflight complete. Review the totals before conversion."
                : "Preflight found blocking issues that must be resolved.";
        touch();
    }

    public synchronized void setPhase(NumismaticsMigrationPhase phase, String message) {
        this.phase = phase == null ? NumismaticsMigrationPhase.FAILED : phase;
        this.statusMessage = safe(message, 1_024);
        if (this.phase == NumismaticsMigrationPhase.FAILED) {
            this.failureMessage = this.statusMessage;
        }
        audit("PHASE", this.phase + " " + this.statusMessage);
        touch();
    }

    public synchronized void setProgress(int current, int total, String message) {
        this.progressCurrent = Math.max(0, current);
        this.progressTotal = Math.max(this.progressCurrent, total);
        if (message != null && !message.isBlank()) this.statusMessage = safe(message, 1_024);
        touch();
    }

    public synchronized void setMaintenanceLocked(boolean value) {
        this.maintenanceLocked = value;
        audit("MAINTENANCE", Boolean.toString(value));
        touch();
    }

    public synchronized void setBackupDirectory(String path) {
        backupDirectory = safe(path, 4_096);
        touch();
    }

    public synchronized void mapAccount(UUID sourceId, UUID targetId) {
        if (sourceId == null || targetId == null) return;
        accountMappings.put(sourceId, targetId);
        touch();
    }

    public synchronized void markAccountApplied(UUID sourceId) {
        if (sourceId != null && appliedSourceAccounts.add(sourceId)) touch();
    }

    public synchronized void markPlayerFileComplete(String relativePath) {
        if (completedPlayerFiles.add(safe(relativePath, 4_096))) touch();
    }

    public synchronized void markChunkComplete(NumismaticsPreflightResult.ChunkRef chunk) {
        if (chunk != null && completedChunks.add(chunkKey(chunk))) touch();
    }

    public synchronized void addRecoveryItem(CompoundTag item) {
        if (item == null || item.isEmpty()) return;
        recoveryItems.add(item.copy());
        touch();
    }

    public synchronized void setSourceBalancesConsumed(boolean consumed) {
        sourceBalancesConsumed = consumed;
        touch();
    }

    public synchronized void audit(String action, String details) {
        String row = System.currentTimeMillis() + "|" + safe(action, 64) + "|" + safe(details, 1_024);
        audit.add(row);
        if (audit.size() > MAX_MESSAGES) audit.remove(0);
        touch();
    }

    public synchronized UUID migrationId() { return migrationId; }
    public synchronized UUID ownerId() { return ownerId; }
    public synchronized NumismaticsMigrationPhase phase() { return phase; }
    public synchronized SourceKind sourceKind() { return sourceKind; }
    public synchronized String sourcePath() { return sourcePath; }
    public synchronized String sourceHash() { return sourceHash; }
    public synchronized NumismaticsMigrationOptions options() { return options; }
    public synchronized NumismaticsPreflightResult preflight() { return preflight; }
    public synchronized String statusMessage() { return statusMessage; }
    public synchronized String failureMessage() { return failureMessage; }
    public synchronized String backupDirectory() { return backupDirectory; }
    public synchronized long sourceAccountSpurs() { return sourceAccountSpurs; }
    public synchronized int sourceAccountCount() { return sourceAccountCount; }
    public synchronized int progressCurrent() { return progressCurrent; }
    public synchronized int progressTotal() { return progressTotal; }
    public synchronized boolean maintenanceLocked() { return maintenanceLocked; }
    public synchronized boolean authoritativeScanComplete() { return authoritativeScanComplete; }
    public synchronized boolean sourceBalancesConsumed() { return sourceBalancesConsumed; }
    public synchronized Map<UUID, UUID> accountMappings() { return Map.copyOf(accountMappings); }
    public synchronized Set<UUID> appliedSourceAccounts() { return Set.copyOf(appliedSourceAccounts); }
    public synchronized Set<String> completedPlayerFiles() { return Set.copyOf(completedPlayerFiles); }
    public synchronized Set<String> completedChunks() { return Set.copyOf(completedChunks); }
    public synchronized List<CompoundTag> recoveryItems() { return recoveryItems.stream().map(CompoundTag::copy).toList(); }
    public synchronized List<String> auditEntries() { return List.copyOf(audit); }

    public synchronized boolean isAccountApplied(UUID id) { return appliedSourceAccounts.contains(id); }
    public synchronized boolean isPlayerFileComplete(String path) { return completedPlayerFiles.contains(path); }
    public synchronized boolean isChunkComplete(NumismaticsPreflightResult.ChunkRef ref) {
        return completedChunks.contains(chunkKey(ref));
    }

    public synchronized void clearRecoveryItems() {
        if (recoveryItems.isEmpty()) return;
        recoveryItems.clear();
        audit("RECOVERY", "Recovery inventory claimed by an administrator.");
        touch();
    }

    public synchronized void replaceRecoveryItems(List<CompoundTag> remaining) {
        recoveryItems.clear();
        if (remaining != null) {
            remaining.stream().filter(item -> item != null && !item.isEmpty())
                    .map(CompoundTag::copy).forEach(recoveryItems::add);
        }
        audit("RECOVERY", "Recovery inventory updated; " + recoveryItems.size() + " stack(s) remain.");
        touch();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (migrationId != null) tag.putUUID("migration_id", migrationId);
        if (ownerId != null) tag.putUUID("owner_id", ownerId);
        tag.putString("phase", phase.name());
        tag.putString("source_kind", sourceKind.name());
        tag.putString("source_path", sourcePath);
        tag.putString("source_hash", sourceHash);
        tag.putInt("cents_per_spur", options.centsPerSpur());
        tag.putString("scope", options.scope().name());
        tag.putBoolean("convert_cards", options.convertBankCards());
        tag.putBoolean("unsafe_account_only", options.allowUnsafeAccountOnlyRemoval());
        tag.putString("status", statusMessage);
        tag.putString("failure", failureMessage);
        tag.putString("backup", backupDirectory);
        tag.putLong("created_at", createdAt);
        tag.putLong("updated_at", updatedAt);
        tag.putLong("source_account_spurs", sourceAccountSpurs);
        tag.putInt("source_account_count", sourceAccountCount);
        tag.putInt("progress_current", progressCurrent);
        tag.putInt("progress_total", progressTotal);
        tag.putBoolean("maintenance", maintenanceLocked);
        tag.putBoolean("authoritative_scan", authoritativeScanComplete);
        tag.putBoolean("source_consumed", sourceBalancesConsumed);
        tag.put("preflight", savePreflight(preflight));

        ListTag mappings = new ListTag();
        accountMappings.forEach((source, target) -> {
            CompoundTag row = new CompoundTag(); row.putUUID("source", source); row.putUUID("target", target); mappings.add(row);
        });
        tag.put("account_mappings", mappings);
        tag.put("applied_accounts", saveUuidSet(appliedSourceAccounts));
        tag.put("completed_players", saveStringSet(completedPlayerFiles));
        tag.put("completed_chunks", saveStringSet(completedChunks));
        ListTag recovery = new ListTag(); recoveryItems.forEach(item -> recovery.add(item.copy())); tag.put("recovery", recovery);
        ListTag auditList = new ListTag(); audit.forEach(value -> { CompoundTag row = new CompoundTag(); row.putString("value", value); auditList.add(row); }); tag.put("audit", auditList);
        return tag;
    }

    private static NumismaticsMigrationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NumismaticsMigrationSavedData data = new NumismaticsMigrationSavedData();
        if (tag.hasUUID("migration_id")) data.migrationId = tag.getUUID("migration_id");
        if (tag.hasUUID("owner_id")) data.ownerId = tag.getUUID("owner_id");
        data.phase = enumValue(NumismaticsMigrationPhase.class, tag.getString("phase"), NumismaticsMigrationPhase.IDLE);
        data.sourceKind = enumValue(SourceKind.class, tag.getString("source_kind"), SourceKind.WORLD);
        data.sourcePath = safe(tag.getString("source_path"), 4_096);
        data.sourceHash = safe(tag.getString("source_hash"), 128);
        int rate = tag.contains("cents_per_spur") ? tag.getInt("cents_per_spur") : 100;
        rate = Math.max(1, Math.min(100_000_000, rate));
        NumismaticsMigrationOptions.Scope scope = enumValue(NumismaticsMigrationOptions.Scope.class,
                tag.getString("scope"), NumismaticsMigrationOptions.Scope.FULL_ECONOMY);
        data.options = new NumismaticsMigrationOptions(rate, scope,
                !tag.contains("convert_cards") || tag.getBoolean("convert_cards"),
                tag.contains("unsafe_account_only") && tag.getBoolean("unsafe_account_only"));
        data.statusMessage = safe(tag.getString("status"), 1_024);
        data.failureMessage = safe(tag.getString("failure"), 1_024);
        data.backupDirectory = safe(tag.getString("backup"), 4_096);
        data.createdAt = tag.getLong("created_at");
        data.updatedAt = tag.getLong("updated_at");
        data.sourceAccountSpurs = tag.getLong("source_account_spurs");
        data.sourceAccountCount = tag.getInt("source_account_count");
        data.progressCurrent = Math.max(0, tag.getInt("progress_current"));
        data.progressTotal = Math.max(data.progressCurrent, tag.getInt("progress_total"));
        data.maintenanceLocked = tag.getBoolean("maintenance");
        data.authoritativeScanComplete = tag.getBoolean("authoritative_scan");
        data.sourceBalancesConsumed = tag.getBoolean("source_consumed");
        if (tag.contains("preflight", Tag.TAG_COMPOUND)) data.preflight = loadPreflight(tag.getCompound("preflight"));

        ListTag mappings = tag.getList("account_mappings", Tag.TAG_COMPOUND);
        for (int i = 0; i < mappings.size(); i++) {
            CompoundTag row = mappings.getCompound(i);
            if (row.hasUUID("source") && row.hasUUID("target")) data.accountMappings.put(row.getUUID("source"), row.getUUID("target"));
        }
        loadUuidSet(tag.getList("applied_accounts", Tag.TAG_COMPOUND), data.appliedSourceAccounts);
        loadStringSet(tag.getList("completed_players", Tag.TAG_COMPOUND), data.completedPlayerFiles);
        loadStringSet(tag.getList("completed_chunks", Tag.TAG_COMPOUND), data.completedChunks);
        ListTag recovery = tag.getList("recovery", Tag.TAG_COMPOUND);
        for (int i = 0; i < recovery.size(); i++) data.recoveryItems.add(recovery.getCompound(i).copy());
        ListTag audit = tag.getList("audit", Tag.TAG_COMPOUND);
        int start = Math.max(0, audit.size() - MAX_MESSAGES);
        for (int i = start; i < audit.size(); i++) data.audit.add(safe(audit.getCompound(i).getString("value"), 1_256));
        return data;
    }

    private static CompoundTag savePreflight(NumismaticsPreflightResult result) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("coins", result.coinItems()); tag.putLong("spurs", result.physicalSpurs());
        tag.putLong("bound_cards", result.boundBankCards()); tag.putLong("blank_cards", result.blankBankCards()); tag.putLong("id_cards", result.idCards());
        ListTag chunks = new ListTag(); result.candidateChunks().forEach(ref -> { CompoundTag row = new CompoundTag(); row.putString("dimension", ref.dimension()); row.putInt("x", ref.x()); row.putInt("z", ref.z()); chunks.add(row); }); tag.put("chunks", chunks);
        tag.put("players", saveStrings(result.candidatePlayerFiles())); tag.put("files", saveStrings(result.affectedFiles()));
        tag.put("warnings", saveStrings(result.warnings())); tag.put("blockers", saveStrings(result.blockers()));
        return tag;
    }

    private static NumismaticsPreflightResult loadPreflight(CompoundTag tag) {
        List<NumismaticsPreflightResult.ChunkRef> chunks = new ArrayList<>();
        ListTag chunkTags = tag.getList("chunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < chunkTags.size(); i++) { CompoundTag row = chunkTags.getCompound(i); chunks.add(new NumismaticsPreflightResult.ChunkRef(row.getString("dimension"), row.getInt("x"), row.getInt("z"))); }
        return new NumismaticsPreflightResult(tag.getLong("coins"), tag.getLong("spurs"), tag.getLong("bound_cards"), tag.getLong("blank_cards"), tag.getLong("id_cards"), chunks,
                loadStrings(tag.getList("players", Tag.TAG_COMPOUND)), loadStrings(tag.getList("files", Tag.TAG_COMPOUND)), loadStrings(tag.getList("warnings", Tag.TAG_COMPOUND)), loadStrings(tag.getList("blockers", Tag.TAG_COMPOUND)));
    }

    private static ListTag saveUuidSet(Set<UUID> values) { ListTag list = new ListTag(); values.forEach(id -> { CompoundTag row = new CompoundTag(); row.putUUID("value", id); list.add(row); }); return list; }
    private static void loadUuidSet(ListTag list, Set<UUID> target) { for (int i = 0; i < list.size(); i++) { CompoundTag row = list.getCompound(i); if (row.hasUUID("value")) target.add(row.getUUID("value")); } }
    private static ListTag saveStringSet(Set<String> values) { return saveStrings(values.stream().toList()); }
    private static ListTag saveStrings(List<String> values) { ListTag list = new ListTag(); values.forEach(value -> { CompoundTag row = new CompoundTag(); row.putString("value", safe(value, 4_096)); list.add(row); }); return list; }
    private static List<String> loadStrings(ListTag list) { List<String> values = new ArrayList<>(); for (int i = 0; i < list.size(); i++) values.add(safe(list.getCompound(i).getString("value"), 4_096)); return values; }
    private static void loadStringSet(ListTag list, Set<String> target) { target.addAll(loadStrings(list)); }
    private static String chunkKey(NumismaticsPreflightResult.ChunkRef ref) { return ref.dimension() + "|" + ref.x() + "|" + ref.z(); }
    private static String safe(String value, int max) { String safe = value == null ? "" : value.trim(); return safe.length() <= max ? safe : safe.substring(0, max); }
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) { try { return Enum.valueOf(type, value); } catch (RuntimeException ignored) { return fallback; } }
    private void touch() { updatedAt = System.currentTimeMillis(); setDirty(); }

    public enum SourceKind { WORLD, UPLOAD, SERVER_PATH }
}
