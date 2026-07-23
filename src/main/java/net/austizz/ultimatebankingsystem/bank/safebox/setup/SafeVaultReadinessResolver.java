package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePairResolver;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SafeVaultReadinessResolver {
    private SafeVaultReadinessResolver() {
    }

    public record LoadedRowSnapshot(String dimension,
                                    BlockPos pos,
                                    SafetyDepositBoxRowBlockEntity.ModuleType[] moduleTypes) {
        public LoadedRowSnapshot {
            dimension = SafeBlockBounds.normalizeDimension(dimension);
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
            moduleTypes = moduleTypes == null ? new SafetyDepositBoxRowBlockEntity.ModuleType[0] : moduleTypes.clone();
        }
    }

    public record LoadedWorldFacts(Map<String, Boolean> completeVaultDoors,
                                   Map<String, BlockPos> resolvedDoorAnchors,
                                   List<LoadedRowSnapshot> loadedRows,
                                   Set<String> readyViewingRoomPremiseIds) {
        public LoadedWorldFacts(Map<String, Boolean> completeVaultDoors,
                                List<LoadedRowSnapshot> loadedRows) {
            this(completeVaultDoors, Map.of(), loadedRows, Set.of());
        }

        public LoadedWorldFacts(Map<String, Boolean> completeVaultDoors,
                                Map<String, BlockPos> resolvedDoorAnchors,
                                List<LoadedRowSnapshot> loadedRows) {
            this(completeVaultDoors, resolvedDoorAnchors, loadedRows, Set.of());
        }

        public LoadedWorldFacts {
            completeVaultDoors = completeVaultDoors == null ? Map.of() : Map.copyOf(completeVaultDoors);
            resolvedDoorAnchors = resolvedDoorAnchors == null ? Map.of() : Map.copyOf(resolvedDoorAnchors);
            loadedRows = loadedRows == null ? List.of() : List.copyOf(loadedRows);
            readyViewingRoomPremiseIds = readyViewingRoomPremiseIds == null
                    ? Set.of() : Set.copyOf(readyViewingRoomPremiseIds);
        }

        public boolean completeDoor(String vaultId) {
            return Boolean.TRUE.equals(completeVaultDoors.get(vaultId == null ? "" : vaultId));
        }

        public Optional<BlockPos> resolvedDoorPos(String vaultId) {
            return Optional.ofNullable(resolvedDoorAnchors.get(vaultId == null ? "" : vaultId));
        }

        public boolean hasReadyViewingRoom(String premiseId) {
            return premiseId != null && readyViewingRoomPremiseIds.contains(premiseId);
        }
    }

    public record RowReadiness(boolean mapped,
                               SafePremiseSnapshot premise,
                               SafeAreaSnapshot safeArea,
                               SafeVaultSnapshot vault,
                               SafeVaultReadinessSummary summary,
                               List<String> humanMissingReasons) {
        public RowReadiness {
            humanMissingReasons = humanMissingReasons == null ? List.of() : List.copyOf(humanMissingReasons);
        }
    }

    public record RowLocation(String dimension, BlockPos position) {
        public RowLocation {
            dimension = SafeBlockBounds.normalizeDimension(dimension);
            position = position == null ? null : position.immutable();
        }
    }

    public record EvaluationContext(SafeTellerRoutePairResolver.Context routes, LoadedWorldFacts facts) {
        public EvaluationContext(CompoundTag metadata, LoadedWorldFacts facts) {
            this(new SafeTellerRoutePairResolver.Context(metadata), facts);
        }

        public EvaluationContext {
            facts = facts == null ? new LoadedWorldFacts(Map.of(), List.of()) : facts;
        }
    }

    public record VaultSelection(SafePremiseSnapshot premise,
                                 SafeAreaSnapshot safeArea,
                                 SafeVaultSnapshot vault) {
    }

    public record RowRequest(EvaluationContext context,
                             SafeDepositSetupSnapshot snapshot,
                             RowLocation location) {
    }

    public record VaultRequest(EvaluationContext context, VaultSelection selection) {
    }

    public static RowReadiness resolveForRow(RowRequest request) {
        if (request == null || request.snapshot() == null || request.location() == null
                || request.location().position() == null) {
            SafeVaultReadinessSummary summary = new SafeVaultReadinessSummary("",
                    false,
                    List.of(SafeReadinessMissingReason.PREMISE_MISSING, SafeReadinessMissingReason.VAULT_MISSING));
            return new RowReadiness(false, null, null, null, summary, humanReasons(summary.missingReasons()));
        }

        String normalizedDimension = request.location().dimension();
        BlockPos rowPos = request.location().position();
        List<Candidate> candidates = new ArrayList<>();
        for (SafePremiseSnapshot premise : request.snapshot().premises()) {
            if (premise == null || premise.bounds() == null || !premise.bounds().contains(normalizedDimension,
                    rowPos.getX(), rowPos.getY(), rowPos.getZ())) {
                continue;
            }
            for (SafeAreaSnapshot safeArea : premise.safeAreas()) {
                if (safeArea == null || safeArea.bounds() == null || !safeArea.bounds().contains(normalizedDimension,
                        rowPos.getX(), rowPos.getY(), rowPos.getZ())) {
                    continue;
                }
                if (safeArea.vaults().size() != 1 || safeArea.vaults().get(0) == null) {
                    continue;
                }
                candidates.add(new Candidate(premise, safeArea, safeArea.vaults().get(0)));
            }
        }

        if (candidates.size() != 1) {
            SafeVaultReadinessSummary summary = new SafeVaultReadinessSummary("",
                    false,
                    List.of(SafeReadinessMissingReason.VAULT_MISSING));
            List<String> humanReasons = new ArrayList<>(humanReasons(summary.missingReasons()));
            if (candidates.size() > 1) {
                humanReasons.add("Safe row is claimed by multiple vault definitions.");
            } else {
                humanReasons.add("Safe area must contain exactly one vault.");
            }
            return new RowReadiness(false, null, null, null, summary, humanReasons);
        }

        Candidate candidate = candidates.get(0);
        return resolveVault(new VaultRequest(request.context(),
                new VaultSelection(candidate.premise(), candidate.safeArea(), candidate.vault())));
    }

    public static RowReadiness resolveVault(VaultRequest request) {
        VaultSelection selection = request == null ? null : request.selection();
        SafePremiseSnapshot premise = selection == null ? null : selection.premise();
        SafeAreaSnapshot safeArea = selection == null ? null : selection.safeArea();
        SafeVaultSnapshot vault = selection == null ? null : selection.vault();
        if (premise == null || safeArea == null || safeArea.bounds() == null || vault == null) {
            SafeVaultReadinessSummary summary = new SafeVaultReadinessSummary("", false,
                    List.of(SafeReadinessMissingReason.VAULT_MISSING));
            return new RowReadiness(false, premise, safeArea, vault, summary, humanReasons(summary.missingReasons()));
        }
        EvaluationContext context = request.context();
        LoadedWorldFacts safeFacts = context == null
                ? new LoadedWorldFacts(Map.of(), List.of()) : context.facts();
        boolean assignableRowExists = safeFacts.loadedRows().stream()
                .filter(row -> row != null && row.pos() != null)
                .filter(row -> safeArea.bounds().contains(row.dimension(),
                        row.pos().getX(), row.pos().getY(), row.pos().getZ()))
                .anyMatch(row -> SafetyDepositBoxRowBlockEntity.isFullyAssignableRow(row.moduleTypes()));

        List<SafeReadinessMissingReason> missing = new ArrayList<>();
        if (premise.exit() == null) {
            missing.add(SafeReadinessMissingReason.PREMISE_EXIT_INVALID);
        }
        if (!safeFacts.completeDoor(vault.id())) {
            missing.add(SafeReadinessMissingReason.VAULT_DOOR_MISSING);
        }
        if (!assignableRowExists) {
            missing.add(SafeReadinessMissingReason.ASSIGNABLE_ROW_MISSING);
        }
        if (!safeFacts.hasReadyViewingRoom(premise.id())) {
            missing.add(SafeReadinessMissingReason.VIEWING_ROOM_MISSING);
        }

        SafeVaultReadinessSummary summary = new SafeVaultReadinessSummary(
                vault.id(),
                missing.isEmpty(),
                missing
        );
        return new RowReadiness(true, premise, safeArea, vault,
                summary, humanReasons(missing));
    }

    public static RowReadiness withEligibleSafeAccessStaff(RowReadiness readiness,
                                                           boolean eligibleSafeAccessStaff) {
        if (readiness == null || readiness.summary() == null) {
            return readiness;
        }
        List<SafeReadinessMissingReason> missing = new ArrayList<>(readiness.summary().missingReasons());
        missing.remove(SafeReadinessMissingReason.SAFE_ACCESS_STAFF_MISSING);
        if (!eligibleSafeAccessStaff) {
            missing.add(SafeReadinessMissingReason.SAFE_ACCESS_STAFF_MISSING);
        }
        SafeVaultReadinessSummary summary = new SafeVaultReadinessSummary(
                readiness.summary().vaultId(),
                missing.isEmpty(),
                missing
        );
        return new RowReadiness(readiness.mapped(), readiness.premise(), readiness.safeArea(), readiness.vault(),
                summary, humanReasons(missing));
    }

    public static List<String> humanReasons(List<SafeReadinessMissingReason> missingReasons) {
        List<String> reasons = new ArrayList<>();
        if (missingReasons == null) {
            return reasons;
        }
        for (SafeReadinessMissingReason reason : missingReasons) {
            reasons.add(switch (reason) {
                case PREMISE_MISSING -> "Safe premise is missing.";
                case PREMISE_EXIT_INVALID -> "Safe premise exit is invalid.";
                case SAFE_AREA_MISSING -> "Safe area is missing.";
                case VAULT_MISSING -> "Safe vault mapping is missing.";
                case VAULT_DOOR_MISSING -> "Complete BANK_VAULT_DOOR multiblock is missing.";
                case ASSIGNABLE_ROW_MISSING -> "At least one fully assignable loaded safety deposit row is required.";
                case TELLER_ROUTE_MISSING -> "Bank-bound teller outbound and return routes are missing.";
                case VIEWING_ROOM_MISSING -> "At least one ready safety-box viewing room is required in this premise.";
                case SAFE_ACCESS_STAFF_MISSING -> "At least one current bank employee must have Safe Access.";
            });
        }
        return reasons;
    }

    private record Candidate(SafePremiseSnapshot premise, SafeAreaSnapshot safeArea, SafeVaultSnapshot vault) {
    }
}
