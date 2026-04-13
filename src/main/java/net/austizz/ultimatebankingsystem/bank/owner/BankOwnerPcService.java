package net.austizz.ultimatebankingsystem.bank.owner;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.command.UBSAdminCommands;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankAppSummary;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankDataPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcDesktopDataPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcFileEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BankOwnerPcService {

    public static final class ActionResult {
        private final String action;
        private final boolean success;
        private final String message;

        public ActionResult(boolean success, String message) {
            this("", success, message);
        }

        public ActionResult(String action, boolean success, String message) {
            this.action = action == null ? "" : action;
            this.success = success;
            this.message = message == null ? "" : message;
        }

        public String action() {
            return action;
        }

        public boolean success() {
            return success;
        }

        public String message() {
            return message;
        }
    }

    private record DesktopContext(String dimensionId, int x, int y, int z) {
        private String storageKey() {
            return normalizeDim(dimensionId) + "|" + x + "|" + y + "|" + z;
        }

        private String label() {
            return normalizeDim(dimensionId) + " (" + x + ", " + y + ", " + z + ")";
        }
    }

    private static final ConcurrentHashMap<UUID, Long> LAST_BANK_CREATE_ATTEMPT_MILLIS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, DesktopContext> ACTIVE_DESKTOP_CONTEXT = new ConcurrentHashMap<>();
    private static final String DESKTOP_STORAGE_TAG = "ownerPcDesktopStorage";
    private static final int NBT_COMPOUND = 10;
    private static final int NBT_STRING = 8;
    private static final int DESKTOP_STORAGE_MAX_BYTES = 48 * 1024;
    private static final int DESKTOP_STORAGE_MAX_FILES = 64;
    private static final int DESKTOP_FILE_MAX_CHARS = 20_000;
    private static final int DESKTOP_FILE_NAME_MAX_CHARS = 48;
    private static final String DESKTOP_PIN_HASH_TAG = "desktopPinHash";
    private static final String DESKTOP_PIN_SALT_TAG = "desktopPinSalt";
    private static final String DESKTOP_RECOVERY_HASH_TAG = "desktopRecoveryHash";

    private BankOwnerPcService() {}

    public static List<OwnerPcBankAppSummary> listAccessibleApps(MinecraftServer server,
                                                                  CentralBank centralBank,
                                                                  UUID playerId) {
        return listAccessibleApps(server, centralBank, playerId, false);
    }

    public static List<OwnerPcBankAppSummary> listAccessibleApps(MinecraftServer server,
                                                                  CentralBank centralBank,
                                                                  UUID playerId,
                                                                  boolean includeCentralBankApp) {
        if (server == null || centralBank == null || playerId == null) {
            return List.of();
        }

        List<OwnerPcBankAppSummary> apps = new ArrayList<>();
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank == null) {
                continue;
            }
            if (bank.getBankId().equals(centralBank.getBankId()) && !includeCentralBankApp) {
                continue;
            }

            boolean owner = playerId.equals(bank.getBankOwnerId());
            if (bank.getBankId().equals(centralBank.getBankId()) && includeCentralBankApp) {
                owner = true;
            }
            String roleLabel = owner ? "OWNER" : resolveRoleLabel(centralBank, bank.getBankId(), playerId);
            if (!owner && roleLabel.isBlank()) {
                continue;
            }

            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
            String color = metadata.getString("color");
            if (color == null || color.isBlank()) {
                color = "#55AAFF";
            }
            String status = metadata.getString("status");
            if (status == null || status.isBlank()) {
                status = "ACTIVE";
            }
            if (bank.getBankId().equals(centralBank.getBankId()) && includeCentralBankApp) {
                roleLabel = "ADMIN";
            }

            apps.add(new OwnerPcBankAppSummary(
                    bank.getBankId(),
                    bank.getBankName(),
                    color,
                    status.toUpperCase(Locale.ROOT),
                    owner,
                    roleLabel
            ));
        }

        apps.sort(Comparator
                .comparing(OwnerPcBankAppSummary::owner).reversed()
                .thenComparing(OwnerPcBankAppSummary::bankName, String.CASE_INSENSITIVE_ORDER));
        return apps;
    }

    public static int countOwnedBanks(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) {
            return 0;
        }
        int count = 0;
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
                continue;
            }
            if (playerId.equals(bank.getBankOwnerId())) {
                count++;
            }
        }
        return count;
    }

    public static void rememberDesktopContext(UUID playerId, String dimensionId, int x, int y, int z) {
        if (playerId == null || dimensionId == null || dimensionId.isBlank()) {
            return;
        }
        ACTIVE_DESKTOP_CONTEXT.put(playerId, new DesktopContext(dimensionId.trim(), x, y, z));
    }

    public static OwnerPcDesktopDataPayload buildDesktopData(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) {
            return new OwnerPcDesktopDataPayload("Unknown PC", DESKTOP_STORAGE_MAX_BYTES, 0, false, List.of(), List.of());
        }
        DesktopContext context = ACTIVE_DESKTOP_CONTEXT.get(playerId);
        if (context == null) {
            return new OwnerPcDesktopDataPayload("Unknown PC", DESKTOP_STORAGE_MAX_BYTES, 0, false, List.of(), List.of());
        }
        CompoundTag userTag = getDesktopUserTag(centralBank, context, playerId, false);
        List<OwnerPcFileEntry> files = readDesktopFiles(userTag);
        Set<String> hiddenApps = readHiddenApps(userTag);
        int used = computeStorageBytes(files);
        boolean pinSet = isDesktopPinConfigured(userTag);
        return new OwnerPcDesktopDataPayload(
                context.label(),
                DESKTOP_STORAGE_MAX_BYTES,
                used,
                pinSet,
                files,
                hiddenApps.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()
        );
    }

    public static ActionResult executeDesktopAction(CentralBank centralBank,
                                                    UUID playerId,
                                                    String action,
                                                    String arg1,
                                                    String arg2) {
        if (centralBank == null || playerId == null) {
            return fail("DESKTOP", "Desktop storage is unavailable.");
        }
        DesktopContext context = ACTIVE_DESKTOP_CONTEXT.get(playerId);
        if (context == null) {
            return fail("DESKTOP", "Open a bank owner PC block first.");
        }

        String normalizedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if ("REFRESH".equals(normalizedAction)) {
            return ok("REFRESH", "Desktop refreshed.");
        }

        CompoundTag userTag = getDesktopUserTag(centralBank, context, playerId, true);
        List<OwnerPcFileEntry> files = readDesktopFiles(userTag);
        Set<String> hiddenApps = readHiddenApps(userTag);
        long now = System.currentTimeMillis();

        return switch (normalizedAction) {
            case "FILE_SAVE", "FILE_SAVE_TEXT", "FILE_SAVE_CANVAS" -> {
                String rawName = arg1 == null ? "" : arg1;
                String name = normalizeDesktopFileName(rawName);
                if (name.isBlank()) {
                    yield fail(normalizedAction, "Enter a valid file name first.");
                }

                String content = arg2 == null ? "" : arg2;
                if (content.length() > DESKTOP_FILE_MAX_CHARS) {
                    yield fail(normalizedAction, "File is too large for this PC.");
                }
                String kind = "FILE_SAVE_CANVAS".equals(normalizedAction) ? "CANVAS" : "TEXT";

                int existingIndex = findFileIndexByName(files, name);
                if (existingIndex < 0 && files.size() >= DESKTOP_STORAGE_MAX_FILES) {
                    yield fail(normalizedAction, "File limit reached on this PC.");
                }

                OwnerPcFileEntry newEntry = new OwnerPcFileEntry(kind, name, content, now);
                List<OwnerPcFileEntry> next = new ArrayList<>(files);
                if (existingIndex >= 0) {
                    next.set(existingIndex, newEntry);
                } else {
                    next.add(newEntry);
                }
                next.sort(Comparator
                        .comparingLong(OwnerPcFileEntry::updatedAtMillis).reversed()
                        .thenComparing(OwnerPcFileEntry::name, String.CASE_INSENSITIVE_ORDER));

                int usedBytes = computeStorageBytes(next);
                if (usedBytes > DESKTOP_STORAGE_MAX_BYTES) {
                    yield fail(normalizedAction, "Not enough PC storage. Delete a file first.");
                }

                writeDesktopFiles(userTag, next);
                writeHiddenApps(userTag, hiddenApps);
                commitDesktopUserTag(centralBank, context, playerId, userTag);
                String typeLabel = "CANVAS".equalsIgnoreCase(newEntry.kind()) ? "canvas" : "text";
                yield ok(normalizedAction, "Saved " + typeLabel + " file: " + name + " (" + usedBytes + "/" + DESKTOP_STORAGE_MAX_BYTES + " bytes).");
            }
            case "FILE_RENAME" -> {
                String currentName = normalizeDesktopFileName(arg1 == null ? "" : arg1);
                if (currentName.isBlank()) {
                    yield fail(normalizedAction, "Select a file to rename.");
                }

                int currentIndex = findFileIndexByName(files, currentName);
                if (currentIndex < 0) {
                    yield fail(normalizedAction, "File not found: " + currentName + ".");
                }

                String newName = normalizeDesktopFileName(arg2 == null ? "" : arg2);
                if (newName.isBlank()) {
                    yield fail(normalizedAction, "Enter a valid file name first.");
                }

                OwnerPcFileEntry currentEntry = files.get(currentIndex);
                if (currentEntry == null || currentEntry.name() == null || currentEntry.name().isBlank()) {
                    yield fail(normalizedAction, "File is unavailable.");
                }

                int collisionIndex = findFileIndexByName(files, newName);
                if (collisionIndex >= 0 && collisionIndex != currentIndex) {
                    yield fail(normalizedAction, "A file with that name already exists.");
                }

                OwnerPcFileEntry renamed = new OwnerPcFileEntry(
                        currentEntry.kind(),
                        newName,
                        currentEntry.content(),
                        now
                );
                List<OwnerPcFileEntry> next = new ArrayList<>(files);
                next.set(currentIndex, renamed);
                next.sort(Comparator
                        .comparingLong(OwnerPcFileEntry::updatedAtMillis).reversed()
                        .thenComparing(OwnerPcFileEntry::name, String.CASE_INSENSITIVE_ORDER));

                int usedBytes = computeStorageBytes(next);
                if (usedBytes > DESKTOP_STORAGE_MAX_BYTES) {
                    yield fail(normalizedAction, "Not enough PC storage. Use a shorter file name.");
                }

                writeDesktopFiles(userTag, next);
                writeHiddenApps(userTag, hiddenApps);
                commitDesktopUserTag(centralBank, context, playerId, userTag);
                yield ok(normalizedAction, "Renamed file to: " + newName + ".");
            }
            case "FILE_DELETE" -> {
                String name = normalizeDesktopFileName(arg1 == null ? "" : arg1);
                if (name.isBlank()) {
                    yield fail(normalizedAction, "Select a file to delete.");
                }
                int existingIndex = findFileIndexByName(files, name);
                if (existingIndex < 0) {
                    yield fail(normalizedAction, "File not found: " + name + ".");
                }
                List<OwnerPcFileEntry> next = new ArrayList<>(files);
                OwnerPcFileEntry removed = next.remove(existingIndex);
                writeDesktopFiles(userTag, next);
                writeHiddenApps(userTag, hiddenApps);
                commitDesktopUserTag(centralBank, context, playerId, userTag);
                yield ok(normalizedAction, "Deleted file: " + removed.name() + ".");
            }
            case "APP_VISIBILITY" -> {
                String appId = normalizeHiddenAppId(arg1);
                if (appId.isBlank()) {
                    yield fail(normalizedAction, "Invalid app id.");
                }
                boolean hide = parseHideFlag(arg2);
                if (hide && "utility:system_monitor".equals(appId)) {
                    yield fail(normalizedAction, "System Monitor cannot be hidden.");
                }
                if (hide) {
                    hiddenApps.add(appId);
                } else {
                    hiddenApps.remove(appId);
                }
                writeDesktopFiles(userTag, files);
                writeHiddenApps(userTag, hiddenApps);
                commitDesktopUserTag(centralBank, context, playerId, userTag);
                yield ok(normalizedAction, (hide ? "Hidden " : "Unhidden ") + appId + ".");
            }
            case "AUTH_SET_PIN" -> {
                String password = arg1 == null ? "" : arg1.trim();
                String recoveryPhrase = normalizeRecoveryPhrase(arg2);
                if (isDesktopPinConfigured(userTag)) {
                    yield fail(normalizedAction, "Password already exists. Use Forgot Password to reset.");
                }
                if (!isValidDesktopPassword(password)) {
                    yield fail(normalizedAction, "Password must be 4-64 characters.");
                }
                if (recoveryPhrase.isBlank() || recoveryPhrase.length() < 4) {
                    yield fail(normalizedAction, "Recovery phrase must be at least 4 characters.");
                }
                String salt = newDesktopSalt();
                userTag.putString(DESKTOP_PIN_SALT_TAG, salt);
                userTag.putString(DESKTOP_PIN_HASH_TAG, hashDesktopSecret(password, salt));
                userTag.putString(DESKTOP_RECOVERY_HASH_TAG, hashDesktopSecret(recoveryPhrase, salt));
                writeDesktopFiles(userTag, files);
                writeHiddenApps(userTag, hiddenApps);
                commitDesktopUserTag(centralBank, context, playerId, userTag);
                yield ok(normalizedAction, "PC password has been set.");
            }
            case "AUTH_VERIFY_PIN" -> {
                String password = arg1 == null ? "" : arg1.trim();
                if (!isDesktopPinConfigured(userTag)) {
                    yield fail(normalizedAction, "This PC has no password yet. Set one first.");
                }
                if (!isValidDesktopPassword(password)) {
                    yield fail(normalizedAction, "Password must be 4-64 characters.");
                }
                String salt = userTag.getString(DESKTOP_PIN_SALT_TAG);
                String expectedHash = userTag.getString(DESKTOP_PIN_HASH_TAG);
                String actualHash = hashDesktopSecret(password, salt);
                if (expectedHash == null || expectedHash.isBlank() || !expectedHash.equals(actualHash)) {
                    yield fail(normalizedAction, "Incorrect password.");
                }
                yield ok(normalizedAction, "Password verified.");
            }
            case "AUTH_RECOVER_RESET" -> {
                String recoveryPhrase = normalizeRecoveryPhrase(arg1);
                String newPassword = arg2 == null ? "" : arg2.trim();
                if (!isDesktopPinConfigured(userTag)) {
                    yield fail(normalizedAction, "This PC has no password yet. Set one first.");
                }
                if (!isValidDesktopPassword(newPassword)) {
                    yield fail(normalizedAction, "New password must be 4-64 characters.");
                }
                String salt = userTag.getString(DESKTOP_PIN_SALT_TAG);
                String expectedRecoveryHash = userTag.getString(DESKTOP_RECOVERY_HASH_TAG);
                String actualRecoveryHash = hashDesktopSecret(recoveryPhrase, salt);
                if (expectedRecoveryHash == null || expectedRecoveryHash.isBlank() || !expectedRecoveryHash.equals(actualRecoveryHash)) {
                    yield fail(normalizedAction, "Recovery phrase does not match.");
                }
                userTag.putString(DESKTOP_PIN_HASH_TAG, hashDesktopSecret(newPassword, salt));
                writeDesktopFiles(userTag, files);
                writeHiddenApps(userTag, hiddenApps);
                commitDesktopUserTag(centralBank, context, playerId, userTag);
                yield ok(normalizedAction, "Password has been reset.");
            }
            default -> fail(normalizedAction, "Unknown desktop action: " + normalizedAction);
        };
    }

    public static boolean canAccessBank(CentralBank centralBank, UUID playerId, UUID bankId) {
        return canAccessBank(centralBank, playerId, bankId, false);
    }

    public static boolean canAccessBank(CentralBank centralBank,
                                        UUID playerId,
                                        UUID bankId,
                                        boolean allowCentralBankAccess) {
        if (centralBank == null || playerId == null || bankId == null) {
            return false;
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return false;
        }
        if (bank.getBankId().equals(centralBank.getBankId())) {
            return allowCentralBankAccess;
        }
        if (playerId.equals(bank.getBankOwnerId())) {
            return true;
        }
        return !resolveRoleLabel(centralBank, bankId, playerId).isBlank();
    }

    public static boolean isOwner(CentralBank centralBank, UUID playerId, UUID bankId) {
        if (centralBank == null || playerId == null || bankId == null) {
            return false;
        }
        Bank bank = centralBank.getBank(bankId);
        return bank != null && playerId.equals(bank.getBankOwnerId());
    }

    public static OwnerPcBankDataPayload buildBankDataPayload(MinecraftServer server,
                                                               CentralBank centralBank,
                                                               UUID playerId,
                                                               UUID bankId) {
        return buildBankDataPayload(server, centralBank, playerId, bankId, false);
    }

    public static OwnerPcBankDataPayload buildBankDataPayload(MinecraftServer server,
                                                               CentralBank centralBank,
                                                               UUID playerId,
                                                               UUID bankId,
                                                               boolean allowCentralBankAccess) {
        if (server == null || centralBank == null || playerId == null || bankId == null) {
            return null;
        }

        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return null;
        }
        if (!canAccessBank(centralBank, playerId, bankId, allowCentralBankAccess)) {
            return null;
        }

        long gameTime = currentOverworldGameTime(server);
        refreshBankOperationalState(centralBank, bank, gameTime, server);

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        boolean ownerView = isOwner(centralBank, playerId, bankId)
                || (allowCentralBankAccess && bankId.equals(centralBank.getBankId()));

        String status = normalizeStatus(metadata.getString("status"));
        String ownershipModel = metadata.getString("ownershipModel");
        if (ownershipModel == null || ownershipModel.isBlank()) {
            ownershipModel = "SOLE";
        }
        String color = metadata.getString("color");
        if (color == null || color.isBlank()) {
            color = "#55AAFF";
        }
        String motto = metadata.getString("motto");
        if (motto == null) {
            motto = "";
        }

        BigDecimal reserve = bank.getDeclaredReserve().setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal deposits = bank.getTotalDeposits().setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal minReserve = deposits.multiply(BigDecimal.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()))
                .setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal reserveRatio = deposits.compareTo(BigDecimal.ZERO) > 0
                ? reserve.divide(deposits, 6, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100))
                : BigDecimal.valueOf(100);

        BigDecimal dailyCap = getDailyCapForBank(bank, metadata);
        BigDecimal dailyUsed = readBigDecimal(metadata, "dailyWithdrawn").setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal dailyRemaining = dailyCap.subtract(dailyUsed).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN);

        List<String> roles = new ArrayList<>();
        decodeUuidStringMap(metadata.getString("roles")).entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> roles.add(resolvePlayerName(server, entry.getKey()) + " - " + entry.getValue()));

        List<String> shares = new ArrayList<>();
        decodeShareMap(metadata.getString("shares")).entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(entry -> shares.add(resolvePlayerName(server, entry.getKey()) + " - " + entry.getValue().toPlainString() + "%"));

        List<String> cofounders = new ArrayList<>();
        decodeUuidList(metadata.getString("cofounders")).stream()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(id -> cofounders.add(resolvePlayerName(server, id)));

        List<String> employees = new ArrayList<>();
        decodeEmployeeMap(metadata.getString("employees")).forEach((id, spec) -> employees.add(
                resolvePlayerName(server, id) + " - " + spec.role() + " ($" + spec.salary().toPlainString() + ")"
        ));
        employees.sort(String.CASE_INSENSITIVE_ORDER);

        List<String> loanProducts = decodeLoanProducts(metadata.getString("loanProducts")).stream()
                .map(product -> product.name() + " | max $" + product.maxAmount().toPlainString()
                        + " | APR " + product.interestRate() + "% | " + product.durationTicks() + " ticks")
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        List<String> interbankOffers = new ArrayList<>();
        centralBank.getInterbankOffers().values().stream()
                .filter(tag -> tag.hasUUID("lenderBankId") && bankId.equals(tag.getUUID("lenderBankId"))
                        || tag.hasUUID("acceptedByBankId") && bankId.equals(tag.getUUID("acceptedByBankId")))
                .sorted(Comparator.comparingLong(tag -> tag.contains("createdTick") ? tag.getLong("createdTick") : 0L))
                .forEach(tag -> {
                    String id = shortId(tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID());
                    String amount = readBigDecimal(tag, "amount").toPlainString();
                    String state = normalizeStatus(tag.getString("status"));
                    interbankOffers.add(id + " | $" + amount + " | " + state);
                });

        List<String> interbankLoans = new ArrayList<>();
        centralBank.getInterbankLoans().values().stream()
                .filter(tag -> tag.hasUUID("lenderBankId") && bankId.equals(tag.getUUID("lenderBankId"))
                        || tag.hasUUID("borrowerBankId") && bankId.equals(tag.getUUID("borrowerBankId"))
                        || tag.hasUUID("bankId") && bankId.equals(tag.getUUID("bankId")))
                .sorted(Comparator.comparingLong(tag -> tag.contains("createdTick") ? tag.getLong("createdTick") : 0L))
                .forEach(tag -> {
                    String id = shortId(tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID());
                    String remaining = readBigDecimal(tag, "remaining").toPlainString();
                    String type = tag.contains("type") ? tag.getString("type") : "UNKNOWN";
                    String state = normalizeStatus(tag.getString("status"));
                    interbankLoans.add(id + " | " + type + " | remaining $" + remaining + " | " + state);
                });

        List<String> accountRoster = bank.getBankAccounts().values().stream()
                .sorted(Comparator.comparing(a -> a.getAccountUUID().toString()))
                .map(account -> resolvePlayerName(server, account.getPlayerUUID())
                        + " | " + account.getAccountType().label
                        + " | $" + account.getBalance().setScale(2, RoundingMode.HALF_EVEN).toPlainString()
                        + " | " + shortId(account.getAccountUUID()))
                .toList();

        List<String> certificateSchedule = bank.getBankAccounts().values().stream()
                .filter(account -> account.getAccountType() == AccountTypes.CertificateAccount)
                .sorted(Comparator.comparingLong(AccountHolder::getCertificateMaturityGameTime))
                .map(account -> shortId(account.getAccountUUID())
                        + " | " + resolvePlayerName(server, account.getPlayerUUID())
                        + " | tier " + account.getCertificateTier()
                        + " | maturity " + account.getCertificateMaturityGameTime())
                .toList();

        return new OwnerPcBankDataPayload(
                bankId,
                bank.getBankName(),
                status,
                resolvePlayerName(server, bank.getBankOwnerId()),
                ownershipModel,
                color,
                motto,
                reserve.toPlainString(),
                deposits.toPlainString(),
                reserveRatio.setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
                minReserve.toPlainString(),
                String.valueOf(bank.getBankAccounts().size()),
                dailyCap.toPlainString(),
                dailyUsed.toPlainString(),
                dailyRemaining.toPlainString(),
                BigDecimal.valueOf(centralBank.getFederalFundsRate()).setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
                ownerView,
                roles,
                shares,
                cofounders,
                employees,
                loanProducts,
                interbankOffers,
                interbankLoans,
                accountRoster,
                certificateSchedule
        );
    }

    public static ActionResult executeAction(MinecraftServer server,
                                             CentralBank centralBank,
                                             ServerPlayer player,
                                             UUID bankId,
                                             String action,
                                             String arg1,
                                             String arg2,
                                             String arg3,
                                             String arg4) {
        if (server == null || centralBank == null || player == null || bankId == null) {
            return new ActionResult(false, "Bank data is unavailable.");
        }
        boolean allowCentralBankAccess = bankId.equals(centralBank.getBankId()) && player.hasPermissions(3);
        if (!canAccessBank(centralBank, player.getUUID(), bankId, allowCentralBankAccess)) {
            return new ActionResult(false, "You do not have access to this bank app.");
        }

        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return new ActionResult(false, "Bank no longer exists.");
        }

        boolean owner = player.getUUID().equals(bank.getBankOwnerId()) || allowCentralBankAccess;
        String normalizedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);

        return switch (normalizedAction) {
            case "SHOW_INFO", "SHOW_RESERVE", "SHOW_DASHBOARD", "SHOW_ACCOUNTS", "SHOW_CDS",
                 "SHOW_LIMITS", "SHOW_ROLES", "SHOW_SHARES", "SHOW_COFOUNDERS", "SHOW_EMPLOYEES",
                 "SHOW_LOAN_PRODUCTS", "SHOW_LOANS", "SHOW_MARKET" ->
                    buildShowActionResult(server, centralBank, bank, normalizedAction, player.getUUID(), allowCentralBankAccess);
            case "SET_MOTTO" -> handleSetMotto(centralBank, bank, owner, arg1);
            case "SET_COLOR" -> handleSetColor(centralBank, bank, owner, arg1);
            case "SET_LIMIT" -> handleSetLimit(centralBank, bank, owner, arg1, arg2);
            case "ROLE_ASSIGN" -> handleRoleAssign(server, centralBank, bank, owner, arg1, arg2);
            case "ROLE_REVOKE" -> handleRoleRevoke(server, centralBank, bank, owner, arg1);
            case "SHARES_SET" -> handleSharesSet(server, centralBank, bank, owner, arg1, arg2);
            case "COFOUNDER_ADD" -> handleCofounderAdd(server, centralBank, bank, owner, arg1);
            case "HIRE" -> handleHire(server, centralBank, bank, owner, arg1, arg2, arg3);
            case "FIRE" -> handleFire(server, centralBank, bank, owner, arg1);
            case "TELLER_ISSUE" -> handleTellerIssue(server, bank, player, owner);
            case "TELLER_COUNT" -> handleTellerCount(server, bank, owner);
            case "BORROW" -> handleBorrow(server, centralBank, bank, owner, arg1);
            case "LEND_OFFER" -> handleLendOffer(server, centralBank, bank, owner, arg1, arg2, arg3);
            case "LEND_ACCEPT" -> handleLendAccept(server, centralBank, bank, owner, arg1);
            case "APPEAL" -> handleAppeal(server, centralBank, bank, player, owner, arg1);
            case "CREATE_LOAN_PRODUCT" -> handleCreateLoanProduct(centralBank, bank, owner, arg1, arg2, arg3, arg4);
            default -> new ActionResult(false, "Unknown action: " + normalizedAction);
        };
    }

    private static ActionResult buildShowActionResult(MinecraftServer server,
                                                      CentralBank centralBank,
                                                      Bank bank,
                                                      String action,
                                                      UUID viewerId,
                                                      boolean allowCentralBankAccess) {
        OwnerPcBankDataPayload data = buildBankDataPayload(server, centralBank, viewerId, bank.getBankId(), allowCentralBankAccess);
        if (data == null) {
            return new ActionResult(false, "Unable to load bank data.");
        }

        String header = "Bank: " + data.bankName() + " [" + data.status() + "]";
        String body;
        switch (action) {
            case "SHOW_INFO" -> body = joinLines(
                    "Owner: " + data.ownerName(),
                    "Model: " + data.ownershipModel(),
                    "Color: " + data.color(),
                    "Motto: " + (data.motto().isBlank() ? "-" : data.motto()),
                    "Accounts: " + data.accountsCount(),
                    "Reserve: $" + data.reserve(),
                    "Deposits: $" + data.deposits(),
                    "Reserve Ratio: " + data.reserveRatio() + "%",
                    "Minimum Reserve: $" + data.minReserve()
            );
            case "SHOW_RESERVE" -> body = joinLines(
                    "Reserve: $" + data.reserve(),
                    "Deposits: $" + data.deposits(),
                    "Reserve Ratio: " + data.reserveRatio() + "%",
                    "Minimum Reserve: $" + data.minReserve(),
                    "Daily Cap: $" + data.dailyCap(),
                    "Daily Used: $" + data.dailyUsed(),
                    "Daily Remaining: $" + data.dailyRemaining()
            );
            case "SHOW_DASHBOARD" -> {
                BigDecimal reserve = parseDecimal(data.reserve());
                BigDecimal minimum = parseDecimal(data.minReserve());
                BigDecimal dailyCap = parseDecimal(data.dailyCap());
                BigDecimal dailyUsed = parseDecimal(data.dailyUsed());
                String risk;
                if ("SUSPENDED".equalsIgnoreCase(data.status()) || "REVOKED".equalsIgnoreCase(data.status())
                        || reserve.compareTo(minimum) < 0) {
                    risk = "RED";
                } else if (dailyCap.compareTo(BigDecimal.ZERO) > 0
                        && dailyUsed.divide(dailyCap, 4, RoundingMode.HALF_EVEN).compareTo(BigDecimal.valueOf(0.90)) >= 0) {
                    risk = "YELLOW";
                } else {
                    risk = "GREEN";
                }
                body = joinLines(
                        "Status: " + data.status(),
                        "Risk: " + risk,
                        "Reserve: $" + data.reserve(),
                        "Deposits: $" + data.deposits(),
                        "Reserve Ratio: " + data.reserveRatio() + "%",
                        "Daily Remaining: $" + data.dailyRemaining(),
                        "Federal Funds: " + data.federalFundsRate() + "%"
                );
            }
            case "SHOW_ACCOUNTS" -> body = formatList("Account Roster (" + data.accountRoster().size() + ")", data.accountRoster());
            case "SHOW_CDS" -> body = formatList("Certificates (" + data.certificateSchedule().size() + ")", data.certificateSchedule());
            case "SHOW_LIMITS" -> body = joinLines(
                    "Daily Cap: $" + data.dailyCap(),
                    "Daily Used: $" + data.dailyUsed(),
                    "Daily Remaining: $" + data.dailyRemaining(),
                    "Minimum Reserve: $" + data.minReserve()
            );
            case "SHOW_ROLES" -> body = formatList("Roles (" + data.roles().size() + ")", data.roles());
            case "SHOW_SHARES" -> body = formatList("Shares (" + data.shares().size() + ")", data.shares());
            case "SHOW_COFOUNDERS" -> body = formatList("Cofounders (" + data.cofounders().size() + ")", data.cofounders());
            case "SHOW_EMPLOYEES" -> body = formatList("Employees (" + data.employees().size() + ")", data.employees());
            case "SHOW_LOAN_PRODUCTS" -> body = formatList("Loan Products (" + data.loanProducts().size() + ")", data.loanProducts());
            case "SHOW_LOANS" -> body = formatList("Interbank Loans (" + data.interbankLoans().size() + ")", data.interbankLoans());
            case "SHOW_MARKET" -> {
                long nowTick = currentOverworldGameTime(server);
                List<String> market = centralBank.getInterbankOffers().values().stream()
                        .filter(tag -> "OPEN".equalsIgnoreCase(tag.getString("status")))
                        .filter(tag -> !tag.contains("expiryTick") || tag.getLong("expiryTick") >= nowTick)
                        .sorted(Comparator.comparingLong(tag -> tag.contains("createdTick") ? tag.getLong("createdTick") : 0L))
                        .map(tag -> {
                            UUID lenderBankId = tag.hasUUID("lenderBankId") ? tag.getUUID("lenderBankId") : null;
                            String lender = lenderBankId == null
                                    ? "unknown"
                                    : centralBank.getBank(lenderBankId) == null
                                    ? shortId(lenderBankId)
                                    : centralBank.getBank(lenderBankId).getBankName();
                            String amount = readBigDecimal(tag, "amount").toPlainString();
                            String rate = String.valueOf(tag.contains("annualRate") ? tag.getDouble("annualRate") : 0.0);
                            String term = String.valueOf(tag.contains("termTicks") ? tag.getLong("termTicks") : 0L);
                            UUID id = tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID();
                            return id + " | " + lender + " | $" + amount + " | APR " + rate + "% | " + term + " ticks";
                        })
                        .toList();
                body = formatList("Open Market Offers (" + market.size() + ")", market);
            }
            default -> body = "No data available for action: " + action;
        }

        return new ActionResult(true, header + "\n" + body);
    }

    public static ActionResult createBank(MinecraftServer server,
                                          CentralBank centralBank,
                                          ServerPlayer player,
                                          String bankName,
                                          String ownershipModel) {
        if (server == null || centralBank == null || player == null) {
            return new ActionResult(false, "Bank data is unavailable.");
        }
        if (!Config.PLAYER_BANKS_ENABLED.get()) {
            return new ActionResult(false, "Player-created banks are disabled by config.");
        }

        String normalizedName = normalizeBankName(bankName);
        if (normalizedName.isBlank()) {
            return new ActionResult(false, "Bank name cannot be empty.");
        }
        if (normalizedName.length() > Config.PLAYER_BANKS_NAME_MAX_LENGTH.get()) {
            return new ActionResult(false, "Bank name is too long (max " + Config.PLAYER_BANKS_NAME_MAX_LENGTH.get() + ").");
        }
        if (resolveBankByName(centralBank, normalizedName) != null) {
            return new ActionResult(false, "A bank with that name already exists.");
        }

        int maxOwned = Math.max(1, Config.PLAYER_BANKS_MAX_BANKS_PER_PLAYER.get());
        int currentlyOwned = countOwnedBanks(centralBank, player.getUUID());
        if (currentlyOwned >= maxOwned) {
            return new ActionResult(false, "You already own the max number of banks (" + maxOwned + ").");
        }

        long nowMillis = System.currentTimeMillis();
        long cooldownMs = Math.max(0, Config.PLAYER_BANKS_CREATION_COOLDOWN_HOURS.get()) * 60L * 60L * 1000L;
        Long lastAttempt = LAST_BANK_CREATE_ATTEMPT_MILLIS.get(player.getUUID());
        if (cooldownMs > 0L && lastAttempt != null && (nowMillis - lastAttempt) < cooldownMs) {
            long remainingMs = cooldownMs - (nowMillis - lastAttempt);
            long remainingMinutes = Math.max(1L, (remainingMs + 59_999L) / 60_000L);
            return new ActionResult(false, "You must wait " + remainingMinutes + " more minute(s) before another attempt.");
        }

        int requiredPlayHours = Math.max(0, Config.PLAYER_BANKS_MIN_PLAYTIME_HOURS.get());
        int playTimeTicks = player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME);
        long playHours = playTimeTicks / (20L * 60L * 60L);
        if (playHours < requiredPlayHours) {
            return new ActionResult(false, "You need at least " + requiredPlayHours + " play-time hour(s) to create a bank.");
        }

        AccountHolder fundingAccount = findPrimaryAccount(centralBank, player.getUUID());
        if (fundingAccount == null) {
            var accounts = centralBank.SearchForAccount(player.getUUID());
            if (!accounts.isEmpty()) {
                fundingAccount = accounts.values().iterator().next();
            }
        }
        if (fundingAccount == null) {
            return new ActionResult(false, "You need a bank account before creating a player bank.");
        }

        BigDecimal minimumBalance = BigDecimal.valueOf(Math.max(0, Config.PLAYER_BANKS_MIN_BALANCE.get()));
        if (fundingAccount.getBalance().compareTo(minimumBalance) < 0) {
            return new ActionResult(false,
                    "Eligibility check failed: minimum required balance is $" + minimumBalance.toPlainString() + ".");
        }

        BigDecimal creationFee = BigDecimal.valueOf(Math.max(0, Config.PLAYER_BANKS_CREATION_FEE.get()));
        boolean charterWaived = UBSAdminCommands.consumeCharterFeeWaiver(player.getUUID());
        BigDecimal charterFee = charterWaived
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(Math.max(0, Config.BANK_CHARTER_FEE.get()));
        BigDecimal totalFee = creationFee.add(charterFee);
        if (fundingAccount.getBalance().compareTo(totalFee) < 0) {
            return new ActionResult(false,
                    "Cannot afford bank creation fees. Required: $" + totalFee.toPlainString() + ".");
        }

        String normalizedOwnership = normalizeOwnershipModel(ownershipModel);
        LAST_BANK_CREATE_ATTEMPT_MILLIS.put(player.getUUID(), nowMillis);

        if (Config.PLAYER_BANKS_REQUIRE_ADMIN_APPROVAL.get()) {
            boolean hasPending = centralBank.getBankApplications().values().stream()
                    .anyMatch(tag -> player.getUUID().equals(readUuid(tag, "applicant"))
                            && "PENDING".equalsIgnoreCase(tag.getString("status")));
            if (hasPending) {
                return new ActionResult(false, "You already have a pending bank application.");
            }

            UUID applicationId = UUID.randomUUID();
            CompoundTag appTag = new CompoundTag();
            appTag.putUUID("id", applicationId);
            appTag.putUUID("applicant", player.getUUID());
            appTag.putString("bankName", normalizedName);
            appTag.putString("ownershipModel", normalizedOwnership);
            appTag.putString("status", "PENDING");
            appTag.putLong("createdMillis", nowMillis);
            appTag.putString("creationFee", creationFee.toPlainString());
            appTag.putString("charterFee", charterFee.toPlainString());
            appTag.putUUID("fundingAccountId", fundingAccount.getAccountUUID());
            centralBank.getBankApplications().put(applicationId, appTag);
            BankManager.markDirty();

            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (!online.hasPermissions(3)) {
                    continue;
                }
                online.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§6[UBS] New bank application from "
                                + player.getName().getString()
                                + " for '" + normalizedName + "' (ID: " + applicationId + ")."
                ));
            }

            return new ActionResult(true, "Application submitted (ID: " + shortId(applicationId) + ").");
        }

        return finalizeBankCreation(
                centralBank,
                player,
                fundingAccount,
                normalizedName,
                normalizedOwnership,
                creationFee,
                charterFee
        );
    }

    private static ActionResult handleSetMotto(CentralBank centralBank, Bank bank, boolean owner, String mottoRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can update motto.");
        }
        String motto = mottoRaw == null ? "" : mottoRaw.trim();
        if (motto.length() > 80) {
            return new ActionResult(false, "Motto is too long (max 80 characters).");
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("motto", motto);
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        return new ActionResult(true, "Motto updated.");
    }

    private static ActionResult handleSetColor(CentralBank centralBank, Bank bank, boolean owner, String colorRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can update color.");
        }
        String color = normalizeBankColor(colorRaw);
        if (color == null) {
            return new ActionResult(false, "Invalid color. Use #RRGGBB or names like blue/red/green.");
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("color", color);
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        return new ActionResult(true, "Brand color updated to " + color + ".");
    }

    private static ActionResult handleSetLimit(CentralBank centralBank,
                                               Bank bank,
                                               boolean owner,
                                               String typeRaw,
                                               String amountRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can update limits.");
        }

        BigDecimal amount = parsePositiveWholeAmount(amountRaw);
        if (amount == null) {
            return new ActionResult(false, "Amount must be a positive whole number.");
        }

        String type = typeRaw == null ? "" : typeRaw.trim().toLowerCase(Locale.ROOT);
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());

        switch (type) {
            case "single", "transaction", "singletransaction" -> {
                BigDecimal maxAllowed = BigDecimal.valueOf(Config.GLOBAL_MAX_SINGLE_TRANSACTION.get());
                if (amount.compareTo(maxAllowed) > 0) {
                    return new ActionResult(false, "Single limit cannot exceed global max $" + maxAllowed.toPlainString() + ".");
                }
                metadata.putString("limitSingle", amount.toPlainString());
            }
            case "dailyplayer", "playerdaily" -> {
                BigDecimal maxAllowed = BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_PLAYER_VOLUME.get());
                if (amount.compareTo(maxAllowed) > 0) {
                    return new ActionResult(false, "Daily player limit cannot exceed global max $" + maxAllowed.toPlainString() + ".");
                }
                metadata.putString("limitDailyPlayer", amount.toPlainString());
            }
            case "dailybank", "bankdaily" -> {
                BigDecimal maxAllowed = BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_BANK_VOLUME.get());
                if (amount.compareTo(maxAllowed) > 0) {
                    return new ActionResult(false, "Daily bank limit cannot exceed global max $" + maxAllowed.toPlainString() + ".");
                }
                metadata.putString("limitDailyBank", amount.toPlainString());
            }
            default -> {
                return new ActionResult(false, "Unknown limit type. Use single, dailyplayer, or dailybank.");
            }
        }

        centralBank.putBankMetadata(bank.getBankId(), metadata);
        return new ActionResult(true, "Limit updated.");
    }

    private static ActionResult handleRoleAssign(MinecraftServer server,
                                                 CentralBank centralBank,
                                                 Bank bank,
                                                 boolean owner,
                                                 String targetRaw,
                                                 String roleRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can assign roles.");
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        if (!"ROLE_BASED".equalsIgnoreCase(metadata.getString("ownershipModel"))) {
            return new ActionResult(false, "This bank is not configured for role-based governance.");
        }

        UUID targetId = resolvePlayerId(server, targetRaw);
        if (targetId == null) {
            return new ActionResult(false, "Target player not found. Use online name or UUID.");
        }

        String role = roleRaw == null ? "" : roleRaw.trim().toUpperCase(Locale.ROOT);
        if (!List.of("FOUNDER", "DIRECTOR", "TELLER", "AUDITOR").contains(role)) {
            return new ActionResult(false, "Role must be FOUNDER, DIRECTOR, TELLER, or AUDITOR.");
        }

        Map<UUID, String> roleMap = decodeUuidStringMap(metadata.getString("roles"));
        roleMap.put(targetId, role);
        metadata.putString("roles", encodeUuidStringMap(roleMap));
        centralBank.putBankMetadata(bank.getBankId(), metadata);

        ServerPlayer onlineTarget = server.getPlayerList().getPlayer(targetId);
        if (onlineTarget != null) {
            onlineTarget.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§aYou were assigned role §e" + role + " §aat bank " + bank.getBankName()
            ));
        }

        return new ActionResult(true, "Assigned role " + role + " to " + resolvePlayerName(server, targetId) + ".");
    }

    private static ActionResult handleRoleRevoke(MinecraftServer server,
                                                 CentralBank centralBank,
                                                 Bank bank,
                                                 boolean owner,
                                                 String targetRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can revoke roles.");
        }

        UUID targetId = resolvePlayerId(server, targetRaw);
        if (targetId == null) {
            return new ActionResult(false, "Target player not found. Use online name or UUID.");
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, String> roleMap = decodeUuidStringMap(metadata.getString("roles"));
        roleMap.remove(targetId);
        metadata.putString("roles", encodeUuidStringMap(roleMap));
        centralBank.putBankMetadata(bank.getBankId(), metadata);

        return new ActionResult(true, "Role revoked for " + resolvePlayerName(server, targetId) + ".");
    }

    private static ActionResult handleSharesSet(MinecraftServer server,
                                                CentralBank centralBank,
                                                Bank bank,
                                                boolean owner,
                                                String targetRaw,
                                                String percentRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can manage shares.");
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        if (!"PERCENTAGE_SHARES".equalsIgnoreCase(metadata.getString("ownershipModel"))) {
            return new ActionResult(false, "This bank is not using percentage-share governance.");
        }

        UUID targetId = resolvePlayerId(server, targetRaw);
        if (targetId == null) {
            return new ActionResult(false, "Target player not found. Use online name or UUID.");
        }

        BigDecimal percent;
        try {
            percent = new BigDecimal(percentRaw == null ? "" : percentRaw.trim()).setScale(2, RoundingMode.HALF_EVEN);
        } catch (NumberFormatException ex) {
            return new ActionResult(false, "Invalid percent.");
        }
        if (percent.compareTo(BigDecimal.ZERO) <= 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            return new ActionResult(false, "Percent must be > 0 and <= 100.");
        }

        Map<UUID, BigDecimal> shares = decodeShareMap(metadata.getString("shares"));
        shares.put(targetId, percent);
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : shares.values()) {
            total = total.add(value);
        }
        if (total.compareTo(BigDecimal.valueOf(100)) > 0) {
            return new ActionResult(false, "Total shares would exceed 100% (" + total.toPlainString() + "%).");
        }

        metadata.putString("shares", encodeShareMap(shares));
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        return new ActionResult(true, "Shares set for " + resolvePlayerName(server, targetId) + ".");
    }

    private static ActionResult handleCofounderAdd(MinecraftServer server,
                                                   CentralBank centralBank,
                                                   Bank bank,
                                                   boolean owner,
                                                   String targetRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can manage cofounders.");
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        if (!"FIXED_COFOUNDERS".equalsIgnoreCase(metadata.getString("ownershipModel"))) {
            return new ActionResult(false, "This bank is not configured for fixed co-founders.");
        }

        UUID targetId = resolvePlayerId(server, targetRaw);
        if (targetId == null) {
            return new ActionResult(false, "Target player not found. Use online name or UUID.");
        }

        List<UUID> cofounders = decodeUuidList(metadata.getString("cofounders"));
        if (!cofounders.contains(targetId)) {
            cofounders.add(targetId);
            metadata.putString("cofounders", encodeUuidList(cofounders));
            centralBank.putBankMetadata(bank.getBankId(), metadata);
        }

        return new ActionResult(true, "Added cofounder " + resolvePlayerName(server, targetId) + ".");
    }

    private static ActionResult handleHire(MinecraftServer server,
                                           CentralBank centralBank,
                                           Bank bank,
                                           boolean owner,
                                           String targetRaw,
                                           String roleRaw,
                                           String salaryRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can hire employees.");
        }

        UUID targetId = resolvePlayerId(server, targetRaw);
        if (targetId == null) {
            return new ActionResult(false, "Target player not found. Use online name or UUID.");
        }

        String role = roleRaw == null ? "" : roleRaw.trim().toUpperCase(Locale.ROOT);
        if (!List.of("TELLER", "DIRECTOR", "AUDITOR", "STAFF").contains(role)) {
            return new ActionResult(false, "Role must be TELLER, DIRECTOR, AUDITOR, or STAFF.");
        }

        BigDecimal salary;
        try {
            salary = new BigDecimal(salaryRaw == null ? "" : salaryRaw.trim());
        } catch (NumberFormatException ex) {
            return new ActionResult(false, "Invalid salary.");
        }
        if (salary.compareTo(BigDecimal.ZERO) < 0) {
            return new ActionResult(false, "Salary must be non-negative.");
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, EmployeeSpec> employees = decodeEmployeeMap(metadata.getString("employees"));
        employees.put(targetId, new EmployeeSpec(role, salary));
        metadata.putString("employees", encodeEmployeeMap(employees));
        centralBank.putBankMetadata(bank.getBankId(), metadata);

        ServerPlayer onlineTarget = server.getPlayerList().getPlayer(targetId);
        if (onlineTarget != null) {
            onlineTarget.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§aYou were hired by " + bank.getBankName() + " as " + role + " ($" + salary.toPlainString() + ")."
            ));
        }

        return new ActionResult(true,
                "Hired " + resolvePlayerName(server, targetId) + " as " + role + " ($" + salary.toPlainString() + ").");
    }

    private static ActionResult handleFire(MinecraftServer server,
                                           CentralBank centralBank,
                                           Bank bank,
                                           boolean owner,
                                           String targetRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can fire employees.");
        }

        UUID targetId = resolvePlayerId(server, targetRaw);
        if (targetId == null) {
            return new ActionResult(false, "Target player not found. Use online name or UUID.");
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, EmployeeSpec> employees = decodeEmployeeMap(metadata.getString("employees"));
        employees.remove(targetId);
        metadata.putString("employees", encodeEmployeeMap(employees));
        centralBank.putBankMetadata(bank.getBankId(), metadata);

        ServerPlayer onlineTarget = server.getPlayerList().getPlayer(targetId);
        if (onlineTarget != null) {
            onlineTarget.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cYou were removed from employment at " + bank.getBankName() + "."
            ));
        }

        return new ActionResult(true, "Fired " + resolvePlayerName(server, targetId) + ".");
    }

    private static ActionResult handleTellerIssue(MinecraftServer server,
                                                  Bank bank,
                                                  ServerPlayer actor,
                                                  boolean owner) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can issue teller eggs.");
        }
        if (server == null || bank == null || actor == null) {
            return new ActionResult(false, "Bank teller service is unavailable.");
        }

        int activeCount = BankTellerEntity.countActiveTellersForBank(server, bank.getBankId());
        if (activeCount >= BankTellerEntity.MAX_TELLERS_PER_BANK) {
            return new ActionResult(false,
                    bank.getBankName() + " already has the max "
                            + BankTellerEntity.MAX_TELLERS_PER_BANK + " active tellers.");
        }

        ItemStack egg = new ItemStack(ModItems.BANK_TELLER_SPAWN_EGG.get());
        BankTellerEntity.applyBankBindingToEgg(egg, bank.getBankId(), bank.getBankName());
        if (!actor.getInventory().add(egg)) {
            actor.drop(egg, false);
        }

        return new ActionResult(true,
                "Issued teller egg for " + bank.getBankName()
                        + ". Active tellers: " + activeCount + "/" + BankTellerEntity.MAX_TELLERS_PER_BANK + ".");
    }

    private static ActionResult handleTellerCount(MinecraftServer server,
                                                  Bank bank,
                                                  boolean owner) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can view teller count.");
        }
        if (server == null || bank == null) {
            return new ActionResult(false, "Bank teller service is unavailable.");
        }

        int activeCount = BankTellerEntity.countActiveTellersForBank(server, bank.getBankId());
        return new ActionResult(true,
                "Active tellers for " + bank.getBankName() + ": "
                        + activeCount + "/" + BankTellerEntity.MAX_TELLERS_PER_BANK + ".");
    }

    private static ActionResult handleBorrow(MinecraftServer server,
                                             CentralBank centralBank,
                                             Bank bank,
                                             boolean owner,
                                             String amountRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can borrow from central bank.");
        }

        BigDecimal amount = parsePositiveWholeAmount(amountRaw);
        if (amount == null) {
            return new ActionResult(false, "Amount must be a positive whole number.");
        }
        if (amount.compareTo(BigDecimal.valueOf(Math.max(1, Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()))) > 0) {
            return new ActionResult(false, "Borrow amount exceeds global transaction cap.");
        }

        long gameTime = currentOverworldGameTime(server);
        refreshBankOperationalState(centralBank, bank, gameTime, server);
        String status = getBankStatus(centralBank, bank);
        if ("SUSPENDED".equals(status) || "REVOKED".equals(status)) {
            return new ActionResult(false, "This bank cannot borrow while " + status.toLowerCase(Locale.ROOT) + ".");
        }

        double annualRate = Math.max(Config.LOAN_BASE_INTEREST_RATE.get() + 2.0, centralBank.getFederalFundsRate());
        int payments = Math.max(1, Config.LOAN_TERM_PAYMENTS.get());
        long interval = Math.max(20, Config.LOAN_PAYMENT_INTERVAL_TICKS.get());
        BigDecimal totalRepayable = amount
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(annualRate).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN)))
                .setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal periodic = totalRepayable
                .divide(BigDecimal.valueOf(payments), 2, RoundingMode.HALF_EVEN);

        UUID loanId = UUID.randomUUID();
        CompoundTag loan = new CompoundTag();
        loan.putUUID("id", loanId);
        loan.putString("type", "CB_DISCOUNT");
        loan.putUUID("bankId", bank.getBankId());
        loan.putString("principal", amount.toPlainString());
        loan.putString("remaining", totalRepayable.toPlainString());
        loan.putDouble("annualRate", annualRate);
        loan.putString("periodicPayment", periodic.toPlainString());
        loan.putInt("paymentsRemaining", payments);
        loan.putLong("paymentIntervalTicks", interval);
        loan.putLong("nextDueTick", gameTime + interval);
        loan.putLong("createdTick", gameTime);
        loan.putString("status", "ACTIVE");
        centralBank.getInterbankLoans().put(loanId, loan);

        bank.setReserve(bank.getDeclaredReserve().add(amount));
        recordSettlement(centralBank, centralBank.getBankId(), bank.getBankId(), amount, "CB_LOAN_DISBURSEMENT", true);
        BankManager.markDirty();

        return new ActionResult(true, "Central bank loan issued: " + shortId(loanId) + ".");
    }

    private static ActionResult handleLendOffer(MinecraftServer server,
                                                CentralBank centralBank,
                                                Bank lenderBank,
                                                boolean owner,
                                                String amountRaw,
                                                String annualRateRaw,
                                                String termTicksRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can post inter-bank offers.");
        }

        BigDecimal amount = parsePositiveWholeAmount(amountRaw);
        if (amount == null) {
            return new ActionResult(false, "Amount must be a positive whole number.");
        }

        double annualRate;
        try {
            annualRate = Double.parseDouble(annualRateRaw == null ? "" : annualRateRaw.trim());
        } catch (NumberFormatException ex) {
            return new ActionResult(false, "Invalid annual rate.");
        }
        if (annualRate <= 0.0 || annualRate > 1000.0) {
            return new ActionResult(false, "Annual rate must be > 0 and <= 1000.");
        }

        long termTicks;
        try {
            termTicks = Long.parseLong(termTicksRaw == null ? "" : termTicksRaw.trim());
        } catch (NumberFormatException ex) {
            return new ActionResult(false, "Invalid term ticks.");
        }
        if (termTicks < 20L) {
            return new ActionResult(false, "Term must be at least 20 ticks.");
        }

        if (lenderBank.getDeclaredReserve().compareTo(amount) < 0) {
            return new ActionResult(false, "Insufficient reserve to back this offer.");
        }

        long gameTime = currentOverworldGameTime(server);
        UUID offerId = UUID.randomUUID();
        CompoundTag offer = new CompoundTag();
        offer.putUUID("id", offerId);
        offer.putUUID("lenderBankId", lenderBank.getBankId());
        offer.putString("amount", amount.toPlainString());
        offer.putDouble("annualRate", annualRate);
        offer.putLong("termTicks", termTicks);
        offer.putLong("createdTick", gameTime);
        offer.putLong("expiryTick", gameTime + Math.max(termTicks, Config.WITHDRAWAL_QUEUE_EXPIRY_TICKS.get()));
        offer.putString("status", "OPEN");
        centralBank.getInterbankOffers().put(offerId, offer);
        BankManager.markDirty();

        return new ActionResult(true, "Inter-bank offer posted: " + shortId(offerId) + ".");
    }

    private static ActionResult handleLendAccept(MinecraftServer server,
                                                 CentralBank centralBank,
                                                 Bank borrowerBank,
                                                 boolean owner,
                                                 String offerIdRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can accept offers.");
        }

        UUID offerId = parseUuid(offerIdRaw);
        if (offerId == null) {
            return new ActionResult(false, "Offer ID must be a full UUID.");
        }

        CompoundTag offer = centralBank.getInterbankOffers().get(offerId);
        if (offer == null) {
            return new ActionResult(false, "Offer not found.");
        }
        if (!"OPEN".equalsIgnoreCase(offer.getString("status"))) {
            return new ActionResult(false, "Offer is not open.");
        }

        long nowTick = currentOverworldGameTime(server);
        if (offer.contains("expiryTick") && offer.getLong("expiryTick") < nowTick) {
            offer.putString("status", "EXPIRED");
            centralBank.getInterbankOffers().put(offerId, offer);
            BankManager.markDirty();
            return new ActionResult(false, "Offer has expired.");
        }

        UUID lenderBankId = offer.getUUID("lenderBankId");
        Bank lenderBank = centralBank.getBank(lenderBankId);
        if (lenderBank == null) {
            return new ActionResult(false, "Lender bank no longer exists.");
        }
        if (lenderBank.getBankId().equals(borrowerBank.getBankId())) {
            return new ActionResult(false, "You cannot accept your own offer.");
        }

        BigDecimal principal = readBigDecimal(offer, "amount");
        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            return new ActionResult(false, "Offer amount is invalid.");
        }
        if (lenderBank.getDeclaredReserve().compareTo(principal) < 0) {
            recordSettlement(
                    centralBank,
                    lenderBank.getBankId(),
                    borrowerBank.getBankId(),
                    principal,
                    "INTERBANK_ACCEPT_FAILED_INSUFFICIENT_LENDER_RESERVE",
                    false
            );
            return new ActionResult(false, "Lender bank no longer has sufficient reserve.");
        }

        double annualRate = offer.getDouble("annualRate");
        long termTicks = Math.max(20L, offer.getLong("termTicks"));
        BigDecimal totalRepayable = principal
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(annualRate).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN)))
                .setScale(2, RoundingMode.HALF_EVEN);

        lenderBank.setReserve(lenderBank.getDeclaredReserve().subtract(principal));
        borrowerBank.setReserve(borrowerBank.getDeclaredReserve().add(principal));

        UUID loanId = UUID.randomUUID();
        CompoundTag loan = new CompoundTag();
        loan.putUUID("id", loanId);
        loan.putString("type", "INTERBANK");
        loan.putUUID("lenderBankId", lenderBank.getBankId());
        loan.putUUID("borrowerBankId", borrowerBank.getBankId());
        loan.putString("principal", principal.toPlainString());
        loan.putString("remaining", totalRepayable.toPlainString());
        loan.putDouble("annualRate", annualRate);
        loan.putLong("termTicks", termTicks);
        loan.putLong("createdTick", nowTick);
        loan.putLong("maturityTick", nowTick + termTicks);
        loan.putString("status", "ACTIVE");
        centralBank.getInterbankLoans().put(loanId, loan);

        offer.putString("status", "ACCEPTED");
        offer.putUUID("acceptedByBankId", borrowerBank.getBankId());
        offer.putLong("acceptedTick", nowTick);
        centralBank.getInterbankOffers().put(offerId, offer);

        recordSettlement(
                centralBank,
                lenderBank.getBankId(),
                borrowerBank.getBankId(),
                principal,
                "INTERBANK_LOAN_ORIGINATION:" + loanId,
                true
        );

        BankManager.markDirty();

        ServerPlayer lenderOwner = server.getPlayerList().getPlayer(lenderBank.getBankOwnerId());
        if (lenderOwner != null) {
            lenderOwner.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§aYour inter-bank offer " + shortId(offerId)
                            + " was accepted by " + borrowerBank.getBankName()
                            + " for $" + principal.toPlainString()
            ));
        }

        return new ActionResult(true, "Accepted offer " + shortId(offerId) + ".");
    }

    private static ActionResult handleAppeal(MinecraftServer server,
                                             CentralBank centralBank,
                                             Bank bank,
                                             ServerPlayer actor,
                                             boolean owner,
                                             String messageRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can submit appeals.");
        }

        String message = messageRaw == null ? "" : messageRaw.trim();
        if (message.isBlank()) {
            return new ActionResult(false, "Appeal message cannot be empty.");
        }
        if (message.length() > 256) {
            return new ActionResult(false, "Appeal message is too long (max 256 chars).");
        }

        boolean hasPendingAppeal = centralBank.getBankAppeals().values().stream()
                .anyMatch(tag -> actor.getUUID().equals(readUuid(tag, "playerId"))
                        && "PENDING".equalsIgnoreCase(tag.getString("status")));
        if (hasPendingAppeal) {
            return new ActionResult(false, "You already have a pending appeal.");
        }

        UUID appealId = UUID.randomUUID();
        CompoundTag appeal = new CompoundTag();
        appeal.putUUID("id", appealId);
        appeal.putUUID("playerId", actor.getUUID());
        appeal.putUUID("bankId", bank.getBankId());
        appeal.putString("playerName", actor.getName().getString());
        appeal.putString("message", message);
        appeal.putString("status", "PENDING");
        appeal.putLong("createdMillis", System.currentTimeMillis());
        centralBank.getBankAppeals().put(appealId, appeal);
        BankManager.markDirty();

        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (!online.hasPermissions(3)) {
                continue;
            }
            online.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§6[UBS] New bank appeal from " + actor.getName().getString()
                            + " (ID: " + appealId + ")."
            ));
        }

        return new ActionResult(true, "Appeal submitted: " + shortId(appealId) + ".");
    }

    private static ActionResult handleCreateLoanProduct(CentralBank centralBank,
                                                        Bank bank,
                                                        boolean owner,
                                                        String nameRaw,
                                                        String maxAmountRaw,
                                                        String rateRaw,
                                                        String durationRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can create loan products.");
        }

        String name = nameRaw == null ? "" : nameRaw.trim();
        if (name.isBlank()) {
            return new ActionResult(false, "Product name is required.");
        }

        BigDecimal maxAmount = parsePositiveWholeAmount(maxAmountRaw);
        if (maxAmount == null) {
            return new ActionResult(false, "Max amount must be a positive whole number.");
        }

        double rate;
        try {
            rate = Double.parseDouble(rateRaw == null ? "" : rateRaw.trim());
        } catch (NumberFormatException ex) {
            return new ActionResult(false, "Invalid APR.");
        }
        if (rate <= 0.0) {
            return new ActionResult(false, "APR must be positive.");
        }

        long durationTicks;
        try {
            durationTicks = Long.parseLong(durationRaw == null ? "" : durationRaw.trim());
        } catch (NumberFormatException ex) {
            return new ActionResult(false, "Invalid duration ticks.");
        }
        if (durationTicks < 20L) {
            return new ActionResult(false, "Duration must be at least 20 ticks.");
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        List<LoanProductSpec> products = decodeLoanProducts(metadata.getString("loanProducts"));
        products.removeIf(product -> product.name().equalsIgnoreCase(name));
        products.add(new LoanProductSpec(name, maxAmount, rate, durationTicks));
        metadata.putString("loanProducts", encodeLoanProducts(products));
        centralBank.putBankMetadata(bank.getBankId(), metadata);

        return new ActionResult(true, "Loan product created: " + name + ".");
    }

    private static ActionResult finalizeBankCreation(CentralBank centralBank,
                                                     ServerPlayer founder,
                                                     AccountHolder fundingAccount,
                                                     String bankName,
                                                     String ownershipModel,
                                                     BigDecimal creationFee,
                                                     BigDecimal charterFee) {
        BigDecimal totalFee = creationFee.add(charterFee);
        if (totalFee.compareTo(BigDecimal.ZERO) > 0 && !fundingAccount.RemoveBalance(totalFee)) {
            return new ActionResult(false, "Could not deduct required creation fees.");
        }

        if (totalFee.compareTo(BigDecimal.ZERO) > 0) {
            centralBank.setReserve(centralBank.getDeclaredReserve().add(totalFee));
            fundingAccount.addTransaction(new UserTransaction(
                    fundingAccount.getAccountUUID(),
                    UUID.nameUUIDFromBytes("ultimatebankingsystem:bank-create-fees".getBytes(StandardCharsets.UTF_8)),
                    totalFee,
                    LocalDateTime.now(),
                    "BANK_CREATION_FEES:" + bankName
            ));
        }

        Bank newBank = new Bank(
                UUID.randomUUID(),
                bankName,
                BigDecimal.ZERO,
                Config.DEFAULT_SERVER_INTEREST_RATE.get(),
                founder.getUUID()
        );
        centralBank.addBank(newBank);

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(newBank.getBankId());
        metadata.putString("status", "ACTIVE");
        metadata.putString("ownershipModel", ownershipModel);
        metadata.putString("motto", "");
        metadata.putString("color", "#55AAFF");
        metadata.putLong("createdMillis", System.currentTimeMillis());
        metadata.putUUID("founder", founder.getUUID());
        metadata.putString("dailyWithdrawn", "0");
        metadata.putLong("dailyWindowDay", -1L);
        metadata.putString("reserveMinRatio", String.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()));
        metadata.putString("dailyCapOverride", "");
        metadata.putString("employees", "");
        metadata.putString("loanProducts", "");

        if ("ROLE_BASED".equalsIgnoreCase(ownershipModel)) {
            HashMap<UUID, String> roles = new HashMap<>();
            roles.put(founder.getUUID(), "FOUNDER");
            metadata.putString("roles", encodeUuidStringMap(roles));
        } else if ("PERCENTAGE_SHARES".equalsIgnoreCase(ownershipModel)) {
            HashMap<UUID, BigDecimal> shares = new HashMap<>();
            shares.put(founder.getUUID(), BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_EVEN));
            metadata.putString("shares", encodeShareMap(shares));
        } else if ("FIXED_COFOUNDERS".equalsIgnoreCase(ownershipModel)) {
            metadata.putString("cofounders", encodeUuidList(List.of(founder.getUUID())));
        }
        centralBank.putBankMetadata(newBank.getBankId(), metadata);

        AccountHolder founderAccount = new AccountHolder(
                founder.getUUID(),
                BigDecimal.ZERO,
                AccountTypes.CheckingAccount,
                "",
                newBank.getBankId(),
                null
        );
        if (!newBank.AddAccount(founderAccount)) {
            return new ActionResult(false, "Bank created, but founder account could not be created.");
        }

        if (findPrimaryAccount(centralBank, founder.getUUID()) == null) {
            founderAccount.setPrimaryAccount(true);
        }

        if (charterFee.compareTo(BigDecimal.ZERO) > 0) {
            recordSettlement(
                    centralBank,
                    fundingAccount.getBankId(),
                    centralBank.getBankId(),
                    charterFee,
                    "CHARTER_FEE:" + bankName,
                    true
            );
        }

        BankManager.markDirty();
        return new ActionResult(true, "Bank created: " + newBank.getBankName() + " (" + shortId(newBank.getBankId()) + ").");
    }

    private static AccountHolder findPrimaryAccount(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) {
            return null;
        }
        var accounts = centralBank.SearchForAccount(playerId);
        for (AccountHolder account : accounts.values()) {
            if (account.isPrimaryAccount()) {
                return account;
            }
        }
        return null;
    }

    private static UUID resolvePlayerId(MinecraftServer server, String raw) {
        if (server == null || raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();

        UUID asUuid = parseUuid(trimmed);
        if (asUuid != null) {
            return asUuid;
        }

        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online.getName().getString().equalsIgnoreCase(trimmed)) {
                return online.getUUID();
            }
        }
        return null;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static BigDecimal parsePositiveWholeAmount(String amountRaw) {
        if (amountRaw == null || amountRaw.isBlank()) {
            return null;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            return null;
        }
        return amount;
    }

    private static String normalizeBankName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeOwnershipModel(String raw) {
        if (raw == null) {
            return "SOLE";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        return switch (normalized) {
            case "sole", "single", "owner" -> "SOLE";
            case "fixedcofounders", "cofounders", "cofounder", "fixed" -> "FIXED_COFOUNDERS";
            case "percentageshares", "shares", "share" -> "PERCENTAGE_SHARES";
            case "rolebased", "roles", "role" -> "ROLE_BASED";
            default -> "SOLE";
        };
    }

    private static String normalizeBankColor(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.matches("^#[0-9a-fA-F]{6}$")) {
            return value.toUpperCase(Locale.ROOT);
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "blue" -> "#55AAFF";
            case "lightblue", "aqua", "cyan" -> "#55FFFF";
            case "green" -> "#55FF55";
            case "red" -> "#FF5555";
            case "gold", "orange" -> "#FFAA00";
            case "yellow" -> "#FFFF55";
            case "white" -> "#FFFFFF";
            case "gray", "grey" -> "#AAAAAA";
            case "black" -> "#000000";
            case "purple", "magenta" -> "#AA55FF";
            default -> null;
        };
    }

    private static Bank resolveBankByName(CentralBank centralBank, String bankNameRaw) {
        if (centralBank == null || bankNameRaw == null) {
            return null;
        }
        String requested = normalizeBankName(bankNameRaw);
        if (requested.isBlank()) {
            return null;
        }
        return centralBank.getBanks().values().stream()
                .filter(bank -> bank.getBankName() != null)
                .filter(bank -> bank.getBankName().trim().equalsIgnoreCase(requested))
                .findFirst()
                .orElse(null);
    }

    private static String resolveRoleLabel(CentralBank centralBank, UUID bankId, UUID playerId) {
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);

        Map<UUID, String> roles = decodeUuidStringMap(metadata.getString("roles"));
        String role = roles.get(playerId);
        if (role != null && !role.isBlank()) {
            return role.toUpperCase(Locale.ROOT);
        }

        Map<UUID, EmployeeSpec> employees = decodeEmployeeMap(metadata.getString("employees"));
        EmployeeSpec employeeSpec = employees.get(playerId);
        if (employeeSpec != null) {
            return "EMPLOYEE " + employeeSpec.role();
        }

        if (decodeUuidList(metadata.getString("cofounders")).contains(playerId)) {
            return "COFOUNDER";
        }

        BigDecimal share = decodeShareMap(metadata.getString("shares")).get(playerId);
        if (share != null && share.compareTo(BigDecimal.ZERO) > 0) {
            return "SHAREHOLDER " + share.toPlainString() + "%";
        }

        return "";
    }

    private static String resolvePlayerName(MinecraftServer server, UUID uuid) {
        if (uuid == null) {
            return "unknown";
        }
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            return online.getName().getString();
        }
        if (server.getProfileCache() != null) {
            var cached = server.getProfileCache().get(uuid);
            if (cached.isPresent() && cached.get().getName() != null && !cached.get().getName().isBlank()) {
                return cached.get().getName();
            }
        }
        return shortId(uuid);
    }

    private static String shortId(UUID id) {
        String raw = id.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }

    private static String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return "ACTIVE";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal getDailyCapForBank(Bank bank, CompoundTag metadata) {
        if (bank == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal configuredCap = bank.getDeclaredReserve()
                .multiply(BigDecimal.valueOf(Config.BANK_DAILY_LIQUIDITY_RATIO.get()))
                .setScale(2, RoundingMode.HALF_EVEN);

        if (metadata != null && metadata.contains("dailyCapOverride")) {
            String overrideRaw = metadata.getString("dailyCapOverride");
            if (overrideRaw != null && !overrideRaw.isBlank()) {
                try {
                    BigDecimal override = new BigDecimal(overrideRaw);
                    if (override.compareTo(BigDecimal.ZERO) >= 0) {
                        return override.setScale(2, RoundingMode.HALF_EVEN);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return configuredCap.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal readBigDecimal(CompoundTag tag, String key) {
        if (tag == null || key == null || key.isBlank() || !tag.contains(key)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(tag.getString(key));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static CompoundTag getDesktopUserTag(CentralBank centralBank,
                                                 DesktopContext context,
                                                 UUID playerId,
                                                 boolean create) {
        if (centralBank == null || context == null || playerId == null) {
            return new CompoundTag();
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag storageRoot = centralMeta.contains(DESKTOP_STORAGE_TAG, NBT_COMPOUND)
                ? centralMeta.getCompound(DESKTOP_STORAGE_TAG)
                : new CompoundTag();
        CompoundTag pcTag = storageRoot.contains(context.storageKey(), NBT_COMPOUND)
                ? storageRoot.getCompound(context.storageKey())
                : new CompoundTag();
        CompoundTag usersTag = pcTag.contains("users", NBT_COMPOUND)
                ? pcTag.getCompound("users")
                : new CompoundTag();
        String userKey = playerId.toString();
        if (usersTag.contains(userKey, NBT_COMPOUND)) {
            return usersTag.getCompound(userKey);
        }
        return create ? new CompoundTag() : new CompoundTag();
    }

    private static void commitDesktopUserTag(CentralBank centralBank,
                                             DesktopContext context,
                                             UUID playerId,
                                             CompoundTag userTag) {
        if (centralBank == null || context == null || playerId == null || userTag == null) {
            return;
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag storageRoot = centralMeta.contains(DESKTOP_STORAGE_TAG, NBT_COMPOUND)
                ? centralMeta.getCompound(DESKTOP_STORAGE_TAG)
                : new CompoundTag();
        CompoundTag pcTag = storageRoot.contains(context.storageKey(), NBT_COMPOUND)
                ? storageRoot.getCompound(context.storageKey())
                : new CompoundTag();
        CompoundTag usersTag = pcTag.contains("users", NBT_COMPOUND)
                ? pcTag.getCompound("users")
                : new CompoundTag();

        usersTag.put(playerId.toString(), userTag);
        pcTag.put("users", usersTag);
        storageRoot.put(context.storageKey(), pcTag);
        centralMeta.put(DESKTOP_STORAGE_TAG, storageRoot);
        centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);
    }

    private static List<OwnerPcFileEntry> readDesktopFiles(CompoundTag userTag) {
        if (userTag == null || !userTag.contains("files", 9)) {
            return new ArrayList<>();
        }
        ListTag list = userTag.getList("files", NBT_COMPOUND);
        List<OwnerPcFileEntry> files = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag fileTag = list.getCompound(i);
            String name = normalizeDesktopFileName(fileTag.getString("name"));
            String content = fileTag.getString("content");
            long updated = fileTag.contains("updatedAtMillis") ? fileTag.getLong("updatedAtMillis") : 0L;
            String kind = normalizeFileKind(fileTag.contains("kind") ? fileTag.getString("kind") : "TEXT");
            if (!name.isBlank()) {
                files.add(new OwnerPcFileEntry(kind, name, content == null ? "" : content, updated));
            }
        }
        files.sort(Comparator
                .comparingLong(OwnerPcFileEntry::updatedAtMillis).reversed()
                .thenComparing(OwnerPcFileEntry::name, String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    private static void writeDesktopFiles(CompoundTag userTag, List<OwnerPcFileEntry> files) {
        ListTag list = new ListTag();
        if (files != null) {
            for (OwnerPcFileEntry file : files) {
                if (file == null || file.name() == null || file.name().isBlank()) {
                    continue;
                }
                CompoundTag fileTag = new CompoundTag();
                fileTag.putString("kind", normalizeFileKind(file.kind()));
                fileTag.putString("name", normalizeDesktopFileName(file.name()));
                fileTag.putString("content", file.content() == null ? "" : file.content());
                fileTag.putLong("updatedAtMillis", file.updatedAtMillis());
                list.add(fileTag);
            }
        }
        userTag.put("files", list);
    }

    private static Set<String> readHiddenApps(CompoundTag userTag) {
        Set<String> hidden = new LinkedHashSet<>();
        if (userTag == null || !userTag.contains("hiddenApps", 9)) {
            return hidden;
        }
        ListTag list = userTag.getList("hiddenApps", NBT_STRING);
        for (int i = 0; i < list.size(); i++) {
            String id = normalizeHiddenAppId(list.getString(i));
            if (!id.isBlank()) {
                hidden.add(id);
            }
        }
        return hidden;
    }

    private static void writeHiddenApps(CompoundTag userTag, Set<String> hiddenApps) {
        ListTag list = new ListTag();
        if (hiddenApps != null) {
            hiddenApps.stream()
                    .map(BankOwnerPcService::normalizeHiddenAppId)
                    .filter(id -> !id.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(id -> list.add(StringTag.valueOf(id)));
        }
        userTag.put("hiddenApps", list);
    }

    private static int computeStorageBytes(List<OwnerPcFileEntry> files) {
        if (files == null || files.isEmpty()) {
            return 0;
        }
        long used = 0L;
        for (OwnerPcFileEntry file : files) {
            if (file == null) {
                continue;
            }
            used += utf8Bytes(file.kind());
            used += utf8Bytes(file.name());
            used += utf8Bytes(file.content());
        }
        if (used <= 0L) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, used);
    }

    private static int findFileIndexByName(List<OwnerPcFileEntry> files, String name) {
        if (files == null || files.isEmpty() || name == null || name.isBlank()) {
            return -1;
        }
        for (int i = 0; i < files.size(); i++) {
            OwnerPcFileEntry entry = files.get(i);
            if (entry != null && entry.name() != null && entry.name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static int utf8Bytes(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String normalizeDesktopFileName(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder filtered = new StringBuilder();
        String trimmed = raw.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                filtered.append(' ');
            } else if (!Character.isISOControl(c)) {
                filtered.append(c);
            }
        }
        String normalized = filtered.toString().trim().replaceAll("\\s+", " ");
        if (normalized.length() > DESKTOP_FILE_NAME_MAX_CHARS) {
            normalized = normalized.substring(0, DESKTOP_FILE_NAME_MAX_CHARS).trim();
        }
        return normalized;
    }

    private static String normalizeHiddenAppId(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 128) {
            normalized = normalized.substring(0, 128);
        }
        return normalized;
    }

    private static boolean parseHideFlag(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("1")
                || normalized.equals("true")
                || normalized.equals("yes")
                || normalized.equals("hide")
                || normalized.equals("on");
    }

    private static ActionResult ok(String action, String message) {
        return new ActionResult(action, true, message);
    }

    private static ActionResult fail(String action, String message) {
        return new ActionResult(action, false, message);
    }

    private static boolean isDesktopPinConfigured(CompoundTag userTag) {
        if (userTag == null) {
            return false;
        }
        String pinHash = userTag.getString(DESKTOP_PIN_HASH_TAG);
        String pinSalt = userTag.getString(DESKTOP_PIN_SALT_TAG);
        return pinHash != null && !pinHash.isBlank()
                && pinSalt != null && !pinSalt.isBlank();
    }

    private static boolean isValidDesktopPassword(String password) {
        if (password == null) {
            return false;
        }
        String normalized = password.trim();
        return normalized.length() >= 4 && normalized.length() <= 64;
    }

    private static String normalizeRecoveryPhrase(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String newDesktopSalt() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String hashDesktopSecret(String secret, String salt) {
        String normalizedSecret = secret == null ? "" : secret.trim();
        String normalizedSalt = salt == null ? "" : salt.trim();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((normalizedSalt + ":" + normalizedSecret).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16));
                out.append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            return normalizedSalt + ":" + normalizedSecret;
        }
    }

    private static String normalizeFileKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return "TEXT";
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "CANVAS" -> "CANVAS";
            default -> "TEXT";
        };
    }

    private static String normalizeDim(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return "unknown";
        }
        return dimensionId.trim();
    }

    private static String formatList(String title, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return title + "\n- none";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(title);
        for (String line : lines) {
            builder.append('\n').append("- ").append(line == null ? "" : line);
        }
        return builder.toString();
    }

    private static String joinLines(String... lines) {
        if (lines == null || lines.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines[i] == null ? "" : lines[i]);
        }
        return builder.toString();
    }

    private static UUID readUuid(CompoundTag tag, String key) {
        if (tag == null || key == null || key.isBlank() || !tag.hasUUID(key)) {
            return null;
        }
        return tag.getUUID(key);
    }

    private static long currentOverworldGameTime(MinecraftServer server) {
        if (server == null) {
            return 0L;
        }
        var level = server.getLevel(Level.OVERWORLD);
        return level == null ? 0L : level.getGameTime();
    }

    private static String getBankStatus(CentralBank centralBank, Bank bank) {
        if (centralBank == null || bank == null) {
            return "UNKNOWN";
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        String status = metadata.getString("status");
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private static void refreshBankOperationalState(CentralBank centralBank,
                                                    Bank bank,
                                                    long gameTime,
                                                    MinecraftServer server) {
        if (centralBank == null || bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            return;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        String previousStatus = getBankStatus(centralBank, bank);
        String nextStatus = previousStatus;

        if ("LOCKDOWN".equals(previousStatus) && metadata.contains("lockdownUntilTick")) {
            long until = metadata.getLong("lockdownUntilTick");
            if (gameTime >= until) {
                nextStatus = "ACTIVE";
            }
        }

        BigDecimal reserve = bank.getDeclaredReserve();
        BigDecimal deposits = bank.getTotalDeposits();
        BigDecimal minReserve = deposits.multiply(BigDecimal.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()))
                .setScale(2, RoundingMode.HALF_EVEN);
        long graceTicks = Math.max(20, Config.BANK_RESERVE_GRACE_TICKS.get());

        if (reserve.compareTo(minReserve) < 0) {
            long breachTick = metadata.contains("reserveBreachStartTick")
                    ? metadata.getLong("reserveBreachStartTick")
                    : gameTime;
            metadata.putLong("reserveBreachStartTick", breachTick);
            if ((gameTime - breachTick) >= graceTicks) {
                if (!"SUSPENDED".equals(previousStatus) && !"REVOKED".equals(previousStatus)) {
                    nextStatus = "RESTRICTED";
                }
            } else if (!"SUSPENDED".equals(previousStatus) && !"REVOKED".equals(previousStatus)) {
                nextStatus = "WARNING";
            }
        } else {
            metadata.remove("reserveBreachStartTick");
            if ("WARNING".equals(previousStatus) || "RESTRICTED".equals(previousStatus)) {
                nextStatus = "ACTIVE";
            }
        }

        if (!nextStatus.equals(previousStatus)) {
            metadata.putString("status", nextStatus);
            notifyBankOwnerStatusChange(server, bank, previousStatus, nextStatus);
        }

        long day = gameTime / 24000L;
        if (!metadata.contains("dailyWindowDay") || metadata.getLong("dailyWindowDay") != day) {
            metadata.putLong("dailyWindowDay", day);
            metadata.putString("dailyWithdrawn", "0");
            metadata.putInt("queuedWithdrawalCount", 0);
        }

        centralBank.putBankMetadata(bank.getBankId(), metadata);
    }

    private static void notifyBankOwnerStatusChange(MinecraftServer server, Bank bank, String oldStatus, String newStatus) {
        if (server == null || bank == null) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(bank.getBankOwnerId());
        if (owner == null) {
            return;
        }
        owner.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§6[UBS] Bank status update for " + bank.getBankName() + ": " + oldStatus + " -> " + newStatus
        ));
    }

    private static void recordSettlement(CentralBank centralBank,
                                         UUID fromBankId,
                                         UUID toBankId,
                                         BigDecimal amount,
                                         String reason,
                                         boolean success) {
        if (centralBank == null || fromBankId == null || toBankId == null || amount == null) {
            return;
        }

        CompoundTag entry = new CompoundTag();
        UUID settlementId = UUID.randomUUID();
        entry.putUUID("id", settlementId);
        entry.putUUID("fromBankId", fromBankId);
        entry.putUUID("toBankId", toBankId);
        entry.putString("amount", amount.toPlainString());
        entry.putLong("timestampMillis", System.currentTimeMillis());
        entry.putString("reason", reason == null ? "" : reason);
        entry.putBoolean("success", success);

        if (success) {
            centralBank.getSettlementLedger().put(settlementId, entry);
            trimTagMap(centralBank.getSettlementLedger(), Math.max(1, Config.CLEARING_LEDGER_LIMIT.get()));
        } else {
            centralBank.getSettlementSuspense().put(settlementId, entry);
            trimTagMap(centralBank.getSettlementSuspense(), Math.max(1, Config.CLEARING_LEDGER_LIMIT.get()));
        }
        BankManager.markDirty();
    }

    private static void trimTagMap(ConcurrentHashMap<UUID, CompoundTag> map, int maxSize) {
        if (map == null || maxSize < 1 || map.size() <= maxSize) {
            return;
        }
        List<Map.Entry<UUID, CompoundTag>> entries = map.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().getLong("timestampMillis")))
                .toList();
        int removeCount = map.size() - maxSize;
        for (int i = 0; i < removeCount && i < entries.size(); i++) {
            map.remove(entries.get(i).getKey());
        }
    }

    private static Map<UUID, String> decodeUuidStringMap(String encoded) {
        Map<UUID, String> result = new HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            String raw = entry.trim();
            if (raw.isBlank() || !raw.contains("=")) {
                continue;
            }
            String[] parts = raw.split("=", 2);
            try {
                UUID id = UUID.fromString(parts[0].trim());
                String value = parts[1].trim();
                if (!value.isBlank()) {
                    result.put(id, value);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    private static String encodeUuidStringMap(Map<UUID, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return map.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null && !entry.getValue().isBlank())
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }

    private static Map<UUID, BigDecimal> decodeShareMap(String encoded) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            String raw = entry.trim();
            if (raw.isBlank() || !raw.contains("=")) {
                continue;
            }
            String[] parts = raw.split("=", 2);
            try {
                UUID id = UUID.fromString(parts[0].trim());
                BigDecimal percent = new BigDecimal(parts[1].trim()).setScale(2, RoundingMode.HALF_EVEN);
                if (percent.compareTo(BigDecimal.ZERO) > 0) {
                    result.put(id, percent);
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private static String encodeShareMap(Map<UUID, BigDecimal> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return map.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(entry -> entry.getKey() + "=" + entry.getValue().setScale(2, RoundingMode.HALF_EVEN).toPlainString())
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }

    private static List<UUID> decodeUuidList(String encoded) {
        List<UUID> result = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        String[] entries = encoded.split(",");
        for (String entry : entries) {
            try {
                result.add(UUID.fromString(entry.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    private static String encodeUuidList(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return "";
        }
        return uuids.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .map(UUID::toString)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private static Map<UUID, EmployeeSpec> decodeEmployeeMap(String encoded) {
        Map<UUID, EmployeeSpec> result = new HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            String raw = entry.trim();
            if (raw.isBlank() || !raw.contains("=") || !raw.contains(":")) {
                continue;
            }
            String[] uuidAndRest = raw.split("=", 2);
            String[] roleAndSalary = uuidAndRest[1].split(":", 2);
            if (roleAndSalary.length < 2) {
                continue;
            }
            try {
                UUID id = UUID.fromString(uuidAndRest[0].trim());
                String role = roleAndSalary[0].trim().toUpperCase(Locale.ROOT);
                BigDecimal salary = new BigDecimal(roleAndSalary[1].trim());
                if (salary.compareTo(BigDecimal.ZERO) < 0) {
                    continue;
                }
                result.put(id, new EmployeeSpec(role, salary));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private static String encodeEmployeeMap(Map<UUID, EmployeeSpec> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return map.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .map(entry -> entry.getKey() + "=" + entry.getValue().role() + ":" + entry.getValue().salary().toPlainString())
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }

    private static List<LoanProductSpec> decodeLoanProducts(String encoded) {
        List<LoanProductSpec> products = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return products;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            String raw = entry.trim();
            if (raw.isBlank()) {
                continue;
            }
            String[] parts = raw.split("\\|");
            if (parts.length < 4) {
                continue;
            }
            try {
                String name = parts[0].trim();
                BigDecimal max = new BigDecimal(parts[1].trim());
                double rate = Double.parseDouble(parts[2].trim());
                long duration = Long.parseLong(parts[3].trim());
                if (!name.isBlank() && max.compareTo(BigDecimal.ZERO) > 0 && rate > 0.0 && duration >= 20L) {
                    products.add(new LoanProductSpec(name, max, rate, duration));
                }
            } catch (Exception ignored) {
            }
        }
        return products;
    }

    private static String encodeLoanProducts(List<LoanProductSpec> products) {
        if (products == null || products.isEmpty()) {
            return "";
        }
        return products.stream()
                .filter(product -> product != null && product.name() != null && !product.name().isBlank())
                .sorted(Comparator.comparing(LoanProductSpec::name, String.CASE_INSENSITIVE_ORDER))
                .map(product -> product.name() + "|"
                        + product.maxAmount().toPlainString() + "|"
                        + product.interestRate() + "|"
                        + product.durationTicks())
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }

    private record EmployeeSpec(String role, BigDecimal salary) {}

    private record LoanProductSpec(String name, BigDecimal maxAmount, double interestRate, long durationTicks) {}
}
