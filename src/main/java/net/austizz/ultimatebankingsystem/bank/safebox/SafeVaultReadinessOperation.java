package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePairResolver;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver;
import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.Optional;

public final class SafeVaultReadinessOperation {
    private final SafeDepositSetupSnapshot snapshot;
    private final SafeVaultReadinessResolver.EvaluationContext context;
    private final boolean eligibleSafeAccessStaff;

    SafeVaultReadinessOperation(SafeDepositSetupSnapshot snapshot,
                                SafeVaultReadinessResolver.EvaluationContext context,
                                boolean eligibleSafeAccessStaff) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.context = Objects.requireNonNull(context, "context");
        this.eligibleSafeAccessStaff = eligibleSafeAccessStaff;
    }

    public SafeVaultReadinessResolver.RowReadiness resolve(
            SafeVaultReadinessResolver.RowLocation location) {
        return withStaffing(SafeVaultReadinessResolver.resolveForRow(
                new SafeVaultReadinessResolver.RowRequest(context, snapshot, location)));
    }

    public SafeVaultReadinessResolver.RowReadiness resolve(
            SafeVaultReadinessResolver.VaultSelection selection) {
        return withStaffing(SafeVaultReadinessResolver.resolveVault(
                new SafeVaultReadinessResolver.VaultRequest(context, selection)));
    }

    public SafeTellerRoutePairResolver.Context routes() {
        return context.routes();
    }

    SafeDepositSetupSnapshot snapshot() {
        return snapshot;
    }

    SafeVaultReadinessResolver.LoadedWorldFacts facts() {
        return context.facts();
    }

    public Optional<BlockPos> resolveDoorMaster(SafeVaultReadinessResolver.RowReadiness readiness) {
        SafeVaultReadinessResolver.RowReadiness current = withStaffing(readiness);
        if (current == null || !current.mapped() || current.vault() == null
                || current.summary() == null || !current.summary().ready()) {
            return Optional.empty();
        }
        return context.facts().resolvedDoorPos(current.vault().id()).map(BlockPos::immutable);
    }

    private SafeVaultReadinessResolver.RowReadiness withStaffing(
            SafeVaultReadinessResolver.RowReadiness readiness) {
        return SafeVaultReadinessResolver.withEligibleSafeAccessStaff(readiness, eligibleSafeAccessStaff);
    }
}
