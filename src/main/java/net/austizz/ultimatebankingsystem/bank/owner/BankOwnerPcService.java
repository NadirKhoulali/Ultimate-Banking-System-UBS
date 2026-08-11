package net.austizz.ultimatebankingsystem.bank.owner;

import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.AccountReadSnapshot;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.BankLevelService;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.owner.premise.OwnerPcPremisePayloadBuilder;
import net.austizz.ultimatebankingsystem.bank.owner.staffing.BankEmployeeRemovalService;
import net.austizz.ultimatebankingsystem.bank.owner.staffing.BankStaffingRoster;
import net.austizz.ultimatebankingsystem.bank.owner.staffing.BankStaffingService;
import net.austizz.ultimatebankingsystem.bank.owner.setup.BankSafeSetupPayloadBuilder;
import net.austizz.ultimatebankingsystem.bank.safebox.SafeAccessLogService;
import net.austizz.ultimatebankingsystem.bank.safebox.SafeAlarmSettingsService;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.VaultStorageSnapshotService;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomAnchor;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomService;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomState;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.SafeBoxViewingCoordinator;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.command.UBSAdminCommands;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.heist.HeistSavedData;
import net.austizz.ultimatebankingsystem.heist.HeistSession;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.austizz.ultimatebankingsystem.util.RegistryKeysCompat;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankAppSummary;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankTellerPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankDataPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcDesktopDataPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcFileEntry;
import net.austizz.ultimatebankingsystem.network.OwnerPcPlayerEmployeePayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcSetupObjectivePayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcSafeAccessLogPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcSafeAlarmPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcViewingRoomPayload;
import net.austizz.ultimatebankingsystem.network.DeliveryAlertPayload;
import net.austizz.ultimatebankingsystem.network.ServerNotification;
import net.austizz.ultimatebankingsystem.network.StockroomLocateRenderPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private static final int ACCOUNT_DETAIL_HISTORY_LIMIT = 200;
    private static final int ACCOUNT_TEMP_LIMIT_MAX_DAYS = 30;
    private static final DateTimeFormatter ACCOUNT_DETAIL_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
            this.message = MoneyText.abbreviateCurrencyTokens(message == null ? "" : message);
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

    private record SafeDashboardData(String areaCount,
                                     String rowCapacity,
                                     String claimedRowUnits,
                                     String totalBoxSlots,
                                     String assignedBoxes,
                                     String freeBoxes,
                                     String lockedBoxes,
                                     String escrowCases,
                                     String policyMode,
                                     String policyAmount,
                                     String rentPeriodTicks,
                                     String overdueTicks,
                                     List<String> areaSummaries,
                                     List<String> boxAssignments,
                                     List<String> lockedQueue) {
    }

    private record SafeAssignmentView(UUID bankId,
                                      UUID accountId,
                                      String dimension,
                                      BlockPos pos,
                                      int doorIndex,
                                      String boxNumber,
                                      boolean locked,
                                      long assignedAtMillis,
                                      BigDecimal paidAmount,
                                      long rentPeriodTicks) {
    }

    private record SafeRowView(int index,
                               String dimension,
                               BlockPos pos,
                               SafetyDepositBoxRowBlockEntity row) {
    }

    private record SafeLocateTarget(String dimension, BlockPos pos, int doorIndex) {
    }

    private record DesktopContext(String dimensionId, int x, int y, int z, String machineId) {
        private String coordinateKey() {
            return normalizeDim(dimensionId) + "|" + x + "|" + y + "|" + z;
        }

        private String storageKey() {
            return machineId == null || machineId.isBlank() ? coordinateKey() : machineId;
        }

        private String label() {
            return normalizeDim(dimensionId) + " (" + x + ", " + y + ", " + z + ")";
        }
    }

    public record ValidDesktopContext(String computerId,
                                      String dimensionId,
                                      int x,
                                      int y,
                                      int z) {
    }

    private static final ConcurrentHashMap<UUID, Long> LAST_BANK_CREATE_ATTEMPT_MILLIS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, DesktopContext> ACTIVE_DESKTOP_CONTEXT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Set<UUID>> DESKTOP_UNLOCKED_SESSIONS = new ConcurrentHashMap<>();
    private static final String DESKTOP_STORAGE_TAG = "ownerPcDesktopStorage";
    private static final String DESKTOP_MACHINE_INDEX_TAG = "ownerPcDesktopMachineIndex";
    private static final String DESKTOP_POWER_STATE_TAG = "poweredOn";
    private static final String DESKTOP_SESSION_MACHINE_TAG = "desktopSessionMachineId";
    private static final String DESKTOP_SESSION_UNLOCKED_AT_TAG = "desktopSessionUnlockedAt";
    private static final int NBT_COMPOUND = 10;
    private static final int NBT_STRING = 8;
    private static final int DESKTOP_STORAGE_MAX_BYTES = 48 * 1024;
    private static final int DESKTOP_STORAGE_MAX_FILES = 64;
    private static final int DESKTOP_FILE_MAX_CHARS = 20_000;
    private static final int DESKTOP_FILE_NAME_MAX_CHARS = 48;
    private static final String DESKTOP_PIN_HASH_TAG = "desktopPinHash";
    private static final String DESKTOP_PIN_SALT_TAG = "desktopPinSalt";
    private static final String DESKTOP_RECOVERY_HASH_TAG = "desktopRecoveryHash";
    private static final BigDecimal DEFAULT_TELLER_WITHDRAWAL_LIMIT = new BigDecimal("250000");
    private static final BigDecimal MAX_TELLER_WITHDRAWAL_LIMIT = BigDecimal.valueOf(Integer.MAX_VALUE / 100L);

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
                    roleLabel,
                    OwnerPcBankAppSummary.APP_TYPE_BANK
            ));
        }

        for (ShopService.ShopSummary shop : ShopService.listOwnerShopSummaries(centralBank, playerId)) {
            if (shop == null || shop.shopId() == null) {
                continue;
            }
            String color = switch (shop.type()) {
                case ShopService.SHOP_TYPE_FRANCHISE -> "#E29A4A";
                case ShopService.SHOP_TYPE_CORPORATE_CHAIN -> "#5BB0FF";
                default -> "#68C18E";
            };
            apps.add(new OwnerPcBankAppSummary(
                    shop.shopId(),
                    shop.name(),
                    color,
                    "ACTIVE",
                    shop.ownerView(),
                    shop.role(),
                    OwnerPcBankAppSummary.APP_TYPE_SHOP
            ));
        }

        apps.sort(Comparator
                .comparing(OwnerPcBankAppSummary::owner).reversed()
                .thenComparing(OwnerPcBankAppSummary::appType)
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

    public static void rememberDesktopContext(CentralBank centralBank, UUID playerId, String dimensionId, int x, int y, int z) {
        if (centralBank == null || playerId == null || dimensionId == null || dimensionId.isBlank()) {
            return;
        }
        String normalizedDim = normalizeDim(dimensionId);
        String machineId = resolveOrCreateDesktopMachineId(centralBank, normalizedDim, x, y, z, true);
        ACTIVE_DESKTOP_CONTEXT.put(playerId, new DesktopContext(normalizedDim, x, y, z, machineId));
    }

    static void clearRememberedDesktopContext(UUID playerId) {
        if (playerId == null) {
            return;
        }
        ACTIVE_DESKTOP_CONTEXT.remove(playerId);
        DESKTOP_UNLOCKED_SESSIONS.values().forEach(players -> players.remove(playerId));
        DESKTOP_UNLOCKED_SESSIONS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public static boolean hasValidDesktopContext(MinecraftServer server,
                                                 ServerPlayer player,
                                                 boolean requireNearby) {
        if (server == null || player == null) {
            return false;
        }
        DesktopContext context = ACTIVE_DESKTOP_CONTEXT.get(player.getUUID());
        if (context == null) {
            return false;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(context.dimensionId());
        if (dimension == null) {
            return false;
        }
        ServerLevel level = server.getLevel(RegistryKeysCompat.createValueKey(
                RegistryKeysCompat.DIMENSION_REGISTRY_KEY, dimension));
        BlockPos pos = new BlockPos(context.x(), context.y(), context.z());
        if (level == null || !level.hasChunkAt(pos)
                || !level.getBlockState(pos).is(ModBlocks.BANK_OWNER_PC.get())) {
            return false;
        }
        if (!requireNearby) {
            return true;
        }
        return player.level() == level
                && player.position().distanceToSqr(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 100.0D;
    }

    public static ValidDesktopContext getValidDesktopContext(MinecraftServer server,
                                                              CentralBank centralBank,
                                                              ServerPlayer player,
                                                              boolean requireNearby) {
        if (centralBank == null || !hasValidDesktopContext(server, player, requireNearby)) {
            return null;
        }
        DesktopContext context = ACTIVE_DESKTOP_CONTEXT.get(player.getUUID());
        if (context == null) {
            return null;
        }
        String machineId = resolveOrCreateDesktopMachineId(centralBank,
                context.dimensionId(), context.x(), context.y(), context.z(), true);
        if (machineId.isBlank()) {
            return null;
        }
        DesktopContext resolved = new DesktopContext(
                context.dimensionId(), context.x(), context.y(), context.z(), machineId);
        ACTIVE_DESKTOP_CONTEXT.put(player.getUUID(), resolved);
        return new ValidDesktopContext(machineId, resolved.dimensionId(),
                resolved.x(), resolved.y(), resolved.z());
    }

    private static OwnerPcActionPolicy.MutationContext directMutationContext(
            MinecraftServer server,
            CentralBank centralBank,
            ServerPlayer player) {
        DesktopContext context = player == null ? null : ACTIVE_DESKTOP_CONTEXT.get(player.getUUID());
        if (server == null || centralBank == null || player == null || context == null) {
            return OwnerPcMutationContextCollector.collect(null, null, null);
        }

        ResourceLocation dimension = ResourceLocation.tryParse(context.dimensionId());
        ServerLevel level = dimension == null ? null : server.getLevel(
                RegistryKeysCompat.createValueKey(RegistryKeysCompat.DIMENSION_REGISTRY_KEY, dimension));
        BlockPos pos = new BlockPos(context.x(), context.y(), context.z());
        boolean chunkLoaded = level != null && level.hasChunkAt(pos);
        OwnerPcMutationContextCollector.BlockKind blockKind = !chunkLoaded
                ? OwnerPcMutationContextCollector.BlockKind.UNAVAILABLE
                : level.getBlockState(pos).is(ModBlocks.BANK_OWNER_PC.get())
                ? OwnerPcMutationContextCollector.BlockKind.OWNER_PC
                : OwnerPcMutationContextCollector.BlockKind.OTHER;

        CompoundTag centralMeta = centralBank.readBankMetadata(centralBank.getBankId());
        CompoundTag machineIndex = getDesktopMachineIndexTag(centralMeta);
        String indexedMachineId = machineIndex.contains(context.coordinateKey(), NBT_STRING)
                ? machineIndex.getString(context.coordinateKey()).trim().toLowerCase(Locale.ROOT)
                : "";
        OwnerPcMutationContextCollector.RememberedPc remembered =
                new OwnerPcMutationContextCollector.RememberedPc(
                        context.dimensionId(), context.x(), context.y(), context.z(), context.machineId());
        OwnerPcMutationContextCollector.PlayerLocation playerLocation =
                new OwnerPcMutationContextCollector.PlayerLocation(
                        player.level().dimension().location().toString(),
                        player.getX(), player.getY(), player.getZ());
        OwnerPcMutationContextCollector.LoadedPc unresolvedPower =
                new OwnerPcMutationContextCollector.LoadedPc(
                        level != null, chunkLoaded, blockKind, indexedMachineId, false, false);
        OwnerPcActionPolicy.MutationContext physical = OwnerPcMutationContextCollector.collect(
                remembered, unresolvedPower, playerLocation);

        CompoundTag storageRoot = getDesktopStorageRoot(centralMeta);
        CompoundTag pcTag = physical.machineMatches() && physical.withinRange()
                && storageRoot.contains(context.storageKey(), NBT_COMPOUND)
                ? storageRoot.getCompound(context.storageKey())
                : new CompoundTag();
        boolean poweredOn = physical.machineMatches() && physical.withinRange() && isDesktopPoweredOn(pcTag);
        CompoundTag users = pcTag.contains("users", NBT_COMPOUND)
                ? pcTag.getCompound("users")
                : new CompoundTag();
        CompoundTag userTag = users.contains(player.getUUID().toString(), NBT_COMPOUND)
                ? users.getCompound(player.getUUID().toString())
                : new CompoundTag();
        boolean sessionUnlocked = poweredOn && isDesktopSessionUnlockedReadOnly(
                indexedMachineId, player.getUUID(), userTag);
        return OwnerPcMutationContextCollector.collect(remembered,
                new OwnerPcMutationContextCollector.LoadedPc(
                        level != null, chunkLoaded, blockKind, indexedMachineId,
                        poweredOn, sessionUnlocked),
                playerLocation);
    }

    public static void unregisterDesktopMachine(MinecraftServer server, String dimensionId, int x, int y, int z) {
        if (server == null || dimensionId == null || dimensionId.isBlank()) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }

        String normalizedDim = normalizeDim(dimensionId);
        String contextKey = buildDesktopCoordinateKey(normalizedDim, x, y, z);
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag indexTag = getDesktopMachineIndexTag(centralMeta);
        CompoundTag storageRoot = getDesktopStorageRoot(centralMeta);

        String machineId = indexTag.contains(contextKey, NBT_STRING) ? indexTag.getString(contextKey) : "";
        boolean changed = false;
        if (!machineId.isBlank() && storageRoot.contains(machineId, NBT_COMPOUND)) {
            storageRoot.remove(machineId);
            clearDesktopSessionsForMachine(machineId);
            changed = true;
        }
        if (storageRoot.contains(contextKey, NBT_COMPOUND)) {
            storageRoot.remove(contextKey);
            changed = true;
        }
        if (indexTag.contains(contextKey, NBT_STRING)) {
            indexTag.remove(contextKey);
            changed = true;
        }

        if (changed) {
            centralMeta.put(DESKTOP_MACHINE_INDEX_TAG, indexTag);
            centralMeta.put(DESKTOP_STORAGE_TAG, storageRoot);
            centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);
        }

        ACTIVE_DESKTOP_CONTEXT.entrySet().removeIf(entry -> {
            DesktopContext ctx = entry.getValue();
            return ctx != null && contextKey.equals(ctx.coordinateKey());
        });
    }

    public static OwnerPcDesktopDataPayload buildDesktopData(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) {
            return new OwnerPcDesktopDataPayload("Unknown PC", "", DESKTOP_STORAGE_MAX_BYTES, 0, false, true, false, List.of(), List.of());
        }
        DesktopContext context = ACTIVE_DESKTOP_CONTEXT.get(playerId);
        if (context == null) {
            return new OwnerPcDesktopDataPayload("Unknown PC", "", DESKTOP_STORAGE_MAX_BYTES, 0, false, true, false, List.of(), List.of());
        }
        String machineId = resolveOrCreateDesktopMachineId(centralBank, context.dimensionId(), context.x(), context.y(), context.z(), true);
        if (machineId.isBlank()) {
            return new OwnerPcDesktopDataPayload(context.label(), "", DESKTOP_STORAGE_MAX_BYTES, 0, false, true, false, List.of(), List.of());
        }
        DesktopContext resolvedContext = new DesktopContext(context.dimensionId(), context.x(), context.y(), context.z(), machineId);
        ACTIVE_DESKTOP_CONTEXT.put(playerId, resolvedContext);
        CompoundTag userTag = getDesktopUserTag(centralBank, resolvedContext, playerId, false);
        List<OwnerPcFileEntry> files = readDesktopFiles(userTag);
        Set<String> hiddenApps = readHiddenApps(userTag);
        int used = computeStorageBytes(files);
        boolean pinSet = isDesktopPinConfigured(userTag);
        CompoundTag pcTag = getDesktopPcTag(centralBank, resolvedContext, false);
        boolean poweredOn = isDesktopPoweredOn(pcTag);
        boolean sessionUnlocked = poweredOn && isDesktopSessionUnlocked(centralBank, resolvedContext, machineId, playerId, userTag);
        return new OwnerPcDesktopDataPayload(
                resolvedContext.label(),
                machineId,
                DESKTOP_STORAGE_MAX_BYTES,
                used,
                pinSet,
                poweredOn,
                sessionUnlocked,
                files,
                hiddenApps.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()
        );
    }

    public static ActionResult executeDesktopAction(MinecraftServer server,
                                                    CentralBank centralBank,
                                                    ServerPlayer player,
                                                    String action,
                                                    String arg1,
                                                    String arg2,
                                                    String arg3) {
        if (centralBank == null || player == null) {
            return fail("DESKTOP", "Desktop storage is unavailable.");
        }
        UUID playerId = player.getUUID();
        DesktopContext context = ACTIVE_DESKTOP_CONTEXT.get(playerId);
        if (context == null) {
            return fail("DESKTOP", "Open a bank owner PC block first.");
        }
        String machineId = resolveOrCreateDesktopMachineId(centralBank, context.dimensionId(), context.x(), context.y(), context.z(), true);
        if (machineId == null || machineId.isBlank()) {
            return fail("DESKTOP", "Unable to resolve this PC. Re-open the block and try again.");
        }
        context = new DesktopContext(context.dimensionId(), context.x(), context.y(), context.z(), machineId);
        ACTIVE_DESKTOP_CONTEXT.put(playerId, context);

        String normalizedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if ("REFRESH".equals(normalizedAction)) {
            return ok("REFRESH", "Desktop refreshed.");
        }

        CompoundTag pcTag = getDesktopPcTag(centralBank, context, true);
        CompoundTag userTag = getDesktopUserTag(centralBank, context, playerId, true);
        List<OwnerPcFileEntry> files = readDesktopFiles(userTag);
        Set<String> hiddenApps = readHiddenApps(userTag);
        long now = System.currentTimeMillis();
        boolean poweredOn = isDesktopPoweredOn(pcTag);
        boolean sessionUnlocked = poweredOn && isDesktopSessionUnlocked(centralBank, context, machineId, playerId, userTag);
        boolean authOrPowerAction = normalizedAction.startsWith("AUTH_") || "POWER_OFF".equals(normalizedAction);

        if (!authOrPowerAction && !sessionUnlocked) {
            return fail(normalizedAction, "This PC is locked. Enter your password first.");
        }

        // Enforce delegated shop roles for the desktop shop app. This guarantees
        // the role assignments in the permissions panel are actually authoritative.
        UUID selectedShopId = parseOptionalUuid(arg2);
        ActionResult shopPermissionFailure = validateShopDesktopPermission(
                normalizedAction,
                centralBank,
                player,
                selectedShopId
        );
        if (shopPermissionFailure != null) {
            return shopPermissionFailure;
        }

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
                persistDesktopSessionUnlocked(userTag, machineId);
                writeDesktopFiles(userTag, files);
                writeHiddenApps(userTag, hiddenApps);
                commitDesktopUserTag(centralBank, context, playerId, userTag);
                setDesktopPowerState(centralBank, context, true);
                markDesktopSessionUnlocked(machineId, playerId);
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
                setDesktopPowerState(centralBank, context, true);
                persistDesktopSessionUnlocked(userTag, machineId);
                commitDesktopUserTag(centralBank, context, playerId, userTag);
                markDesktopSessionUnlocked(machineId, playerId);
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
                persistDesktopSessionUnlocked(userTag, machineId);
                writeDesktopFiles(userTag, files);
                writeHiddenApps(userTag, hiddenApps);
                commitDesktopUserTag(centralBank, context, playerId, userTag);
                setDesktopPowerState(centralBank, context, true);
                markDesktopSessionUnlocked(machineId, playerId);
                yield ok(normalizedAction, "Password has been reset.");
            }
            case "AUTH_LOGOUT" -> {
                clearDesktopSession(machineId, playerId);
                clearPersistentDesktopSession(userTag);
                commitDesktopUserTag(centralBank, context, playerId, userTag);
                yield ok(normalizedAction, "Logged out of this PC.");
            }
            case "POWER_OFF" -> {
                clearDesktopSessionsForMachine(machineId);
                clearPersistentDesktopSession(userTag);
                commitDesktopUserTag(centralBank, context, playerId, userTag);
                setDesktopPowerState(centralBank, context, false);
                yield ok(normalizedAction, "PC turned off.");
            }
            case "SHOP_CREATE" -> {
                ShopService.ShopActionResult result = ShopService.createShop(centralBank, player, arg1, arg2);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_OVERVIEW" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.overview(server, centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_CHECKOUT_DIAGNOSTIC" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.overview(server, centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_LEVEL_ROADMAP" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.levelRoadmapReport(centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_CLAIM" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.claimAroundPosition(
                        centralBank,
                        player,
                        shopId,
                        context.dimensionId(),
                        context.x(),
                        context.y(),
                        context.z()
                );
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_CLAIM_TOOL_PLOT" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.startClaimToolSession(centralBank, player, shopId, false);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_CLAIM_TOOL_STOCKROOM" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.startClaimToolSession(centralBank, player, shopId, true);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_CLAIM_TOOL_PALLETS" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.startPalletClaimToolSession(centralBank, player, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_SET_STOCKROOM" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.setStockroomNearPosition(
                        server,
                        centralBank,
                        playerId,
                        shopId,
                        context.dimensionId(),
                        context.x(),
                        context.y(),
                        context.z()
                );
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_ORDER_REPORT" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.orderManagerReport(server, centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_ORDER_ITEM_PICKER" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.orderItemPickerReport(server, centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_ORDER_CREATE" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.createOrder(centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_ORDER_CANCEL" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.cancelOrderByOwner(centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_ORDER_ASSIGN_PALLET" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.assignOrderPallet(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_ORDER_UNASSIGN_PALLET" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.unassignOrderPallet(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_ORDER_BIND_PALLET" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.bindOrderPallet(centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_ORDER_CLEAR_PALLET" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.clearOrderPalletBinding(centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_PALLET_LOCATE" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.beginDeliveryPalletLocate(
                        server,
                        centralBank,
                        playerId,
                        shopId,
                        player,
                        arg1
                );
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_SET_CHECKOUT_TERMINAL" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.setCheckoutTerminalNearPosition(
                        server,
                        centralBank,
                        playerId,
                        shopId,
                        context.dimensionId(),
                        context.x(),
                        context.y(),
                        context.z()
                );
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_SCAN_CASHIERS" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.cashierReport(server, centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_HIRE_CASHIER" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.hireCashierNpc(server, centralBank, player, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_LIST_EMPLOYEES" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.listEmployeesReport(server, centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_FIRE_EMPLOYEE" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.fireEmployee(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_LINK_CASHIER_TERMINAL" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.beginCashierTerminalSelection(
                        server,
                        centralBank,
                        playerId,
                        shopId,
                        player,
                        arg1 == null ? "" : arg1
                );
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_LIST_OWNER_ACCOUNTS" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.listOwnerAccountsForSettlement(centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_PERMISSIONS_REPORT" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.permissionsReport(server, centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_PERMISSIONS_SET" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.setPermissionRole(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_PERMISSIONS_REMOVE" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.removePermissionRole(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_HOURS_LIGHTING_REPORT" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.shopHoursLightingReport(server, centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_HOURS_SET" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.setShopHours(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_HOURS_DELIVERER_STOCKROOM_ACCESS" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.setShopClosedDelivererStockroomAccess(
                        server,
                        centralBank,
                        playerId,
                        shopId,
                        arg1
                );
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_LIGHTING_ENABLED" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.setShopLightingEnabled(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_LIGHTING_MAIN_MODE" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.setShopMainLightingMode(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_LIGHTING_STOCKROOM_MODE" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.setShopStockroomLightingMode(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_LIGHTING_EXCLUDE_STOCKROOM" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.setShopExcludeStockroomLighting(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_LIGHTING_LEVEL" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.setShopLightingLevel(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_SCAN" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.shelfReport(server, centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_STOCKROOM_REPORT" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.stockroomReport(server, centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_STOCKROOM_LOCATE" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.beginStockroomLocate(server, centralBank, playerId, shopId, player, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_RESTOCK" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.restockFromStockroom(server, centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_RESTOCK_SLOT" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.restockShelfSlot(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_RESTOCK_LOW" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.restockLowStockSlots(server, centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_RESTOCK_SHELF" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.restockShelf(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_REMOVE_SHELF_SLOT" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.removeShelfSlotToStockroom(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_SET_SLOT_TARGETS" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.setShelfSlotTargets(centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_FINANCE_REPORT" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.financeReport(server, centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_SHOW_CASH_VAULT" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.cashVaultReport(centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_VAULT_WITHDRAW_AMOUNT" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.withdrawCashVaultAmount(
                        centralBank,
                        playerId,
                        shopId,
                        player,
                        arg1
                );
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_VAULT_WITHDRAW_PLAN" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.withdrawCashVaultPlan(
                        centralBank,
                        playerId,
                        shopId,
                        player,
                        arg1
                );
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_SET_SETTLEMENT_ACCOUNT" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.setSettlementAccount(centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_RENAME" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.renameShop(centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_SET_TYPE" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.setShopType(centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_TYPE_REPORT" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.shopTypeSystemReport(server, centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_TYPE_PAY_FEES" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.payShopTypeFees(centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_FRANCHISE_PUBLISH_OFFER" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.publishFranchiseOffer(centralBank, playerId, shopId, arg1, arg3);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_FRANCHISE_CANCEL_OFFER" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.cancelFranchiseOffer(centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_FRANCHISE_ACCEPT_OFFER" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.acceptFranchiseOffer(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_FRANCHISE_NPC_LICENSE" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.addNpcFranchisee(centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_CORPORATE_ADD_BRANCH" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.addCorporateBranch(centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_CORPORATE_REMOVE_BRANCH" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.removeCorporateBranch(centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_CLEAR_CHECKOUT_TERMINAL" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.clearCheckoutTerminal(centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_CLEAR_CASHIER_LINKS" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.clearCashierTerminalLinks(centralBank, playerId, shopId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_DELETE" -> {
                UUID shopId = parseOptionalUuid(arg2);
                ShopService.ShopActionResult result = ShopService.deleteShop(server, centralBank, playerId, shopId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_WEBSHOP_REPORT" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                ShopService.ShopActionResult result = ShopService.webshopReport(server, centralBank, playerId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_WEBSHOP_ADD" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                String[] parts = (arg1 == null ? "" : arg1).split("\\|", -1);
                String itemId = parts.length >= 1 ? parts[0] : "";
                String qty = parts.length >= 2 ? parts[1] : "1";
                ShopService.ShopActionResult result = ShopService.webshopAddToCart(server, centralBank, playerId, itemId, qty);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_WEBSHOP_SET_QTY" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                String[] parts = (arg1 == null ? "" : arg1).split("\\|", -1);
                String itemId = parts.length >= 1 ? parts[0] : "";
                String qty = parts.length >= 2 ? parts[1] : "0";
                ShopService.ShopActionResult result = ShopService.webshopSetCartQuantity(server, centralBank, playerId, itemId, qty);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_WEBSHOP_REMOVE" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                ShopService.ShopActionResult result = ShopService.webshopRemoveFromCart(server, centralBank, playerId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_WEBSHOP_CLEAR_CART" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                ShopService.ShopActionResult result = ShopService.webshopClearCart(server, centralBank, playerId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_WEBSHOP_SELECT_ACCOUNT" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                ShopService.ShopActionResult result = ShopService.webshopSelectAccount(server, centralBank, playerId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_WEBSHOP_MODE" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                ShopService.ShopActionResult result = ShopService.webshopSetDeliveryMode(server, centralBank, playerId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_WEBSHOP_COORDS" -> {
                yield fail(normalizedAction, "Coordinates delivery mode was removed. Use delivery pallets.");
            }
            case "SHOP_WEBSHOP_SELECT_SHOP" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                ShopService.ShopActionResult result = ShopService.webshopSelectShop(server, centralBank, playerId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_WEBSHOP_SELECT_PALLET" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                ShopService.ShopActionResult result = ShopService.webshopSelectPallet(server, centralBank, playerId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_WEBSHOP_EXPEDITE" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                ShopService.ShopActionResult result = ShopService.webshopSetExpedite(server, centralBank, playerId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_WEBSHOP_CHECKOUT" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                ShopService.ShopActionResult result = ShopService.webshopCheckout(server, centralBank, playerId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_WEBSHOP_CANCEL_ORDER" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                ShopService.ShopActionResult result = ShopService.webshopCancelOrder(server, centralBank, playerId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "SHOP_WEBSHOP_REPLACE_ORDER" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                ShopService.ShopActionResult result = ShopService.webshopReplaceOrder(server, centralBank, playerId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "ORDER_BOARD_REPORT" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                ShopService.ShopActionResult result = ShopService.orderBoardReport(server, centralBank, playerId);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "ORDER_BOARD_ACCEPT" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                ShopService.ShopActionResult result = ShopService.orderBoardAccept(server, centralBank, playerId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
            }
            case "ORDER_BOARD_CANCEL" -> {
                if (server == null) {
                    yield fail(normalizedAction, "Server context is unavailable.");
                }
                ShopService.ShopActionResult result = ShopService.orderBoardCancel(server, centralBank, playerId, arg1);
                yield result.success() ? ok(normalizedAction, result.message()) : fail(normalizedAction, result.message());
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

    public static boolean canAccessProtectedSafeArea(CentralBank centralBank, UUID playerId, UUID bankId) {
        if (centralBank == null || playerId == null || bankId == null) {
            return false;
        }
        return SafetyDepositBoxService.canAccessProtectedSafeArea(centralBank, playerId, bankId);
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

        CompoundTag metadata = OwnerPcBankReadSupport.operationalMetadataSnapshot(
                centralBank, bank, currentOverworldGameTime(server));
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
        String singleLimit = OwnerPcBankReadSupport.positiveLimit(metadata, "limitSingle",
                BigDecimal.valueOf(Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()), null);
        String dailyPlayerLimit = OwnerPcBankReadSupport.positiveLimit(metadata, "limitDailyPlayer",
                BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_PLAYER_VOLUME.get()), null);
        String dailyBankLimit = OwnerPcBankReadSupport.positiveLimit(metadata, "limitDailyBank",
                BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_BANK_VOLUME.get()), null);
        String tellerLimit = OwnerPcBankReadSupport.positiveLimit(metadata, "limitTeller",
                DEFAULT_TELLER_WITHDRAWAL_LIMIT, MAX_TELLER_WITHDRAWAL_LIMIT);

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

        BankStaffingRoster staffingRoster = BankStaffingService.readRoster(server, metadata, bankId);
        List<String> employees = staffingRoster.playerEmployees().stream()
                .map(employee -> employee.resolvedName() + " - " + employee.role()
                        + " ($" + employee.salary().toPlainString() + ")")
                .toList();
        List<OwnerPcPlayerEmployeePayload> playerEmployees = staffingRoster.playerEmployees().stream()
                .map(employee -> new OwnerPcPlayerEmployeePayload(
                        employee.playerId(),
                        employee.resolvedName(),
                        employee.role(),
                        employee.salary().setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
                        employee.online(),
                        employee.safeAccessGranted()
                ))
                .toList();
        List<OwnerPcBankTellerPayload> bankTellers = staffingRoster.bankTellers().stream()
                .map(teller -> new OwnerPcBankTellerPayload(
                        teller.entityId(),
                        teller.displayName(),
                        teller.variant(),
                        teller.dimension(),
                        (int) Math.floor(teller.x()),
                        (int) Math.floor(teller.y()),
                        (int) Math.floor(teller.z()),
                        teller.active()
                ))
                .toList();

        List<String> loanProducts = decodeLoanProducts(metadata.getString("loanProducts")).stream()
                .map(product -> product.name() + " | max $" + product.maxAmount().toPlainString()
                        + " | APR " + product.interestRate() + "% | " + product.durationTicks() + " ticks")
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        List<String> interbankOffers = new ArrayList<>();
        centralBank.readInterbankOffers().values().stream()
                .filter(tag -> tag.hasUUID("lenderBankId") && bankId.equals(tag.getUUID("lenderBankId"))
                        || tag.hasUUID("acceptedByBankId") && bankId.equals(tag.getUUID("acceptedByBankId")))
                .sorted(Comparator.comparingLong(tag -> tag.contains("createdTick") ? tag.getLong("createdTick") : 0L))
                .forEach(tag -> {
                    String id = tag.hasUUID("id")
                            ? shortId(tag.getUUID("id"))
                            : OwnerPcBankReadSupport.stableTagId(tag);
                    String amount = readBigDecimal(tag, "amount").toPlainString();
                    String state = normalizeStatus(tag.getString("status"));
                    interbankOffers.add(id + " | $" + amount + " | " + state);
                });

        List<String> interbankLoans = new ArrayList<>();
        centralBank.readInterbankLoans().values().stream()
                .filter(tag -> tag.hasUUID("lenderBankId") && bankId.equals(tag.getUUID("lenderBankId"))
                        || tag.hasUUID("borrowerBankId") && bankId.equals(tag.getUUID("borrowerBankId"))
                        || tag.hasUUID("bankId") && bankId.equals(tag.getUUID("bankId")))
                .sorted(Comparator.comparingLong(tag -> tag.contains("createdTick") ? tag.getLong("createdTick") : 0L))
                .forEach(tag -> {
                    String id = tag.hasUUID("id")
                            ? shortId(tag.getUUID("id"))
                            : OwnerPcBankReadSupport.stableTagId(tag);
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
                        + " | " + account.getAccountUUID())
                .toList();

        List<String> certificateSchedule = bank.getBankAccounts().values().stream()
                .filter(account -> account.getAccountType() == AccountTypes.CertificateAccount)
                .sorted(Comparator.comparingLong(AccountHolder::getCertificateMaturityGameTime))
                .map(account -> shortId(account.getAccountUUID())
                        + " | " + resolvePlayerName(server, account.getPlayerUUID())
                        + " | tier " + account.getCertificateTier()
                        + " | maturity " + account.getCertificateMaturityGameTime())
                .toList();

        SafeDashboardData safe = buildSafeDashboardData(server, centralBank, bankId, bank, metadata);
        BankSafeSetupPayloadBuilder.Result safeSetup = BankSafeSetupPayloadBuilder.build(server, metadata);
        BankLevelService.BankLevelSnapshot bankLevel = BankLevelService.snapshot(centralBank, bank);
        List<OwnerPcSafeAccessLogPayload> safeAccessLogs = SafeAccessLogService.snapshot(metadata).stream()
                .map(entry -> new OwnerPcSafeAccessLogPayload(
                        entry.eventId(), entry.occurredAtMillis(), entry.category(), entry.outcome(),
                        entry.action(), entry.actorName(), entry.subject(), entry.detail(), entry.dimension(),
                        entry.x(), entry.y(), entry.z()))
                .toList();
        SafeAlarmSettingsService.Settings alarmSettings = SafeAlarmSettingsService.read(metadata);
        HeistSession activeAlarm = HeistSavedData.get(server).sessions().stream()
                .filter(session -> bankId.equals(session.bankId())
                        && session.phase().isRunning() && session.alarmed())
                .findFirst()
                .orElse(null);
        var safeSetupSnapshot = SafetyDepositBoxService.safeDepositSetupSnapshot(metadata);
        OwnerPcSafeAlarmPayload safeAlarm = new OwnerPcSafeAlarmPayload(
                alarmSettings.enabled(), alarmSettings.soundEventId(), alarmSettings.volume(),
                alarmSettings.primaryPitch(), alarmSettings.secondaryPitch(), alarmSettings.intervalTicks(),
                activeAlarm != null, activeAlarm == null ? "" : activeAlarm.alarmReason(),
                SafeAlarmSettingsService.countLoadedLinkedScanners(
                        server, safeSetupSnapshot, bankId, bank.getBankName()));

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
                singleLimit,
                dailyPlayerLimit,
                dailyBankLimit,
                tellerLimit,
                OwnerPcBankReadSupport.cardIssueFee(metadata).toPlainString(),
                OwnerPcBankReadSupport.cardReplacementFee(metadata).toPlainString(),
                BigDecimal.valueOf(centralBank.getFederalFundsRate()).setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
                String.valueOf(bankLevel.level()),
                String.valueOf(bankLevel.derivedLevel()),
                bankLevel.manual() ? "true" : "false",
                String.format(Locale.ROOT, "%.6f", bankLevel.progressRatio()),
                String.valueOf(bankLevel.nextDepositTargetDollars()),
                String.valueOf(bankLevel.nextAccountTarget()),
                bankLevel.roadmapNodes(),
                ownerView,
                roles,
                shares,
                cofounders,
                employees,
                loanProducts,
                interbankOffers,
                interbankLoans,
                accountRoster,
                certificateSchedule,
                safe.areaCount(),
                safe.rowCapacity(),
                safe.claimedRowUnits(),
                safe.totalBoxSlots(),
                safe.assignedBoxes(),
                safe.freeBoxes(),
                safe.lockedBoxes(),
                safe.escrowCases(),
                safe.policyMode(),
                safe.policyAmount(),
                safe.rentPeriodTicks(),
                safe.overdueTicks(),
                safe.areaSummaries(),
                safe.boxAssignments(),
                safe.lockedQueue(),
                playerEmployees,
                bankTellers,
                safeSetup.vaults(),
                safeSetup.objective(),
                OwnerPcPremisePayloadBuilder.build(server, metadata, bankId),
                bankLevel.viewingRoomCapacity(),
                buildViewingRoomPayloads(server, centralBank, bankId),
                safeAccessLogs,
                safeAlarm,
                VaultStorageSnapshotService.build(server, safeSetupSnapshot)
        );
    }

    private static List<OwnerPcViewingRoomPayload> buildViewingRoomPayloads(MinecraftServer server,
                                                                             CentralBank centralBank,
                                                                             UUID bankId) {
        return ViewingRoomService.states(server, centralBank, bankId,
                        SafeBoxViewingCoordinator.activeRoomIds(server)).stream()
                .map(state -> {
                    var room = state.room();
                    return new OwnerPcViewingRoomPayload(
                            room.id().toString(), room.name(), room.premiseId(), room.bounds().dimension(),
                            room.bounds().minX() + "," + room.bounds().minY() + "," + room.bounds().minZ()
                                    + " -> " + room.bounds().maxX() + "," + room.bounds().maxY() + "," + room.bounds().maxZ(),
                            state.status().name(), state.reasons(),
                            formatViewingRoomAnchor(room.customerAnchor()),
                            formatViewingRoomAnchor(room.tellerAnchor()),
                            formatViewingRoomAnchor(room.displayAnchor()));
                })
                .toList();
    }

    private static String formatViewingRoomAnchor(ViewingRoomAnchor anchor) {
        if (anchor == null) {
            return "";
        }
        return String.format(Locale.ROOT, "%s | %.2f, %.2f, %.2f | yaw %.1f",
                anchor.dimension(), anchor.x(), anchor.y(), anchor.z(), anchor.yaw());
    }

    public static OwnerPcSetupObjectivePayload buildSafeDepositSetupObjective(MinecraftServer server,
                                                                               CentralBank centralBank,
                                                                               UUID bankId) {
        if (centralBank == null || bankId == null) {
            return OwnerPcSetupObjectivePayload.unavailable();
        }
        SafetyDepositBoxService.ensureSafeDepositSetup(centralBank, bankId);
        return BankSafeSetupPayloadBuilder.build(server, centralBank.getOrCreateBankMetadata(bankId)).objective();
    }

    private static SafeDashboardData buildSafeDashboardData(MinecraftServer server,
                                                            CentralBank centralBank,
                                                            UUID bankId,
                                                            Bank bank,
                                                            CompoundTag metadata) {
        CompoundTag safeMetadata = metadata == null ? new CompoundTag() : metadata;
        ListTag areas = safeMetadata.getList(SafetyDepositBoxService.AREAS_KEY, Tag.TAG_COMPOUND);
        ListTag assignments = safeMetadata.getList(SafetyDepositBoxService.ASSIGNMENTS_KEY, Tag.TAG_COMPOUND);
        ListTag escrow = safeMetadata.getList(SafetyDepositBoxService.ESCROW_KEY, Tag.TAG_COMPOUND);

        int rowCapacity = BankLevelService.safeRowCapacity(centralBank, bank);
        int loadedRowBlocks = SafetyDepositBoxService.countLoadedSafeRowBlocks(server, areas);
        List<SafeRowView> liveRows = collectLoadedSafeRows(server, areas);
        Map<String, SafeAssignmentView> assignmentsByLocation = new HashMap<>();

        List<String> areaSummaries = new ArrayList<>();
        for (int i = 0; i < areas.size(); i++) {
            CompoundTag area = areas.getCompound(i);
            int units = SafetyDepositBoxService.claimedRowUnits(area);
            String min = area.getInt("minX") + "," + area.getInt("minY") + "," + area.getInt("minZ");
            String max = area.getInt("maxX") + "," + area.getInt("maxY") + "," + area.getInt("maxZ");
            areaSummaries.add(safeField(area.getString("dimension"))
                    + "|" + min
                    + "|" + max
                    + "|" + units
                    + "|" + area.getLong("claimedAtMillis"));
        }

        List<String> boxAssignments = new ArrayList<>();
        List<String> lockedQueue = new ArrayList<>();
        int assignmentCount = 0;
        int locked = 0;
        for (int i = 0; i < assignments.size(); i++) {
            CompoundTag tag = assignments.getCompound(i);
            SafeAssignmentView assignment = readSafeAssignment(tag, safeMetadata);
            if (assignment == null) {
                continue;
            }
            assignmentsByLocation.put(safeAssignmentLocationKey(assignment.dimension(), assignment.pos(), assignment.doorIndex()), assignment);
            assignmentCount++;
            UUID accountId = assignment.accountId();
            AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
            String accountName = account == null ? "Missing account" : resolvePlayerName(server, account.getPlayerUUID());
            String status = assignment.locked() ? "LOCKED" : "ASSIGNED";
            if ("LOCKED".equals(status)) {
                locked++;
            }
            String boxNumber = assignment.boxNumber();
            if (boxNumber == null || boxNumber.isBlank()) {
                boxNumber = "SDB-" + shortId(accountId);
            }
            String pos = assignment.pos().getX() + "," + assignment.pos().getY() + "," + assignment.pos().getZ();
            String row = safeField(boxNumber)
                    + "|" + safeField(accountName)
                    + "|" + accountId
                    + "|" + safeField(assignment.dimension())
                    + "|" + pos
                    + "|" + assignment.doorIndex()
                    + "|" + status
                    + "|" + assignment.assignedAtMillis();
            boxAssignments.add(row);
            if ("LOCKED".equals(status)) {
                lockedQueue.add(row);
            }
        }
        boxAssignments.sort(String.CASE_INSENSITIVE_ORDER);
        lockedQueue.sort(String.CASE_INSENSITIVE_ORDER);

        String mode = safeMetadata.getString(SafetyDepositBoxService.POLICY_MODE_KEY);
        if (mode == null || mode.isBlank()) {
            mode = "FREE";
        } else {
            mode = mode.trim().toUpperCase(Locale.ROOT);
        }
        BigDecimal policyAmount = switch (mode) {
            case "ONE_TIME" -> readBigDecimal(safeMetadata, SafetyDepositBoxService.ONE_TIME_FEE_KEY);
            case "RECURRING" -> readBigDecimal(safeMetadata, SafetyDepositBoxService.RENT_AMOUNT_KEY);
            default -> BigDecimal.ZERO;
        };
        long rentPeriod = safeMetadata.contains(SafetyDepositBoxService.RENT_PERIOD_TICKS_KEY)
                ? safeMetadata.getLong(SafetyDepositBoxService.RENT_PERIOD_TICKS_KEY)
                : 7L * 24L * 60L * 60L * 20L;
        long overduePeriod = safeMetadata.contains(SafetyDepositBoxService.OVERDUE_TICKS_KEY)
                ? safeMetadata.getLong(SafetyDepositBoxService.OVERDUE_TICKS_KEY)
                : 3L * 24L * 60L * 60L * 20L;

        List<String> liveMapRows = buildSafeBoxWallMapRows(server, centralBank, liveRows, assignmentsByLocation, safeMetadata);
        List<String> policyRows = OwnerPcSafePolicySnapshot.rows(server, safeMetadata);
        if (!liveMapRows.isEmpty()) {
            boxAssignments.addAll(0, liveMapRows);
        }
        if (!policyRows.isEmpty()) {
            boxAssignments.addAll(0, policyRows);
        }
        int liveTotalBoxes = countLiveSafeBoxes(liveRows);
        int liveAssignedBoxes = countLiveAssignedBoxes(liveRows, assignmentsByLocation);
        int totalSlots = liveRows.isEmpty() ? assignmentCount : liveTotalBoxes;
        int assigned = liveRows.isEmpty() ? assignmentCount : liveAssignedBoxes;

        return new SafeDashboardData(
                String.valueOf(areas.size()),
                String.valueOf(rowCapacity),
                String.valueOf(loadedRowBlocks),
                String.valueOf(totalSlots),
                String.valueOf(assigned),
                String.valueOf(Math.max(0, totalSlots - assigned)),
                String.valueOf(locked),
                String.valueOf(escrow.size()),
                mode,
                policyAmount.setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
                String.valueOf(Math.max(1L, rentPeriod)),
                String.valueOf(Math.max(1L, overduePeriod)),
                areaSummaries,
                boxAssignments,
                lockedQueue
        );
    }

    private static List<SafeRowView> collectLoadedSafeRows(MinecraftServer server, ListTag areas) {
        List<SafetyDepositBoxService.LoadedSafeRow> loadedRows = SafetyDepositBoxService.collectLoadedSafeRows(server, areas);
        if (loadedRows.isEmpty()) {
            return List.of();
        }
        List<SafeRowView> rows = new ArrayList<>();
        for (SafetyDepositBoxService.LoadedSafeRow loaded : loadedRows) {
            if (loaded == null || loaded.pos() == null || loaded.row() == null) {
                continue;
            }
            rows.add(new SafeRowView(
                    rows.size() + 1,
                    normalizeDim(loaded.dimension()),
                    loaded.pos().immutable(),
                    loaded.row()
            ));
        }
        rows.sort(Comparator
                .comparing(SafeRowView::dimension, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(view -> view.pos().getY())
                .thenComparingInt(view -> view.pos().getX())
                .thenComparingInt(view -> view.pos().getZ()));
        List<SafeRowView> indexed = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            SafeRowView row = rows.get(i);
            indexed.add(new SafeRowView(i + 1, row.dimension(), row.pos(), row.row()));
        }
        return indexed;
    }

    public static List<String> buildSafePolicyRows(MinecraftServer server, CentralBank centralBank, UUID bankId) {
        return centralBank == null || bankId == null
                ? List.of()
                : OwnerPcSafePolicySnapshot.rows(
                        server, OwnerPcBankReadSupport.metadataSnapshot(centralBank, bankId));
    }

    private static List<String> buildSafeBoxWallMapRows(MinecraftServer server,
                                                        CentralBank centralBank,
                                                        List<SafeRowView> rows,
                                                        Map<String, SafeAssignmentView> assignmentsByLocation,
                                                        CompoundTag safeMetadata) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (SafeRowView view : rows) {
            if (view == null || view.row() == null || view.pos() == null) {
                continue;
            }
            int boxCount = 0;
            int assignedCount = 0;
            int lockedCount = 0;
            int coverCount = 0;
            int emptyCount = 0;
            List<String> boxLines = new ArrayList<>();
            for (int door = 0; door < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; door++) {
                int moduleStart = view.row().getModuleStartForRow(door);
                if (moduleStart >= 0 && moduleStart != door) {
                    continue;
                }
                SafetyDepositBoxRowBlockEntity.ModuleType type = moduleStart < 0
                        ? SafetyDepositBoxRowBlockEntity.ModuleType.EMPTY
                        : view.row().getModuleType(door);
                if (type == null) {
                    type = SafetyDepositBoxRowBlockEntity.ModuleType.EMPTY;
                }
                boolean assignable = type.assignable() && view.row().isAssignableBoxStart(door);
                SafeAssignmentView assignment = assignable
                        ? assignmentsByLocation.get(safeAssignmentLocationKey(view.dimension(), view.pos(), door))
                        : null;
                UUID assignedAccountId = assignment == null ? view.row().getAssignedAccountId(door) : assignment.accountId();
                AccountHolder account = assignedAccountId == null ? null : centralBank.SearchForAccountByAccountId(assignedAccountId);
                String owner = assignedAccountId == null
                        ? ""
                        : (account == null ? "Missing account" : resolvePlayerName(server, account.getPlayerUUID()));
                String status;
                if (assignable) {
                    boxCount++;
                    if (assignedAccountId != null) {
                        assignedCount++;
                    }
                    if (assignment != null && assignment.locked()) {
                        lockedCount++;
                        status = "LOCKED";
                    } else {
                        status = assignedAccountId == null ? "FREE" : "ASSIGNED";
                    }
                } else if (type == SafetyDepositBoxRowBlockEntity.ModuleType.COVER) {
                    coverCount++;
                    status = "COVER";
                } else {
                    emptyCount++;
                    status = "EMPTY";
                }

                String boxNumber = assignment == null ? view.row().getBoxNumber(door) : assignment.boxNumber();
                if (boxNumber == null || boxNumber.isBlank()) {
                    boxNumber = assignable && assignedAccountId != null ? "SDB-" + shortId(assignedAccountId) : "";
                }
                BigDecimal paidAmount = assignment == null ? safePolicyAmount(safeMetadata, type) : assignment.paidAmount();
                long rentPeriod = assignment == null ? safeRentPeriodTicks(safeMetadata, type) : assignment.rentPeriodTicks();
                int rowSpan = Math.max(1, type.rowSpan());
                boxLines.add("@safe_box="
                        + view.index()
                        + "|" + door
                        + "|" + safeField(type.name())
                        + "|" + safeField(safeModuleLabel(type))
                        + "|" + rowSpan
                        + "|" + status
                        + "|" + safeField(boxNumber)
                        + "|" + safeField(owner)
                        + "|" + (assignedAccountId == null ? "" : assignedAccountId)
                        + "|" + paidAmount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN).toPlainString()
                        + "|" + Math.max(1L, rentPeriod)
                        + "|" + (assignment == null ? 0L : Math.max(0L, assignment.assignedAtMillis()))
                        + "|" + safeField(encodeSafeLocateTarget(view.dimension(), view.pos(), door)));
            }

            lines.add("@safe_row="
                    + view.index()
                    + "|" + safeField(view.dimension())
                    + "|" + view.pos().getX()
                    + "|" + view.pos().getY()
                    + "|" + view.pos().getZ()
                    + "|" + safeField(encodeSafeLocateTarget(view.dimension(), view.pos(), 0))
                    + "|" + boxCount
                    + "|" + assignedCount
                    + "|" + lockedCount
                    + "|" + coverCount
                    + "|" + emptyCount);
            lines.addAll(boxLines);
        }
        return lines;
    }

    private static int countLiveSafeBoxes(List<SafeRowView> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (SafeRowView view : rows) {
            if (view == null || view.row() == null) {
                continue;
            }
            for (int door = 0; door < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; door++) {
                if (view.row().isAssignableBoxStart(door)) {
                    total++;
                }
            }
        }
        return total;
    }

    private static int countLiveAssignedBoxes(List<SafeRowView> rows, Map<String, SafeAssignmentView> assignmentsByLocation) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (SafeRowView view : rows) {
            if (view == null || view.row() == null) {
                continue;
            }
            for (int door = 0; door < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; door++) {
                if (!view.row().isAssignableBoxStart(door)) {
                    continue;
                }
                boolean assigned = view.row().getAssignedAccountId(door) != null
                        || (assignmentsByLocation != null
                        && assignmentsByLocation.containsKey(safeAssignmentLocationKey(view.dimension(), view.pos(), door)));
                if (assigned) {
                    total++;
                }
            }
        }
        return total;
    }

    private static SafeAssignmentView readSafeAssignment(CompoundTag tag, CompoundTag safeMetadata) {
        if (tag == null || !tag.hasUUID("accountId") || !tag.hasUUID("bankId")) {
            return null;
        }
        int doorIndex = tag.getInt("doorIndex");
        if (doorIndex < 0 || doorIndex >= SafetyDepositBoxRowBlockEntity.DOOR_COUNT) {
            return null;
        }
        BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        String boxNumber = tag.getString("boxNumber");
        if (boxNumber == null) {
            boxNumber = "";
        }
        BigDecimal paidAmount = tag.contains("paidAmount")
                ? readBigDecimal(tag, "paidAmount")
                : safePolicyAmount(safeMetadata);
        long rentPeriod = tag.contains("rentPeriodTicks")
                ? tag.getLong("rentPeriodTicks")
                : safeRentPeriodTicks(safeMetadata);
        return new SafeAssignmentView(
                tag.getUUID("bankId"),
                tag.getUUID("accountId"),
                normalizeDim(tag.getString("dimension")),
                pos,
                doorIndex,
                boxNumber,
                tag.getBoolean("locked"),
                tag.getLong("assignedAtMillis"),
                paidAmount,
                Math.max(1L, rentPeriod)
        );
    }

    private static String safeAssignmentLocationKey(String dimension, BlockPos pos, int doorIndex) {
        if (pos == null) {
            return normalizeDim(dimension) + "|0|0|0|" + doorIndex;
        }
        return normalizeDim(dimension)
                + "|" + pos.getX()
                + "|" + pos.getY()
                + "|" + pos.getZ()
                + "|" + Math.max(0, doorIndex);
    }

    private static BigDecimal safePolicyAmount(CompoundTag safeMetadata) {
        return safePolicyAmount(safeMetadata, SafetyDepositBoxRowBlockEntity.ModuleType.SMALL);
    }

    private static BigDecimal safePolicyAmount(CompoundTag safeMetadata, SafetyDepositBoxRowBlockEntity.ModuleType type) {
        return SafetyDepositBoxService.pricingPolicy(safeMetadata, type).amount();
    }

    private static long safeRentPeriodTicks(CompoundTag safeMetadata) {
        return safeRentPeriodTicks(safeMetadata, SafetyDepositBoxRowBlockEntity.ModuleType.SMALL);
    }

    private static long safeRentPeriodTicks(CompoundTag safeMetadata, SafetyDepositBoxRowBlockEntity.ModuleType type) {
        return SafetyDepositBoxService.pricingPolicy(safeMetadata, type).rentPeriodTicks();
    }

    private static String encodeSafeLocateTarget(String dimension, BlockPos pos, int doorIndex) {
        if (pos == null) {
            return "";
        }
        return normalizeDim(dimension)
                + ";" + pos.getX()
                + ";" + pos.getY()
                + ";" + pos.getZ()
                + ";" + Math.max(0, doorIndex);
    }

    private static SafeLocateTarget parseSafeLocateTarget(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split(";", -1);
        if (parts.length < 5) {
            return null;
        }
        try {
            String dimension = normalizeDim(parts[0]);
            int x = Integer.parseInt(parts[1].trim());
            int y = Integer.parseInt(parts[2].trim());
            int z = Integer.parseInt(parts[3].trim());
            int door = Integer.parseInt(parts[4].trim());
            if (dimension.isBlank()) {
                return null;
            }
            return new SafeLocateTarget(dimension, new BlockPos(x, y, z), door);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean isInsideSafeAreas(ListTag areas, String dimension, BlockPos pos) {
        return SafetyDepositBoxService.containsSafeRowScanBounds(areas, dimension, pos);
    }

    private static String safeModuleLabel(SafetyDepositBoxRowBlockEntity.ModuleType type) {
        if (type == null) {
            return "Empty";
        }
        return switch (type) {
            case SMALL -> "Small";
            case MEDIUM -> "Medium";
            case LARGE -> "Large";
            case EXTRA_LARGE -> "Extra Large";
            case COVER -> "Cover";
            default -> "Empty";
        };
    }

    private static ServerLevel levelForSafeDashboard(MinecraftServer server, String dimension) {
        if (server == null || dimension == null || dimension.isBlank()) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    private static String safeField(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace('|', '/').trim();
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
        return executeAction(server, centralBank, player, bankId, action, arg1, arg2, arg3, arg4,
                OwnerPcActionPolicy.Channel.TRUSTED_REMOTE);
    }

    public static ActionResult executeDirectAction(MinecraftServer server,
                                                   CentralBank centralBank,
                                                   ServerPlayer player,
                                                   UUID bankId,
                                                   String action,
                                                   String arg1,
                                                   String arg2,
                                                   String arg3,
                                                   String arg4) {
        return executeAction(server, centralBank, player, bankId, action, arg1, arg2, arg3, arg4,
                OwnerPcActionPolicy.Channel.DIRECT_OWNER_PC);
    }

    private static ActionResult executeAction(MinecraftServer server,
                                              CentralBank centralBank,
                                              ServerPlayer player,
                                              UUID bankId,
                                              String action,
                                              String arg1,
                                              String arg2,
                                              String arg3,
                                              String arg4,
                                              OwnerPcActionPolicy.Channel channel) {
        if (server == null || centralBank == null || player == null || bankId == null) {
            return new ActionResult(false, "Bank data is unavailable.");
        }
        OwnerPcActionPolicy.Action classifiedAction = OwnerPcActionPolicy.classify(action);
        if (classifiedAction == null) {
            OwnerPcActionPolicy.Decision unknown = OwnerPcActionPolicy.authorize(
                    action, channel, null);
            return new ActionResult(false, unknown.message());
        }

        boolean allowCentralBankAccess = bankId.equals(centralBank.getBankId()) && player.hasPermissions(3);
        if (!canAccessBank(centralBank, player.getUUID(), bankId, allowCentralBankAccess)) {
            return new ActionResult(false, "You do not have access to this bank app.");
        }

        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return new ActionResult(false, "Bank no longer exists.");
        }

        OwnerPcActionPolicy.MutationContext mutationContext =
                channel == OwnerPcActionPolicy.Channel.DIRECT_OWNER_PC
                        && classifiedAction.access() == OwnerPcActionPolicy.Access.MUTATION
                        ? directMutationContext(server, centralBank, player)
                        : null;
        OwnerPcActionPolicy.Decision decision = OwnerPcActionPolicy.authorize(
                action, channel, mutationContext);
        if (!decision.allowed()) {
            return new ActionResult(false, decision.message());
        }

        boolean owner = player.getUUID().equals(bank.getBankOwnerId()) || allowCentralBankAccess;
        String normalizedAction = classifiedAction.name();

        return switch (classifiedAction) {
            case SHOW_INFO, SHOW_RESERVE, SHOW_DASHBOARD, SHOW_ACCOUNTS, SHOW_CDS,
                 SHOW_LIMITS, SHOW_ROLES, SHOW_SHARES, SHOW_COFOUNDERS, SHOW_EMPLOYEES,
                 SHOW_LOAN_PRODUCTS, SHOW_LOANS, SHOW_MARKET ->
                    buildShowActionResult(server, centralBank, bank, normalizedAction,
                            player.getUUID(), allowCentralBankAccess);
            case BANK_LEVEL_ROADMAP -> new ActionResult(normalizedAction, true,
                    BankLevelService.levelRoadmapReport(centralBank, bank));
            case SET_MOTTO -> handleSetMotto(centralBank, bank, owner, arg1);
            case SET_COLOR -> handleSetColor(centralBank, bank, owner, arg1);
            case SET_LIMIT -> handleSetLimit(centralBank, bank, owner, arg1, arg2);
            case SET_CARD_FEES -> handleSetCardFees(centralBank, bank, owner, arg1, arg2);
            case ROLE_ASSIGN -> handleRoleAssign(server, centralBank, bank, owner, arg1, arg2);
            case ROLE_REVOKE -> handleRoleRevoke(server, centralBank, bank, owner, arg1);
            case SHARES_SET -> handleSharesSet(server, centralBank, bank, owner, arg1, arg2);
            case COFOUNDER_ADD -> handleCofounderAdd(server, centralBank, bank, owner, arg1);
            case HIRE -> handleHire(server, centralBank, bank, owner, arg1, arg2, arg3);
            case FIRE -> handleFire(server, centralBank, bank, owner, arg1);
            case TELLER_ISSUE -> handleTellerIssue(server, bank, player, owner);
            case TELLER_COUNT -> handleTellerCount(server, bank, owner);
            case BORROW -> handleBorrow(server, centralBank, bank, owner, arg1);
            case LEND_OFFER -> handleLendOffer(server, centralBank, bank, owner, arg1, arg2, arg3);
            case LEND_ACCEPT -> handleLendAccept(server, centralBank, bank, owner, arg1);
            case APPEAL -> handleAppeal(server, centralBank, bank, player, owner, arg1);
            case CREATE_LOAN_PRODUCT -> handleCreateLoanProduct(
                    centralBank, bank, owner, arg1, arg2, arg3, arg4);
            case ACCOUNT_DETAIL -> handleAccountDetail(server, centralBank, bank, arg1, arg2, "");
            case ACCOUNT_FREEZE -> handleAccountFreeze(server, centralBank, bank, owner, arg1, arg2);
            case ACCOUNT_UNFREEZE -> handleAccountUnfreeze(server, centralBank, bank, owner, arg1);
            case ACCOUNT_TEMP_LIMIT -> handleAccountTemporaryLimit(
                    server, centralBank, bank, owner, arg1, arg2, arg3);
            case SAFE_AREA_CLAIM_TOOL -> handleSafeAreaClaimTool(server, centralBank, bank, player);
            case SAFE_BOX_ASSIGN -> handleSafeBoxAssign(server, centralBank, bank, player, arg1);
            case SAFE_BOX_LOCATE -> handleSafeBoxLocate(server, centralBank, bank, player, arg1);
            case SAFE_BOX_POLICY -> handleSafeBoxPolicy(
                    centralBank, bank, player, arg1, arg2, arg3, arg4);
            case SAFE_BOX_SEIZE -> handleSafeBoxSeize(centralBank, bank, player, arg1);
            case SAFE_ACCESS_GRANT -> handleSafeAccess(
                    server, centralBank, bank, player, arg1, true);
            case SAFE_ACCESS_REVOKE -> handleSafeAccess(
                    server, centralBank, bank, player, arg1, false);
            case SAFE_ALARM_CONFIG -> handleSafeAlarmConfig(
                    centralBank, bank, player, arg1, arg2, arg3, arg4, true);
            case SAFE_ALARM_TEST -> handleSafeAlarmTest(
                    centralBank, bank, player, arg1, arg2, arg3, arg4);
            case SAFE_ALARM_STOP_TEST -> handleSafeAlarmStopTest(centralBank, bank, player);
            case SAFE_ALARM_RESET -> handleSafeAlarmReset(centralBank, bank, player);
            case VIEWING_ROOM_CLAIM_TOOL -> handleViewingRoomClaimTool(
                    server, centralBank, bank, player);
            case VIEWING_ROOM_ANCHOR -> handleViewingRoomAnchor(
                    server, centralBank, bank, player, arg1, arg2);
            case VIEWING_ROOM_RENAME -> handleViewingRoomRename(
                    server, centralBank, bank, player, arg1, arg2);
            case VIEWING_ROOM_SUSPEND -> handleViewingRoomSuspend(
                    server, centralBank, bank, player, arg1, arg2);
            case VIEWING_ROOM_DELETE -> handleViewingRoomDelete(
                    server, centralBank, bank, player, arg1);
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
                    "Minimum Reserve: $" + data.minReserve(),
                    "Card Issue Fee: $" + data.cardIssueFee(),
                    "Card Replacement Fee: $" + data.cardReplacementFee()
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
                    risk = "High Risk";
                } else if (dailyCap.compareTo(BigDecimal.ZERO) > 0
                        && dailyUsed.divide(dailyCap, 4, RoundingMode.HALF_EVEN).compareTo(BigDecimal.valueOf(0.90)) >= 0) {
                    risk = "Medium Risk";
                } else {
                    risk = "No Risk";
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
                    "Single Tx Limit: $" + data.singleLimit(),
                    "Daily Player Limit: $" + data.dailyPlayerLimit(),
                    "Daily Bank Limit: $" + data.dailyBankLimit(),
                    "Teller Cash Limit: $" + data.tellerLimit(),
                    "Daily Cap: $" + data.dailyCap(),
                    "Daily Used: $" + data.dailyUsed(),
                    "Daily Remaining: $" + data.dailyRemaining(),
                    "Minimum Reserve: $" + data.minReserve(),
                    "Card Issue Fee: $" + data.cardIssueFee(),
                    "Card Replacement Fee: $" + data.cardReplacementFee()
            );
            case "SHOW_ROLES" -> body = formatList("Roles (" + data.roles().size() + ")", data.roles());
            case "SHOW_SHARES" -> body = formatList("Shares (" + data.shares().size() + ")", data.shares());
            case "SHOW_COFOUNDERS" -> body = formatList("Cofounders (" + data.cofounders().size() + ")", data.cofounders());
            case "SHOW_EMPLOYEES" -> body = formatList("Employees (" + data.employees().size() + ")", data.employees());
            case "SHOW_LOAN_PRODUCTS" -> body = formatList("Loan Products (" + data.loanProducts().size() + ")", data.loanProducts());
            case "SHOW_LOANS" -> body = formatList("Interbank Loans (" + data.interbankLoans().size() + ")", data.interbankLoans());
            case "SHOW_MARKET" -> {
                long nowTick = currentOverworldGameTime(server);
                List<String> market = centralBank.readInterbankOffers().values().stream()
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
                            String id = tag.hasUUID("id")
                                    ? tag.getUUID("id").toString()
                                    : OwnerPcBankReadSupport.stableTagId(tag);
                            return id + " | " + lender + " | $" + amount + " | APR " + rate + "% | " + term + " ticks";
                        })
                        .toList();
                body = formatList("Open Market Offers (" + market.size() + ")", market);
            }
            default -> body = "No data available for action: " + action;
        }

        return new ActionResult(true, header + "\n" + body);
    }

    private static ActionResult handleAccountDetail(MinecraftServer server,
                                                    CentralBank centralBank,
                                                    Bank bank,
                                                    String accountIdRaw,
                                                    String pageRaw,
                                                    String notice) {
        UUID accountId = parseUuid(accountIdRaw);
        if (accountId == null) {
            return new ActionResult(false, "Account detail failed: invalid account id.");
        }
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null || !bank.getBankId().equals(account.getBankId())) {
            return new ActionResult(false, "Account detail failed: account is not in this bank.");
        }
        long gameTime = currentOverworldGameTime(server);
        AccountReadSnapshot accountSnapshot = account.readOnlySnapshot(gameTime);

        List<UserTransaction> transactions = account.readOnlyTransactions().values().stream()
                .filter(tx -> tx != null)
                .sorted(Comparator.comparing(UserTransaction::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        int total = transactions.size();
        int from = 0;
        int to = Math.min(total, ACCOUNT_DETAIL_HISTORY_LIMIT);

        List<String> lines = new ArrayList<>();
        lines.add("@account.detail");
        if (notice != null && !notice.isBlank()) {
            lines.add("notice=" + sanitizeLine(notice));
        }
        lines.add("account.id=" + account.getAccountUUID());
        lines.add("account.player=" + sanitizeLine(resolvePlayerName(server, account.getPlayerUUID())));
        lines.add("account.type=" + sanitizeLine(account.getAccountType() == null ? "Unknown" : account.getAccountType().label));
        lines.add("account.balance=" + account.getBalance().toPlainString());
        lines.add("account.bank=" + sanitizeLine(bank.getBankName()));
        lines.add("account.created=" + formatAccountDate(account.getDateOfCreation()));
        lines.add("account.primary=" + account.isPrimaryAccount());
        lines.add("account.frozen=" + account.isFrozen());
        lines.add("account.frozen_reason=" + sanitizeLine(account.getFrozenReason()));
        lines.add("account.access_type=" + sanitizeLine(account.getAccountAccessType()));
        lines.add("account.business_label=" + sanitizeLine(account.getBusinessLabel()));
        lines.add("daily.limit=" + accountSnapshot.dailyLimit().toPlainString());
        lines.add("daily.used=" + accountSnapshot.dailyUsed().toPlainString());
        lines.add("daily.remaining=" + accountSnapshot.dailyRemaining().toPlainString());
        BigDecimal tempLimit = accountSnapshot.temporaryLimit();
        long tempExpires = accountSnapshot.temporaryExpiresAtEpochMillis();
        lines.add("temp.limit=" + (tempLimit == null ? "" : tempLimit.toPlainString()));
        lines.add("temp.expires_millis=" + Math.max(-1L, tempExpires));
        lines.add("credit.score=" + account.getCreditScore());
        lines.add("credit.defaulted=" + account.isDefaulted());
        lines.add("certificate.tier=" + sanitizeLine(account.getCertificateTier()));
        lines.add("certificate.locked=" + accountSnapshot.certificateLocked());
        lines.add("history.page=0");
        lines.add("history.page_size=" + ACCOUNT_DETAIL_HISTORY_LIMIT);
        lines.add("history.total=" + total);
        lines.add("history.has_prev=false");
        lines.add("history.has_next=false");

        int outIndex = 0;
        for (int i = from; i < to; i++) {
            UserTransaction tx = transactions.get(i);
            UUID counterparty = account.getAccountUUID().equals(tx.getSenderUUID()) ? tx.getReceiverUUID() : tx.getSenderUUID();
            AccountHolder other = centralBank.SearchForAccountByAccountId(counterparty);
            String direction = account.getAccountUUID().equals(tx.getSenderUUID()) ? "OUTGOING" : "INCOMING";
            String prefix = "tx." + outIndex + ".";
            lines.add(prefix + "id=" + tx.getTransactionUUID());
            lines.add(prefix + "date=" + formatAccountDate(tx.getTimestamp()));
            lines.add(prefix + "direction=" + direction);
            lines.add(prefix + "amount=" + (tx.getAmount() == null ? "0" : tx.getAmount().toPlainString()));
            lines.add(prefix + "description=" + sanitizeLine(tx.getTransactionDescription()));
            lines.add(prefix + "counterparty_type=" + (other == null ? "EXTERNAL" : "ACCOUNT"));
            lines.add(prefix + "counterparty_short=" + sanitizeLine(counterparty == null ? "unknown" : shortId(counterparty)));
            outIndex++;
        }

        return new ActionResult("ACCOUNT_DETAIL", true, String.join("\n", lines));
    }

    private static ActionResult handleSafeAreaClaimTool(MinecraftServer server,
                                                        CentralBank centralBank,
                                                        Bank bank,
                                                        ServerPlayer player) {
        if (!SafetyDepositBoxService.canManageSafeArea(centralBank, player, bank.getBankId())) {
            return new ActionResult(false, SafetyDepositBoxService.safeAreaManagementDeniedMessage("claim safe areas"));
        }
        SafetyDepositBoxService.ActionResult result =
                SafetyDepositBoxService.startSafeAreaClaimToolSession(server, centralBank, player, bank.getBankId());
        return new ActionResult(result.success(), result.message());
    }

    private static ActionResult handleViewingRoomClaimTool(MinecraftServer server,
                                                            CentralBank centralBank,
                                                            Bank bank,
                                                            ServerPlayer player) {
        SafetyDepositBoxService.ActionResult result =
                SafetyDepositBoxService.startViewingRoomClaimToolSession(
                        server, centralBank, player, bank.getBankId(), false);
        return new ActionResult(result.success(), result.message());
    }

    private static ActionResult handleViewingRoomAnchor(MinecraftServer server,
                                                         CentralBank centralBank,
                                                         Bank bank,
                                                         ServerPlayer player,
                                                         String roomIdRaw,
                                                         String kindRaw) {
        UUID roomId = parseUuid(roomIdRaw);
        ViewingRoomService.AnchorKind kind = ViewingRoomService.AnchorKind.parse(kindRaw);
        if (SafeBoxViewingCoordinator.isRoomActive(server, roomId)) {
            return new ActionResult(false, "An active viewing session must finish before changing room anchors.");
        }
        SafetyDepositBoxService.ActionResult result =
                SafetyDepositBoxService.startViewingRoomAnchorToolSession(
                        server, centralBank, player, bank.getBankId(), roomId, kind, false);
        return new ActionResult(result.success(), result.message());
    }

    private static ActionResult handleViewingRoomRename(MinecraftServer server,
                                                         CentralBank centralBank,
                                                         Bank bank,
                                                         ServerPlayer player,
                                                         String roomIdRaw,
                                                         String name) {
        if (!SafetyDepositBoxService.canManageSafeArea(centralBank, player, bank.getBankId())) {
            return new ActionResult(false,
                    SafetyDepositBoxService.safeAreaManagementDeniedMessage("rename viewing rooms"));
        }
        UUID roomId = parseUuid(roomIdRaw);
        if (SafeBoxViewingCoordinator.isRoomActive(server, roomId)) {
            return new ActionResult(false, "An active viewing session must finish before renaming this room.");
        }
        ViewingRoomService.MutationResult result = ViewingRoomService.rename(
                centralBank, bank.getBankId(), roomId, name);
        return new ActionResult(result.success(), result.message());
    }

    private static ActionResult handleViewingRoomSuspend(MinecraftServer server,
                                                          CentralBank centralBank,
                                                          Bank bank,
                                                          ServerPlayer player,
                                                          String roomIdRaw,
                                                          String suspendedRaw) {
        if (!SafetyDepositBoxService.canManageSafeArea(centralBank, player, bank.getBankId())) {
            return new ActionResult(false,
                    SafetyDepositBoxService.safeAreaManagementDeniedMessage("suspend viewing rooms"));
        }
        UUID roomId = parseUuid(roomIdRaw);
        if (SafeBoxViewingCoordinator.isRoomActive(server, roomId)) {
            return new ActionResult(false, "An active viewing session must finish before suspending this room.");
        }
        boolean suspended = Boolean.parseBoolean(suspendedRaw);
        ViewingRoomService.MutationResult result = ViewingRoomService.setAdminSuspended(
                centralBank, bank.getBankId(), roomId, suspended);
        return new ActionResult(result.success(), result.message());
    }

    private static ActionResult handleViewingRoomDelete(MinecraftServer server,
                                                         CentralBank centralBank,
                                                         Bank bank,
                                                         ServerPlayer player,
                                                         String roomIdRaw) {
        if (!SafetyDepositBoxService.canManageSafeArea(centralBank, player, bank.getBankId())) {
            return new ActionResult(false,
                    SafetyDepositBoxService.safeAreaManagementDeniedMessage("delete viewing rooms"));
        }
        UUID roomId = parseUuid(roomIdRaw);
        if (SafeBoxViewingCoordinator.isRoomActive(server, roomId)) {
            return new ActionResult(false, "An active viewing session must finish before deleting this room.");
        }
        ViewingRoomService.MutationResult result = ViewingRoomService.delete(
                centralBank, bank.getBankId(), roomId);
        return new ActionResult(result.success(), result.message());
    }

    private static ActionResult handleSafeBoxAssign(MinecraftServer server,
                                                    CentralBank centralBank,
                                                    Bank bank,
                                                    ServerPlayer player,
                                                    String accountIdRaw) {
        if (!SafetyDepositBoxService.canManageSafeArea(centralBank, player, bank.getBankId())) {
            return new ActionResult(false, SafetyDepositBoxService.safeAreaManagementDeniedMessage("assign safety boxes"));
        }
        UUID accountId = parseUuid(accountIdRaw);
        if (accountId == null) {
            return new ActionResult(false, "Safety box assignment failed: invalid account id.");
        }
        SafetyDepositBoxService.ActionResult result =
                SafetyDepositBoxService.assignFirstFreeBox(server, centralBank, bank.getBankId(), accountId);
        if (result.success()) {
            SafeAccessLogService.record(centralBank, bank.getBankId(), player,
                    SafeAccessLogService.CATEGORY_ASSIGNMENT, SafeAccessLogService.OUTCOME_SUCCESS,
                    "BOX_ASSIGNED", shortId(accountId), result.message(),
                    player.level().dimension().location().toString(), player.blockPosition());
        }
        return new ActionResult(result.success(), result.message());
    }

    private static ActionResult handleSafeBoxPolicy(CentralBank centralBank,
                                                    Bank bank,
                                                    ServerPlayer player,
                                                    String modeRaw,
                                                    String amountRaw,
                                                    String periodTicksRaw,
                                                    String overdueTicksRaw) {
        if (!SafetyDepositBoxService.canManageSafeArea(centralBank, player, bank.getBankId())) {
            return new ActionResult(false, SafetyDepositBoxService.safeAreaManagementDeniedMessage("edit safety box pricing"));
        }
        String sizeRaw = "";
        String cleanModeRaw = modeRaw;
        if (modeRaw != null) {
            int separator = modeRaw.indexOf(':');
            if (separator > 0) {
                sizeRaw = modeRaw.substring(0, separator);
                cleanModeRaw = modeRaw.substring(separator + 1);
            }
        }
        SafetyDepositBoxService.ActionResult result =
                SafetyDepositBoxService.setPricingPolicy(centralBank, bank.getBankId(), sizeRaw, cleanModeRaw, amountRaw, periodTicksRaw, overdueTicksRaw);
        if (result.success()) {
            SafeAccessLogService.record(centralBank, bank.getBankId(), player,
                    SafeAccessLogService.CATEGORY_SYSTEM, SafeAccessLogService.OUTCOME_SUCCESS,
                    "PRICING_POLICY_UPDATED", sizeRaw.isBlank() ? "All box sizes" : sizeRaw,
                    result.message(), player.level().dimension().location().toString(), player.blockPosition());
        }
        return new ActionResult(result.success(), result.message());
    }

    private static ActionResult handleSafeBoxLocate(MinecraftServer server,
                                                    CentralBank centralBank,
                                                    Bank bank,
                                                    ServerPlayer player,
                                                    String locateTargetRaw) {
        if (!SafetyDepositBoxService.canManageSafeArea(centralBank, player, bank.getBankId())) {
            return new ActionResult(false, SafetyDepositBoxService.safeAreaManagementDeniedMessage("locate safety boxes"));
        }
        SafeLocateTarget target = parseSafeLocateTarget(locateTargetRaw);
        if (target == null) {
            return new ActionResult(false, "Safety box locate failed: invalid target.");
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        ListTag areas = metadata.getList(SafetyDepositBoxService.AREAS_KEY, Tag.TAG_COMPOUND);
        if (!isInsideSafeAreas(areas, target.dimension(), target.pos())) {
            return new ActionResult(false, "Safety box locate failed: target is outside this bank's safe claim area.");
        }
        ServerLevel level = levelForSafeDashboard(server, target.dimension());
        if (level == null || !level.hasChunkAt(target.pos())) {
            return new ActionResult(false, "Safety box locate failed: target dimension or chunk is not loaded.");
        }
        if (!(level.getBlockEntity(target.pos()) instanceof SafetyDepositBoxRowBlockEntity row)) {
            return new ActionResult(false, "Safety box locate failed: row block is missing.");
        }
        int door = Math.max(0, Math.min(SafetyDepositBoxRowBlockEntity.DOOR_COUNT - 1, target.doorIndex()));
        int moduleStart = row.getModuleStartForRow(door);
        if (moduleStart < 0) {
            moduleStart = door;
        }
        SafetyDepositBoxRowBlockEntity.ModuleType type = row.getModuleType(moduleStart);
        if (type == null || type == SafetyDepositBoxRowBlockEntity.ModuleType.EMPTY) {
            return new ActionResult(false, "Safety box locate failed: selected shell row is empty.");
        }
        PacketDistributor.sendToPlayer(player, new StockroomLocateRenderPayload(
                true,
                normalizeDim(level.dimension().location().toString()),
                target.pos().getX(),
                target.pos().getY(),
                target.pos().getZ(),
                door + 1
        ));
        String label = type.assignable() ? safeModuleLabel(type) + " box" : safeModuleLabel(type) + " plate";
        String message = "Locating " + label + " at "
                + normalizeDim(level.dimension().location().toString())
                + " (" + target.pos().getX() + ", " + target.pos().getY() + ", " + target.pos().getZ()
                + "), door " + (door + 1) + ".";
        player.displayClientMessage(Component.literal("§b" + message), true);
        ServerNotification.send(player, "Safe Box Locate", message, DeliveryAlertPayload.AlertTone.INFO, 5200);
        return new ActionResult(true, message);
    }

    private static ActionResult handleSafeBoxSeize(CentralBank centralBank,
                                                   Bank bank,
                                                   ServerPlayer player,
                                                   String accountIdRaw) {
        if (!SafetyDepositBoxService.canManageSafeArea(centralBank, player, bank.getBankId())) {
            return new ActionResult(false, SafetyDepositBoxService.safeAreaManagementDeniedMessage("seize overdue safety boxes"));
        }
        UUID accountId = parseUuid(accountIdRaw);
        if (accountId == null) {
            return new ActionResult(false, "Safety box seizure failed: invalid account id.");
        }
        SafetyDepositBoxService.ActionResult result =
                SafetyDepositBoxService.seizeOverdueBox(centralBank, bank.getBankId(), accountId);
        if (result.success()) {
            SafeAccessLogService.record(centralBank, bank.getBankId(), player,
                    SafeAccessLogService.CATEGORY_ASSIGNMENT, SafeAccessLogService.OUTCOME_SUCCESS,
                    "BOX_SEIZED", shortId(accountId), result.message(),
                    player.level().dimension().location().toString(), player.blockPosition());
        }
        return new ActionResult(result.success(), result.message());
    }

    private static ActionResult handleSafeAccess(MinecraftServer server,
                                                 CentralBank centralBank,
                                                 Bank bank,
                                                 ServerPlayer player,
                                                 String targetRaw,
                                                 boolean grant) {
        if (!SafetyDepositBoxService.canManageSafeArea(centralBank, player, bank.getBankId())) {
            return new ActionResult(false, SafetyDepositBoxService.safeAreaManagementDeniedMessage("manage employee Safe Access"));
        }
        UUID targetId = resolvePlayerId(server, targetRaw);
        if (targetId == null) {
            return new ActionResult(false, "Target employee not found. Use online name or UUID.");
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        if (!BankStaffingService.hasEmployee(metadata, targetId)) {
            return new ActionResult(false,
                    resolvePlayerName(server, targetId) + " is not employed at " + bank.getBankName() + ".");
        }
        boolean changed = grant
                ? BankStaffingService.grantSafeAccess(metadata, targetId)
                : BankStaffingService.revokeSafeAccess(metadata, targetId);
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        String verb = grant ? "granted" : "revoked";
        String suffix = changed ? "." : " (already " + (grant ? "granted" : "revoked") + ").";
        SafeAccessLogService.record(centralBank, bank.getBankId(), player,
                SafeAccessLogService.CATEGORY_SECURITY, SafeAccessLogService.OUTCOME_SUCCESS,
                grant ? "SAFE_ACCESS_GRANTED" : "SAFE_ACCESS_REVOKED",
                resolvePlayerName(server, targetId), "Employee safe access " + verb + suffix,
                player.level().dimension().location().toString(), player.blockPosition());
        return new ActionResult(true,
                "Safe Access " + verb + " for " + resolvePlayerName(server, targetId) + suffix);
    }

    private static ActionResult handleSafeAlarmConfig(CentralBank centralBank,
                                                       Bank bank,
                                                       ServerPlayer player,
                                                       String enabledRaw,
                                                       String soundRaw,
                                                       String tonesRaw,
                                                       String intervalRaw,
                                                       boolean persist) {
        if (!SafetyDepositBoxService.canManageSafeArea(centralBank, player, bank.getBankId())) {
            return new ActionResult(false,
                    SafetyDepositBoxService.safeAreaManagementDeniedMessage("configure safe alarms"));
        }
        SafeAlarmSettingsService.Settings settings;
        try {
            String[] tones = tonesRaw == null ? new String[0] : tonesRaw.split(",", -1);
            if (tones.length != 3 || ResourceLocation.tryParse(soundRaw == null ? "" : soundRaw.trim()) == null) {
                throw new IllegalArgumentException();
            }
            settings = new SafeAlarmSettingsService.Settings(
                    Boolean.parseBoolean(enabledRaw),
                    soundRaw,
                    Float.parseFloat(tones[0].trim()),
                    Float.parseFloat(tones[1].trim()),
                    Float.parseFloat(tones[2].trim()),
                    Integer.parseInt(intervalRaw == null ? "" : intervalRaw.trim()));
        } catch (IllegalArgumentException ex) {
            return new ActionResult(false,
                    "Alarm settings are invalid. Check sound ID, volume, pitches, and interval.");
        }
        if (persist) {
            SafeAlarmSettingsService.save(centralBank, bank.getBankId(), settings);
            SafeAccessLogService.record(centralBank, bank.getBankId(), player,
                    SafeAccessLogService.CATEGORY_SECURITY, SafeAccessLogService.OUTCOME_SUCCESS,
                    "ALARM_SETTINGS_UPDATED", bank.getBankName(),
                    "Sound " + settings.soundEventId() + ", interval " + settings.intervalTicks() + " ticks.",
                    player.level().dimension().location().toString(), player.blockPosition());
        }
        return new ActionResult(true, persist
                ? "Safe alarm settings saved."
                : "Safe alarm settings validated.");
    }

    private static ActionResult handleSafeAlarmTest(CentralBank centralBank,
                                                     Bank bank,
                                                     ServerPlayer player,
                                                     String enabledRaw,
                                                     String soundRaw,
                                                     String tonesRaw,
                                                     String intervalRaw) {
        ActionResult validated = handleSafeAlarmConfig(
                centralBank, bank, player, enabledRaw, soundRaw, tonesRaw, intervalRaw, false);
        if (!validated.success()) return validated;
        String[] tones = tonesRaw.split(",", -1);
        SafeAlarmSettingsService.Settings preview = new SafeAlarmSettingsService.Settings(
                true, soundRaw,
                Float.parseFloat(tones[0].trim()), Float.parseFloat(tones[1].trim()),
                Float.parseFloat(tones[2].trim()), Integer.parseInt(intervalRaw.trim()));
        SafeAlarmSettingsService.playPreview(player, preview);
        SafeAccessLogService.record(centralBank, bank.getBankId(), player,
                SafeAccessLogService.CATEGORY_SECURITY, SafeAccessLogService.OUTCOME_INFO,
                "ALARM_TESTED", bank.getBankName(), "Alarm audio preview played.",
                player.level().dimension().location().toString(), player.blockPosition());
        return new ActionResult(true, "Alarm preview restarted for you.");
    }

    private static ActionResult handleSafeAlarmStopTest(CentralBank centralBank,
                                                         Bank bank,
                                                         ServerPlayer player) {
        if (!SafetyDepositBoxService.canManageSafeArea(centralBank, player, bank.getBankId())) {
            return new ActionResult(false,
                    SafetyDepositBoxService.safeAreaManagementDeniedMessage("stop alarm previews"));
        }
        boolean stopped = SafeAlarmSettingsService.stopPreview(player);
        return new ActionResult(true, stopped
                ? "Alarm preview stopped."
                : "No alarm preview is currently playing.");
    }

    private static ActionResult handleSafeAlarmReset(CentralBank centralBank,
                                                      Bank bank,
                                                      ServerPlayer player) {
        if (!SafetyDepositBoxService.canManageSafeArea(centralBank, player, bank.getBankId())) {
            return new ActionResult(false,
                    SafetyDepositBoxService.safeAreaManagementDeniedMessage("reset safe alarms"));
        }
        SafeAlarmSettingsService.reset(centralBank, bank.getBankId());
        SafeAccessLogService.record(centralBank, bank.getBankId(), player,
                SafeAccessLogService.CATEGORY_SECURITY, SafeAccessLogService.OUTCOME_SUCCESS,
                "ALARM_SETTINGS_RESET", bank.getBankName(), "Restored the default two-tone bell alarm.",
                player.level().dimension().location().toString(), player.blockPosition());
        return new ActionResult(true, "Safe alarm settings restored to the default two-tone bell.");
    }

    private static ActionResult handleAccountFreeze(MinecraftServer server,
                                                    CentralBank centralBank,
                                                    Bank bank,
                                                    boolean owner,
                                                    String accountIdRaw,
                                                    String reasonRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can freeze accounts.");
        }
        AccountHolder account = resolveBankAccount(centralBank, bank, accountIdRaw);
        if (account == null) {
            return new ActionResult(false, "Account freeze failed: account is not in this bank.");
        }
        account.freeze(reasonRaw == null ? "" : reasonRaw);
        return handleAccountDetail(server, centralBank, bank, account.getAccountUUID().toString(), "0", "Account frozen.");
    }

    private static ActionResult handleAccountUnfreeze(MinecraftServer server,
                                                      CentralBank centralBank,
                                                      Bank bank,
                                                      boolean owner,
                                                      String accountIdRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can unfreeze accounts.");
        }
        AccountHolder account = resolveBankAccount(centralBank, bank, accountIdRaw);
        if (account == null) {
            return new ActionResult(false, "Account unfreeze failed: account is not in this bank.");
        }
        account.unfreeze();
        return handleAccountDetail(server, centralBank, bank, account.getAccountUUID().toString(), "0", "Account unfrozen.");
    }

    private static ActionResult handleAccountTemporaryLimit(MinecraftServer server,
                                                            CentralBank centralBank,
                                                            Bank bank,
                                                            boolean owner,
                                                            String accountIdRaw,
                                                            String amountRaw,
                                                            String expiresRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can set temporary account limits.");
        }
        AccountHolder account = resolveBankAccount(centralBank, bank, accountIdRaw);
        if (account == null) {
            return new ActionResult(false, "Temporary limit failed: account is not in this bank.");
        }
        BigDecimal amount = parsePositiveWholeAmount(amountRaw);
        if (amount == null) {
            return new ActionResult(false, "Temporary limit failed: enter a positive whole-dollar amount.");
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(expiresRaw == null ? "" : expiresRaw.trim());
        } catch (NumberFormatException ex) {
            return new ActionResult(false, "Temporary limit failed: pick a valid expiry date and time.");
        }
        long now = System.currentTimeMillis();
        long max = now + (ACCOUNT_TEMP_LIMIT_MAX_DAYS * 24L * 60L * 60L * 1000L);
        if (expiresAt <= now) {
            return new ActionResult(false, "Temporary limit failed: expiry must be in the future.");
        }
        if (expiresAt > max) {
            return new ActionResult(false, "Temporary limit failed: expiry cannot be more than 30 days away.");
        }
        boolean changed = account.setTemporaryWithdrawalLimitUntil(amount, currentOverworldGameTime(server), expiresAt);
        if (!changed) {
            return new ActionResult(false, "Temporary limit failed: amount must be a positive whole-dollar value.");
        }
        return handleAccountDetail(server, centralBank, bank, account.getAccountUUID().toString(), "0", "Temporary withdrawal limit updated.");
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
                online.sendSystemMessage(net.austizz.ultimatebankingsystem.i18n.UbsTranslations.literal(
                        MoneyText.abbreviateCurrencyTokens(
                                "§6[UBS] New bank application from "
                                        + player.getName().getString()
                                        + " for '" + normalizedName + "' (ID: " + applicationId + ")."
                        )
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
            case "teller", "tellercash", "cash", "withdrawal" -> {
                BigDecimal maxAllowed = MAX_TELLER_WITHDRAWAL_LIMIT;
                if (amount.compareTo(maxAllowed) > 0) {
                    return new ActionResult(false, "Teller limit cannot exceed $" + maxAllowed.toPlainString() + ".");
                }
                metadata.putString("limitTeller", amount.toPlainString());
            }
            default -> {
                return new ActionResult(false, "Unknown limit type. Use single, dailyplayer, dailybank, or teller.");
            }
        }

        centralBank.putBankMetadata(bank.getBankId(), metadata);
        return new ActionResult(true, "Limit updated.");
    }

    private static ActionResult handleSetCardFees(CentralBank centralBank,
                                                  Bank bank,
                                                  boolean owner,
                                                  String issueFeeRaw,
                                                  String replacementFeeRaw) {
        if (!owner) {
            return new ActionResult(false, "Only bank owners can update card fees.");
        }

        BigDecimal issueFee = parseNonNegativeWholeAmount(issueFeeRaw);
        if (issueFee == null) {
            return new ActionResult(false, "Issue fee must be a non-negative whole number.");
        }
        BigDecimal replacementFee = parseNonNegativeWholeAmount(replacementFeeRaw);
        if (replacementFee == null) {
            return new ActionResult(false, "Replacement fee must be a non-negative whole number.");
        }

        boolean applied = CreditCardService.setFees(centralBank, bank.getBankId(), issueFee, replacementFee);
        if (!applied) {
            return new ActionResult(false, "Could not update card fees.");
        }
        return new ActionResult(true, "Card fees updated (issue $" + issueFee.toPlainString()
                + ", replacement $" + replacementFee.toPlainString() + ").");
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
            onlineTarget.sendSystemMessage(net.austizz.ultimatebankingsystem.i18n.UbsTranslations.literal(
                    MoneyText.abbreviateCurrencyTokens(
                            "§aYou were assigned role §e" + role + " §aat bank " + bank.getBankName()
                    )
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
            onlineTarget.sendSystemMessage(net.austizz.ultimatebankingsystem.i18n.UbsTranslations.literal(
                    MoneyText.abbreviateCurrencyTokens(
                            "§aYou were hired by " + bank.getBankName() + " as " + role + " ($" + salary.toPlainString() + ")."
                    )
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

        boolean removed = BankEmployeeRemovalService.removeAndPersist(
                centralBank, bank.getBankId(), targetId);
        if (!removed) {
            return new ActionResult(false,
                    resolvePlayerName(server, targetId) + " is not employed at " + bank.getBankName() + ".");
        }

        ServerPlayer onlineTarget = server.getPlayerList().getPlayer(targetId);
        if (onlineTarget != null) {
            onlineTarget.sendSystemMessage(net.austizz.ultimatebankingsystem.i18n.UbsTranslations.literal(
                    MoneyText.abbreviateCurrencyTokens(
                            "§cYou were removed from employment at " + bank.getBankName() + "."
                    )
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

        CentralBank centralBank = BankManager.getCentralBank(server);
        int maxTellers = BankLevelService.bankTellerCapacity(centralBank, bank);
        int activeCount = BankTellerEntity.countActiveTellersForBank(server, bank.getBankId());
        if (activeCount >= maxTellers) {
            return new ActionResult(false,
                    bank.getBankName() + " already has the max "
                            + maxTellers + " active tellers.");
        }

        ItemStack egg = new ItemStack(ModItems.BANK_TELLER_SPAWN_EGG.get());
        BankTellerEntity.applyBankBindingToEgg(egg, bank.getBankId(), bank.getBankName());
        if (!actor.getInventory().add(egg)) {
            actor.drop(egg, false);
        }

        return new ActionResult(true,
                "Issued teller egg for " + bank.getBankName()
                        + ". Active tellers: " + activeCount + "/" + maxTellers + ".");
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

        CentralBank centralBank = BankManager.getCentralBank(server);
        int maxTellers = BankLevelService.bankTellerCapacity(centralBank, bank);
        int activeCount = BankTellerEntity.countActiveTellersForBank(server, bank.getBankId());
        return new ActionResult(true,
                "Active tellers for " + bank.getBankName() + ": "
                        + activeCount + "/" + maxTellers + ".");
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
            lenderOwner.sendSystemMessage(net.austizz.ultimatebankingsystem.i18n.UbsTranslations.literal(
                    MoneyText.abbreviateCurrencyTokens(
                            "§aYour inter-bank offer " + shortId(offerId)
                                    + " was accepted by " + borrowerBank.getBankName()
                                    + " for $" + principal.toPlainString()
                    )
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
            online.sendSystemMessage(net.austizz.ultimatebankingsystem.i18n.UbsTranslations.literal(
                    MoneyText.abbreviateCurrencyTokens(
                            "§6[UBS] New bank appeal from " + actor.getName().getString()
                                    + " (ID: " + appealId + ")."
                    )
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
        metadata.putString("cardIssueFee", "25");
        metadata.putString("cardReplacementFee", "50");

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

        String privateCardMessage = tryIssueFounderPrivateCard(centralBank, founder, founderAccount);

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
        return new ActionResult(true,
                "Bank created: " + newBank.getBankName() + " (" + shortId(newBank.getBankId()) + "). "
                        + privateCardMessage);
    }

    /**
     * Founders get one private card at creation time if there is inventory room.
     * If there is no room, we keep the action non-fatal and direct them to teller issuance.
     */
    private static String tryIssueFounderPrivateCard(CentralBank centralBank,
                                                     ServerPlayer founder,
                                                     AccountHolder founderAccount) {
        if (centralBank == null || founder == null || founderAccount == null) {
            return "Private card not issued (missing data).";
        }

        if (founder.getInventory().getFreeSlot() < 0) {
            String warning = "Inventory full: private bank card was not delivered. Visit your bank teller to issue it.";
            founder.sendSystemMessage(Component.literal("§e" + warning));
            ServerNotification.send(founder, "Banking", warning, DeliveryAlertPayload.AlertTone.WARNING, 6800);
            return "Private card pending teller issue (inventory full).";
        }

        CreditCardService.CardIssueResult issued = CreditCardService.issueCard(
                centralBank,
                founderAccount,
                founder.getName().getString(),
                false,
                true
        );
        if (!issued.success() || issued.cardStack().isEmpty()) {
            return "Private card could not be issued (" + issued.message() + ").";
        }

        founder.getInventory().add(issued.cardStack().copy());
        founder.containerMenu.broadcastChanges();
        String masked = CreditCardService.maskCardNumber(issued.cardNumber());
        String success = "Private bank card issued: " + masked + ".";
        founder.sendSystemMessage(Component.literal("§a" + success));
        ServerNotification.send(founder, "Banking", success, DeliveryAlertPayload.AlertTone.SUCCESS, 6000);
        return success;
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

    private static AccountHolder resolveBankAccount(CentralBank centralBank, Bank bank, String accountIdRaw) {
        UUID accountId = parseUuid(accountIdRaw);
        if (centralBank == null || bank == null || accountId == null) {
            return null;
        }
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null || !bank.getBankId().equals(account.getBankId())) {
            return null;
        }
        return account;
    }

    private static String sanitizeLine(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String formatAccountDate(LocalDateTime value) {
        if (value == null) {
            return "-";
        }
        return ACCOUNT_DETAIL_DATE_FORMAT.format(value);
    }

    private static String formatAccountEpochMillis(long epochMillis) {
        if (epochMillis <= 0L) {
            return "-";
        }
        return ACCOUNT_DETAIL_DATE_FORMAT.format(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(epochMillis),
                ZoneId.systemDefault()
        ));
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
        if (server.getProfileCache() != null) {
            var cached = server.getProfileCache().get(trimmed);
            if (cached.isPresent()) {
                return cached.get().getId();
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

    private static BigDecimal parseNonNegativeWholeAmount(String amountRaw) {
        if (amountRaw == null || amountRaw.isBlank()) {
            return null;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
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
        CompoundTag metadata = OwnerPcBankReadSupport.metadataSnapshot(centralBank, bankId);

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

    private static CompoundTag getDesktopMachineIndexTag(CompoundTag centralMeta) {
        if (centralMeta == null || !centralMeta.contains(DESKTOP_MACHINE_INDEX_TAG, NBT_COMPOUND)) {
            return new CompoundTag();
        }
        return centralMeta.getCompound(DESKTOP_MACHINE_INDEX_TAG);
    }

    private static CompoundTag getDesktopStorageRoot(CompoundTag centralMeta) {
        if (centralMeta == null || !centralMeta.contains(DESKTOP_STORAGE_TAG, NBT_COMPOUND)) {
            return new CompoundTag();
        }
        return centralMeta.getCompound(DESKTOP_STORAGE_TAG);
    }

    private static String buildDesktopCoordinateKey(String dimensionId, int x, int y, int z) {
        return normalizeDim(dimensionId) + "|" + x + "|" + y + "|" + z;
    }

    private static String resolveOrCreateDesktopMachineId(CentralBank centralBank,
                                                           String dimensionId,
                                                           int x,
                                                           int y,
                                                           int z,
                                                           boolean create) {
        if (centralBank == null || dimensionId == null || dimensionId.isBlank()) {
            return "";
        }
        String contextKey = buildDesktopCoordinateKey(dimensionId, x, y, z);
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag indexTag = getDesktopMachineIndexTag(centralMeta);
        CompoundTag storageRoot = getDesktopStorageRoot(centralMeta);
        boolean changed = false;

        String machineId = indexTag.contains(contextKey, NBT_STRING)
                ? indexTag.getString(contextKey).trim().toLowerCase(Locale.ROOT)
                : "";
        if (machineId.isBlank() && storageRoot.contains(contextKey, NBT_COMPOUND)) {
            machineId = UUID.randomUUID().toString();
            while (storageRoot.contains(machineId, NBT_COMPOUND)) {
                machineId = UUID.randomUUID().toString();
            }
            storageRoot.put(machineId, storageRoot.getCompound(contextKey));
            storageRoot.remove(contextKey);
            indexTag.putString(contextKey, machineId);
            changed = true;
        }
        if (machineId.isBlank() && create) {
            machineId = UUID.randomUUID().toString();
            while (storageRoot.contains(machineId, NBT_COMPOUND)) {
                machineId = UUID.randomUUID().toString();
            }
            indexTag.putString(contextKey, machineId);
            changed = true;
        }
        if (!machineId.isBlank() && !indexTag.contains(contextKey, NBT_STRING)) {
            indexTag.putString(contextKey, machineId);
            changed = true;
        }
        if (changed) {
            centralMeta.put(DESKTOP_MACHINE_INDEX_TAG, indexTag);
            centralMeta.put(DESKTOP_STORAGE_TAG, storageRoot);
            centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);
        }
        return machineId;
    }

    private static CompoundTag getDesktopPcTag(CentralBank centralBank,
                                               DesktopContext context,
                                               boolean create) {
        if (centralBank == null || context == null) {
            return new CompoundTag();
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag storageRoot = getDesktopStorageRoot(centralMeta);
        String storageKey = context.storageKey();
        if (storageKey.isBlank()) {
            return new CompoundTag();
        }
        if (storageRoot.contains(storageKey, NBT_COMPOUND)) {
            return storageRoot.getCompound(storageKey);
        }
        return create ? new CompoundTag() : new CompoundTag();
    }

    private static CompoundTag getDesktopUserTag(CentralBank centralBank,
                                                 DesktopContext context,
                                                 UUID playerId,
                                                 boolean create) {
        if (centralBank == null || context == null || playerId == null) {
            return new CompoundTag();
        }
        CompoundTag pcTag = getDesktopPcTag(centralBank, context, create);
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
        CompoundTag storageRoot = getDesktopStorageRoot(centralMeta);
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

    private static boolean isDesktopPoweredOn(CompoundTag pcTag) {
        if (pcTag == null || !pcTag.contains(DESKTOP_POWER_STATE_TAG)) {
            return true;
        }
        return pcTag.getBoolean(DESKTOP_POWER_STATE_TAG);
    }

    private static void setDesktopPowerState(CentralBank centralBank,
                                             DesktopContext context,
                                             boolean poweredOn) {
        if (centralBank == null || context == null) {
            return;
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag storageRoot = getDesktopStorageRoot(centralMeta);
        CompoundTag pcTag = storageRoot.contains(context.storageKey(), NBT_COMPOUND)
                ? storageRoot.getCompound(context.storageKey())
                : new CompoundTag();
        pcTag.putBoolean(DESKTOP_POWER_STATE_TAG, poweredOn);
        storageRoot.put(context.storageKey(), pcTag);
        centralMeta.put(DESKTOP_STORAGE_TAG, storageRoot);
        centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);
    }

    private static boolean isDesktopSessionUnlocked(String machineId, UUID playerId) {
        if (machineId == null || machineId.isBlank() || playerId == null) {
            return false;
        }
        Set<UUID> unlocked = DESKTOP_UNLOCKED_SESSIONS.get(machineId);
        return unlocked != null && unlocked.contains(playerId);
    }

    private static boolean isDesktopSessionUnlockedReadOnly(String machineId,
                                                            UUID playerId,
                                                            CompoundTag userTag) {
        if (isDesktopSessionUnlocked(machineId, playerId)) {
            return true;
        }
        if (machineId == null || machineId.isBlank() || playerId == null || userTag == null) {
            return false;
        }
        String persistedMachine = userTag.getString(DESKTOP_SESSION_MACHINE_TAG);
        return persistedMachine != null
                && !persistedMachine.isBlank()
                && persistedMachine.equalsIgnoreCase(machineId);
    }

    private static boolean isDesktopSessionUnlocked(CentralBank centralBank,
                                                    DesktopContext context,
                                                    String machineId,
                                                    UUID playerId,
                                                    CompoundTag userTag) {
        if (isDesktopSessionUnlocked(machineId, playerId)) {
            return true;
        }
        if (centralBank == null || context == null || machineId == null || machineId.isBlank() || playerId == null) {
            return false;
        }
        CompoundTag sessionTag = userTag == null ? getDesktopUserTag(centralBank, context, playerId, false) : userTag;
        String persistedMachine = sessionTag.getString(DESKTOP_SESSION_MACHINE_TAG);
        if (persistedMachine == null || persistedMachine.isBlank() || !persistedMachine.equalsIgnoreCase(machineId)) {
            return false;
        }
        markDesktopSessionUnlocked(machineId, playerId);
        return true;
    }

    private static void markDesktopSessionUnlocked(String machineId, UUID playerId) {
        if (machineId == null || machineId.isBlank() || playerId == null) {
            return;
        }
        DESKTOP_UNLOCKED_SESSIONS
                .computeIfAbsent(machineId, key -> ConcurrentHashMap.newKeySet())
                .add(playerId);
    }

    private static void persistDesktopSessionUnlocked(CompoundTag userTag, String machineId) {
        if (userTag == null || machineId == null || machineId.isBlank()) {
            return;
        }
        userTag.putString(DESKTOP_SESSION_MACHINE_TAG, machineId);
        userTag.putLong(DESKTOP_SESSION_UNLOCKED_AT_TAG, System.currentTimeMillis());
    }

    private static void clearPersistentDesktopSession(CompoundTag userTag) {
        if (userTag == null) {
            return;
        }
        userTag.remove(DESKTOP_SESSION_MACHINE_TAG);
        userTag.remove(DESKTOP_SESSION_UNLOCKED_AT_TAG);
    }

    private static void clearDesktopSession(String machineId, UUID playerId) {
        if (machineId == null || machineId.isBlank() || playerId == null) {
            return;
        }
        Set<UUID> unlocked = DESKTOP_UNLOCKED_SESSIONS.get(machineId);
        if (unlocked == null) {
            return;
        }
        unlocked.remove(playerId);
        if (unlocked.isEmpty()) {
            DESKTOP_UNLOCKED_SESSIONS.remove(machineId, unlocked);
        }
    }

    private static void clearDesktopSessionsForMachine(String machineId) {
        if (machineId == null || machineId.isBlank()) {
            return;
        }
        DESKTOP_UNLOCKED_SESSIONS.remove(machineId);
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

    private enum ShopRoleRequirement {
        NONE,
        STAFF,
        BUILDER,
        MANAGER,
        OWNER
    }

    /**
     * Validates desktop shop-action authorization from delegated shop roles.
     * Returns null when allowed, otherwise a fail() action result.
     */
    private static ActionResult validateShopDesktopPermission(String normalizedAction,
                                                              CentralBank centralBank,
                                                              ServerPlayer player,
                                                              UUID selectedShopId) {
        if (normalizedAction == null
                || normalizedAction.isBlank()
                || centralBank == null
                || player == null) {
            return null;
        }
        if (!normalizedAction.startsWith("SHOP_")
                || normalizedAction.startsWith("SHOP_WEBSHOP")) {
            return null;
        }
        if (player.hasPermissions(3)) {
            return null;
        }

        ShopRoleRequirement requirement = requiredShopRoleForAction(normalizedAction);
        if (requirement == ShopRoleRequirement.NONE) {
            return null;
        }

        UUID actorId = player.getUUID();
        UUID shopId = selectedShopId != null ? selectedShopId : ShopService.resolveDefaultShopIdForActor(centralBank, actorId);
        if (shopId == null) {
            return fail(normalizedAction, "Select a shop first.");
        }

        String role = ShopService.resolveShopRole(centralBank, actorId, shopId);
        if (role == null || role.isBlank()) {
            return fail(normalizedAction, "You do not have access to this shop.");
        }
        if (roleMeetsRequirement(role, requirement)) {
            return null;
        }

        return fail(
                normalizedAction,
                "Insufficient role permissions. Required: " + requirementLabel(requirement)
                        + ". Current role: " + role.toUpperCase(Locale.ROOT) + "."
        );
    }

    /**
     * Maps each desktop shop action to its minimum delegated role.
     */
    private static ShopRoleRequirement requiredShopRoleForAction(String action) {
        return switch (action) {
            case "SHOP_OVERVIEW",
                 "SHOP_CHECKOUT_DIAGNOSTIC",
                 "SHOP_LEVEL_ROADMAP",
                 "SHOP_ORDER_REPORT",
                 "SHOP_HOURS_LIGHTING_REPORT",
                 "SHOP_SCAN",
                 "SHOP_STOCKROOM_REPORT",
                 "SHOP_STOCKROOM_LOCATE",
                 "SHOP_PALLET_LOCATE",
                 "SHOP_RESTOCK",
                 "SHOP_RESTOCK_SLOT",
                 "SHOP_RESTOCK_LOW",
                 "SHOP_RESTOCK_SHELF",
                 "SHOP_SCAN_CASHIERS",
                 "SHOP_LIST_EMPLOYEES",
                 "SHOP_FINANCE_REPORT",
                 "SHOP_SHOW_CASH_VAULT",
                 "SHOP_PERMISSIONS_REPORT" -> ShopRoleRequirement.STAFF;

            case "SHOP_CLAIM",
                 "SHOP_CLAIM_TOOL_PLOT",
                 "SHOP_CLAIM_TOOL_STOCKROOM",
                 "SHOP_CLAIM_TOOL_PALLETS",
                 "SHOP_SET_STOCKROOM",
                 "SHOP_REMOVE_SHELF_SLOT",
                 "SHOP_SET_SLOT_TARGETS" -> ShopRoleRequirement.BUILDER;

            case "SHOP_HIRE_CASHIER",
                 "SHOP_FIRE_EMPLOYEE",
                 "SHOP_LINK_CASHIER_TERMINAL",
                 "SHOP_SET_CHECKOUT_TERMINAL",
                 "SHOP_CLEAR_CHECKOUT_TERMINAL",
                 "SHOP_CLEAR_CASHIER_LINKS",
                 "SHOP_ORDER_ITEM_PICKER",
                 "SHOP_ORDER_CREATE",
                 "SHOP_ORDER_CANCEL",
                 "SHOP_ORDER_ASSIGN_PALLET",
                 "SHOP_ORDER_UNASSIGN_PALLET",
                 "SHOP_ORDER_BIND_PALLET",
                 "SHOP_ORDER_CLEAR_PALLET" -> ShopRoleRequirement.MANAGER;

            case "SHOP_LIST_OWNER_ACCOUNTS",
                 "SHOP_VAULT_WITHDRAW_AMOUNT",
                 "SHOP_VAULT_WITHDRAW_PLAN",
                 "SHOP_SET_SETTLEMENT_ACCOUNT",
                 "SHOP_HOURS_SET",
                 "SHOP_HOURS_DELIVERER_STOCKROOM_ACCESS",
                 "SHOP_LIGHTING_ENABLED",
                 "SHOP_LIGHTING_LEVEL",
                 "SHOP_LIGHTING_MAIN_MODE",
                 "SHOP_LIGHTING_STOCKROOM_MODE",
                 "SHOP_LIGHTING_EXCLUDE_STOCKROOM",
                 "SHOP_RENAME",
                 "SHOP_SET_TYPE",
                 "SHOP_TYPE_REPORT",
                 "SHOP_TYPE_PAY_FEES",
                 "SHOP_FRANCHISE_PUBLISH_OFFER",
                 "SHOP_FRANCHISE_CANCEL_OFFER",
                 "SHOP_FRANCHISE_ACCEPT_OFFER",
                 "SHOP_FRANCHISE_NPC_LICENSE",
                 "SHOP_CORPORATE_ADD_BRANCH",
                 "SHOP_CORPORATE_REMOVE_BRANCH",
                 "SHOP_DELETE",
                 "SHOP_PERMISSIONS_SET",
                 "SHOP_PERMISSIONS_REMOVE" -> ShopRoleRequirement.OWNER;

            default -> ShopRoleRequirement.NONE;
        };
    }

    private static boolean roleMeetsRequirement(String role, ShopRoleRequirement requirement) {
        return shopRoleRank(role) >= switch (requirement) {
            case STAFF -> 1;
            case BUILDER -> 2;
            case MANAGER -> 3;
            case OWNER -> 4;
            case NONE -> 0;
        };
    }

    private static int shopRoleRank(String role) {
        if (role == null || role.isBlank()) {
            return 0;
        }
        return switch (role.trim().toUpperCase(Locale.ROOT)) {
            case ShopService.SHOP_ROLE_OWNER -> 4;
            case ShopService.SHOP_ROLE_MANAGER -> 3;
            case ShopService.SHOP_ROLE_BUILDER -> 2;
            case ShopService.SHOP_ROLE_STAFF -> 1;
            default -> 0;
        };
    }

    private static String requirementLabel(ShopRoleRequirement requirement) {
        return switch (requirement) {
            case STAFF -> ShopService.SHOP_ROLE_STAFF;
            case BUILDER -> ShopService.SHOP_ROLE_BUILDER;
            case MANAGER -> ShopService.SHOP_ROLE_MANAGER;
            case OWNER -> ShopService.SHOP_ROLE_OWNER;
            case NONE -> "NONE";
        };
    }

    private static UUID parseOptionalUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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
        owner.sendSystemMessage(net.austizz.ultimatebankingsystem.i18n.UbsTranslations.literal(
                MoneyText.abbreviateCurrencyTokens(
                        "§6[UBS] Bank status update for " + bank.getBankName() + ": " + oldStatus + " -> " + newStatus
                )
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
