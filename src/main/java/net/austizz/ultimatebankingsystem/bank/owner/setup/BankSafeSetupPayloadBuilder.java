package net.austizz.ultimatebankingsystem.bank.owner.setup;

import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeReadinessMissingReason;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver;
import net.austizz.ultimatebankingsystem.network.OwnerPcSetupObjectivePayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultSetupPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BankSafeSetupPayloadBuilder {
    private BankSafeSetupPayloadBuilder() {
    }

    public record Result(List<OwnerPcVaultSetupPayload> vaults,
                         OwnerPcSetupObjectivePayload objective) {
        public Result {
            vaults = vaults == null ? List.of() : List.copyOf(vaults);
            objective = objective == null ? OwnerPcSetupObjectivePayload.unavailable() : objective;
        }
    }

    public static Result build(MinecraftServer server, CompoundTag metadata) {
        CompoundTag safeMetadata = metadata == null ? new CompoundTag() : metadata;
        SafeDepositSetupSnapshot snapshot = SafetyDepositBoxService.safeDepositSetupSnapshot(safeMetadata);
        List<SafeVaultReadinessResolver.RowReadiness> readiness =
                SafetyDepositBoxService.safeDepositVaultReadiness(server, safeMetadata);
        return buildProjected(snapshot, readiness);
    }

    static Result buildFromSnapshot(SafeDepositSetupSnapshot snapshot,
                                    List<SafeVaultReadinessResolver.RowReadiness> readiness,
                                    CompoundTag metadata) {
        return buildProjected(snapshot, SafetyDepositBoxService.applyStaffingReadiness(metadata, readiness));
    }

    private static Result buildProjected(SafeDepositSetupSnapshot snapshot,
                                         List<SafeVaultReadinessResolver.RowReadiness> readiness) {
        SafeDepositSetupSnapshot safeSnapshot = snapshot == null
                ? new SafeDepositSetupSnapshot(1, List.of())
                : snapshot;
        List<SafeVaultReadinessResolver.RowReadiness> safeReadiness = readiness == null
                ? List.of()
                : List.copyOf(readiness);
        List<OwnerPcVaultSetupPayload> payloads = safeReadiness.stream()
                .filter(row -> row != null && row.premise() != null && row.safeArea() != null && row.vault() != null)
                .map(BankSafeSetupPayloadBuilder::payload)
                .sorted(Comparator.comparing(OwnerPcVaultSetupPayload::premiseId, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(OwnerPcVaultSetupPayload::safeAreaId, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(OwnerPcVaultSetupPayload::vaultId, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int premiseCount = safeSnapshot.premises().size();
        int vaultCount = payloads.size();
        int readyVaultCount = (int) payloads.stream().filter(OwnerPcVaultSetupPayload::ready).count();
        boolean ready = readyVaultCount > 0;
        List<String> missingSteps = ready
                ? List.of()
                : missingSteps(safeSnapshot, safeReadiness, vaultCount);
        return new Result(payloads, new OwnerPcSetupObjectivePayload(
                ready, premiseCount, vaultCount, readyVaultCount, missingSteps));
    }

    private static OwnerPcVaultSetupPayload payload(SafeVaultReadinessResolver.RowReadiness readiness) {
        List<SafeReadinessMissingReason> missing = readiness.summary().missingReasons();
        return new OwnerPcVaultSetupPayload(
                readiness.premise().id(),
                readiness.vault().id(),
                readiness.premise().mode().name(),
                boundsLabel(readiness.premise().bounds()),
                readiness.premise().exit() != null,
                readiness.safeArea().id(),
                boundsLabel(readiness.safeArea().bounds()),
                readiness.summary().ready() ? "READY" : "NOT_READY",
                readiness.summary().ready(),
                missing.stream().map(Enum::name).toList(),
                readiness.humanMissingReasons(),
                status(missing, SafeReadinessMissingReason.VAULT_DOOR_MISSING),
                status(missing, SafeReadinessMissingReason.ASSIGNABLE_ROW_MISSING),
                status(missing, SafeReadinessMissingReason.VIEWING_ROOM_MISSING)
        );
    }

    private static String status(List<SafeReadinessMissingReason> missing,
                                 SafeReadinessMissingReason reason) {
        return missing.contains(reason) ? "MISSING" : "READY";
    }

    private static List<String> missingSteps(SafeDepositSetupSnapshot snapshot,
                                             List<SafeVaultReadinessResolver.RowReadiness> readiness,
                                             int vaultCount) {
        Set<String> steps = new LinkedHashSet<>();
        if (snapshot.premises().isEmpty()) {
            steps.add("Claim a bank premise in Bank Owner PC > Premises > Claim Premise.");
            return List.copyOf(steps);
        }
        boolean safeAreaPresent = snapshot.premises().stream()
                .flatMap(premise -> premise.safeAreas().stream())
                .findAny()
                .isPresent();
        if (!safeAreaPresent) {
            steps.add("Claim a safe area in Bank Owner PC > Safe > Claim Safe Area.");
        }
        if (vaultCount == 0) {
            steps.add("Create a vault setup for the claimed safe area.");
            return List.copyOf(steps);
        }
        readiness.stream()
                .filter(row -> row != null && row.summary() != null)
                .min(Comparator.comparingInt(row -> row.summary().missingReasons().size()))
                .ifPresent(row -> steps.addAll(row.humanMissingReasons()));
        if (steps.isEmpty()) {
            steps.add("Complete one safe-deposit vault setup.");
        }
        return List.copyOf(steps);
    }

    private static String boundsLabel(SafeBlockBounds bounds) {
        if (bounds == null) {
            return "";
        }
        return bounds.dimension() + " "
                + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ()
                + " -> "
                + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ();
    }
}
