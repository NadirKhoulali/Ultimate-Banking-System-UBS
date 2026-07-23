package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.BankLevelService;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;
import net.austizz.ultimatebankingsystem.bank.owner.premise.OwnerPcPremiseService;
import net.austizz.ultimatebankingsystem.bank.owner.premise.OwnerPcPremiseAdminService;
import net.austizz.ultimatebankingsystem.bank.owner.staffing.BankStaffingService;
import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimToolLifecycle;
import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimToolPurpose;
import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimSelection;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePairResolver;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeAreaSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupBinder;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupNbtCodec;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultDoorSelection;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessSummary;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomService;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomAnchor;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomNbtStore;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.SafeBoxViewingCoordinator;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.BankVaultDoorBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.BankVaultDoorBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.claim.ClaimOutline;
import net.austizz.ultimatebankingsystem.claim.ClaimModeService;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.menu.SafetyDepositBoxMenu;
import net.austizz.ultimatebankingsystem.network.OwnerPcPremiseActionPayload;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SafetyDepositBoxService {
    public static final String AREAS_KEY = "safeDepositAreas";
    public static final String ASSIGNMENTS_KEY = "safeDepositAssignments";
    public static final String ESCROW_KEY = "safeDepositEscrow";
    public static final String NEXT_NUMBER_KEY = "safeDepositNextNumber";
    public static final String POLICY_MODE_KEY = "safeDepositPolicyMode";
    public static final String ONE_TIME_FEE_KEY = "safeDepositOneTimeFee";
    public static final String RENT_AMOUNT_KEY = "safeDepositRentAmount";
    public static final String RENT_PERIOD_TICKS_KEY = "safeDepositRentPeriodTicks";
    public static final String OVERDUE_TICKS_KEY = "safeDepositOverdueTicks";
    public static final long DEFAULT_RENT_PERIOD_TICKS = 7L * 24L * 60L * 60L * 20L;
    public static final long DEFAULT_OVERDUE_TICKS = 3L * 24L * 60L * 60L * 20L;

    private static final long OPEN_MENU_TIMEOUT_TICKS = 20L * 60L;
    private static final long OPEN_MENU_DELAY_TICKS = SafetyDepositBoxRowBlockEntity.OPEN_ANIMATION_TICKS + 2L;
    private static final int SAFE_CLAIM_TOOL_TIMEOUT_TICKS = 20 * 60 * 5;
    private static final String SAFE_CLAIM_TOOL_ITEM_TAG = "ubs_safe_claim_tool_item";
    private static final String SAFE_CLAIM_TOOL_ADD = "add";
    private static final String SAFE_CLAIM_TOOL_REMOVE = "remove";
    private static final String SAFE_CLAIM_TOOL_APPLY = "apply";
    private static final String SAFE_CLAIM_TOOL_CLEAR = "clear";
    private static final String SAFE_CLAIM_TOOL_OVERLAY = "overlay";
    private static final String SAFE_CLAIM_TOOL_EXIT = "exit";
    private static final String SAFE_CLAIM_TOOL_LOCK = "lock";
    private static final String SAFE_CLAIM_TOOL_FINISH = "finish";
    private static final int SAFE_ROW_SCAN_MARGIN_BLOCKS = 1;
    private static final ConcurrentHashMap<UUID, PendingMenuOpen> PENDING_MENU_OPENS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, SafeClaimToolSession> SAFE_CLAIM_TOOL_SESSIONS = new ConcurrentHashMap<>();

    public record LoadedSafeRow(String dimension, BlockPos pos, SafetyDepositBoxRowBlockEntity row, boolean strictClaim) {
    }

    public record PricingPolicy(SafetyDepositBoxRowBlockEntity.ModuleType type,
                                String mode,
                                BigDecimal amount,
                                long rentPeriodTicks,
                                long overdueTicks) {
    }

    public record SafeBoxAvailability(SafetyDepositBoxRowBlockEntity.ModuleType type,
                                      int total,
                                      int assigned,
                                      int free) {
    }

    public record SafeBoxPolicySummary(SafetyDepositBoxRowBlockEntity.ModuleType type,
                                       String label,
                                       String mode,
                                       BigDecimal amount,
                                       long rentPeriodTicks,
                                       long overdueTicks,
                                       int total,
                                       int assigned,
                                       int free) {
    }

    private SafetyDepositBoxService() {
    }

    public static List<SafetyDepositBoxRowBlockEntity.ModuleType> assignableModuleTypes() {
        return List.of(
                SafetyDepositBoxRowBlockEntity.ModuleType.SMALL,
                SafetyDepositBoxRowBlockEntity.ModuleType.MEDIUM,
                SafetyDepositBoxRowBlockEntity.ModuleType.LARGE,
                SafetyDepositBoxRowBlockEntity.ModuleType.EXTRA_LARGE
        );
    }

    public static boolean migrateSafeDepositSetup(CompoundTag metadata, UUID bankId) {
        return SafeDepositSetupNbtCodec.migrateLegacy(metadata, bankId);
    }

    public static boolean ensureSafeDepositSetup(CentralBank centralBank, UUID bankId) {
        if (centralBank == null || bankId == null) {
            return false;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        boolean changed = SafeDepositSetupNbtCodec.migrateLegacy(metadata, bankId);
        if (changed) {
            centralBank.putBankMetadata(bankId, metadata);
            BankManager.markDirty();
        }
        return changed;
    }

    public static SafeDepositSetupSnapshot safeDepositSetupSnapshot(CompoundTag metadata) {
        return SafeDepositSetupNbtCodec.snapshot(metadata);
    }

    public static SafeDepositSetupSnapshot safeDepositSetupSnapshot(CentralBank centralBank, UUID bankId) {
        CompoundTag metadata = centralBank == null || bankId == null
                ? new CompoundTag()
                : centralBank.getOrCreateBankMetadata(bankId);
        return safeDepositSetupSnapshot(metadata);
    }

    public static SafeVaultReadinessResolver.RowReadiness safeDepositVaultReadiness(MinecraftServer server,
                                                                                    CompoundTag metadata,
                                                                                    SafeVaultReadinessResolver.RowLocation location) {
        return safeDepositVaultReadinessOperation(server, metadata).resolve(location);
    }

    public static SafeVaultReadinessOperation safeDepositVaultReadinessOperation(
            MinecraftServer server,
            CompoundTag metadata) {
        SafeDepositSetupSnapshot snapshot = safeDepositSetupSnapshot(metadata);
        SafeVaultReadinessResolver.LoadedWorldFacts facts = loadedWorldFacts(server, snapshot, metadata);
        SafeTellerRoutePairResolver.Context routes = new SafeTellerRoutePairResolver.Context(metadata);
        return new SafeVaultReadinessOperation(snapshot,
                new SafeVaultReadinessResolver.EvaluationContext(routes, facts),
                BankStaffingService.hasEligibleSafeAccessEmployee(
                        server, metadata, setupBankId(snapshot).orElse(null)));
    }

    private static Optional<UUID> setupBankId(SafeDepositSetupSnapshot snapshot) {
        if (snapshot == null || snapshot.premises().isEmpty()) {
            return Optional.empty();
        }
        UUID resolved = null;
        for (SafePremiseSnapshot premise : snapshot.premises()) {
            if (premise == null || premise.bankId() == null || premise.bankId().isBlank()) {
                continue;
            }
            UUID candidate;
            try {
                candidate = UUID.fromString(premise.bankId().trim());
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
            if (resolved != null && !resolved.equals(candidate)) {
                return Optional.empty();
            }
            resolved = candidate;
        }
        return Optional.ofNullable(resolved);
    }

    public static List<SafeVaultReadinessResolver.RowReadiness> safeDepositVaultReadiness(
            MinecraftServer server,
            CompoundTag metadata) {
        SafeVaultReadinessOperation operation = safeDepositVaultReadinessOperation(server, metadata);
        SafeDepositSetupSnapshot snapshot = operation.snapshot();
        List<SafeVaultReadinessResolver.RowReadiness> readiness = new ArrayList<>();
        for (var premise : snapshot.premises()) {
            if (premise == null) {
                continue;
            }
            for (SafeAreaSnapshot safeArea : premise.safeAreas()) {
                if (safeArea == null) {
                    continue;
                }
                for (SafeVaultSnapshot vault : safeArea.vaults()) {
                    if (vault != null) {
                        readiness.add(operation.resolve(new SafeVaultReadinessResolver.VaultSelection(
                                premise, safeArea, vault)));
                    }
                }
            }
        }
        return List.copyOf(readiness);
    }

    public static SafeVaultReadinessResolver.RowReadiness applyStaffingReadiness(
            CompoundTag metadata,
            SafeVaultReadinessResolver.RowReadiness readiness) {
        return applyStaffingReadiness(BankStaffingService.hasEligibleSafeAccessEmployee(metadata), readiness);
    }

    public static List<SafeVaultReadinessResolver.RowReadiness> applyStaffingReadiness(
            CompoundTag metadata,
            List<SafeVaultReadinessResolver.RowReadiness> readiness) {
        if (readiness == null || readiness.isEmpty()) {
            return List.of();
        }
        boolean eligibleSafeAccessStaff = BankStaffingService.hasEligibleSafeAccessEmployee(metadata);
        return readiness.stream()
                .map(row -> applyStaffingReadiness(eligibleSafeAccessStaff, row))
                .toList();
    }

    private static SafeVaultReadinessResolver.RowReadiness applyStaffingReadiness(
            boolean eligibleSafeAccessStaff,
            SafeVaultReadinessResolver.RowReadiness readiness) {
        return SafeVaultReadinessResolver.withEligibleSafeAccessStaff(readiness, eligibleSafeAccessStaff);
    }

    public static Optional<BlockPos> resolveEscortVaultDoorMaster(MinecraftServer server,
                                                                   CompoundTag metadata,
                                                                   SafeVaultReadinessResolver.RowLocation location) {
        if (server == null || metadata == null || location == null || location.position() == null) {
            return Optional.empty();
        }
        SafeVaultReadinessOperation operation = safeDepositVaultReadinessOperation(server, metadata);
        return operation.resolveDoorMaster(operation.resolve(location));
    }

    public static boolean shouldSkipVaultForAssignment(
            CompoundTag metadata,
            SafeVaultReadinessResolver.RowReadiness readiness) {
        return shouldSkipVaultForAssignment(
                BankStaffingService.hasEligibleSafeAccessEmployee(metadata), readiness);
    }

    private static boolean shouldSkipVaultForAssignment(
            boolean eligibleSafeAccessStaff,
            SafeVaultReadinessResolver.RowReadiness readiness) {
        SafeVaultReadinessResolver.RowReadiness current = applyStaffingReadiness(eligibleSafeAccessStaff, readiness);
        return current.summary() != null && !current.summary().ready();
    }

    public static ActionResult validateVaultAccessForReadiness(boolean managementAccess,
                                                               SafeVaultReadinessSummary summary) {
        if (summary == null || summary.ready() || managementAccess) {
            return ActionResult.ok("");
        }
        List<String> reasons = SafeVaultReadinessResolver.humanReasons(summary.missingReasons());
        String suffix = reasons.isEmpty() ? "" : " Missing: " + String.join("; ", reasons);
        return ActionResult.fail("This safety deposit vault is temporarily unavailable." + suffix);
    }

    public static String safeAreaManagerLabel() {
        return "bank owners, founders/directors, director employees, cofounders, shareholders, and server operators";
    }

    public static String safeAreaManagementDeniedMessage(String action) {
        String cleanAction = action == null || action.isBlank() ? "manage safe areas" : action.trim();
        return "Only " + safeAreaManagerLabel() + " can " + cleanAction + ".";
    }

    public static ActionResult validateCoverInstallationForVaultRows(List<SafetyDepositBoxRowBlockEntity.ModuleType[]> rows,
                                                                     int targetRowIndex) {
        if (rows == null || rows.isEmpty() || targetRowIndex < 0 || targetRowIndex >= rows.size()) {
            return ActionResult.fail("Cover installation failed: safe vault row data is unavailable.");
        }
        if (rows.size() == 1) {
            return ActionResult.fail("Cannot install a cover in the only loaded row of this vault.");
        }
        for (int i = 0; i < rows.size(); i++) {
            if (i != targetRowIndex && SafetyDepositBoxRowBlockEntity.isFullyAssignableRow(rows.get(i))) {
                return ActionResult.ok("");
            }
        }
        return ActionResult.fail("Cover installation requires another fully assignable row in the same vault.");
    }

    public static ActionResult validateCoverInstallation(MinecraftServer server,
                                                         CentralBank centralBank,
                                                         Level level,
                                                         BlockPos rowPos) {
        if (centralBank == null || level == null || rowPos == null) {
            return ActionResult.fail("Cover installation failed: bank data is unavailable.");
        }
        SafeAreaClaimMatch match = findSafeAreaClaimMatch(centralBank, level, rowPos);
        if (match.ambiguous()) {
            return ActionResult.fail("Cover installation failed: this position is claimed by multiple bank safe areas.");
        }
        if (!match.claimed()) {
            return ActionResult.ok("");
        }
        if (server == null) {
            return ActionResult.fail("Cover installation failed: bank data is unavailable.");
        }
        UUID bankId = match.bankId();
        ensureSafeDepositSetup(centralBank, bankId);
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        SafeVaultReadinessOperation operation = safeDepositVaultReadinessOperation(server, metadata);
        SafeVaultReadinessResolver.LoadedWorldFacts facts = operation.facts();
        SafeVaultReadinessResolver.RowReadiness readiness = operation.resolve(
                new SafeVaultReadinessResolver.RowLocation(
                        level.dimension().location().toString(), rowPos));
        if (!readiness.mapped() || readiness.safeArea() == null) {
            return ActionResult.fail("Cover installation failed: this row is not mapped to exactly one safe vault.");
        }

        List<SafetyDepositBoxRowBlockEntity.ModuleType[]> rows = new ArrayList<>();
        int targetIndex = -1;
        SafeAreaSnapshot safeArea = readiness.safeArea();
        for (SafeVaultReadinessResolver.LoadedRowSnapshot loadedRow : facts.loadedRows()) {
            if (loadedRow == null || loadedRow.pos() == null || !safeArea.bounds().contains(
                    loadedRow.dimension(),
                    loadedRow.pos().getX(),
                    loadedRow.pos().getY(),
                    loadedRow.pos().getZ())) {
                continue;
            }
            if (loadedRow.pos().equals(rowPos)) {
                targetIndex = rows.size();
            }
            rows.add(loadedRow.moduleTypes());
        }
        return validateCoverInstallationForVaultRows(rows, targetIndex);
    }

    public static PricingPolicy pricingPolicy(CentralBank centralBank,
                                              UUID bankId,
                                              SafetyDepositBoxRowBlockEntity.ModuleType type) {
        CompoundTag metadata = centralBank == null || bankId == null
                ? new CompoundTag()
                : centralBank.getOrCreateBankMetadata(bankId);
        return pricingPolicy(metadata, type);
    }

    public static PricingPolicy pricingPolicy(CompoundTag metadata,
                                              SafetyDepositBoxRowBlockEntity.ModuleType type) {
        SafetyDepositBoxRowBlockEntity.ModuleType cleanType = normalizeAssignableType(type);
        CompoundTag safeMetadata = metadata == null ? new CompoundTag() : metadata;
        String mode = readSizedString(safeMetadata, POLICY_MODE_KEY, cleanType, safeMetadata.getString(POLICY_MODE_KEY));
        mode = normalizePolicyMode(mode);
        BigDecimal amount = switch (mode) {
            case "ONE_TIME" -> readSizedMoney(safeMetadata, ONE_TIME_FEE_KEY, cleanType);
            case "RECURRING" -> readSizedMoney(safeMetadata, RENT_AMOUNT_KEY, cleanType);
            default -> BigDecimal.ZERO;
        };
        long rentPeriod = readSizedPositiveLong(safeMetadata, RENT_PERIOD_TICKS_KEY, cleanType, DEFAULT_RENT_PERIOD_TICKS);
        long overdue = readSizedPositiveLong(safeMetadata, OVERDUE_TICKS_KEY, cleanType, DEFAULT_OVERDUE_TICKS);
        return new PricingPolicy(
                cleanType,
                mode,
                amount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN),
                Math.max(1L, rentPeriod),
                Math.max(1L, overdue)
        );
    }

    public static List<SafeBoxPolicySummary> safeBoxPolicySummaries(MinecraftServer server,
                                                                    CentralBank centralBank,
                                                                    UUID bankId) {
        Map<SafetyDepositBoxRowBlockEntity.ModuleType, SafeBoxAvailability> availability = new EnumMap<>(SafetyDepositBoxRowBlockEntity.ModuleType.class);
        for (SafeBoxAvailability entry : safeBoxAvailability(server, centralBank, bankId)) {
            availability.put(entry.type(), entry);
        }
        CompoundTag metadata = centralBank == null || bankId == null
                ? new CompoundTag()
                : centralBank.getOrCreateBankMetadata(bankId);
        List<SafeBoxPolicySummary> summaries = new ArrayList<>();
        for (SafetyDepositBoxRowBlockEntity.ModuleType type : assignableModuleTypes()) {
            PricingPolicy policy = pricingPolicy(metadata, type);
            SafeBoxAvailability counts = availability.getOrDefault(type, new SafeBoxAvailability(type, 0, 0, 0));
            summaries.add(new SafeBoxPolicySummary(
                    type,
                    shortModuleLabel(type),
                    policy.mode(),
                    policy.amount(),
                    policy.rentPeriodTicks(),
                    policy.overdueTicks(),
                    counts.total(),
                    counts.assigned(),
                    counts.free()
            ));
        }
        return summaries;
    }

    public static List<SafeBoxAvailability> safeBoxAvailability(MinecraftServer server,
                                                               CentralBank centralBank,
                                                               UUID bankId) {
        Map<SafetyDepositBoxRowBlockEntity.ModuleType, int[]> counts = new EnumMap<>(SafetyDepositBoxRowBlockEntity.ModuleType.class);
        for (SafetyDepositBoxRowBlockEntity.ModuleType type : assignableModuleTypes()) {
            counts.put(type, new int[]{0, 0, 0});
        }
        if (server == null || centralBank == null || bankId == null) {
            return availabilityList(counts);
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        ListTag areas = metadata.getList(AREAS_KEY, Tag.TAG_COMPOUND);
        Set<String> assignmentKeys = assignmentLocationKeys(metadata.getList(ASSIGNMENTS_KEY, Tag.TAG_COMPOUND));
        for (LoadedSafeRow loaded : collectLoadedSafeRows(server, areas)) {
            if (loaded == null || loaded.row() == null || loaded.pos() == null) {
                continue;
            }
            String dimension = normalizedDimension(loaded.dimension());
            for (int door = 0; door < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; door++) {
                int moduleStart = loaded.row().getModuleStartForRow(door);
                if (moduleStart >= 0 && moduleStart != door) {
                    continue;
                }
                if (!loaded.row().isAssignableBoxStart(door)) {
                    continue;
                }
                SafetyDepositBoxRowBlockEntity.ModuleType type = normalizeAssignableType(loaded.row().getModuleType(door));
                int[] values = counts.get(type);
                if (values == null) {
                    continue;
                }
                values[0]++;
                boolean assigned = loaded.row().getAssignedAccountId(door) != null
                        || assignmentKeys.contains(assignmentLocationKey(dimension, loaded.pos(), door));
                if (assigned) {
                    values[1]++;
                } else {
                    values[2]++;
                }
            }
        }
        return availabilityList(counts);
    }

    public static void openDoorForPlayer(ServerPlayer player, SafetyDepositBoxRowBlockEntity row, int doorIndex) {
        if (player == null || row == null || player.getServer() == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        CentralBank centralBank = BankManager.getCentralBank(server);
        syncAssignmentsToRow(centralBank, row);
        OpenBoxContext context = resolveOpenBoxContext(centralBank, player, row, doorIndex, true);
        if (context == null) {
            return;
        }

        ServerLevel rowLevel = row.getLevel() instanceof ServerLevel serverLevel ? serverLevel : player.serverLevel();
        String dimension = rowLevel.dimension().location().toString();
        BlockPos rowPos = row.getBlockPos().immutable();
        PendingMenuOpen existing = PENDING_MENU_OPENS.get(player.getUUID());
        if (existing != null
                && context.account().getAccountUUID().equals(existing.accountId())
                && dimension.equals(existing.dimension())
                && rowPos.equals(existing.rowPos())
                && context.doorIndex() == existing.doorIndex()) {
            return;
        }

        long now = rowLevel.getGameTime();
        row.openDoor(context.doorIndex(), now + OPEN_MENU_TIMEOUT_TICKS);
        float currentProgress = Math.max(0.0F, Math.min(1.0F, row.getCurrentDoorProgress(context.doorIndex())));
        long remainingAnimationTicks = Math.max(2L, (long) Math.ceil((1.0F - currentProgress) * OPEN_MENU_DELAY_TICKS));
        PENDING_MENU_OPENS.put(player.getUUID(), new PendingMenuOpen(
                player.getUUID(),
                context.account().getAccountUUID(),
                dimension,
                rowPos,
                context.doorIndex(),
                context.displayBoxNumber(),
                now + remainingAnimationTicks,
                now + OPEN_MENU_TIMEOUT_TICKS
        ));
        player.displayClientMessage(Component.literal("Opening " + context.displayBoxNumber() + "..."), true);
    }

    public static void revokeEscortAccess(UUID playerId) {
        if (playerId != null) {
            PENDING_MENU_OPENS.remove(playerId);
        }
    }

    private static OpenBoxContext resolveOpenBoxContext(CentralBank centralBank,
                                                        ServerPlayer player,
                                                        SafetyDepositBoxRowBlockEntity row,
                                                        int doorIndex,
                                                        boolean notifyPlayer) {
        if (centralBank == null) {
            if (notifyPlayer && player != null) {
                player.sendSystemMessage(Component.literal("Bank data is unavailable."));
            }
            return null;
        }
        if (row == null || player == null) {
            return null;
        }

        int clickedRow = Math.max(0, Math.min(SafetyDepositBoxRowBlockEntity.DOOR_COUNT - 1, doorIndex));
        int normalizedDoorIndex = row.getModuleStartForRow(clickedRow);
        if (normalizedDoorIndex < 0) {
            if (notifyPlayer) {
                player.sendSystemMessage(Component.literal("This safety deposit shell row is empty."));
            }
            return null;
        }
        SafetyDepositBoxRowBlockEntity.ModuleType moduleType = row.getModuleType(normalizedDoorIndex);
        if (!moduleType.assignable()) {
            if (notifyPlayer) {
                player.sendSystemMessage(Component.literal("This shell row is covered and does not contain a safety deposit box."));
            }
            return null;
        }
        OpenAuthorityResult authority = evaluateOpenBoxAuthority(
                centralBank, player, row, normalizedDoorIndex, null);
        if (!authority.decision().allowed()) {
            logDeniedBoxAccess(centralBank, player, row, authority.assignment(),
                    authority.decision().message());
            if (notifyPlayer) {
                player.sendSystemMessage(Component.literal(authority.decision().message()));
            }
            return null;
        }

        String boxNumber = authority.assignment().boxNumber();
        String displayBoxNumber = boxNumber == null || boxNumber.isBlank() ? "Safety Box" : boxNumber;
        int slotCount = Math.max(1, Math.min(54, moduleType.inventorySlots()));
        return new OpenBoxContext(authority.account(), normalizedDoorIndex, displayBoxNumber, slotCount);
    }

    public static boolean hasOpenBoxAuthority(ServerPlayer player,
                                              UUID accountId,
                                              BlockPos rowPos,
                                              int doorIndex) {
        if (player == null || player.getServer() == null || accountId == null || rowPos == null
                || doorIndex < 0 || doorIndex >= SafetyDepositBoxRowBlockEntity.DOOR_COUNT
                || !(player.serverLevel().getBlockEntity(rowPos) instanceof SafetyDepositBoxRowBlockEntity row)
                || row.getModuleStartForRow(doorIndex) != doorIndex
                || !row.isAssignableBoxStart(doorIndex)) {
            return false;
        }
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        return centralBank != null
                && evaluateOpenBoxAuthority(centralBank, player, row, doorIndex, accountId).decision().allowed();
    }

    private static OpenAuthorityResult evaluateOpenBoxAuthority(CentralBank centralBank,
                                                                 ServerPlayer player,
                                                                 SafetyDepositBoxRowBlockEntity row,
                                                                 int doorIndex,
                                                                 UUID expectedAccountId) {
        Level rowLevel = row.getLevel();
        String dimension = rowLevel == null ? "" : rowLevel.dimension().location().toString();
        BlockPos rowPos = row.getBlockPos();
        SafetyDepositBoxOpenAuthority.Target target = new SafetyDepositBoxOpenAuthority.Target(
                dimension, rowPos.getX(), rowPos.getY(), rowPos.getZ(), doorIndex);
        SafetyDepositBoxOpenAuthority.Decision decision = new SafetyDepositBoxOpenAuthority(
                new ServerOpenAuthorityPorts(centralBank, player, row)).authorize(
                new SafetyDepositBoxOpenAuthority.Request(player.getUUID(), expectedAccountId, target));
        Assignment assignment = toServiceAssignment(decision.assignment());
        AccountHolder account = assignment == null
                ? null
                : centralBank.SearchForAccountByAccountId(assignment.accountId());
        return new OpenAuthorityResult(assignment, account, decision);
    }

    private static Assignment toServiceAssignment(SafetyDepositBoxOpenAuthority.Assignment assignment) {
        if (assignment == null) {
            return null;
        }
        SafetyDepositBoxOpenAuthority.Target target = assignment.target();
        return new Assignment(
                assignment.bankId(),
                assignment.accountId(),
                target.dimension(),
                new BlockPos(target.x(), target.y(), target.z()),
                target.doorIndex(),
                assignment.boxNumber(),
                assignment.locked()
        );
    }

    private static final class ServerOpenAuthorityPorts implements SafetyDepositBoxOpenAuthority.Ports {
        private final CentralBank centralBank;
        private final ServerPlayer player;
        private final SafetyDepositBoxRowBlockEntity row;

        private ServerOpenAuthorityPorts(CentralBank centralBank,
                                         ServerPlayer player,
                                         SafetyDepositBoxRowBlockEntity row) {
            this.centralBank = centralBank;
            this.player = player;
            this.row = row;
        }

        @Override
        public Optional<SafetyDepositBoxOpenAuthority.Assignment> findExactAssignment(
                SafetyDepositBoxOpenAuthority.Target target) {
            if (target == null || row.getLevel() == null) {
                return Optional.empty();
            }
            BlockPos rowPos = row.getBlockPos();
            SafetyDepositBoxOpenAuthority.Target actual = new SafetyDepositBoxOpenAuthority.Target(
                    row.getLevel().dimension().location().toString(),
                    rowPos.getX(), rowPos.getY(), rowPos.getZ(), target.doorIndex());
            if (!actual.equals(target)) {
                return Optional.empty();
            }
            Assignment assignment = findAssignmentAt(centralBank, row.getLevel(), rowPos, target.doorIndex());
            if (assignment == null) {
                return Optional.empty();
            }
            SafetyDepositBoxOpenAuthority.Target assignedTarget = new SafetyDepositBoxOpenAuthority.Target(
                    assignment.dimension(), assignment.pos().getX(), assignment.pos().getY(),
                    assignment.pos().getZ(), assignment.doorIndex());
            return Optional.of(new SafetyDepositBoxOpenAuthority.Assignment(
                    assignment.bankId(), assignment.accountId(), assignedTarget,
                    assignment.boxNumber(), assignment.locked()));
        }

        @Override
        public boolean accountAuthorized(UUID playerId, SafetyDepositBoxOpenAuthority.Assignment assignment) {
            if (!player.getUUID().equals(playerId)) {
                return false;
            }
            AccountHolder account = centralBank.SearchForAccountByAccountId(assignment.accountId());
            return account != null
                    && assignment.accountId().equals(account.getAccountUUID())
                    && assignment.bankId().equals(account.getBankId())
                    && canOpenAccountBox(account, playerId);
        }

        @Override
        public boolean vaultReady(SafetyDepositBoxOpenAuthority.Assignment assignment) {
            if (player.getServer() == null) {
                return false;
            }
            ensureSafeDepositSetup(centralBank, assignment.bankId());
            SafetyDepositBoxOpenAuthority.Target target = assignment.target();
            SafeVaultReadinessResolver.RowReadiness readiness = safeDepositVaultReadiness(
                    player.getServer(),
                    centralBank.getOrCreateBankMetadata(assignment.bankId()),
                    new SafeVaultReadinessResolver.RowLocation(
                            target.dimension(), new BlockPos(target.x(), target.y(), target.z()))
            );
            return readiness.mapped()
                    && readiness.vault() != null
                    && readiness.summary() != null
                    && readiness.summary().ready();
        }

        @Override
        public SafetyDepositBoxOpenAuthority.EscortAccess escortAccess(
                UUID playerId, SafetyDepositBoxOpenAuthority.Assignment assignment) {
            SafetyDepositBoxOpenAuthority.Target target = assignment.target();
            SafeBoxViewingCoordinator.AccessDecision decision = SafeBoxViewingCoordinator.inspectAccess(
                    player.getServer(), playerId, assignment.bankId(), assignment.accountId(),
                    target.dimension(), new BlockPos(target.x(), target.y(), target.z()), target.doorIndex());
            return switch (decision) {
                case ALLOWED -> SafetyDepositBoxOpenAuthority.EscortAccess.ALLOWED;
                case DENIED_ACTIVE_VIEWING -> SafetyDepositBoxOpenAuthority.EscortAccess.DENIED_ACTIVE_ESCORT;
                case NO_ACTIVE_VIEWING -> SafetyDepositBoxOpenAuthority.EscortAccess.NO_ACTIVE_ESCORT;
            };
        }
    }

    private static void openSafetyDepositBoxMenu(ServerPlayer player,
                                                 SafetyDepositBoxRowBlockEntity row,
                                                 OpenBoxContext context) {
        AccountHolder account = context.account();
        int slotCount = Math.max(1, Math.min(54, context.slotCount()));
        String displayBoxNumber = context.displayBoxNumber();
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        SafeAccessLogService.record(centralBank, account.getBankId(), player,
                SafeAccessLogService.CATEGORY_BOX_ACCESS, SafeAccessLogService.OUTCOME_SUCCESS,
                "BOX_OPENED", displayBoxNumber, "Safety deposit box inventory opened.",
                row.getLevel() == null ? "" : row.getLevel().dimension().location().toString(),
                row.getBlockPos());
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new SafetyDepositBoxMenu(
                                containerId,
                                inventory,
                                account.getAccountUUID(),
                                row.getBlockPos(),
                                context.doorIndex(),
                                slotCount,
                                displayBoxNumber
                        ),
                        Component.literal(displayBoxNumber)
                ),
                buffer -> {
                    buffer.writeLong(account.getAccountUUID().getMostSignificantBits());
                    buffer.writeLong(account.getAccountUUID().getLeastSignificantBits());
                    buffer.writeBlockPos(row.getBlockPos());
                    buffer.writeVarInt(context.doorIndex());
                    buffer.writeVarInt(slotCount);
                    buffer.writeUtf(displayBoxNumber, 64);
                }
        );
    }

    public static boolean openViewingDepositBox(ServerPlayer player,
                                                UUID accountId,
                                                String dimension,
                                                BlockPos rowPos,
                                                int doorIndex) {
        if (player == null || player.getServer() == null || accountId == null
                || dimension == null || rowPos == null) {
            return false;
        }
        ServerLevel level = levelForDimension(player.getServer(), dimension);
        if (level == null || !(level.getBlockEntity(rowPos) instanceof SafetyDepositBoxRowBlockEntity row)) {
            player.sendSystemMessage(Component.literal("The source safety deposit box is unavailable."));
            return false;
        }
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        OpenAuthorityResult authority = evaluateOpenBoxAuthority(
                centralBank, player, row, doorIndex, accountId);
        if (!authority.decision().allowed() || authority.account() == null || authority.assignment() == null) {
            logDeniedBoxAccess(centralBank, player, row, authority.assignment(),
                    authority.decision().message());
            player.sendSystemMessage(Component.literal(authority.decision().message()));
            return false;
        }
        SafetyDepositBoxRowBlockEntity.ModuleType type = row.getModuleType(doorIndex);
        String boxNumber = authority.assignment().boxNumber();
        openSafetyDepositBoxMenu(player, row, new OpenBoxContext(
                authority.account(), doorIndex,
                boxNumber == null || boxNumber.isBlank() ? "Safety Box" : boxNumber,
                Math.max(1, Math.min(54, type.inventorySlots()))));
        return true;
    }

    private static void logDeniedBoxAccess(CentralBank centralBank,
                                           ServerPlayer player,
                                           SafetyDepositBoxRowBlockEntity row,
                                           Assignment assignment,
                                           String reason) {
        if (centralBank == null || assignment == null) return;
        SafeAccessLogService.record(centralBank, assignment.bankId(), player,
                SafeAccessLogService.CATEGORY_BOX_ACCESS, SafeAccessLogService.OUTCOME_DENIED,
                "BOX_OPEN_DENIED",
                assignment.boxNumber() == null || assignment.boxNumber().isBlank()
                        ? "Safety Box" : assignment.boxNumber(),
                reason,
                row.getLevel() == null ? assignment.dimension()
                        : row.getLevel().dimension().location().toString(),
                row.getBlockPos());
    }

    public static ActionResult startSafeAreaClaimToolSession(MinecraftServer server,
                                                             CentralBank centralBank,
                                                             ServerPlayer player,
                                                             UUID bankId) {
        if (server == null || centralBank == null || player == null || bankId == null) {
            return ActionResult.fail("Safe-area claim tool failed: missing bank data.");
        }
        if (!canManageSafeArea(centralBank, player, bankId)) {
            return ActionResult.fail(safeAreaManagementDeniedMessage("claim safe areas"));
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return ActionResult.fail("Safe-area claim tool failed: bank no longer exists.");
        }
        if (ClaimModeService.hasSession(player.getUUID())
                || SAFE_CLAIM_TOOL_SESSIONS.containsKey(player.getUUID())
                || ShopService.hasAnyClaimToolSession(player.getUUID())) {
            return ActionResult.fail("Another claim mode is already active. Close it first.");
        }

        long gameTime = player.serverLevel().getGameTime();
        int selected = Math.max(0, Math.min(8, player.getInventory().selected));
        SafeClaimToolLifecycle lifecycle = new SafeClaimToolLifecycle(
                SafeClaimToolPurpose.SAFE_AREA, List.of(), selected,
                (token, slot) -> restoreSafeClaimSnapshot(player, token, slot));
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), new SafeClaimToolSession(
                player.getUUID(),
                bankId,
                true,
                true,
                gameTime,
                gameTime,
                "",
                null,
                null,
                List.of(),
                selected,
                lifecycle,
                SafeClaimToolPurpose.SAFE_AREA,
                "",
                false
        ));
        if (!ClaimModeService.begin(player, ClaimModeService.fromSafePurpose(SafeClaimToolPurpose.SAFE_AREA))) {
            SAFE_CLAIM_TOOL_SESSIONS.remove(player.getUUID());
            return ActionResult.fail("Another claim mode is already active.");
        }
        return ActionResult.ok("Safe-area claim tool enabled for " + bank.getBankName()
                + ". Left-click sets Pos1, right-click sets Pos2, and Tab opens controls.");
    }

    public static ActionResult startPremiseClaimToolSession(MinecraftServer server,
                                                            CentralBank centralBank,
                                                            ServerPlayer player,
                                                            UUID bankId,
                                                            SafeClaimToolPurpose purpose,
                                                            String premiseId) {
        return startPremiseClaimToolSession(
                server, centralBank, player, bankId, purpose, premiseId, false);
    }

    public static ActionResult startViewingRoomClaimToolSession(MinecraftServer server,
                                                                 CentralBank centralBank,
                                                                 ServerPlayer player,
                                                                 UUID bankId,
                                                                 boolean adminAuthorized) {
        return startViewingRoomToolSession(server, centralBank, player, bankId,
                SafeClaimToolPurpose.VIEWING_ROOM_CREATE, "", adminAuthorized);
    }

    public static ActionResult startViewingRoomAnchorToolSession(MinecraftServer server,
                                                                  CentralBank centralBank,
                                                                  ServerPlayer player,
                                                                  UUID bankId,
                                                                  UUID roomId,
                                                                  ViewingRoomService.AnchorKind kind,
                                                                  boolean adminAuthorized) {
        if (roomId == null || kind == null) {
            return ActionResult.fail("Viewing-room anchor picker requires a room and anchor type.");
        }
        SafeClaimToolPurpose purpose = switch (kind) {
            case CUSTOMER -> SafeClaimToolPurpose.VIEWING_ROOM_CUSTOMER_ANCHOR;
            case TELLER -> SafeClaimToolPurpose.VIEWING_ROOM_TELLER_ANCHOR;
            case DISPLAY -> SafeClaimToolPurpose.VIEWING_ROOM_DISPLAY_ANCHOR;
        };
        return startViewingRoomToolSession(server, centralBank, player, bankId,
                purpose, roomId.toString(), adminAuthorized);
    }

    private static ActionResult startViewingRoomToolSession(MinecraftServer server,
                                                             CentralBank centralBank,
                                                             ServerPlayer player,
                                                             UUID bankId,
                                                             SafeClaimToolPurpose purpose,
                                                             String targetId,
                                                             boolean adminAuthorized) {
        if (server == null || centralBank == null || player == null || bankId == null) {
            return ActionResult.fail("Viewing-room picker failed: missing bank data.");
        }
        if (adminAuthorized && !player.hasPermissions(3)) {
            return ActionResult.fail("Administrator permission is required for this viewing-room picker.");
        }
        if (!adminAuthorized && !canManageSafeArea(centralBank, player, bankId)) {
            return ActionResult.fail(safeAreaManagementDeniedMessage("manage viewing rooms"));
        }
        Bank bank = bankId.equals(centralBank.getBankId()) ? centralBank : centralBank.getBank(bankId);
        if (bank == null) {
            return ActionResult.fail("Viewing-room picker failed: bank no longer exists.");
        }
        if (purpose != SafeClaimToolPurpose.VIEWING_ROOM_CREATE && !purpose.isViewingRoomAnchor()) {
            return ActionResult.fail("Viewing-room picker purpose is invalid.");
        }
        if (purpose.isViewingRoomAnchor() && (targetId == null || targetId.isBlank())) {
            return ActionResult.fail("Viewing-room anchor picker requires a room target.");
        }
        if (ClaimModeService.hasSession(player.getUUID())
                || SAFE_CLAIM_TOOL_SESSIONS.containsKey(player.getUUID())
                || ShopService.hasAnyClaimToolSession(player.getUUID())) {
            return ActionResult.fail("Another claim mode is already active. Close it first.");
        }

        long gameTime = player.serverLevel().getGameTime();
        int selected = Math.max(0, Math.min(8, player.getInventory().selected));
        SafeClaimToolLifecycle lifecycle = new SafeClaimToolLifecycle(
                purpose, List.of(), selected,
                (token, slot) -> restoreSafeClaimSnapshot(player, token, slot));
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), new SafeClaimToolSession(
                player.getUUID(), bankId, true, true, gameTime, gameTime,
                "", null, null, List.of(), selected, lifecycle, purpose,
                targetId == null ? "" : targetId.trim(), adminAuthorized));

        if (!ClaimModeService.begin(player, ClaimModeService.fromSafePurpose(purpose))) {
            SAFE_CLAIM_TOOL_SESSIONS.remove(player.getUUID());
            return ActionResult.fail("Another claim mode is already active.");
        }

        if (purpose.isViewingRoomAnchor()) {
            return ActionResult.ok("Viewing-room anchor mode enabled. Stand at the exact anchor, face the intended direction, then press Enter.");
        }
        return ActionResult.ok("Viewing-room claim tool enabled for " + bank.getBankName()
                + ". Left-click sets Pos1, right-click sets Pos2, and Tab opens controls.");
    }

    public static ActionResult startAdminPremiseClaimToolSession(MinecraftServer server,
                                                                 CentralBank centralBank,
                                                                 ServerPlayer player,
                                                                 UUID bankId,
                                                                 SafeClaimToolPurpose purpose,
                                                                 String premiseId) {
        return startPremiseClaimToolSession(
                server, centralBank, player, bankId, purpose, premiseId, true);
    }

    private static ActionResult startPremiseClaimToolSession(MinecraftServer server,
                                                              CentralBank centralBank,
                                                              ServerPlayer player,
                                                              UUID bankId,
                                                              SafeClaimToolPurpose purpose,
                                                              String premiseId,
                                                              boolean adminAuthorized) {
        if (server == null || centralBank == null || player == null || bankId == null) {
            return ActionResult.fail("Premise claim tool failed: missing bank data.");
        }
        if (adminAuthorized && !player.hasPermissions(3)) {
            return ActionResult.fail("Administrator permission is required for this premise claim tool.");
        }
        if (purpose != SafeClaimToolPurpose.PREMISE_CREATE
                && purpose != SafeClaimToolPurpose.PREMISE_EXIT_EDIT) {
            return ActionResult.fail("Premise claim tool purpose is invalid.");
        }
        Bank bank = bankId.equals(centralBank.getBankId())
                ? centralBank
                : centralBank.getBank(bankId);
        if (bank == null) {
            return ActionResult.fail("Premise claim tool failed: bank no longer exists.");
        }
        if (ClaimModeService.hasSession(player.getUUID())
                || SAFE_CLAIM_TOOL_SESSIONS.containsKey(player.getUUID())
                || ShopService.hasAnyClaimToolSession(player.getUUID())) {
            return ActionResult.fail("Another claim mode is already active. Close it first.");
        }
        String targetId = premiseId == null ? "" : premiseId.trim();
        if (purpose == SafeClaimToolPurpose.PREMISE_EXIT_EDIT && targetId.isEmpty()) {
            return ActionResult.fail("Premise exit editing requires a live premise target.");
        }

        long gameTime = player.serverLevel().getGameTime();
        int selected = Math.max(0, Math.min(8, player.getInventory().selected));
        SafeClaimToolLifecycle lifecycle = new SafeClaimToolLifecycle(
                purpose, List.of(), selected,
                (token, slot) -> restoreSafeClaimSnapshot(player, token, slot));
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), new SafeClaimToolSession(
                player.getUUID(), bankId, true, true, gameTime, gameTime,
                "", null, null, List.of(), selected, lifecycle, purpose, targetId,
                adminAuthorized));

        if (!ClaimModeService.begin(player, ClaimModeService.fromSafePurpose(purpose))) {
            SAFE_CLAIM_TOOL_SESSIONS.remove(player.getUUID());
            return ActionResult.fail("Another claim mode is already active.");
        }

        if (purpose == SafeClaimToolPurpose.PREMISE_EXIT_EDIT) {
            return ActionResult.ok("Premise exit editor enabled for " + bank.getBankName()
                    + ". Stand outside, face away from the premise, press Enter to capture, then Enter again to apply.");
        }
        return ActionResult.ok("Premise claim tool enabled for " + bank.getBankName()
                + ". Select Pos1/Pos2, stand at the outside exit and press Enter, then apply from the HUD.");
    }

    public static boolean hasSafeAreaClaimToolSession(UUID playerId) {
        return playerId != null && SAFE_CLAIM_TOOL_SESSIONS.containsKey(playerId);
    }

    public record SafeClaimToolView(UUID bankId,
                                    SafeClaimToolPurpose purpose,
                                    boolean addMode,
                                    boolean outlinesVisible,
                                    long startedTick,
                                    long lastUpdatedTick,
                                    String dimensionId,
                                    BlockPos firstCorner,
                                    BlockPos secondCorner,
                                    boolean hasAnchor,
                                    double anchorX,
                                    double anchorY,
                                    double anchorZ,
                                    float anchorYaw,
                                    String bankName,
                                    String ownerName,
                                    boolean adminAuthorized) {
    }

    public static SafeClaimToolView safeClaimToolView(CentralBank centralBank, UUID playerId) {
        SafeClaimToolSession session = playerId == null ? null : SAFE_CLAIM_TOOL_SESSIONS.get(playerId);
        if (session == null) {
            return null;
        }
        Bank bank = centralBank == null ? null
                : (session.bankId().equals(centralBank.getBankId())
                ? centralBank : centralBank.getBank(session.bankId()));
        SafeClaimSelection selection = session.lifecycle().selection();
        SafeClaimSelection.Exit exit = selection == null ? null : selection.exit();
        String bankName = bank == null ? "Bank" : bank.getBankName();
        return new SafeClaimToolView(
                session.bankId(), session.purpose(), session.addMode(), session.overlayEnabled(),
                session.startedTick(), session.lastUpdatedTick(),
                normalizedDimension(session.firstDimensionId()),
                session.firstCorner() == null ? null : session.firstCorner().immutable(),
                session.secondCorner() == null ? null : session.secondCorner().immutable(),
                exit != null, exit == null ? 0.0D : exit.x() + 0.5D,
                exit == null ? 0.0D : exit.y(), exit == null ? 0.0D : exit.z() + 0.5D,
                exit == null ? 0.0F : exit.yaw(), bankName, bankName,
                session.adminAuthorized()
        );
    }

    public static List<ClaimOutline> collectClaimOutlines(CentralBank centralBank,
                                                           ServerPlayer viewer,
                                                           int range,
                                                           int limit) {
        return collectClaimOutlines(centralBank, viewer, range, limit, null);
    }

    public static List<ClaimOutline> collectClaimOutlines(CentralBank centralBank,
                                                           ServerPlayer viewer,
                                                           int range,
                                                           int limit,
                                                           UUID visibleOwnerId) {
        return collectClaimOutlines(centralBank, viewer, range, limit, visibleOwnerId, false);
    }

    public static List<ClaimOutline> collectClaimOutlines(CentralBank centralBank,
                                                           ServerPlayer viewer,
                                                           int range,
                                                           int limit,
                                                           UUID visibleOwnerId,
                                                           boolean includeCentralBank) {
        if (centralBank == null || viewer == null || limit <= 0) {
            return List.of();
        }
        String dimension = normalizedDimension(viewer.serverLevel().dimension().location().toString());
        BlockPos origin = viewer.blockPosition();
        List<ClaimOutline> outlines = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        List<Bank> banks = new ArrayList<>();
        if (includeCentralBank) banks.add(centralBank);
        banks.addAll(centralBank.getBanks().values());
        for (Bank bank : banks) {
            if (bank == null || !visited.add(bank.getBankId())) {
                continue;
            }
            UUID ownerId = bank.getBankOwnerId();
            boolean central = bank.getBankId().equals(centralBank.getBankId());
            if (visibleOwnerId != null && !visibleOwnerId.equals(ownerId) && !(includeCentralBank && central)) {
                continue;
            }
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
            String owner = bank.getBankName();
            SafeDepositSetupSnapshot setup = SafeDepositSetupNbtCodec.snapshot(metadata);
            for (SafePremiseSnapshot premise : setup.premises()) {
                appendBounds(outlines, premise.bounds(), dimension, origin, range, limit,
                        "BANK_PREMISE", ownerId, owner);
                for (SafeAreaSnapshot area : premise.safeAreas()) {
                    appendBounds(outlines, area.bounds(), dimension, origin, range, limit,
                            "BANK_SAFE_AREA", ownerId, owner);
                }
                if (outlines.size() >= limit) {
                    return List.copyOf(outlines);
                }
            }
            for (ViewingRoomSnapshot room : ViewingRoomNbtStore.read(metadata)) {
                appendBounds(outlines, room.bounds(), dimension, origin, range, limit,
                        "VIEWING_ROOM", ownerId, owner);
                if (outlines.size() >= limit) {
                    return List.copyOf(outlines);
                }
            }
        }
        return List.copyOf(outlines);
    }

    private static void appendBounds(List<ClaimOutline> target,
                                     SafeBlockBounds bounds,
                                     String dimension,
                                     BlockPos origin,
                                     int range,
                                     int limit,
                                     String type,
                                     UUID ownerId,
                                     String owner) {
        if (bounds == null || target.size() >= limit
                || !dimension.equals(normalizedDimension(bounds.dimension()))) {
            return;
        }
        ClaimOutline outline = new ClaimOutline(
                bounds.dimension(), type, ownerId == null ? "" : ownerId.toString(), owner,
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
        if (outline.near(origin.getX(), origin.getY(), origin.getZ(), range)) {
            target.add(outline);
        }
    }

    public static boolean overlapsClaimOwnedByAnotherPlayer(CentralBank centralBank,
                                                             UUID requestedOwnerId,
                                                             String dimension,
                                                             int minX,
                                                             int minY,
                                                             int minZ,
                                                             int maxX,
                                                             int maxY,
                                                             int maxZ) {
        if (centralBank == null || requestedOwnerId == null) {
            return false;
        }
        SafeBlockBounds requested = new SafeBlockBounds(
                normalizedDimension(dimension), minX, minY, minZ, maxX, maxY, maxZ);
        Set<UUID> visited = new HashSet<>();
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank == null || !visited.add(bank.getBankId())
                    || requestedOwnerId.equals(bank.getBankOwnerId())) {
                continue;
            }
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
            SafeDepositSetupSnapshot setup = SafeDepositSetupNbtCodec.snapshot(metadata);
            for (SafePremiseSnapshot premise : setup.premises()) {
                if (premise.bounds() != null && premise.bounds().overlaps(requested)) {
                    return true;
                }
                for (SafeAreaSnapshot area : premise.safeAreas()) {
                    if (area.bounds() != null && area.bounds().overlaps(requested)) {
                        return true;
                    }
                }
            }
            for (ViewingRoomSnapshot room : ViewingRoomNbtStore.read(metadata)) {
                if (room.bounds() != null && room.bounds().overlaps(requested)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasMatchingPremiseClaimToolSession(UUID playerId,
                                                              UUID bankId,
                                                              SafeClaimToolPurpose purpose,
                                                              String premiseId) {
        if (playerId == null || bankId == null
                || (purpose != SafeClaimToolPurpose.PREMISE_CREATE
                && purpose != SafeClaimToolPurpose.PREMISE_EXIT_EDIT)) {
            return false;
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(playerId);
        String targetId = premiseId == null ? "" : premiseId.trim();
        return session != null
                && playerId.equals(session.playerId())
                && bankId.equals(session.bankId())
                && purpose == session.purpose()
                && targetId.equals(session.premiseId());
    }

    public static boolean isSafeAreaClaimToolStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String marker = ItemStackDataCompat.getCustomString(SAFE_CLAIM_TOOL_ITEM_TAG, stack);
        if ("wand".equals(marker)) {
            return true;
        }
        return SAFE_CLAIM_TOOL_ADD.equals(marker)
                || SAFE_CLAIM_TOOL_REMOVE.equals(marker)
                || SAFE_CLAIM_TOOL_APPLY.equals(marker)
                || SAFE_CLAIM_TOOL_CLEAR.equals(marker)
                || SAFE_CLAIM_TOOL_OVERLAY.equals(marker)
                || SAFE_CLAIM_TOOL_EXIT.equals(marker)
                || SAFE_CLAIM_TOOL_LOCK.equals(marker)
                || SAFE_CLAIM_TOOL_FINISH.equals(marker);
    }

    public static String safeAreaClaimToolMarker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return ItemStackDataCompat.getCustomString(SAFE_CLAIM_TOOL_ITEM_TAG, stack);
    }

    public static ClaimToolContext safeAreaClaimToolContext(UUID playerId) {
        SafeClaimToolSession session = playerId == null ? null : SAFE_CLAIM_TOOL_SESSIONS.get(playerId);
        return session == null ? null
                : new ClaimToolContext(session.bankId(), session.purpose(), session.premiseId());
    }

    public static ActionResult setSafeAreaClaimToolMode(ServerPlayer player, boolean addMode) {
        if (player == null) {
            return ActionResult.fail("Safe-area claim tool is unavailable.");
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return ActionResult.fail("No safe-area claim tool session is active.");
        }
        if (session.purpose() != SafeClaimToolPurpose.SAFE_AREA) {
            return ActionResult.fail("This premise tool does not support add/remove mode.");
        }
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), copySafeClaimSession(
                player,
                session,
                addMode,
                session.overlayEnabled(),
                session.firstDimensionId(),
                session.firstCorner(),
                session.secondCorner()
        ));
        return ActionResult.ok("Mode set to " + (addMode ? "Add Safe Area" : "Remove Safe Area")
                + ". Left-click sets Pos1, right-click sets Pos2, then press Enter to apply.");
    }

    public static ActionResult toggleSafeAreaClaimOverlay(ServerPlayer player) {
        if (player == null) {
            return ActionResult.fail("Safe-area claim tool is unavailable.");
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return ActionResult.fail("No safe-area claim tool session is active.");
        }
        boolean overlayEnabled = !session.overlayEnabled();
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), copySafeClaimSession(
                player,
                session,
                session.addMode(),
                overlayEnabled,
                session.firstDimensionId(),
                session.firstCorner(),
                session.secondCorner()
        ));
        return ActionResult.ok("Safe-area overlay " + (overlayEnabled ? "enabled" : "disabled") + ".");
    }

    public static ActionResult setSafeAreaClaimToolFirstCorner(ServerPlayer player, BlockPos clickedPos) {
        if (player == null || clickedPos == null) {
            return ActionResult.fail("Safe-area claim tool is unavailable.");
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return ActionResult.fail("No safe-area claim tool session is active.");
        }
        String dim = normalizedDimension(player.serverLevel().dimension().location().toString());
        if (!session.lifecycle().observeFirstCorner(dim, clickedPos.getX(), clickedPos.getY(), clickedPos.getZ())) {
            return ActionResult.fail("Safe-area Pos1 could not be observed.");
        }
        BlockPos second = session.secondCorner() != null
                && dim.equals(normalizedDimension(session.firstDimensionId()))
                ? session.secondCorner().immutable()
                : null;
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), copySafeClaimSession(
                player,
                session,
                session.addMode(),
                session.overlayEnabled(),
                dim,
                clickedPos.immutable(),
                second
        ));
        return ActionResult.ok("Pos1 set at " + clickedPos.getX() + ", " + clickedPos.getY() + ", " + clickedPos.getZ()
                + ". Right-click a block to set Pos2.");
    }

    public static ActionResult setSafeAreaClaimToolSecondCorner(ServerPlayer player, BlockPos clickedPos) {
        if (player == null || clickedPos == null) {
            return ActionResult.fail("Safe-area claim tool is unavailable.");
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return ActionResult.fail("No safe-area claim tool session is active.");
        }
        String dim = normalizedDimension(player.serverLevel().dimension().location().toString());
        if (!session.lifecycle().observeSecondCorner(dim, clickedPos.getX(), clickedPos.getY(), clickedPos.getZ())) {
            return ActionResult.fail("Safe-area Pos2 could not be observed.");
        }
        BlockPos first = session.firstCorner();
        if (!dim.equals(normalizedDimension(session.firstDimensionId()))) {
            first = null;
        }
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), copySafeClaimSession(
                player,
                session,
                session.addMode(),
                session.overlayEnabled(),
                dim,
                first == null ? null : first.immutable(),
                clickedPos.immutable()
        ));
        if (first == null) {
            return ActionResult.ok("Pos2 set at " + clickedPos.getX() + ", " + clickedPos.getY() + ", " + clickedPos.getZ()
                    + ". Left-click a block to set Pos1.");
        }
        return ActionResult.ok("Pos2 set at " + clickedPos.getX() + ", " + clickedPos.getY() + ", " + clickedPos.getZ()
                + ". Press Enter to apply " + (session.addMode() ? "Add" : "Remove") + " safe area.");
    }

    public static ActionResult capturePremiseClaimToolExit(ServerPlayer player,
                                                           BlockPos observedPosition,
                                                           float observedYaw) {
        if (player == null || observedPosition == null || !Float.isFinite(observedYaw)) {
            return ActionResult.fail("Premise exit observation is unavailable.");
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return ActionResult.fail("No safe-area claim tool session is active.");
        }
        if (session.purpose() == SafeClaimToolPurpose.SAFE_AREA
                || session.purpose() == SafeClaimToolPurpose.VIEWING_ROOM_CREATE) {
            return ActionResult.fail("The safe-area tool does not capture premise exits.");
        }
        if (session.purpose().isViewingRoomAnchor()) {
            return captureViewingRoomAnchor(player, session);
        }
        String dimension = normalizedDimension(player.serverLevel().dimension().location().toString());
        if (!session.lifecycle().observeExit(
                dimension,
                observedPosition.getX(), observedPosition.getY(), observedPosition.getZ(),
                observedYaw)) {
            return ActionResult.fail("Premise exit could not be observed.");
        }
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), copySafeClaimSession(
                player, session, session.addMode(), session.overlayEnabled(),
                session.firstDimensionId(), session.firstCorner(), session.secondCorner()));
        return ActionResult.ok("Premise exit captured at "
                + observedPosition.getX() + ", " + observedPosition.getY() + ", "
                + observedPosition.getZ() + " with yaw " + observedYaw + ".");
    }

    private static ActionResult captureViewingRoomAnchor(ServerPlayer player,
                                                          SafeClaimToolSession session) {
        UUID roomId;
        try {
            roomId = UUID.fromString(session.premiseId());
        } catch (IllegalArgumentException ignored) {
            return ActionResult.fail("Viewing-room anchor target is invalid.");
        }
        ViewingRoomService.AnchorKind kind = switch (session.purpose()) {
            case VIEWING_ROOM_CUSTOMER_ANCHOR -> ViewingRoomService.AnchorKind.CUSTOMER;
            case VIEWING_ROOM_TELLER_ANCHOR -> ViewingRoomService.AnchorKind.TELLER;
            case VIEWING_ROOM_DISPLAY_ANCHOR -> ViewingRoomService.AnchorKind.DISPLAY;
            default -> null;
        };
        if (kind == null) {
            return ActionResult.fail("Viewing-room anchor type is invalid.");
        }
        MinecraftServer server = player.getServer();
        CentralBank centralBank = server == null ? null : BankManager.getCentralBank(server);
        if (centralBank == null) {
            return ActionResult.fail("Bank data is unavailable.");
        }
        ViewingRoomAnchor anchor = new ViewingRoomAnchor(
                normalizedDimension(player.serverLevel().dimension().location().toString()),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        ViewingRoomService.MutationResult result = ViewingRoomService.setAnchor(
                centralBank, session.bankId(), roomId, kind, anchor);
        if (!result.success()) {
            return ActionResult.fail(result.message());
        }
        if (SAFE_CLAIM_TOOL_SESSIONS.remove(player.getUUID(), session)) {
            session.lifecycle().close(SafeClaimToolLifecycle.TerminalReason.APPLY_SUCCESS);
        }
        return ActionResult.ok(result.message());
    }

    public static ActionResult clearSafeAreaClaimToolSelection(ServerPlayer player) {
        if (player == null) {
            return ActionResult.fail("Safe-area claim tool is unavailable.");
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return ActionResult.fail("No safe-area claim tool session is active.");
        }
        session.lifecycle().clearSelection();
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), copySafeClaimSession(
                player,
                session,
                session.addMode(),
                session.overlayEnabled(),
                "",
                null,
                null
        ));
        return ActionResult.ok("Safe-area selection cleared.");
    }

    public static ActionResult applySafeAreaClaimToolSelection(MinecraftServer server,
                                                               CentralBank centralBank,
                                                               ServerPlayer player) {
        return applyClaimToolSelection(server, centralBank, player).result();
    }

    public static ClaimToolApplyResult applyClaimToolSelection(MinecraftServer server,
                                                               CentralBank centralBank,
                                                               ServerPlayer player) {
        if (server == null || centralBank == null || player == null) {
            return new ClaimToolApplyResult(
                    ActionResult.fail("Safe-area claim tool is unavailable."), null, null);
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return new ClaimToolApplyResult(
                    ActionResult.fail("No safe-area claim tool session is active."), null, null);
        }
        ClaimToolContext context = new ClaimToolContext(
                session.bankId(), session.purpose(), session.premiseId());
        if (session.purpose() == SafeClaimToolPurpose.VIEWING_ROOM_CREATE) {
            return applyViewingRoomClaimToolSelection(centralBank, player, session, context);
        }
        if (session.purpose() != SafeClaimToolPurpose.SAFE_AREA) {
            return applyPremiseClaimToolSelection(server, centralBank, player, session, context);
        }
        return new ClaimToolApplyResult(
                applySafeAreaClaimToolSelection(server, centralBank, player, session),
                context,
                null);
    }

    private static ClaimToolApplyResult applyViewingRoomClaimToolSelection(
            CentralBank centralBank,
            ServerPlayer player,
            SafeClaimToolSession session,
            ClaimToolContext context) {
        SafeClaimSelection selection = session.lifecycle().selection();
        if (!session.lifecycle().readyToApply() || selection.firstCorner() == null
                || selection.secondCorner() == null
                || !selection.firstCorner().dimension().equals(selection.secondCorner().dimension())) {
            return new ClaimToolApplyResult(
                    ActionResult.fail("Select Pos1 and Pos2 for the viewing room first."), context, null);
        }
        SafeBlockBounds bounds = new SafeBlockBounds(
                selection.firstCorner().dimension(),
                selection.firstCorner().x(), selection.firstCorner().y(), selection.firstCorner().z(),
                selection.secondCorner().x(), selection.secondCorner().y(), selection.secondCorner().z());
        ViewingRoomService.MutationResult result = ViewingRoomService.claim(
                centralBank, session.bankId(), bounds, session.adminAuthorized());
        if (result.success()) {
            if (SAFE_CLAIM_TOOL_SESSIONS.remove(player.getUUID(), session)) {
                session.lifecycle().close(SafeClaimToolLifecycle.TerminalReason.APPLY_SUCCESS);
            }
        } else {
            SAFE_CLAIM_TOOL_SESSIONS.replace(player.getUUID(), session, copySafeClaimSession(
                    player, session, session.addMode(), session.overlayEnabled(),
                    session.firstDimensionId(), session.firstCorner(), session.secondCorner()));
        }
        return new ClaimToolApplyResult(
                result.success() ? ActionResult.ok(result.message()) : ActionResult.fail(result.message()),
                context, null);
    }

    private static ActionResult applySafeAreaClaimToolSelection(MinecraftServer server,
                                                                CentralBank centralBank,
                                                                ServerPlayer player,
                                                                SafeClaimToolSession session) {
        if (!session.lifecycle().readyToApply() || session.firstCorner() == null || session.secondCorner() == null
                || session.firstDimensionId() == null || session.firstDimensionId().isBlank()) {
            return ActionResult.fail("Select Pos1 and Pos2 first, then apply.");
        }
        String currentDim = normalizedDimension(player.serverLevel().dimension().location().toString());
        String selectedDim = normalizedDimension(session.firstDimensionId());
        if (!selectedDim.equals(currentDim)) {
            return ActionResult.fail("Return to " + selectedDim + " to apply this safe-area selection.");
        }

        ActionResult result = session.addMode()
                ? claimSafeArea(centralBank, player, session.bankId(), selectedDim, session.firstCorner(), session.secondCorner())
                : removeSafeArea(centralBank, player, session.bankId(), selectedDim, session.firstCorner(), session.secondCorner());
        if (result.success()) {
            session.lifecycle().clearSelection();
        }
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), copySafeClaimSession(
                player,
                session,
                session.addMode(),
                session.overlayEnabled(),
                result.success() ? "" : selectedDim,
                result.success() ? null : session.firstCorner().immutable(),
                result.success() ? null : session.secondCorner().immutable()
        ));
        return result.success()
                ? ActionResult.ok(result.message() + " Selection cleared for next region.")
                : result;
    }

    private static ClaimToolApplyResult applyPremiseClaimToolSelection(
            MinecraftServer server,
            CentralBank centralBank,
            ServerPlayer player,
            SafeClaimToolSession session,
            ClaimToolContext context) {
        SafeClaimSelection selection = session.lifecycle().selection();
        if (session.purpose() == SafeClaimToolPurpose.PREMISE_CREATE
                && selection.firstCorner() != null
                && selection.secondCorner() != null
                && selection.exit() != null
                && selection.firstCorner().dimension().equals(selection.secondCorner().dimension())
                && selection.firstCorner().dimension().equals(selection.exit().dimension())) {
            SafeBlockBounds bounds = new SafeBlockBounds(
                    selection.firstCorner().dimension(),
                    selection.firstCorner().x(), selection.firstCorner().y(), selection.firstCorner().z(),
                    selection.secondCorner().x(), selection.secondCorner().y(), selection.secondCorner().z());
            if (bounds.contains(selection.exit().x(), selection.exit().y(), selection.exit().z())) {
                return failedPremiseApply(
                        player, session, context, "Premise exit must be outside the premise.");
            }
        }
        if (!session.lifecycle().readyToApply()) {
            String message = session.purpose() == SafeClaimToolPurpose.PREMISE_EXIT_EDIT
                    ? "Capture an outside premise exit before applying."
                    : "Select Pos1, Pos2, and an outside exit before applying.";
            return failedPremiseApply(player, session, context, message);
        }

        OwnerPcPremiseService.Result premiseResult = session.adminAuthorized()
                ? OwnerPcPremiseAdminService.applyObservedSelection(
                        server, centralBank, player, session.bankId(), session.purpose(),
                        session.premiseId(), selection)
                : OwnerPcPremiseService.applyObservedSelection(
                        server, centralBank, player, session.bankId(), session.purpose(),
                        session.premiseId(), selection);
        ActionResult actionResult = premiseResult.success()
                ? ActionResult.ok(premiseResult.message())
                : ActionResult.fail(premiseResult.message());
        if (premiseResult.success()) {
            if (SAFE_CLAIM_TOOL_SESSIONS.remove(player.getUUID(), session)) {
                session.lifecycle().close(SafeClaimToolLifecycle.TerminalReason.APPLY_SUCCESS);
            }
        } else {
            SAFE_CLAIM_TOOL_SESSIONS.replace(player.getUUID(), session, copySafeClaimSession(
                    player, session, session.addMode(), session.overlayEnabled(),
                    session.firstDimensionId(), session.firstCorner(), session.secondCorner()));
        }
        return new ClaimToolApplyResult(actionResult, context, premiseResult);
    }

    private static ClaimToolApplyResult failedPremiseApply(ServerPlayer player,
                                                           SafeClaimToolSession session,
                                                           ClaimToolContext context,
                                                           String message) {
        SAFE_CLAIM_TOOL_SESSIONS.replace(player.getUUID(), session, copySafeClaimSession(
                player, session, session.addMode(), session.overlayEnabled(),
                session.firstDimensionId(), session.firstCorner(), session.secondCorner()));
        OwnerPcPremiseActionPayload.Action action =
                context.purpose() == SafeClaimToolPurpose.PREMISE_EXIT_EDIT
                        ? OwnerPcPremiseActionPayload.Action.START_EXIT_EDIT
                        : OwnerPcPremiseActionPayload.Action.START_CLAIM;
        OwnerPcPremiseService.Result premiseResult = new OwnerPcPremiseService.Result(
                context.bankId(), action, context.premiseId(), false, message);
        return new ClaimToolApplyResult(ActionResult.fail(message), context, premiseResult);
    }

    public static ActionResult finishSafeAreaClaimToolSession(ServerPlayer player, String reason) {
        return finishSafeAreaClaimToolSession(player, reason, SafeClaimToolLifecycle.TerminalReason.BARRIER_CANCEL);
    }

    public static ActionResult finishSafeAreaClaimToolSession(ServerPlayer player, String reason,
                                                               SafeClaimToolLifecycle.TerminalReason terminalReason) {
        if (player == null) {
            return ActionResult.fail("Safe-area claim tool is unavailable.");
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.remove(player.getUUID());
        if (session == null) {
            return ActionResult.fail("No safe-area claim tool session is active.");
        }
        session.lifecycle().close(terminalReason);
        ClaimModeService.domainClosed(player);
        return ActionResult.ok(reason == null || reason.isBlank() ? "Safe-area claim tool closed." : reason);
    }

    public static void closeSafeAreaClaimToolSession(ServerPlayer player, String reason) {
        closeSafeAreaClaimToolSession(player, reason, SafeClaimToolLifecycle.TerminalReason.INTERRUPTED);
    }

    public static void closeSafeAreaClaimToolSession(ServerPlayer player, String reason,
                                                      SafeClaimToolLifecycle.TerminalReason terminalReason) {
        if (player == null || !hasSafeAreaClaimToolSession(player.getUUID())) {
            return;
        }
        finishSafeAreaClaimToolSession(player, reason, terminalReason);
    }

    public static void onServerStopping() {
        for (SafeClaimToolSession session : new ArrayList<>(SAFE_CLAIM_TOOL_SESSIONS.values())) {
            if (session != null && SAFE_CLAIM_TOOL_SESSIONS.remove(session.playerId(), session)) {
                session.lifecycle().close(SafeClaimToolLifecycle.TerminalReason.SERVER_STOP);
                ClaimModeService.clear(session.playerId());
            }
        }
    }

    public static void tickSessions(MinecraftServer server) {
        if (server == null) {
            return;
        }
        tickPendingMenuOpens(server);
        if (SAFE_CLAIM_TOOL_SESSIONS.isEmpty()) {
            return;
        }
        for (UUID playerId : new ArrayList<>(SAFE_CLAIM_TOOL_SESSIONS.keySet())) {
            SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(playerId);
            if (session == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                if (SAFE_CLAIM_TOOL_SESSIONS.remove(playerId, session)) {
                    session.lifecycle().close(SafeClaimToolLifecycle.TerminalReason.LOGOUT);
                    ClaimModeService.clear(playerId);
                }
                continue;
            }
            long now = player.serverLevel().getGameTime();
            if ((now - session.lastUpdatedTick()) > SAFE_CLAIM_TOOL_TIMEOUT_TICKS) {
                finishSafeAreaClaimToolSession(player, "Bank claim mode timed out.",
                        SafeClaimToolLifecycle.TerminalReason.TIMEOUT);
            }
        }
    }

    private static void tickPendingMenuOpens(MinecraftServer server) {
        if (PENDING_MENU_OPENS.isEmpty()) {
            return;
        }
        for (UUID playerId : new ArrayList<>(PENDING_MENU_OPENS.keySet())) {
            PendingMenuOpen pending = PENDING_MENU_OPENS.get(playerId);
            if (pending == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                PENDING_MENU_OPENS.remove(playerId);
                continue;
            }

            ServerLevel level = levelForDimension(server, pending.dimension());
            long now = level == null ? player.serverLevel().getGameTime() : level.getGameTime();
            if (now > pending.expiresAtGameTime()) {
                PENDING_MENU_OPENS.remove(playerId);
                continue;
            }
            if (now < pending.openAtGameTime()) {
                continue;
            }

            PENDING_MENU_OPENS.remove(playerId);
            if (player.containerMenu != player.inventoryMenu) {
                continue;
            }
            if (level == null || !(level.getBlockEntity(pending.rowPos()) instanceof SafetyDepositBoxRowBlockEntity row)) {
                player.sendSystemMessage(Component.literal("Safety deposit box is no longer available."));
                continue;
            }

            CentralBank centralBank = BankManager.getCentralBank(server);
            OpenBoxContext context = resolveOpenBoxContext(centralBank, player, row, pending.doorIndex(), true);
            if (context == null) {
                continue;
            }
            if (!context.account().getAccountUUID().equals(pending.accountId())) {
                player.sendSystemMessage(Component.literal("This safety deposit box assignment changed."));
                continue;
            }
            openSafetyDepositBoxMenu(player, row, context);
        }
    }

    public static ActionResult claimSafeArea(CentralBank centralBank,
                                             ServerPlayer player,
                                             UUID bankId,
                                             String dimension,
                                             BlockPos min,
                                             BlockPos max) {
        if (centralBank == null || bankId == null || min == null || max == null) {
            return ActionResult.fail("Safe-area claim failed: invalid bounds.");
        }
        if (player != null && !canManageSafeArea(centralBank, player, bankId)) {
            return ActionResult.fail(safeAreaManagementDeniedMessage("claim safe areas"));
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return ActionResult.fail("Safe-area claim failed: bank no longer exists.");
        }
        CompoundTag stagedMetadata = centralBank.getOrCreateBankMetadata(bankId).copy();
        SafeDepositSetupNbtCodec.migrateLegacy(stagedMetadata, bankId);
        SafeBlockBounds requestedBounds = new SafeBlockBounds(
                dimension,
                min.getX(),
                min.getY(),
                min.getZ(),
                max.getX(),
                max.getY(),
                max.getZ()
        );
        SafeDepositSetupBinder.ParentResolution parent = SafeDepositSetupBinder.resolveContainingPremise(
                stagedMetadata, bankId, requestedBounds);
        if (parent.containment() == SafeDepositSetupBinder.Containment.NONE) {
            return ActionResult.fail("Safe area must be inside exactly one bank premise.");
        }
        if (parent.containment() == SafeDepositSetupBinder.Containment.MULTIPLE) {
            return ActionResult.fail("Safe area is contained by multiple bank premises.");
        }
        if (overlapsAnyClaimedSafeArea(
                centralBank,
                requestedBounds.dimension(),
                requestedBounds.minX(),
                requestedBounds.minY(),
                requestedBounds.minZ(),
                requestedBounds.maxX(),
                requestedBounds.maxY(),
                requestedBounds.maxZ())) {
            return ActionResult.fail("New safe area overlaps an existing safe area. Add adjacent regions instead.");
        }
        ListTag areas = stagedMetadata.getList(AREAS_KEY, Tag.TAG_COMPOUND);
        CompoundTag area = buildSafeAreaTag(
                requestedBounds.dimension(),
                requestedBounds.minX(),
                requestedBounds.minY(),
                requestedBounds.minZ(),
                requestedBounds.maxX(),
                requestedBounds.maxY(),
                requestedBounds.maxZ());
        area.putLong("claimedAtMillis", System.currentTimeMillis());
        MinecraftServer server = player == null ? net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() : player.getServer();
        int rowCapacity = safeBoxRowCapacity(bank);
        int loadedRowsAfterClaim = countLoadedSafeRowBlocks(server, withAdditionalArea(areas, area));
        if (loadedRowsAfterClaim > rowCapacity) {
            return ActionResult.fail("Safe-area claim contains " + loadedRowsAfterClaim
                    + " loaded row blocks, but this bank's tier capacity is " + rowCapacity
                    + ". Remove row blocks or level up the bank first.");
        }
        areas.add(area);
        stagedMetadata.put(AREAS_KEY, areas);
        if (!SafeDepositSetupBinder.attachGeneratedSafeArea(
                stagedMetadata, bankId, parent.premiseId(), requestedBounds)) {
            return ActionResult.fail("Safe-area claim failed: containing premise changed.");
        }
        SafeDepositSetupNbtCodec.migrateLegacy(stagedMetadata, bankId);
        centralBank.putBankMetadata(bankId, stagedMetadata);
        return ActionResult.ok("Safe area claimed for " + bank.getBankName() + ".");
    }

    public static ActionResult removeSafeArea(CentralBank centralBank,
                                              ServerPlayer player,
                                              UUID bankId,
                                              String dimension,
                                              BlockPos min,
                                              BlockPos max) {
        if (centralBank == null || bankId == null || min == null || max == null) {
            return ActionResult.fail("Safe-area remove failed: invalid bounds.");
        }
        if (player != null && !canManageSafeArea(centralBank, player, bankId)) {
            return ActionResult.fail(safeAreaManagementDeniedMessage("remove safe areas"));
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return ActionResult.fail("Safe-area remove failed: bank no longer exists.");
        }
        CompoundTag stagedMetadata = centralBank.getOrCreateBankMetadata(bankId).copy();
        SafeDepositSetupNbtCodec.migrateLegacy(stagedMetadata, bankId);
        ListTag areas = stagedMetadata.getList(AREAS_KEY, Tag.TAG_COMPOUND);
        if (areas.isEmpty()) {
            return ActionResult.fail("No safe areas are claimed for this bank.");
        }
        String normalizedDimension = normalizedDimension(dimension);
        int minX = Math.min(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxX = Math.max(min.getX(), max.getX());
        int maxY = Math.max(min.getY(), max.getY());
        int maxZ = Math.max(min.getZ(), max.getZ());

        List<CompoundTag> next = new ArrayList<>();
        boolean changed = false;
        for (Tag tag : areas) {
            if (!(tag instanceof CompoundTag existing)) {
                continue;
            }
            if (!normalizedDimension(existing.getString("dimension")).equals(normalizedDimension)) {
                next.add(existing.copy());
                continue;
            }
            if (!regionsOverlap(
                    minX, minY, minZ, maxX, maxY, maxZ,
                    existing.getInt("minX"), existing.getInt("minY"), existing.getInt("minZ"),
                    existing.getInt("maxX"), existing.getInt("maxY"), existing.getInt("maxZ")
            )) {
                next.add(existing.copy());
                continue;
            }
            changed = true;
            next.addAll(subtractSafeArea(existing, minX, minY, minZ, maxX, maxY, maxZ));
        }
        if (!changed) {
            return ActionResult.fail("No claimed safe area intersects that selection.");
        }
        ListTag replaced = new ListTag();
        for (CompoundTag area : next) {
            replaced.add(area);
        }
        stagedMetadata.put(AREAS_KEY, replaced);
        SafeDepositSetupNbtCodec.migrateLegacy(stagedMetadata, bankId);
        centralBank.putBankMetadata(bankId, stagedMetadata);
        return ActionResult.ok("Safe area removed for " + bank.getBankName() + ".");
    }

    public static ActionResult assignFirstFreeBox(MinecraftServer server,
                                                  CentralBank centralBank,
                                                  UUID bankId,
                                                  UUID accountId) {
        return assignFirstFreeBox(server, centralBank, bankId, accountId, null);
    }

    public static ActionResult assignFirstFreeBox(MinecraftServer server,
                                                  CentralBank centralBank,
                                                  UUID bankId,
                                                  UUID accountId,
                                                  SafetyDepositBoxRowBlockEntity.ModuleType requestedType) {
        if (server == null || centralBank == null || bankId == null || accountId == null) {
            return ActionResult.fail("Safety box assignment failed: missing data.");
        }
        Bank bank = centralBank.getBank(bankId);
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (bank == null || account == null || !bankId.equals(account.getBankId())) {
            return ActionResult.fail("Safety box assignment failed: account is not in this bank.");
        }
        Assignment existing = findAssignmentForAccount(centralBank, accountId);
        if (existing != null) {
            return ActionResult.fail("This account already has safety box " + existing.boxNumber() + ".");
        }

        ensureSafeDepositSetup(centralBank, bankId);
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        ListTag areas = metadata.getList(AREAS_KEY, Tag.TAG_COMPOUND);
        if (areas.isEmpty()) {
            return ActionResult.fail("No safe area is claimed for this bank.");
        }

        SafetyDepositBoxRowBlockEntity.ModuleType cleanRequestedType = requestedType == null
                ? null
                : normalizeAssignableType(requestedType);
        SafeVaultReadinessOperation operation = safeDepositVaultReadinessOperation(server, metadata);
        for (LoadedSafeRow loaded : collectLoadedSafeRows(server, areas)) {
            if (loaded == null || loaded.row() == null || loaded.pos() == null) {
                continue;
            }
            Level level = loaded.row().getLevel();
            if (level == null) {
                continue;
            }
            SafeVaultReadinessResolver.RowReadiness readiness = operation.resolve(
                    new SafeVaultReadinessResolver.RowLocation(loaded.dimension(), loaded.pos()));
            if (readiness.summary() != null && !readiness.summary().ready()) {
                continue;
            }
            for (int door = 0; door < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; door++) {
                SafetyDepositBoxRowBlockEntity.ModuleType moduleType = normalizeAssignableType(loaded.row().getModuleType(door));
                if (cleanRequestedType != null && moduleType != cleanRequestedType) {
                    continue;
                }
                if (!loaded.row().isAssignableBoxStart(door)
                        || loaded.row().isAssigned(door)
                        || findAssignmentAt(centralBank, level, loaded.pos(), door) != null) {
                    continue;
                }
                String boxNumber = nextBoxNumber(metadata);
                loaded.row().assignDoor(door, accountId, boxNumber);
                addAssignment(metadata, account, level, loaded.pos(), door, boxNumber, moduleType);
                centralBank.putBankMetadata(bankId, metadata);
                return ActionResult.ok("Assigned " + shortModuleLabel(moduleType).toLowerCase(Locale.ROOT)
                        + " safety box " + boxNumber + " to account " + shortId(accountId) + ".");
            }
        }
        if (cleanRequestedType != null) {
            return ActionResult.fail("No free " + shortModuleLabel(cleanRequestedType).toLowerCase(Locale.ROOT)
                    + " safety deposit boxes were found in loaded safe areas.");
        }
        return ActionResult.fail("No free safety deposit boxes were found in loaded safe areas.");
    }

    public static ActionResult setPricingPolicy(CentralBank centralBank,
                                                UUID bankId,
                                                String modeRaw,
                                                String amountRaw,
                                                String periodTicksRaw,
                                                String overdueTicksRaw) {
        return setPricingPolicy(centralBank, bankId, "", modeRaw, amountRaw, periodTicksRaw, overdueTicksRaw);
    }

    public static ActionResult setPricingPolicy(CentralBank centralBank,
                                                UUID bankId,
                                                String sizeRaw,
                                                String modeRaw,
                                                String amountRaw,
                                                String periodTicksRaw,
                                                String overdueTicksRaw) {
        if (centralBank == null || bankId == null) {
            return ActionResult.fail("Pricing update failed: missing bank data.");
        }
        String mode = normalizePolicyMode(modeRaw);
        if (!mode.equals("FREE") && !mode.equals("ONE_TIME") && !mode.equals("RECURRING")) {
            return ActionResult.fail("Pricing mode must be FREE, ONE_TIME, or RECURRING.");
        }
        BigDecimal amount = parseMoney(amountRaw);
        if ((mode.equals("ONE_TIME") || mode.equals("RECURRING")) && amount.compareTo(BigDecimal.ZERO) < 0) {
            return ActionResult.fail("Pricing amount must be non-negative.");
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        SafetyDepositBoxRowBlockEntity.ModuleType type = parseAssignableType(sizeRaw);
        String modeKey = type == null ? POLICY_MODE_KEY : sizedPolicyKey(POLICY_MODE_KEY, type);
        String oneTimeKey = type == null ? ONE_TIME_FEE_KEY : sizedPolicyKey(ONE_TIME_FEE_KEY, type);
        String rentKey = type == null ? RENT_AMOUNT_KEY : sizedPolicyKey(RENT_AMOUNT_KEY, type);
        String periodKey = type == null ? RENT_PERIOD_TICKS_KEY : sizedPolicyKey(RENT_PERIOD_TICKS_KEY, type);
        String overdueKey = type == null ? OVERDUE_TICKS_KEY : sizedPolicyKey(OVERDUE_TICKS_KEY, type);
        metadata.putString(modeKey, mode);
        metadata.putString(oneTimeKey, mode.equals("ONE_TIME") ? amount.toPlainString() : metadata.getString(oneTimeKey));
        metadata.putString(rentKey, mode.equals("RECURRING") ? amount.toPlainString() : metadata.getString(rentKey));
        metadata.putLong(periodKey, parsePositiveLong(periodTicksRaw, DEFAULT_RENT_PERIOD_TICKS));
        metadata.putLong(overdueKey, parsePositiveLong(overdueTicksRaw, DEFAULT_OVERDUE_TICKS));
        centralBank.putBankMetadata(bankId, metadata);
        String target = type == null ? "Default" : shortModuleLabel(type);
        return ActionResult.ok(target + " safety deposit box pricing updated to " + mode + ".");
    }

    public static ActionResult seizeOverdueBox(CentralBank centralBank, UUID bankId, UUID accountId) {
        if (centralBank == null || bankId == null || accountId == null) {
            return ActionResult.fail("Seizure failed: missing data.");
        }
        Assignment assignment = findAssignmentForAccount(centralBank, accountId);
        if (assignment == null || !bankId.equals(assignment.bankId())) {
            return ActionResult.fail("No safety box assignment found for that account.");
        }
        if (!assignment.locked()) {
            return ActionResult.fail("Only locked overdue safety boxes can be seized.");
        }
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            return ActionResult.fail("Account no longer exists.");
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        ListTag escrow = metadata.getList(ESCROW_KEY, Tag.TAG_COMPOUND);
        CompoundTag seized = new CompoundTag();
        seized.putUUID("accountId", accountId);
        seized.putString("boxNumber", assignment.boxNumber());
        seized.putLong("seizedAtMillis", System.currentTimeMillis());
        ListTag contents = new ListTag();
        account.getSafeBoxSlots().forEach((slot, stackTag) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", slot);
            entry.put("stack", stackTag.copy());
            contents.add(entry);
        });
        seized.put("contents", contents);
        escrow.add(seized);
        metadata.put(ESCROW_KEY, escrow);
        account.getSafeBoxSlots().clear();
        removeAssignment(metadata, assignment);
        centralBank.putBankMetadata(bankId, metadata);
        BankManager.markDirty();
        return ActionResult.ok("Seized contents of " + assignment.boxNumber() + " into bank escrow.");
    }

    public static boolean isInsideSafeArea(CentralBank centralBank, Level level, BlockPos pos) {
        return findSafeAreaClaimMatch(centralBank, level, pos).claimed();
    }

    public static ActionResult validateSafeRowPlacement(MinecraftServer server,
                                                        CentralBank centralBank,
                                                        ServerPlayer player,
                                                        Level level,
                                                        BlockPos pos) {
        if (centralBank == null || level == null || pos == null) {
            return ActionResult.fail("Safe row placement failed: bank data is unavailable.");
        }
        SafeAreaClaimMatch match = findSafeAreaClaimMatch(centralBank, level, pos);
        if (match.ambiguous()) {
            return ActionResult.fail("Safe row placement failed: this position is claimed by multiple bank safe areas.");
        }
        if (!match.claimed()) {
            return ActionResult.ok("");
        }
        UUID bankId = match.bankId();
        if (player != null && !canManageSafeArea(centralBank, player, bankId)) {
            return ActionResult.fail(safeAreaManagementDeniedMessage("place safe row blocks here"));
        }
        if (server == null) {
            return ActionResult.fail("Safe row placement failed: bank data is unavailable.");
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return ActionResult.fail("Safe row placement failed: bank no longer exists.");
        }
        int rowCapacity = safeBoxRowCapacity(bank);
        int loadedRows = countLoadedSafeRowBlocks(server, centralBank, bankId);
        boolean placedStateVisible = level.getBlockState(pos).is(ModBlocks.SAFETY_DEPOSIT_BOX_ROW.get());
        int projectedRows = placedStateVisible ? loadedRows : loadedRows + 1;
        if (projectedRows > rowCapacity) {
            return ActionResult.fail("Safe row capacity reached for " + bank.getBankName()
                    + ": " + loadedRows + " / " + rowCapacity
                    + ". Remove a row block or level up the bank first.");
        }
        return ActionResult.ok("");
    }

    public static UUID findBankIdForSafeArea(CentralBank centralBank, Level level, BlockPos pos) {
        SafeAreaClaimMatch match = findSafeAreaClaimMatch(centralBank, level, pos);
        return match.single() ? match.bankId() : null;
    }

    private static SafeAreaClaimMatch findSafeAreaClaimMatch(CentralBank centralBank, Level level, BlockPos pos) {
        if (centralBank == null || level == null || pos == null) {
            return SafeAreaClaimMatch.unclaimed();
        }
        String dimension = level.dimension().location().toString();
        UUID matchedBankId = null;
        for (UUID bankId : centralBank.getBankMetadata().keySet()) {
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
            ListTag areas = metadata.getList(AREAS_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < areas.size(); i++) {
                if (contains(areas.getCompound(i), dimension, pos)) {
                    if (matchedBankId != null && !matchedBankId.equals(bankId)) {
                        return SafeAreaClaimMatch.ambiguousMatch();
                    }
                    matchedBankId = bankId;
                }
            }
        }
        return matchedBankId == null ? SafeAreaClaimMatch.unclaimed() : SafeAreaClaimMatch.single(matchedBankId);
    }

    public static boolean canManageSafeArea(CentralBank centralBank, ServerPlayer player, UUID bankId) {
        if (player == null) {
            return false;
        }
        if (player.hasPermissions(3)) {
            return true;
        }
        return canManageSafeArea(centralBank, player.getUUID(), bankId);
    }

    public static boolean canManageSafeArea(CentralBank centralBank, UUID playerId, UUID bankId) {
        if (centralBank == null || playerId == null || bankId == null) {
            return false;
        }
        if (BankOwnerPcService.isOwner(centralBank, playerId, bankId)) {
            return true;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        String role = valueForUuid(metadata.getString("roles"), playerId);
        if ("DIRECTOR".equalsIgnoreCase(role) || "FOUNDER".equalsIgnoreCase(role)) {
            return true;
        }
        String employeeRole = employeeRoleForUuid(metadata.getString("employees"), playerId);
        if ("DIRECTOR".equalsIgnoreCase(employeeRole)) {
            return true;
        }
        if (uuidListContains(metadata.getString("cofounders"), playerId)) {
            return true;
        }
        BigDecimal share = shareForUuid(metadata.getString("shares"), playerId);
        return share.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean canAccessProtectedSafeArea(CentralBank centralBank, ServerPlayer player, UUID bankId) {
        if (player == null) {
            return false;
        }
        if (player.hasPermissions(3)) {
            return true;
        }
        return canAccessProtectedSafeArea(centralBank, player.getUUID(), bankId);
    }

    public static boolean canAccessProtectedSafeArea(CentralBank centralBank, UUID playerId, UUID bankId) {
        if (centralBank == null || playerId == null || bankId == null) {
            return false;
        }
        if (canManageSafeArea(centralBank, playerId, bankId)) {
            return true;
        }
        return hasExplicitCurrentEmployeeSafeAccess(centralBank, playerId, bankId);
    }

    public static boolean hasExplicitCurrentEmployeeSafeAccess(CentralBank centralBank, UUID playerId, UUID bankId) {
        if (centralBank == null || playerId == null || bankId == null) {
            return false;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        return BankStaffingService.canAccessProtectedSafeArea(false, metadata, playerId);
    }

    private static boolean canOpenAccountBox(AccountHolder account, UUID playerId) {
        return account != null && playerId != null
                && (playerId.equals(account.getPlayerUUID()) || account.canManage(playerId) || account.canWithdraw(playerId));
    }

    public static void syncAssignmentsToRow(SafetyDepositBoxRowBlockEntity row) {
        if (row == null || !(row.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(serverLevel.getServer());
        syncAssignmentsToRow(centralBank, row);
    }

    private static void syncAssignmentsToRow(CentralBank centralBank, SafetyDepositBoxRowBlockEntity row) {
        if (centralBank == null || row == null) {
            return;
        }
        for (int door = 0; door < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; door++) {
            if (!row.isAssignableBoxStart(door)) {
                continue;
            }
            Assignment assignment = findAssignment(centralBank, row, door);
            if (assignment == null) {
                if (row.getAssignedAccountId(door) != null || !row.getBoxNumber(door).isBlank()) {
                    row.clearDoorAssignment(door);
                }
                continue;
            }
            if (!Objects.equals(row.getAssignedAccountId(door), assignment.accountId())
                    || !Objects.equals(row.getBoxNumber(door), assignment.boxNumber())) {
                row.assignDoor(door, assignment.accountId(), assignment.boxNumber());
            }
        }
    }

    private static Assignment findAssignment(CentralBank centralBank, SafetyDepositBoxRowBlockEntity row, int doorIndex) {
        Level level = row.getLevel();
        return level == null ? null : findAssignmentAt(centralBank, level, row.getBlockPos(), doorIndex);
    }

    private static Assignment findAssignmentAt(CentralBank centralBank, Level level, BlockPos pos, int doorIndex) {
        if (centralBank == null || level == null || pos == null) {
            return null;
        }
        String dimension = level.dimension().location().toString();
        Assignment found = null;
        for (UUID bankId : centralBank.getBankMetadata().keySet()) {
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
            ListTag assignments = metadata.getList(ASSIGNMENTS_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < assignments.size(); i++) {
                Assignment assignment = readAssignment(assignments.getCompound(i));
                if (assignment != null
                        && bankId.equals(assignment.bankId())
                        && dimension.equals(assignment.dimension())
                        && pos.equals(assignment.pos())
                        && doorIndex == assignment.doorIndex()) {
                    if (found != null) {
                        return null;
                    }
                    found = assignment;
                }
            }
        }
        return found;
    }

    private static Assignment findAssignmentForAccount(CentralBank centralBank, UUID accountId) {
        if (centralBank == null || accountId == null) {
            return null;
        }
        for (UUID bankId : centralBank.getBankMetadata().keySet()) {
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
            ListTag assignments = metadata.getList(ASSIGNMENTS_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < assignments.size(); i++) {
                Assignment assignment = readAssignment(assignments.getCompound(i));
                if (assignment != null && accountId.equals(assignment.accountId())) {
                    return assignment;
                }
            }
        }
        return null;
    }

    private static void addAssignment(CompoundTag metadata,
                                      AccountHolder account,
                                      Level level,
                                      BlockPos pos,
                                      int doorIndex,
                                      String boxNumber,
                                      SafetyDepositBoxRowBlockEntity.ModuleType moduleType) {
        ListTag assignments = metadata.getList(ASSIGNMENTS_KEY, Tag.TAG_COMPOUND);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("accountId", account.getAccountUUID());
        tag.putUUID("bankId", account.getBankId());
        tag.putString("dimension", level.dimension().location().toString());
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        tag.putInt("doorIndex", doorIndex);
        tag.putString("boxNumber", boxNumber == null ? "" : boxNumber);
        tag.putBoolean("locked", false);
        tag.putLong("assignedAtMillis", System.currentTimeMillis());
        SafetyDepositBoxRowBlockEntity.ModuleType cleanType = normalizeAssignableType(moduleType);
        PricingPolicy policy = pricingPolicy(metadata, cleanType);
        tag.putString("moduleType", cleanType.name());
        tag.putString("policyMode", policy.mode());
        BigDecimal paidAmount = policy.amount();
        long rentPeriod = policy.rentPeriodTicks();
        tag.putString("paidAmount", paidAmount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN).toPlainString());
        tag.putLong("rentPeriodTicks", Math.max(1L, rentPeriod));
        assignments.add(tag);
        metadata.put(ASSIGNMENTS_KEY, assignments);
    }

    private static void removeAssignment(CompoundTag metadata, Assignment assignment) {
        ListTag oldAssignments = metadata.getList(ASSIGNMENTS_KEY, Tag.TAG_COMPOUND);
        ListTag nextAssignments = new ListTag();
        for (int i = 0; i < oldAssignments.size(); i++) {
            Assignment current = readAssignment(oldAssignments.getCompound(i));
            if (current == null || !current.matches(assignment)) {
                nextAssignments.add(oldAssignments.getCompound(i));
            }
        }
        metadata.put(ASSIGNMENTS_KEY, nextAssignments);
    }

    public static int forceRemoveAssignmentsInPremise(MinecraftServer server,
                                                       CompoundTag metadata,
                                                       UUID bankId,
                                                       SafeBlockBounds premiseBounds) {
        if (metadata == null || bankId == null || premiseBounds == null) {
            return 0;
        }
        ListTag oldAssignments = metadata.getList(ASSIGNMENTS_KEY, Tag.TAG_COMPOUND);
        ListTag retained = new ListTag();
        int removed = 0;
        for (int index = 0; index < oldAssignments.size(); index++) {
            CompoundTag raw = oldAssignments.getCompound(index);
            Assignment assignment = readAssignment(raw);
            if (assignment == null || !bankId.equals(assignment.bankId())
                    || !premiseBounds.contains(
                            assignment.dimension(), assignment.pos().getX(),
                            assignment.pos().getY(), assignment.pos().getZ())) {
                retained.add(raw.copy());
                continue;
            }
            clearPhysicalAssignment(server, assignment);
            removed++;
        }
        metadata.put(ASSIGNMENTS_KEY, retained);
        return removed;
    }

    private static void clearPhysicalAssignment(MinecraftServer server, Assignment assignment) {
        ServerLevel level = assignment == null
                ? null
                : levelForDimension(server, assignment.dimension());
        if (level == null) {
            return;
        }
        BlockPos pos = assignment.pos();
        LevelChunk chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk.getBlockEntity(pos) instanceof SafetyDepositBoxRowBlockEntity row) {
            row.clearDoorAssignment(assignment.doorIndex());
        }
    }

    private static Assignment readAssignment(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("accountId") || !tag.hasUUID("bankId")) {
            return null;
        }
        int doorIndex = tag.getInt("doorIndex");
        if (doorIndex < 0 || doorIndex >= SafetyDepositBoxRowBlockEntity.DOOR_COUNT) {
            return null;
        }
        return new Assignment(
                tag.getUUID("bankId"),
                tag.getUUID("accountId"),
                tag.getString("dimension"),
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                doorIndex,
                tag.getString("boxNumber"),
                tag.getBoolean("locked")
        );
    }

    private static String nextBoxNumber(CompoundTag metadata) {
        int next = Math.max(1, metadata.getInt(NEXT_NUMBER_KEY));
        metadata.putInt(NEXT_NUMBER_KEY, next + 1);
        return "SDB-" + String.format(Locale.ROOT, "%04d", next);
    }

    private static ServerLevel levelForDimension(MinecraftServer server, String dimension) {
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

    private static SafeVaultReadinessResolver.LoadedWorldFacts loadedWorldFacts(MinecraftServer server,
                                                                                SafeDepositSetupSnapshot snapshot,
                                                                                CompoundTag metadata) {
        if (server == null || snapshot == null || snapshot.premises().isEmpty()) {
            return new SafeVaultReadinessResolver.LoadedWorldFacts(Map.of(), List.of());
        }
        Map<String, Boolean> completeDoors = new java.util.HashMap<>();
        Map<String, BlockPos> resolvedDoorAnchors = new java.util.HashMap<>();
        Set<String> readyViewingRoomPremises = new HashSet<>();
        List<SafeVaultReadinessResolver.LoadedRowSnapshot> rows = new ArrayList<>();
        Set<String> seenRows = new HashSet<>();
        List<SafeBlockBounds> rowBounds = new ArrayList<>();
        for (var premise : snapshot.premises()) {
            if (premise == null) {
                continue;
            }
            for (SafeAreaSnapshot safeArea : premise.safeAreas()) {
                if (safeArea == null || safeArea.bounds() == null) {
                    continue;
                }
                rowBounds.add(safeArea.bounds());
                ServerLevel level = levelForDimension(server, safeArea.bounds().dimension());
                if (level == null) {
                    continue;
                }
                for (var vault : safeArea.vaults()) {
                    if (vault == null || vault.id().isBlank()) {
                        continue;
                    }
                    BlockPos doorAnchor = resolveVaultDoorAnchor(level, premise, safeArea, vault);
                    completeDoors.put(vault.id(), doorAnchor != null);
                    if (doorAnchor != null) {
                        resolvedDoorAnchors.put(vault.id(), doorAnchor);
                    }
                }
            }
        }
        List<LoadedSafeStructureIndex.Entry> indexedRows = LoadedSafeStructureIndex.findInBounds(
                server,
                LoadedSafeStructureIndex.Kind.ROW,
                rowBounds,
                entry -> loadedSafeRowAt(server, entry) != null
        );
        for (LoadedSafeStructureIndex.Entry entry : indexedRows) {
            SafetyDepositBoxRowBlockEntity row = loadedSafeRowAt(server, entry);
            if (row == null) {
                continue;
            }
            BlockPos pos = entry.blockPos();
            String key = entry.dimension() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
            if (seenRows.add(key)) {
                rows.add(new SafeVaultReadinessResolver.LoadedRowSnapshot(
                        entry.dimension(),
                        pos,
                        row.getModuleTypesSnapshot()
                ));
            }
        }
        UUID bankId = setupBankId(snapshot).orElse(null);
        CentralBank centralBank = server == null ? null : BankManager.getCentralBank(server);
        if (bankId != null && centralBank != null) {
            ViewingRoomService.states(server, centralBank, bankId,
                            SafeBoxViewingCoordinator.activeRoomIds(server)).stream()
                    .filter(state -> state.ready()
                            || state.status() == net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomStatus.OCCUPIED)
                    .map(state -> state.room().premiseId())
                    .forEach(readyViewingRoomPremises::add);
        }
        return new SafeVaultReadinessResolver.LoadedWorldFacts(
                completeDoors, resolvedDoorAnchors, rows, readyViewingRoomPremises);
    }

    private static BlockPos resolveVaultDoorAnchor(ServerLevel level,
                                                   SafePremiseSnapshot premise,
                                                   SafeAreaSnapshot safeArea,
                                                   SafeVaultSnapshot vault) {
        if (level == null || premise == null || premise.bounds() == null
                || safeArea == null || safeArea.bounds() == null || vault == null) {
            return null;
        }
        if (vault.vaultDoorX().isPresent() && vault.vaultDoorY().isPresent() && vault.vaultDoorZ().isPresent()) {
            BlockPos persisted = new BlockPos(
                    vault.vaultDoorX().getAsInt(),
                    vault.vaultDoorY().getAsInt(),
                    vault.vaultDoorZ().getAsInt()
            );
            BlockPos resolved = completeDoorMasterWithin(level, premise, safeArea, persisted);
            if (resolved != null) {
                return resolved;
            }
        }
        reconcileLoadedVaultDoorIndex(level, premise, safeArea);
        Set<BlockPos> containedMasters = new LinkedHashSet<>();
        List<LoadedSafeStructureIndex.Entry> indexedDoors = LoadedSafeStructureIndex.findInBounds(
                level.getServer(),
                LoadedSafeStructureIndex.Kind.VAULT_DOOR_MASTER,
                List.of(premise.bounds()),
                entry -> loadedVaultDoorMasterAt(level, entry.blockPos()),
                entry -> completeDoorMasterWithin(level, premise, safeArea, entry.blockPos()) != null
        );
        for (LoadedSafeStructureIndex.Entry entry : indexedDoors) {
            BlockPos resolved = completeDoorMasterWithin(level, premise, safeArea, entry.blockPos());
            if (resolved != null) {
                containedMasters.add(resolved);
            }
        }
        return SafeVaultDoorSelection.select(Optional.empty(), containedMasters).orElse(null);
    }

    static void reconcileLoadedVaultDoorIndex(ServerLevel level,
                                              SafePremiseSnapshot premise,
                                              SafeAreaSnapshot safeArea) {
        if (level == null || premise == null || premise.bounds() == null
                || safeArea == null || safeArea.bounds() == null) {
            return;
        }
        SafeBlockBounds premiseBounds = premise.bounds();
        SafeBlockBounds safeBounds = safeArea.bounds();
        int minX = Math.max(premiseBounds.minX(), safeBounds.minX() - 4);
        int maxX = Math.min(premiseBounds.maxX(), safeBounds.maxX() + 4);
        int minZ = Math.max(premiseBounds.minZ(), safeBounds.minZ() - 4);
        int maxZ = Math.min(premiseBounds.maxZ(), safeBounds.maxZ() + 4);
        if (minX > maxX || minZ > maxZ) {
            return;
        }
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof BankVaultDoorBlockEntity)) {
                        continue;
                    }
                    BlockPos master = blockEntity.getBlockPos();
                    if (premiseBounds.contains(level.dimension().location().toString(),
                            master.getX(), master.getY(), master.getZ())) {
                        LoadedSafeStructureIndex.register(
                                level, master, LoadedSafeStructureIndex.Kind.VAULT_DOOR_MASTER);
                    }
                }
            }
        }
    }

    private static boolean loadedVaultDoorMasterAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        BlockState state = loadedBlockState(level, pos);
        return state != null
                && state.is(ModBlocks.BANK_VAULT_DOOR.get())
                && pos.equals(BankVaultDoorBlock.getMasterPos(state, pos))
                && loadedBlockEntity(level, pos) instanceof BankVaultDoorBlockEntity;
    }

    static BlockPos completeDoorMasterWithin(ServerLevel level,
                                             SafePremiseSnapshot premise,
                                             SafeAreaSnapshot safeArea,
                                             BlockPos pos) {
        if (level == null || premise == null || premise.bounds() == null
                || safeArea == null || safeArea.bounds() == null || pos == null) {
            return null;
        }
        BlockState state = loadedBlockState(level, pos);
        if (state == null || !state.is(ModBlocks.BANK_VAULT_DOOR.get())) {
            return null;
        }
        BlockPos master = BankVaultDoorBlock.getMasterPos(state, pos);
        BlockState masterState = loadedBlockState(level, master);
        if (masterState == null || !masterState.is(ModBlocks.BANK_VAULT_DOOR.get())) {
            return null;
        }
        if (!(loadedBlockEntity(level, master) instanceof BankVaultDoorBlockEntity)) {
            return null;
        }
        boolean intersectsSafeArea = false;
        for (BlockPos part : BankVaultDoorBlock.multiblockPartPositions(level, master)) {
            if (!premise.bounds().contains(premise.bounds().dimension(), part.getX(), part.getY(), part.getZ())) {
                return null;
            }
            if (loadedBlockState(level, part) == null) {
                return null;
            }
            intersectsSafeArea |= safeArea.bounds().contains(
                    safeArea.bounds().dimension(), part.getX(), part.getY(), part.getZ());
        }
        if (!intersectsSafeArea) {
            return null;
        }
        if (!BankVaultDoorBlock.isCompleteMultiblock(level, master)) {
            return null;
        }
        return master.immutable();
    }

    public static List<LoadedSafeRow> collectLoadedSafeRows(MinecraftServer server, ListTag areas) {
        if (server == null || areas == null || areas.isEmpty()) {
            return List.of();
        }
        List<LoadedSafeRow> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<LoadedSafeStructureIndex.Entry> indexedRows = LoadedSafeStructureIndex.findInBounds(
                server,
                LoadedSafeStructureIndex.Kind.ROW,
                boundsFromAreas(areas, SAFE_ROW_SCAN_MARGIN_BLOCKS),
                entry -> loadedSafeRowAt(server, entry) != null
        );
        for (LoadedSafeStructureIndex.Entry entry : indexedRows) {
            SafetyDepositBoxRowBlockEntity row = loadedSafeRowAt(server, entry);
            if (row == null) {
                continue;
            }
            BlockPos pos = entry.blockPos();
            String key = entry.dimension() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
            if (!seen.add(key)) {
                continue;
            }
            rows.add(new LoadedSafeRow(entry.dimension(), pos, row, contains(areas, entry.dimension(), pos)));
        }
        return rows;
    }

    public static boolean containsSafeRowScanBounds(ListTag areas, String dimension, BlockPos pos) {
        if (areas == null || areas.isEmpty() || pos == null) {
            return false;
        }
        for (Tag raw : areas) {
            if (raw instanceof CompoundTag area && contains(area, dimension, pos, SAFE_ROW_SCAN_MARGIN_BLOCKS)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(CompoundTag area, String dimension, BlockPos pos) {
        return contains(area, dimension, pos, 0);
    }

    private static boolean contains(ListTag areas, String dimension, BlockPos pos) {
        if (areas == null || pos == null) {
            return false;
        }
        for (Tag raw : areas) {
            if (raw instanceof CompoundTag area && contains(area, dimension, pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlapsAnyClaimedSafeArea(CentralBank centralBank,
                                                      String dimension,
                                                      int minX,
                                                      int minY,
                                                      int minZ,
                                                      int maxX,
                                                      int maxY,
                                                      int maxZ) {
        if (centralBank == null) {
            return false;
        }
        String normalizedDimension = normalizedDimension(dimension);
        for (UUID bankId : centralBank.getBankMetadata().keySet()) {
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
            ListTag areas = metadata.getList(AREAS_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < areas.size(); i++) {
                CompoundTag existing = areas.getCompound(i);
                if (!normalizedDimension(existing.getString("dimension")).equals(normalizedDimension)) {
                    continue;
                }
                if (regionsOverlap(
                        minX, minY, minZ, maxX, maxY, maxZ,
                        existing.getInt("minX"), existing.getInt("minY"), existing.getInt("minZ"),
                        existing.getInt("maxX"), existing.getInt("maxY"), existing.getInt("maxZ")
                )) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean contains(CompoundTag area, String dimension, BlockPos pos, int margin) {
        return area != null
                && normalizedDimension(dimension).equals(normalizedDimension(area.getString("dimension")))
                && pos.getX() >= Math.min(area.getInt("minX"), area.getInt("maxX")) - Math.max(0, margin)
                && pos.getX() <= Math.max(area.getInt("minX"), area.getInt("maxX")) + Math.max(0, margin)
                && pos.getY() >= Math.min(area.getInt("minY"), area.getInt("maxY")) - Math.max(0, margin)
                && pos.getY() <= Math.max(area.getInt("minY"), area.getInt("maxY")) + Math.max(0, margin)
                && pos.getZ() >= Math.min(area.getInt("minZ"), area.getInt("maxZ")) - Math.max(0, margin)
                && pos.getZ() <= Math.max(area.getInt("minZ"), area.getInt("maxZ")) + Math.max(0, margin);
    }

    private static List<SafeBlockBounds> boundsFromAreas(ListTag areas, int margin) {
        if (areas == null || areas.isEmpty()) {
            return List.of();
        }
        int cleanMargin = Math.max(0, margin);
        List<SafeBlockBounds> bounds = new ArrayList<>();
        for (Tag raw : areas) {
            if (!(raw instanceof CompoundTag area)) {
                continue;
            }
            int minX = Math.min(area.getInt("minX"), area.getInt("maxX")) - cleanMargin;
            int maxX = Math.max(area.getInt("minX"), area.getInt("maxX")) + cleanMargin;
            int minY = Math.min(area.getInt("minY"), area.getInt("maxY")) - cleanMargin;
            int maxY = Math.max(area.getInt("minY"), area.getInt("maxY")) + cleanMargin;
            int minZ = Math.min(area.getInt("minZ"), area.getInt("maxZ")) - cleanMargin;
            int maxZ = Math.max(area.getInt("minZ"), area.getInt("maxZ")) + cleanMargin;
            bounds.add(new SafeBlockBounds(area.getString("dimension"), minX, minY, minZ, maxX, maxY, maxZ));
        }
        return bounds;
    }

    private static SafetyDepositBoxRowBlockEntity loadedSafeRowAt(MinecraftServer server,
                                                                  LoadedSafeStructureIndex.Entry entry) {
        if (server == null || entry == null) {
            return null;
        }
        ServerLevel level = levelForDimension(server, entry.dimension());
        if (level == null) {
            return null;
        }
        BlockState state = loadedBlockState(level, entry.blockPos());
        if (state == null || !state.is(ModBlocks.SAFETY_DEPOSIT_BOX_ROW.get())) {
            return null;
        }
        BlockEntity blockEntity = loadedBlockEntity(level, entry.blockPos());
        return blockEntity instanceof SafetyDepositBoxRowBlockEntity row ? row : null;
    }

    private static BlockState loadedBlockState(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loadedChunkAt(level, pos);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    private static BlockEntity loadedBlockEntity(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loadedChunkAt(level, pos);
        return chunk == null ? null : chunk.getBlockEntities().get(pos);
    }

    private static LevelChunk loadedChunkAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static int safeBoxRowCapacity(Bank bank) {
        if (bank == null) {
            return 0;
        }
        MinecraftServer server = bank.getBankAccounts() == null ? null : net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        CentralBank centralBank = server == null || BankManager.isDirtySuspended() ? null : BankManager.getCentralBank(server);
        return centralBank == null
                ? BankLevelService.safeRowCapacityForLevel(1)
                : BankLevelService.safeRowCapacity(centralBank, bank);
    }

    public static int countClaimedRows(CentralBank centralBank, UUID bankId) {
        if (centralBank == null || bankId == null) {
            return 0;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        ListTag areas = metadata.getList(AREAS_KEY, Tag.TAG_COMPOUND);
        int claimed = 0;
        for (int i = 0; i < areas.size(); i++) {
            claimed += claimedRowUnits(areas.getCompound(i));
        }
        return claimed;
    }

    public static int countLoadedSafeRowBlocks(MinecraftServer server, CentralBank centralBank, UUID bankId) {
        if (centralBank == null || bankId == null) {
            return 0;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        return countLoadedSafeRowBlocks(server, metadata.getList(AREAS_KEY, Tag.TAG_COMPOUND));
    }

    public static int countLoadedSafeRowBlocks(MinecraftServer server, ListTag areas) {
        if (server == null || areas == null || areas.isEmpty()) {
            return 0;
        }
        return LoadedSafeStructureIndex.findInBounds(
                server,
                LoadedSafeStructureIndex.Kind.ROW,
                boundsFromAreas(areas, 0),
                entry -> loadedSafeRowAt(server, entry) != null
        ).size();
    }

    public static int claimedRowUnits(CompoundTag area) {
        if (area == null) {
            return 0;
        }
        return Math.max(1, blockVolume(
                new BlockPos(area.getInt("minX"), area.getInt("minY"), area.getInt("minZ")),
                new BlockPos(area.getInt("maxX"), area.getInt("maxY"), area.getInt("maxZ"))
        ) / 8);
    }

    private static int blockVolume(BlockPos min, BlockPos max) {
        int width = Math.abs(max.getX() - min.getX()) + 1;
        int height = Math.abs(max.getY() - min.getY()) + 1;
        int depth = Math.abs(max.getZ() - min.getZ()) + 1;
        return Math.max(1, width * height * depth);
    }

    private static ListTag withAdditionalArea(ListTag areas, CompoundTag area) {
        ListTag out = new ListTag();
        if (areas != null) {
            for (Tag tag : areas) {
                if (tag instanceof CompoundTag existing) {
                    out.add(existing.copy());
                }
            }
        }
        if (area != null) {
            out.add(area.copy());
        }
        return out;
    }

    private static SafeClaimToolSession copySafeClaimSession(ServerPlayer player,
                                                             SafeClaimToolSession session,
                                                             boolean addMode,
                                                             boolean overlayEnabled,
                                                             String firstDimensionId,
                                                             BlockPos firstCorner,
                                                             BlockPos secondCorner) {
        long now = player == null || player.level() == null ? session.lastUpdatedTick() : player.serverLevel().getGameTime();
        return new SafeClaimToolSession(
                session.playerId(),
                session.bankId(),
                addMode,
                overlayEnabled,
                session.startedTick(),
                now,
                firstDimensionId == null ? "" : firstDimensionId,
                firstCorner == null ? null : firstCorner.immutable(),
                secondCorner == null ? null : secondCorner.immutable(),
                session.hotbarSnapshot(),
                session.selectedHotbarSlot(),
                session.lifecycle(),
                session.purpose(),
                session.premiseId(),
                session.adminAuthorized()
        );
    }

    private static List<ItemStack> snapshotHotbar(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            out.add(player.getInventory().getItem(i).copy());
        }
        return out;
    }

    private static void restoreHotbar(ServerPlayer player, List<ItemStack> snapshot, int selectedSlot) {
        if (player == null || snapshot == null || snapshot.size() < 9) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, snapshot.get(i).copy());
        }
        player.getInventory().selected = Math.max(0, Math.min(8, selectedSlot));
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    @SuppressWarnings("unchecked")
    private static void restoreSafeClaimSnapshot(ServerPlayer player, Object snapshotToken, int selectedSlot) {
        purgeSafeClaimToolItemsOutsideHotbar(player);
        if (snapshotToken instanceof List<?> snapshot) {
            restoreHotbar(player, (List<ItemStack>) snapshot, selectedSlot);
        }
    }

    private static void purgeSafeClaimToolItemsOutsideHotbar(ServerPlayer player) {
        if (player == null) {
            return;
        }
        boolean changed = false;
        int size = player.getInventory().getContainerSize();
        for (int slot = 9; slot < size; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isSafeAreaClaimToolStack(stack)) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    private static void installSafeClaimToolHotbar(ServerPlayer player,
                                                   SafeClaimToolPurpose purpose) {
        ItemStack wand = claimToolItem(Items.STICK, "Premise Corner Wand", "wand", ChatFormatting.GOLD);
        ItemStack add = claimToolItem(Items.LIME_CONCRETE, "Add Safe Area", SAFE_CLAIM_TOOL_ADD,
                ChatFormatting.GREEN);
        ItemStack remove = claimToolItem(Items.RED_CONCRETE, "Remove Safe Area", SAFE_CLAIM_TOOL_REMOVE,
                ChatFormatting.RED);
        ItemStack exit = claimToolItem(Items.COMPASS, "Capture Outside Exit", SAFE_CLAIM_TOOL_EXIT,
                ChatFormatting.AQUA);
        ItemStack apply = claimToolItem(Items.PAPER, "Apply Selection", SAFE_CLAIM_TOOL_APPLY,
                ChatFormatting.YELLOW);
        ItemStack clear = claimToolItem(Items.SPONGE, "Clear Selection", SAFE_CLAIM_TOOL_CLEAR,
                ChatFormatting.GOLD);
        ItemStack overlay = claimToolItem(Items.ENDER_EYE, "Toggle Safe Overlay", SAFE_CLAIM_TOOL_OVERLAY,
                ChatFormatting.AQUA);
        ItemStack locked = claimToolItem(Items.GRAY_STAINED_GLASS_PANE, "Safe Claim Slot (Locked)",
                SAFE_CLAIM_TOOL_LOCK, ChatFormatting.DARK_GRAY);
        ItemStack finish = claimToolItem(Items.BARRIER, "Cancel and Restore Hotbar",
                SAFE_CLAIM_TOOL_FINISH, ChatFormatting.YELLOW);

        if (purpose.isViewingRoomAnchor()) {
            String label = switch (purpose) {
                case VIEWING_ROOM_CUSTOMER_ANCHOR -> "Capture Customer Anchor";
                case VIEWING_ROOM_TELLER_ANCHOR -> "Capture Teller Anchor";
                case VIEWING_ROOM_DISPLAY_ANCHOR -> "Capture Deposit Box Anchor";
                default -> "Capture Anchor";
            };
            player.getInventory().setItem(0, claimToolItem(Items.COMPASS, label,
                    SAFE_CLAIM_TOOL_EXIT, ChatFormatting.AQUA));
            for (int slot = 1; slot < 8; slot++) {
                player.getInventory().setItem(slot, locked.copy());
            }
        } else if (purpose == SafeClaimToolPurpose.PREMISE_EXIT_EDIT) {
            player.getInventory().setItem(0, exit.copy());
            player.getInventory().setItem(1, apply.copy());
            for (int slot = 2; slot < 8; slot++) {
                player.getInventory().setItem(slot, locked.copy());
            }
        } else if (purpose == SafeClaimToolPurpose.PREMISE_CREATE) {
            player.getInventory().setItem(0, wand.copy());
            player.getInventory().setItem(1, exit.copy());
            player.getInventory().setItem(2, apply.copy());
            player.getInventory().setItem(3, clear.copy());
            player.getInventory().setItem(4, overlay.copy());
            player.getInventory().setItem(5, locked.copy());
            player.getInventory().setItem(6, locked.copy());
            player.getInventory().setItem(7, locked.copy());
        } else if (purpose == SafeClaimToolPurpose.VIEWING_ROOM_CREATE) {
            player.getInventory().setItem(0, wand.copy());
            player.getInventory().setItem(1, apply.copy());
            player.getInventory().setItem(2, clear.copy());
            player.getInventory().setItem(3, overlay.copy());
            for (int slot = 4; slot < 8; slot++) {
                player.getInventory().setItem(slot, locked.copy());
            }
        } else {
            ItemStack safeWand = claimToolItem(Items.STICK, "Safe Area Claim Wand", "wand",
                    ChatFormatting.GOLD);
            player.getInventory().setItem(0, safeWand);
            player.getInventory().setItem(1, add.copy());
            player.getInventory().setItem(2, remove.copy());
            player.getInventory().setItem(3, apply.copy());
            player.getInventory().setItem(4, clear.copy());
            player.getInventory().setItem(5, overlay.copy());
            player.getInventory().setItem(6, locked.copy());
            player.getInventory().setItem(7, locked.copy());
        }
        player.getInventory().setItem(8, finish.copy());
        player.getInventory().selected = 0;
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static ItemStack claimToolItem(net.minecraft.world.item.Item item,
                                           String name,
                                           String marker,
                                           ChatFormatting color) {
        ItemStack stack = new ItemStack(item);
        ItemStackDataCompat.setCustomName(stack, UbsTranslations.literal(name).withStyle(color));
        ItemStackDataCompat.putCustomString(SAFE_CLAIM_TOOL_ITEM_TAG, stack, marker);
        return stack;
    }

    private static void renderSafeAreaClaimOverlay(MinecraftServer server, ServerPlayer player, SafeClaimToolSession session) {
        if (server == null || player == null || session == null) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(session.bankId());
        String playerDim = normalizedDimension(player.serverLevel().dimension().location().toString());
        int[] budget = new int[]{220};
        renderSafeRegionsForPlayer(player, metadata.getList(AREAS_KEY, Tag.TAG_COMPOUND), playerDim, ParticleTypes.HAPPY_VILLAGER, budget);
        if (session.firstCorner() != null && normalizedDimension(session.firstDimensionId()).equals(playerDim)) {
            sendOverlayParticle(
                    player,
                    ParticleTypes.END_ROD,
                    session.firstCorner().getX() + 0.5D,
                    session.firstCorner().getY() + 1.05D,
                    session.firstCorner().getZ() + 0.5D,
                    budget
            );
            if (session.secondCorner() != null) {
                sendOverlayParticle(
                        player,
                        ParticleTypes.GLOW,
                        session.secondCorner().getX() + 0.5D,
                        session.secondCorner().getY() + 1.05D,
                        session.secondCorner().getZ() + 0.5D,
                        budget
                );
                BlockPos first = session.firstCorner();
                BlockPos second = session.secondCorner();
                drawRegionOutlineForPlayer(
                        player,
                        Math.min(first.getX(), second.getX()),
                        Math.min(first.getY(), second.getY()),
                        Math.min(first.getZ(), second.getZ()),
                        Math.max(first.getX(), second.getX()),
                        Math.max(first.getY(), second.getY()),
                        Math.max(first.getZ(), second.getZ()),
                        session.addMode() ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.FLAME,
                        budget
                );
            }
        }
    }

    private static void renderSafeRegionsForPlayer(ServerPlayer player,
                                                   ListTag regions,
                                                   String playerDim,
                                                   ParticleOptions particle,
                                                   int[] budget) {
        if (player == null || regions == null || regions.isEmpty() || budget == null || budget.length == 0 || budget[0] <= 0) {
            return;
        }
        BlockPos viewer = player.blockPosition();
        for (Tag tag : regions) {
            if (!(tag instanceof CompoundTag region)) {
                continue;
            }
            if (!normalizedDimension(region.getString("dimension")).equals(playerDim)) {
                continue;
            }
            int minX = Math.min(region.getInt("minX"), region.getInt("maxX"));
            int minY = Math.min(region.getInt("minY"), region.getInt("maxY"));
            int minZ = Math.min(region.getInt("minZ"), region.getInt("maxZ"));
            int maxX = Math.max(region.getInt("minX"), region.getInt("maxX"));
            int maxY = Math.max(region.getInt("minY"), region.getInt("maxY"));
            int maxZ = Math.max(region.getInt("minZ"), region.getInt("maxZ"));
            if (!regionCloseToPlayer(viewer, minX, minY, minZ, maxX, maxY, maxZ, 96)) {
                continue;
            }
            drawRegionOutlineForPlayer(player, minX, minY, minZ, maxX, maxY, maxZ, particle, budget);
            if (budget[0] <= 0) {
                return;
            }
        }
    }

    private static void sendSafeAreaClaimToolStatusActionBar(ServerPlayer player, SafeClaimToolSession session) {
        if (player == null || session == null) {
            return;
        }
        String mode = session.addMode() ? "Add" : "Remove";
        String pos1 = session.firstCorner() == null ? "Pos1: -" : "Pos1: set";
        String pos2 = session.secondCorner() == null ? "Pos2: -" : "Pos2: set";
        String selectionInfo = "";
        if (session.firstCorner() != null && session.secondCorner() != null) {
            selectionInfo = " | Sel: " + blockVolume(session.firstCorner(), session.secondCorner()) + " blocks";
        }
        player.displayClientMessage(
                UbsTranslations.literal("§bSafe Claim §7| §fMode: " + mode + " §7| §f" + pos1 + " §7| §f" + pos2
                        + selectionInfo + " §7| §ePaper=Apply"),
                true
        );
    }

    private static void drawRegionOutlineForPlayer(ServerPlayer player,
                                                   int minX, int minY, int minZ,
                                                   int maxX, int maxY, int maxZ,
                                                   ParticleOptions particle,
                                                   int[] budget) {
        if (player == null || particle == null || budget == null || budget.length == 0 || budget[0] <= 0) {
            return;
        }
        int xSpan = Math.max(1, maxX - minX + 1);
        int ySpan = Math.max(1, maxY - minY + 1);
        int zSpan = Math.max(1, maxZ - minZ + 1);
        int hStep = Math.max(1, Math.max(xSpan, zSpan) / 24);
        int vStep = Math.max(1, ySpan / 12);
        double topY = maxY + 1.02D;

        for (int x = minX; x <= maxX; x += hStep) {
            if (!sendOverlayParticle(player, particle, x + 0.5D, topY, minZ + 0.5D, budget)) {
                return;
            }
            if (!sendOverlayParticle(player, particle, x + 0.5D, topY, maxZ + 0.5D, budget)) {
                return;
            }
        }
        for (int z = minZ; z <= maxZ; z += hStep) {
            if (!sendOverlayParticle(player, particle, minX + 0.5D, topY, z + 0.5D, budget)) {
                return;
            }
            if (!sendOverlayParticle(player, particle, maxX + 0.5D, topY, z + 0.5D, budget)) {
                return;
            }
        }
        for (int y = minY; y <= maxY; y += vStep) {
            double py = y + 0.08D;
            if (!sendOverlayParticle(player, particle, minX + 0.5D, py, minZ + 0.5D, budget)) {
                return;
            }
            if (!sendOverlayParticle(player, particle, minX + 0.5D, py, maxZ + 0.5D, budget)) {
                return;
            }
            if (!sendOverlayParticle(player, particle, maxX + 0.5D, py, minZ + 0.5D, budget)) {
                return;
            }
            if (!sendOverlayParticle(player, particle, maxX + 0.5D, py, maxZ + 0.5D, budget)) {
                return;
            }
        }
    }

    private static boolean sendOverlayParticle(ServerPlayer player,
                                               ParticleOptions particle,
                                               double x,
                                               double y,
                                               double z,
                                               int[] budget) {
        if (player == null || particle == null || budget == null || budget.length == 0 || budget[0] <= 0) {
            return false;
        }
        player.serverLevel().sendParticles(player, particle, true, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        budget[0]--;
        return budget[0] > 0;
    }

    private static boolean regionCloseToPlayer(BlockPos viewer,
                                               int minX, int minY, int minZ,
                                               int maxX, int maxY, int maxZ,
                                               int margin) {
        if (viewer == null) {
            return false;
        }
        return viewer.getX() >= (minX - margin) && viewer.getX() <= (maxX + margin)
                && viewer.getY() >= (minY - margin) && viewer.getY() <= (maxY + margin)
                && viewer.getZ() >= (minZ - margin) && viewer.getZ() <= (maxZ + margin);
    }

    private static CompoundTag buildSafeAreaTag(String dimension,
                                                int minX,
                                                int minY,
                                                int minZ,
                                                int maxX,
                                                int maxY,
                                                int maxZ) {
        CompoundTag area = new CompoundTag();
        area.putString("dimension", normalizedDimension(dimension));
        area.putInt("minX", Math.min(minX, maxX));
        area.putInt("minY", Math.min(minY, maxY));
        area.putInt("minZ", Math.min(minZ, maxZ));
        area.putInt("maxX", Math.max(minX, maxX));
        area.putInt("maxY", Math.max(minY, maxY));
        area.putInt("maxZ", Math.max(minZ, maxZ));
        area.putLong("claimedAtMillis", System.currentTimeMillis());
        return area;
    }

    private static List<CompoundTag> subtractSafeArea(CompoundTag source,
                                                      int removeMinX,
                                                      int removeMinY,
                                                      int removeMinZ,
                                                      int removeMaxX,
                                                      int removeMaxY,
                                                      int removeMaxZ) {
        List<CompoundTag> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        String dim = normalizedDimension(source.getString("dimension"));
        int ax1 = Math.min(source.getInt("minX"), source.getInt("maxX"));
        int ay1 = Math.min(source.getInt("minY"), source.getInt("maxY"));
        int az1 = Math.min(source.getInt("minZ"), source.getInt("maxZ"));
        int ax2 = Math.max(source.getInt("minX"), source.getInt("maxX"));
        int ay2 = Math.max(source.getInt("minY"), source.getInt("maxY"));
        int az2 = Math.max(source.getInt("minZ"), source.getInt("maxZ"));

        int bx1 = Math.min(removeMinX, removeMaxX);
        int by1 = Math.min(removeMinY, removeMaxY);
        int bz1 = Math.min(removeMinZ, removeMaxZ);
        int bx2 = Math.max(removeMinX, removeMaxX);
        int by2 = Math.max(removeMinY, removeMaxY);
        int bz2 = Math.max(removeMinZ, removeMaxZ);

        if (!regionsOverlap(ax1, ay1, az1, ax2, ay2, az2, bx1, by1, bz1, bx2, by2, bz2)) {
            out.add(buildSafeAreaTag(dim, ax1, ay1, az1, ax2, ay2, az2));
            return out;
        }

        int ox1 = Math.max(ax1, bx1);
        int oy1 = Math.max(ay1, by1);
        int oz1 = Math.max(az1, bz1);
        int ox2 = Math.min(ax2, bx2);
        int oy2 = Math.min(ay2, by2);
        int oz2 = Math.min(az2, bz2);

        addSafeAreaIfValid(out, dim, ax1, ay1, az1, ox1 - 1, ay2, az2);
        addSafeAreaIfValid(out, dim, ox2 + 1, ay1, az1, ax2, ay2, az2);

        int rx1 = Math.max(ax1, ox1);
        int rx2 = Math.min(ax2, ox2);
        addSafeAreaIfValid(out, dim, rx1, ay1, az1, rx2, oy1 - 1, az2);
        addSafeAreaIfValid(out, dim, rx1, oy2 + 1, az1, rx2, ay2, az2);

        int ry1 = Math.max(ay1, oy1);
        int ry2 = Math.min(ay2, oy2);
        addSafeAreaIfValid(out, dim, rx1, ry1, az1, rx2, ry2, oz1 - 1);
        addSafeAreaIfValid(out, dim, rx1, ry1, oz2 + 1, rx2, ry2, az2);

        return out;
    }

    private static void addSafeAreaIfValid(List<CompoundTag> out,
                                           String dimension,
                                           int minX,
                                           int minY,
                                           int minZ,
                                           int maxX,
                                           int maxY,
                                           int maxZ) {
        if (out == null || minX > maxX || minY > maxY || minZ > maxZ) {
            return;
        }
        out.add(buildSafeAreaTag(dimension, minX, minY, minZ, maxX, maxY, maxZ));
    }

    private static boolean regionsOverlap(int aMinX, int aMinY, int aMinZ,
                                          int aMaxX, int aMaxY, int aMaxZ,
                                          int bMinX, int bMinY, int bMinZ,
                                          int bMaxX, int bMaxY, int bMaxZ) {
        int ax1 = Math.min(aMinX, aMaxX);
        int ay1 = Math.min(aMinY, aMaxY);
        int az1 = Math.min(aMinZ, aMaxZ);
        int ax2 = Math.max(aMinX, aMaxX);
        int ay2 = Math.max(aMinY, aMaxY);
        int az2 = Math.max(aMinZ, aMaxZ);

        int bx1 = Math.min(bMinX, bMaxX);
        int by1 = Math.min(bMinY, bMaxY);
        int bz1 = Math.min(bMinZ, bMaxZ);
        int bx2 = Math.max(bMinX, bMaxX);
        int by2 = Math.max(bMinY, bMaxY);
        int bz2 = Math.max(bMinZ, bMaxZ);

        return ax1 <= bx2 && bx1 <= ax2
                && ay1 <= by2 && by1 <= ay2
                && az1 <= bz2 && bz1 <= az2;
    }

    private static String normalizedDimension(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return Level.OVERWORLD.location().toString();
        }
        return dimension.trim().toLowerCase(Locale.ROOT);
    }

    private static List<SafeBoxAvailability> availabilityList(Map<SafetyDepositBoxRowBlockEntity.ModuleType, int[]> counts) {
        List<SafeBoxAvailability> result = new ArrayList<>();
        for (SafetyDepositBoxRowBlockEntity.ModuleType type : assignableModuleTypes()) {
            int[] values = counts == null ? null : counts.get(type);
            int total = values == null ? 0 : Math.max(0, values[0]);
            int assigned = values == null ? 0 : Math.max(0, values[1]);
            int free = values == null ? Math.max(0, total - assigned) : Math.max(0, values[2]);
            result.add(new SafeBoxAvailability(type, total, assigned, free));
        }
        return result;
    }

    private static Set<String> assignmentLocationKeys(ListTag assignments) {
        Set<String> keys = new HashSet<>();
        if (assignments == null) {
            return keys;
        }
        for (int i = 0; i < assignments.size(); i++) {
            Assignment assignment = readAssignment(assignments.getCompound(i));
            if (assignment == null) {
                continue;
            }
            keys.add(assignmentLocationKey(assignment.dimension(), assignment.pos(), assignment.doorIndex()));
        }
        return keys;
    }

    private static String assignmentLocationKey(String dimension, BlockPos pos, int doorIndex) {
        if (pos == null) {
            return normalizedDimension(dimension) + "|0|0|0|" + Math.max(0, doorIndex);
        }
        return normalizedDimension(dimension)
                + "|" + pos.getX()
                + "|" + pos.getY()
                + "|" + pos.getZ()
                + "|" + Math.max(0, doorIndex);
    }

    private static SafetyDepositBoxRowBlockEntity.ModuleType normalizeAssignableType(SafetyDepositBoxRowBlockEntity.ModuleType type) {
        return type != null && type.assignable() ? type : SafetyDepositBoxRowBlockEntity.ModuleType.SMALL;
    }

    public static SafetyDepositBoxRowBlockEntity.ModuleType parseAssignableType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        SafetyDepositBoxRowBlockEntity.ModuleType type = SafetyDepositBoxRowBlockEntity.ModuleType.byName(raw.trim());
        return type.assignable() ? type : null;
    }

    public static String shortModuleLabel(SafetyDepositBoxRowBlockEntity.ModuleType type) {
        SafetyDepositBoxRowBlockEntity.ModuleType cleanType = normalizeAssignableType(type);
        return switch (cleanType) {
            case EXTRA_LARGE -> "Extra Large";
            case LARGE -> "Large";
            case MEDIUM -> "Medium";
            default -> "Small";
        };
    }

    private static String sizedPolicyKey(String baseKey, SafetyDepositBoxRowBlockEntity.ModuleType type) {
        return baseKey + "." + normalizeAssignableType(type).name();
    }

    private static String readSizedString(CompoundTag metadata,
                                          String baseKey,
                                          SafetyDepositBoxRowBlockEntity.ModuleType type,
                                          String fallback) {
        if (metadata == null) {
            return fallback == null ? "" : fallback;
        }
        String sizedKey = sizedPolicyKey(baseKey, type);
        if (metadata.contains(sizedKey)) {
            String value = metadata.getString(sizedKey);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        String value = metadata.getString(baseKey);
        return value == null || value.isBlank() ? (fallback == null ? "" : fallback) : value;
    }

    private static BigDecimal readSizedMoney(CompoundTag metadata,
                                             String baseKey,
                                             SafetyDepositBoxRowBlockEntity.ModuleType type) {
        if (metadata == null) {
            return BigDecimal.ZERO;
        }
        String sizedKey = sizedPolicyKey(baseKey, type);
        BigDecimal amount = metadata.contains(sizedKey)
                ? parseMoney(metadata.getString(sizedKey))
                : parseMoney(metadata.getString(baseKey));
        return amount.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : amount;
    }

    private static long readSizedPositiveLong(CompoundTag metadata,
                                              String baseKey,
                                              SafetyDepositBoxRowBlockEntity.ModuleType type,
                                              long fallback) {
        if (metadata == null) {
            return fallback;
        }
        String sizedKey = sizedPolicyKey(baseKey, type);
        if (metadata.contains(sizedKey)) {
            return Math.max(1L, metadata.getLong(sizedKey));
        }
        if (metadata.contains(baseKey)) {
            return Math.max(1L, metadata.getLong(baseKey));
        }
        return fallback;
    }

    private static String normalizePolicyMode(String raw) {
        return raw == null || raw.isBlank() ? "FREE" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static long parsePositiveLong(String raw, long fallback) {
        try {
            return Math.max(1L, Long.parseLong(raw == null ? "" : raw.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_EVEN);
        } catch (NumberFormatException ignored) {
            return BigDecimal.valueOf(-1);
        }
    }

    private static String valueForUuid(String encoded, UUID playerId) {
        if (encoded == null || encoded.isBlank() || playerId == null) {
            return "";
        }
        String prefix = playerId + "=";
        for (String entry : encoded.split(";")) {
            String raw = entry.trim();
            if (raw.startsWith(prefix)) {
                return raw.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static String employeeRoleForUuid(String encoded, UUID playerId) {
        String value = valueForUuid(encoded, playerId);
        int split = value.indexOf(':');
        return split < 0 ? value : value.substring(0, split);
    }

    private static boolean uuidListContains(String encoded, UUID playerId) {
        if (encoded == null || encoded.isBlank() || playerId == null) {
            return false;
        }
        for (String entry : encoded.split(",")) {
            if (playerId.toString().equals(entry.trim())) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal shareForUuid(String encoded, UUID playerId) {
        String value = valueForUuid(encoded, playerId);
        if (value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static String shortId(UUID id) {
        return id == null ? "unknown" : id.toString().substring(0, 8);
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult ok(String message) {
            return new ActionResult(true, message == null ? "" : message);
        }

        public static ActionResult fail(String message) {
            return new ActionResult(false, message == null ? "" : message);
        }
    }

    public record ClaimToolContext(UUID bankId,
                                   SafeClaimToolPurpose purpose,
                                   String premiseId) {
        public ClaimToolContext {
            premiseId = premiseId == null ? "" : premiseId;
        }
    }

    public record ClaimToolApplyResult(ActionResult result,
                                       ClaimToolContext context,
                                       OwnerPcPremiseService.Result premiseResult) {
    }

    private record Assignment(UUID bankId,
                              UUID accountId,
                              String dimension,
                              BlockPos pos,
                              int doorIndex,
                              String boxNumber,
                              boolean locked) {
        private boolean matches(Assignment other) {
            return other != null
                    && bankId.equals(other.bankId)
                    && accountId.equals(other.accountId)
                    && dimension.equals(other.dimension)
                    && pos.equals(other.pos)
                    && doorIndex == other.doorIndex;
        }
    }

    private record OpenBoxContext(AccountHolder account,
                                  int doorIndex,
                                  String displayBoxNumber,
                                  int slotCount) {
    }

    private record OpenAuthorityResult(Assignment assignment,
                                       AccountHolder account,
                                       SafetyDepositBoxOpenAuthority.Decision decision) {
    }

    private record SafeAreaClaimMatch(UUID bankId, boolean ambiguous) {
        private static SafeAreaClaimMatch unclaimed() {
            return new SafeAreaClaimMatch(null, false);
        }

        private static SafeAreaClaimMatch single(UUID bankId) {
            return new SafeAreaClaimMatch(bankId, false);
        }

        private static SafeAreaClaimMatch ambiguousMatch() {
            return new SafeAreaClaimMatch(null, true);
        }

        private boolean claimed() {
            return bankId != null || ambiguous;
        }

        private boolean single() {
            return bankId != null && !ambiguous;
        }
    }

    private record PendingMenuOpen(UUID playerId,
                                   UUID accountId,
                                   String dimension,
                                   BlockPos rowPos,
                                   int doorIndex,
                                   String displayBoxNumber,
                                   long openAtGameTime,
                                   long expiresAtGameTime) {
    }

    private record SafeClaimToolSession(UUID playerId,
                                        UUID bankId,
                                        boolean addMode,
                                        boolean overlayEnabled,
                                        long startedTick,
                                        long lastUpdatedTick,
                                        String firstDimensionId,
                                        BlockPos firstCorner,
                                        BlockPos secondCorner,
                                        List<ItemStack> hotbarSnapshot,
                                        int selectedHotbarSlot,
                                        SafeClaimToolLifecycle lifecycle,
                                        SafeClaimToolPurpose purpose,
                                        String premiseId,
                                        boolean adminAuthorized) {
    }
}
