package net.austizz.ultimatebankingsystem.migration.numismatics;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.network.NumismaticsMigrationOpenPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.ModList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class NumismaticsMigrationService {
    private static final long SESSION_TTL_MILLIS = 30L * 60L * 1_000L;
    private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();
    private static final Map<UUID, Upload> UPLOADS = new LinkedHashMap<>();
    private static final Set<MinecraftServer> FROZEN_BY_MIGRATION =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static MinecraftServer activeServer;
    private static RuntimeJob activeJob;

    private NumismaticsMigrationService() {
    }

    public static void open(ServerPlayer player) {
        requireAdmin(player);
        Session session = new Session(UUID.randomUUID(), System.currentTimeMillis());
        SESSIONS.put(player.getUUID(), session);
        sendSnapshot(player, session.token());
    }

    public static void refresh(ServerPlayer player, UUID token) {
        Session session = requireSession(player, token);
        session.touch();
        sendSnapshot(player, token);
    }

    public static void selectWorldSource(ServerPlayer player, UUID token) {
        requireSession(player, token);
        try {
            selectSource(player, NumismaticsMigrationSavedData.SourceKind.WORLD,
                    NumismaticsSourceService.prepareWorldSource(player.getServer()));
        } catch (IOException exception) {
            notifyFailure(player, "Source rejected: " + rootMessage(exception));
        }
    }

    public static void selectServerSource(ServerPlayer player, UUID token, Path path) {
        requireSession(player, token);
        try {
            Path staged = NumismaticsSourceService.stageExternalSource(
                    player.getServer(), player.getUUID(), path.toAbsolutePath().normalize());
            selectSource(player, NumismaticsMigrationSavedData.SourceKind.SERVER_PATH, staged);
        } catch (IOException exception) {
            notifyFailure(player, "Could not import source: " + rootMessage(exception));
        }
    }

    public static String selectServerSource(MinecraftServer server, UUID ownerId, Path path) throws IOException {
        Path staged = NumismaticsSourceService.stageExternalSource(server, ownerId, path.toAbsolutePath().normalize());
        NumismaticsSourceSnapshot source = NumismaticsDataReader.read(staged);
        NumismaticsMigrationSavedData journal = data(server);
        if (journal.phase().running() || journal.maintenanceLocked()) {
            throw new IOException("The active migration is locked and cannot change source.");
        }
        journal.begin(ownerId, NumismaticsMigrationSavedData.SourceKind.SERVER_PATH,
                staged.toString(), source);
        return "Validated and staged " + source.accounts().size() + " Numismatics account(s).";
    }

    public static String selectWorldSource(MinecraftServer server, UUID ownerId) throws IOException {
        Path sourcePath = NumismaticsSourceService.prepareWorldSource(server);
        NumismaticsSourceSnapshot source = NumismaticsDataReader.read(sourcePath);
        NumismaticsMigrationSavedData journal = data(server);
        if (journal.phase().running() || journal.maintenanceLocked()) {
            throw new IOException("The active migration is locked and cannot change source.");
        }
        journal.begin(ownerId, NumismaticsMigrationSavedData.SourceKind.WORLD,
                sourcePath.toString(), source);
        return "Selected this world's Numismatics data with " + source.accounts().size() + " account(s).";
    }

    public static void beginUpload(ServerPlayer player, UUID token, UUID uploadId,
                                   String fileName, long size, int chunks, String sha256) {
        requireSession(player, token);
        if (uploadId == null || size <= 0 || size > NumismaticsDataReader.MAX_SOURCE_BYTES) {
            throw new IllegalArgumentException("Uploaded Numismatics files must be between 1 byte and 32 MiB.");
        }
        int expectedChunks = (int) ((size + 256L * 1024L - 1L) / (256L * 1024L));
        if (chunks != expectedChunks || chunks <= 0 || chunks > 128) {
            throw new IllegalArgumentException("Upload chunk count is invalid.");
        }
        String hash = sha256 == null ? "" : sha256.trim().toLowerCase(Locale.ROOT);
        if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Upload checksum is invalid.");
        UPLOADS.put(player.getUUID(), new Upload(uploadId, sanitizeFileName(fileName), size, chunks, hash));
        notifySuccess(player, "Upload started. Keep the migration screen open.");
    }

    public static void acceptUploadChunk(ServerPlayer player, UUID token, UUID uploadId,
                                         int index, byte[] bytes) {
        requireSession(player, token);
        Upload upload = requireUpload(player, uploadId);
        upload.accept(index, bytes);
    }

    public static void finishUpload(ServerPlayer player, UUID token, UUID uploadId) {
        requireSession(player, token);
        Upload upload = requireUpload(player, uploadId);
        UPLOADS.remove(player.getUUID());
        try {
            byte[] bytes = upload.finish();
            if (!sha256(bytes).equals(upload.sha256())) throw new IOException("Upload checksum verification failed.");
            Path worldRoot = player.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path staging = worldRoot.resolve("ubs-migrations").resolve("numismatics").resolve("staging");
            Files.createDirectories(staging);
            Path source = staging.resolve(player.getUUID() + "-" + upload.id() + ".dat");
            Files.write(source, bytes);
            selectSource(player, NumismaticsMigrationSavedData.SourceKind.UPLOAD, source);
        } catch (IOException exception) {
            notifyFailure(player, "Upload rejected: " + rootMessage(exception));
        }
    }

    public static void setOptions(ServerPlayer player, UUID token, int centsPerSpur,
                                  String scopeName, boolean convertCards, boolean allowUnsafe) {
        requireSession(player, token);
        NumismaticsMigrationOptions.Scope scope;
        try {
            scope = NumismaticsMigrationOptions.Scope.valueOf(
                    scopeName == null ? "" : scopeName.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException malformed) {
            notifyFailure(player, "Choose a valid conversion scope.");
            return;
        }
        try {
            NumismaticsMigrationSavedData journal = data(player.getServer());
            journal.setOptions(new NumismaticsMigrationOptions(centsPerSpur, scope, convertCards, allowUnsafe));
            sendSnapshot(player, token);
        } catch (RuntimeException exception) {
            notifyFailure(player, rootMessage(exception));
        }
    }

    public static void runPreflight(ServerPlayer player, UUID token) {
        requireSession(player, token);
        MinecraftServer server = player.getServer();
        NumismaticsMigrationSavedData journal = data(server);
        if (journal.sourcePath().isBlank() || journal.phase().running()) {
            notifyFailure(player, "Select a source and wait for the current operation to finish.");
            return;
        }
        server.getPlayerList().saveAll();
        journal.setPhase(NumismaticsMigrationPhase.PREFLIGHT_RUNNING,
                "Saved online inventories. Running a fresh player, chunk, entity, and SavedData scan...");
        broadcast(server);
        CompletableFuture.supplyAsync(() -> scan(server, journal)).whenComplete((result, error) ->
                server.execute(() -> {
                    if (error != null) {
                        journal.setPhase(NumismaticsMigrationPhase.FAILED,
                                "Preflight failed: " + rootMessage(error));
                    } else {
                        journal.setPreflight(result, false);
                    }
                    broadcast(server);
                }));
    }

    public static void execute(ServerPlayer player, UUID token) {
        requireSession(player, token);
        startExecution(player.getServer());
    }

    public static void resume(ServerPlayer player, UUID token) {
        requireSession(player, token);
        MinecraftServer server = player.getServer();
        NumismaticsMigrationSavedData journal = data(server);
        if (journal.migrationId() == null || journal.sourcePath().isBlank()) {
            notifyFailure(player, "There is no migration to resume.");
            return;
        }
        if (journal.phase() == NumismaticsMigrationPhase.COMPLETE
                || journal.phase() == NumismaticsMigrationPhase.ROLLED_BACK) {
            notifyFailure(player, "This migration is already finalized.");
            return;
        }
        if (activeJob != null) {
            notifyFailure(player, "The migration is already active.");
            return;
        }
        if (journal.backupDirectory().isBlank()) startExecution(server);
        else {
            journal.setMaintenanceLocked(true);
            ensureMaintenanceFreeze(server);
            kickForMaintenance(server);
            try {
                NumismaticsSourceSnapshot source = NumismaticsSourceService.readJournalSource(journal);
                if (!journal.sourceBalancesConsumed()
                        && !source.sha256().equalsIgnoreCase(journal.sourceHash())) {
                    throw new IOException("The Numismatics source changed after backup. Restore the backup or select the source again.");
                }
                beginConversion(server, journal, source);
            } catch (IOException exception) {
                fail(server, journal, "Resume failed: " + rootMessage(exception), true);
            }
        }
    }

    public static void reset(ServerPlayer player, UUID token) {
        requireSession(player, token);
        NumismaticsMigrationSavedData journal = data(player.getServer());
        if (journal.phase().running() || journal.maintenanceLocked()) {
            notifyFailure(player, "A locked migration cannot be reset. Resume it or restore its backup.");
            return;
        }
        journal.reset();
        sendSnapshot(player, token);
    }

    public static void requestRollback(ServerPlayer player, UUID token) {
        requireSession(player, token);
        MinecraftServer server = player.getServer();
        NumismaticsMigrationSavedData journal = data(server);
        try {
            NumismaticsBackupService.requestRollback(server, journal.migrationId());
            journal.setPhase(NumismaticsMigrationPhase.FAILED,
                    "Rollback scheduled. The server is stopping so files can be restored before the next world load.");
            server.saveEverything(false, true, true);
            broadcast(server);
            server.execute(() -> server.halt(false));
        } catch (IOException exception) {
            notifyFailure(player, "Could not schedule rollback: " + rootMessage(exception));
        }
    }

    public static void stopServer(ServerPlayer player, UUID token) {
        requireSession(player, token);
        NumismaticsMigrationSavedData journal = data(player.getServer());
        if (journal.phase() != NumismaticsMigrationPhase.COMPLETE) {
            notifyFailure(player, "The server can only be stopped from this setup after reconciliation succeeds.");
            return;
        }
        player.getServer().saveEverything(false, true, true);
        player.getServer().execute(() -> player.getServer().halt(false));
    }

    public static int claimRecovery(ServerPlayer player, UUID token) {
        requireSession(player, token);
        NumismaticsMigrationSavedData journal = data(player.getServer());
        List<CompoundTag> stored = journal.recoveryItems();
        int count = 0;
        List<CompoundTag> remaining = new ArrayList<>();
        for (CompoundTag tag : stored) {
            ItemStack stack = ItemStack.parseOptional(player.getServer().registryAccess(), tag);
            if (stack.isEmpty()) continue;
            ItemStack toGive = stack.copy();
            int before = toGive.getCount();
            player.addItem(toGive);
            count += before - toGive.getCount();
            if (!toGive.isEmpty() && toGive.saveOptional(player.getServer().registryAccess()) instanceof CompoundTag remainder) {
                remaining.add(remainder);
            }
        }
        if (!stored.isEmpty()) journal.replaceRecoveryItems(remaining);
        if (count > 0) notifySuccess(player, "Moved " + count + " recovered item(s) into your inventory.");
        else if (!stored.isEmpty()) notifyFailure(player, "Your inventory has no room for the recovery items.");
        sendSnapshot(player, token);
        return count;
    }

    public static void tick(MinecraftServer server) {
        expireSessions();
        if (activeJob == null || activeServer != server) return;
        NumismaticsMigrationSavedData journal = data(server);
        try {
            if (journal.phase() == NumismaticsMigrationPhase.CONVERTING_PLAYERS) {
                processPlayerFile(server, journal, activeJob);
            } else if (journal.phase() == NumismaticsMigrationPhase.CONVERTING_WORLD) {
                processChunk(server, journal, activeJob);
            }
        } catch (RuntimeException | IOException exception) {
            fail(server, journal, "Conversion failed: " + rootMessage(exception), true);
        }
    }

    public static boolean maintenanceLocked(MinecraftServer server) {
        return server != null && data(server).maintenanceLocked();
    }

    public static void onServerStopping(MinecraftServer server) {
        SESSIONS.clear();
        UPLOADS.clear();
        if (activeServer == server) {
            activeServer = null;
            activeJob = null;
        }
        FROZEN_BY_MIGRATION.remove(server);
    }

    public static String status(MinecraftServer server) {
        NumismaticsMigrationSavedData journal = data(server);
        return journal.phase() + " - " + journal.statusMessage() + " ("
                + journal.progressCurrent() + "/" + journal.progressTotal() + ")";
    }

    public static Path reportPath(MinecraftServer server) {
        NumismaticsMigrationSavedData journal = data(server);
        if (journal.migrationId() == null) return null;
        return server.getWorldPath(LevelResource.ROOT).resolve("ubs-migrations").resolve("numismatics")
                .resolve(journal.migrationId().toString()).resolve("migration-report.json")
                .toAbsolutePath().normalize();
    }

    private static void selectSource(ServerPlayer player, NumismaticsMigrationSavedData.SourceKind kind,
                                     Path sourcePath) {
        try {
            NumismaticsSourceSnapshot source = NumismaticsDataReader.read(sourcePath);
            NumismaticsMigrationSavedData journal = data(player.getServer());
            if (journal.phase().running() || journal.maintenanceLocked()) {
                throw new IOException("The active migration is locked and cannot change source.");
            }
            journal.begin(player.getUUID(), kind, sourcePath.toAbsolutePath().normalize().toString(), source);
            notifySuccess(player, "Source validated: " + source.accounts().size()
                    + " Numismatics account(s) found. Physical currency will be counted during preflight.");
        } catch (IOException exception) {
            notifyFailure(player, "Source rejected: " + rootMessage(exception));
        }
    }

    private static void startExecution(MinecraftServer server) {
        NumismaticsMigrationSavedData journal = data(server);
        if (activeJob != null) return;
        if (journal.phase() != NumismaticsMigrationPhase.READY || !journal.preflight().blockers().isEmpty()) {
            fail(server, journal, "Run a clean preflight before starting conversion.", false);
            return;
        }
        journal.setMaintenanceLocked(true);
        ensureMaintenanceFreeze(server);
        journal.setPhase(NumismaticsMigrationPhase.PREFLIGHT_RUNNING,
                "Maintenance lock active. Running authoritative preflight...");
        kickForMaintenance(server);
        server.saveEverything(false, true, true);
        broadcast(server);
        CompletableFuture.supplyAsync(() -> scan(server, journal)).whenComplete((result, error) ->
                server.execute(() -> {
                    if (error != null) {
                        journal.setMaintenanceLocked(false);
                        releaseMaintenanceFreeze(server);
                        fail(server, journal, "Authoritative preflight failed: " + rootMessage(error), false);
                        return;
                    }
                    journal.setPreflight(result, true);
                    if (!result.blockers().isEmpty()) {
                        journal.setMaintenanceLocked(false);
                        releaseMaintenanceFreeze(server);
                        broadcast(server);
                        return;
                    }
                    journal.setPhase(NumismaticsMigrationPhase.BACKING_UP,
                            "Creating a checksum-verified world backup...");
                    server.saveEverything(false, true, true);
                    broadcast(server);
                    CompletableFuture.supplyAsync(() -> createBackup(server, journal)).whenComplete((backup, backupError) ->
                            server.execute(() -> {
                                if (backupError != null) {
                                    fail(server, journal, "Backup failed: " + rootMessage(backupError), false);
                                    return;
                                }
                                journal.setBackupDirectory(backup.migrationRoot().toString());
                                try {
                                    NumismaticsSourceSnapshot source = NumismaticsSourceService.readJournalSource(journal);
                                    if (!source.sha256().equalsIgnoreCase(journal.sourceHash())) {
                                        throw new IOException("The Numismatics source changed after it was reviewed.");
                                    }
                                    beginConversion(server, journal, source);
                                } catch (IOException exception) {
                                    fail(server, journal, "Source verification failed: " + rootMessage(exception), false);
                                }
                            }));
                }));
    }

    private static void beginConversion(MinecraftServer server, NumismaticsMigrationSavedData journal,
                                        NumismaticsSourceSnapshot source) {
        activeServer = server;
        journal.setPhase(NumismaticsMigrationPhase.CONVERTING_ACCOUNTS,
                "Converting Numismatics accounts into UBS accounts...");
        broadcast(server);
        NumismaticsAccountMigrator.Result accounts = NumismaticsAccountMigrator.migrate(server, source, journal);
        if (!accounts.success()) {
            fail(server, journal, "Account conversion stopped: " + String.join("; ", accounts.errors()), true);
            return;
        }
        server.saveEverything(false, true, true);
        if (journal.options().scope() == NumismaticsMigrationOptions.Scope.ACCOUNTS_ONLY) {
            activeJob = new RuntimeJob(source, List.of(), List.of());
            beginReconciliation(server, journal, activeJob);
            return;
        }

        NumismaticsLiveMigration.Result online = NumismaticsLiveMigration.migrateOnlinePlayers(server, journal);
        if (!online.unresolved().isEmpty()) {
            fail(server, journal, "Online inventory conversion stopped: "
                    + String.join("; ", online.unresolved()), true);
            return;
        }
        List<String> playerFiles = journal.preflight().candidatePlayerFiles().stream()
                .filter(path -> !journal.isPlayerFileComplete(path)).toList();
        List<NumismaticsPreflightResult.ChunkRef> chunks = journal.preflight().candidateChunks().stream()
                .filter(ref -> !journal.isChunkComplete(ref)).toList();
        activeJob = new RuntimeJob(source, playerFiles, chunks);
        journal.setProgress(0, playerFiles.size() + chunks.size(), "Converting offline player inventories...");
        journal.setPhase(NumismaticsMigrationPhase.CONVERTING_PLAYERS,
                playerFiles.isEmpty() ? "Player inventories complete." : "Converting offline player inventories...");
        broadcast(server);
        if (playerFiles.isEmpty()) transitionToWorld(server, journal, activeJob);
    }

    private static void processPlayerFile(MinecraftServer server, NumismaticsMigrationSavedData journal,
                                          RuntimeJob job) throws IOException {
        String path = job.playerFiles.pollFirst();
        if (path == null) {
            transitionToWorld(server, journal, job);
            return;
        }
        NumismaticsLiveMigration.Result result = NumismaticsLiveMigration.migratePlayerFile(server, journal, path);
        if (!result.unresolved().isEmpty()) throw new IOException(String.join("; ", result.unresolved()));
        journal.markPlayerFileComplete(path);
        job.processed++;
        journal.setProgress(job.processed, job.total, "Converted " + path + ".");
        if (job.playerFiles.isEmpty()) transitionToWorld(server, journal, job);
        broadcast(server);
    }

    private static void transitionToWorld(MinecraftServer server, NumismaticsMigrationSavedData journal,
                                          RuntimeJob job) {
        journal.setPhase(NumismaticsMigrationPhase.CONVERTING_WORLD,
                job.chunks.isEmpty() ? "World inventories complete." : "Converting world inventories...");
        if (job.chunks.isEmpty()) beginReconciliation(server, journal, job);
        else broadcast(server);
    }

    private static void processChunk(MinecraftServer server, NumismaticsMigrationSavedData journal,
                                     RuntimeJob job) throws IOException {
        NumismaticsPreflightResult.ChunkRef ref = job.chunks.pollFirst();
        if (ref == null) {
            beginReconciliation(server, journal, job);
            return;
        }
        NumismaticsLiveMigration.Result result = NumismaticsLiveMigration.migrateChunk(server, journal, ref);
        if (!result.unresolved().isEmpty()) throw new IOException(String.join("; ", result.unresolved()));
        journal.markChunkComplete(ref);
        job.processed++;
        journal.setProgress(job.processed, job.total,
                "Converted chunk " + ref.dimension() + " " + ref.x() + ", " + ref.z() + ".");
        if (job.chunks.isEmpty()) beginReconciliation(server, journal, job);
        broadcast(server);
    }

    private static void beginReconciliation(MinecraftServer server, NumismaticsMigrationSavedData journal,
                                            RuntimeJob job) {
        if (job.reconciliationStarted) return;
        job.reconciliationStarted = true;
        journal.setPhase(NumismaticsMigrationPhase.RECONCILING,
                "Saving and verifying that no convertible assets were missed...");
        server.saveEverything(false, true, true);
        broadcast(server);
        CompletableFuture.supplyAsync(() -> scan(server, journal)).whenComplete((remaining, error) ->
                server.execute(() -> {
                    if (error != null) {
                        fail(server, journal, "Reconciliation failed: " + rootMessage(error), true);
                        return;
                    }
                    boolean full = journal.options().scope() == NumismaticsMigrationOptions.Scope.FULL_ECONOMY;
                    if (full && remaining.coinItems() > 0) {
                        fail(server, journal, "Reconciliation found " + remaining.coinItems()
                                + " Numismatics coin item(s) still in the world.", true);
                        return;
                    }
                    if (full && journal.options().convertBankCards()
                            && remaining.boundBankCards() + remaining.blankBankCards() > 0) {
                        fail(server, journal, "Reconciliation found Numismatics bank cards still in the world.", true);
                        return;
                    }
                    try {
                        NumismaticsSourceService.consumeSourceBalances(journal, job.source);
                        journal.setSourceBalancesConsumed(true);
                        journal.setProgress(journal.progressTotal(), journal.progressTotal(),
                                "Migration reconciled successfully.");
                        journal.setPhase(NumismaticsMigrationPhase.COMPLETE,
                                "Migration complete. Claim recovery items, review the report, then stop the server and remove Numismatics.");
                        Path report = NumismaticsMigrationReport.write(server, journal);
                        journal.audit("REPORT", report.toString());
                        server.saveEverything(false, true, true);
                        activeJob = null;
                        activeServer = null;
                        broadcast(server);
                    } catch (IOException exception) {
                        fail(server, journal, "Finalization failed: " + rootMessage(exception), true);
                    }
                }));
    }

    private static NumismaticsPreflightResult scan(MinecraftServer server,
                                                   NumismaticsMigrationSavedData journal) {
        try {
            NumismaticsSourceSnapshot source = NumismaticsSourceService.readJournalSource(journal);
            if (!journal.sourceBalancesConsumed() && !source.sha256().equalsIgnoreCase(journal.sourceHash())) {
                throw new IOException("The selected source file changed. Select it again before continuing.");
            }
            return augmentPreflight(NumismaticsWorldScanner.scan(server, journal.options()), source, journal.options());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static NumismaticsPreflightResult augmentPreflight(NumismaticsPreflightResult scanned,
                                                                NumismaticsSourceSnapshot source,
                                                                NumismaticsMigrationOptions options) {
        List<String> warnings = new ArrayList<>(scanned.warnings());
        List<String> blockers = new ArrayList<>();
        if (options.scope() == NumismaticsMigrationOptions.Scope.FULL_ECONOMY) {
            blockers.addAll(scanned.blockers());
            if (!ModList.get().isLoaded("numismatics")) {
                blockers.add("Full physical conversion requires Create: Numismatics to remain installed for this run.");
            }
            if (source.sourcePath() != null && !source.sourcePath().getFileName().toString().equals("numismatics_bank.dat")) {
                warnings.add("The imported file supplies account balances; physical conversion always scans this server's active world.");
            }
        } else {
            warnings.add("Account-only conversion leaves physical Numismatics assets untouched.");
            if (!options.allowUnsafeAccountOnlyRemoval()) {
                blockers.add("Confirm the account-only uninstall warning before continuing.");
            }
        }
        if (options.scope() == NumismaticsMigrationOptions.Scope.FULL_ECONOMY
                && !options.convertBankCards()
                && scanned.boundBankCards() + scanned.blankBankCards() > 0) {
            blockers.add("Full conversion cannot safely remove Numismatics while bank-card conversion is disabled.");
        }
        source.accounts().stream()
                .filter(account -> account.kind() == NumismaticsAccountRecord.AccountKind.BLAZE_BANKER)
                .filter(account -> account.trustedPlayers().isEmpty())
                .forEach(account -> blockers.add("Shared Numismatics account " + account.sourceAccountId()
                        + " has no trusted owner."));
        if (source.accounts().isEmpty()) warnings.add("The source contains no bank accounts.");
        return new NumismaticsPreflightResult(scanned.coinItems(), scanned.physicalSpurs(),
                scanned.boundBankCards(), scanned.blankBankCards(), scanned.idCards(),
                scanned.candidateChunks(), scanned.candidatePlayerFiles(), scanned.affectedFiles(),
                warnings.stream().distinct().toList(), blockers.stream().distinct().toList());
    }

    private static NumismaticsBackupService.BackupResult createBackup(MinecraftServer server,
                                                                      NumismaticsMigrationSavedData journal) {
        try {
            return NumismaticsBackupService.create(server, journal, Path.of(journal.sourcePath()));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void kickForMaintenance(MinecraftServer server) {
        for (ServerPlayer online : List.copyOf(server.getPlayerList().getPlayers())) {
            if (!server.getPlayerList().isOp(online.getGameProfile())) {
                online.connection.disconnect(Component.literal(
                        "UBS economy migration maintenance is in progress. Please reconnect after the server restarts."));
            }
        }
    }

    private static void fail(MinecraftServer server, NumismaticsMigrationSavedData journal,
                             String message, boolean keepLocked) {
        journal.setMaintenanceLocked(keepLocked);
        if (keepLocked) ensureMaintenanceFreeze(server);
        else releaseMaintenanceFreeze(server);
        journal.setPhase(NumismaticsMigrationPhase.FAILED, message);
        activeJob = null;
        activeServer = null;
        UltimateBankingSystem.LOGGER.error("[UBS Numismatics Migration] {}", message);
        broadcast(server);
    }

    private static void broadcast(MinecraftServer server) {
        for (Map.Entry<UUID, Session> entry : List.copyOf(SESSIONS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) sendSnapshot(player, entry.getValue().token());
        }
    }

    private static void sendSnapshot(ServerPlayer player, UUID token) {
        Session session = SESSIONS.get(player.getUUID());
        String feedback = session == null ? "" : session.feedbackMessage();
        boolean feedbackError = session != null && session.feedbackError();
        PacketDistributor.sendToPlayer(player, new NumismaticsMigrationOpenPayload(
                NumismaticsMigrationSnapshot.from(data(player.getServer()), token,
                        feedback, feedbackError).toJson()));
    }

    private static Session requireSession(ServerPlayer player, UUID token) {
        requireAdmin(player);
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || token == null || !session.token().equals(token)
                || System.currentTimeMillis() - session.touchedAt > SESSION_TTL_MILLIS) {
            throw new IllegalStateException("The migration setup session expired. Reopen it with the admin command.");
        }
        return session;
    }

    private static Upload requireUpload(ServerPlayer player, UUID uploadId) {
        Upload upload = UPLOADS.get(player.getUUID());
        if (upload == null || uploadId == null || !upload.id().equals(uploadId)) {
            throw new IllegalStateException("The file upload session is invalid or expired.");
        }
        return upload;
    }

    private static void requireAdmin(ServerPlayer player) {
        if (player == null || !player.hasPermissions(3)) {
            throw new SecurityException("UBS administrator permission level 3 is required.");
        }
    }

    private static NumismaticsMigrationSavedData data(MinecraftServer server) {
        return NumismaticsMigrationSavedData.get(server);
    }

    public static void ensureMaintenanceFreeze(MinecraftServer server) {
        if (server == null || server.tickRateManager().isFrozen()) return;
        server.tickRateManager().setFrozen(true);
        FROZEN_BY_MIGRATION.add(server);
    }

    public static void releaseMaintenanceFreeze(MinecraftServer server) {
        if (server != null && FROZEN_BY_MIGRATION.remove(server)) {
            server.tickRateManager().setFrozen(false);
        }
    }

    private static void expireSessions() {
        long cutoff = System.currentTimeMillis() - SESSION_TTL_MILLIS;
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().touchedAt < cutoff);
        UPLOADS.keySet().removeIf(playerId -> !SESSIONS.containsKey(playerId));
    }

    private static void notifyFailure(ServerPlayer player, String message) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
            return;
        }
        session.feedback(message, true);
        sendSnapshot(player, session.token());
    }

    private static void notifySuccess(ServerPlayer player, String message) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GREEN));
            return;
        }
        session.feedback(message, false);
        sendSnapshot(player, session.token());
    }

    private static String sanitizeFileName(String value) {
        String fileName = value == null ? "numismatics_bank.dat" : Path.of(value).getFileName().toString();
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable.", impossible);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null && cursor.getCause() != null) cursor = cursor.getCause();
        if (cursor == null) return "Unknown error";
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    private static final class Session {
        private final UUID token;
        private long touchedAt;
        private String feedbackMessage = "";
        private boolean feedbackError;
        private Session(UUID token, long touchedAt) { this.token = token; this.touchedAt = touchedAt; }
        UUID token() { return token; }
        String feedbackMessage() { return feedbackMessage; }
        boolean feedbackError() { return feedbackError; }
        void feedback(String message, boolean error) {
            feedbackMessage = message == null ? "" : message;
            feedbackError = error;
            touch();
        }
        void touch() { touchedAt = System.currentTimeMillis(); }
    }

    private static final class Upload {
        private final UUID id;
        private final String fileName;
        private final long size;
        private final int chunks;
        private final String sha256;
        private final byte[][] values;
        private int received;

        private Upload(UUID id, String fileName, long size, int chunks, String sha256) {
            this.id = id; this.fileName = fileName; this.size = size; this.chunks = chunks; this.sha256 = sha256;
            this.values = new byte[chunks][];
        }

        UUID id() { return id; }
        String sha256() { return sha256; }

        void accept(int index, byte[] bytes) {
            if (index < 0 || index >= chunks || bytes == null || bytes.length == 0 || bytes.length > 256 * 1024) {
                throw new IllegalArgumentException("Upload chunk is invalid.");
            }
            if (values[index] != null) throw new IllegalArgumentException("Duplicate upload chunk " + index + ".");
            values[index] = Arrays.copyOf(bytes, bytes.length);
            received++;
        }

        byte[] finish() throws IOException {
            if (received != chunks) throw new IOException("Upload is incomplete (" + received + "/" + chunks + ").");
            ByteArrayOutputStream output = new ByteArrayOutputStream((int) size);
            for (byte[] value : values) output.write(value);
            byte[] bytes = output.toByteArray();
            if (bytes.length != size) throw new IOException("Uploaded size does not match the declared file size.");
            return bytes;
        }
    }

    private static final class RuntimeJob {
        private final NumismaticsSourceSnapshot source;
        private final ArrayDeque<String> playerFiles;
        private final ArrayDeque<NumismaticsPreflightResult.ChunkRef> chunks;
        private final int total;
        private int processed;
        private boolean reconciliationStarted;

        private RuntimeJob(NumismaticsSourceSnapshot source, List<String> playerFiles,
                           List<NumismaticsPreflightResult.ChunkRef> chunks) {
            this.source = source;
            this.playerFiles = new ArrayDeque<>(playerFiles);
            this.chunks = new ArrayDeque<>(chunks);
            this.total = playerFiles.size() + chunks.size();
        }
    }
}
