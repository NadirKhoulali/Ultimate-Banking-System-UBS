package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.owner.staffing.BankStaffingService;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteNbtStore;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePosition;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteSaveResult;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteStep;
import net.austizz.ultimatebankingsystem.network.BankTellerSafeBoxState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

final class SafetyDepositBoxOpenAuthorityGameTestMetadata {
    static final String VAULT_ID = "task14-vault";
    static final String PRIMARY_LABEL = "SDB-PRIMARY";
    static final String SIBLING_LABEL = "SDB-SIBLING";

    private SafetyDepositBoxOpenAuthorityGameTestMetadata() {
    }

    static CompoundTag ready(SafetyDepositBoxOpenAuthorityGameTestSpec spec) {
        var ids = spec.identities();
        var layout = spec.layout();
        CompoundTag metadata = new CompoundTag();
        metadata.putBoolean("rateExempt", true);
        metadata.putInt("safeDepositSetupVersion", 1);
        ListTag assignments = new ListTag();
        assignments.add(assignment(ids.bankId(), ids.accountId(), layout, 0, PRIMARY_LABEL));
        assignments.add(assignment(ids.bankId(), ids.siblingAccountId(), layout, 1, SIBLING_LABEL));
        metadata.put(SafetyDepositBoxService.ASSIGNMENTS_KEY, assignments);
        metadata.put("safeDepositPremises", premises(ids.bankId(), layout));
        metadata.putString(BankStaffingService.EMPLOYEES_KEY, ids.playerId() + "=VAULT:0");
        CompoundTag access = new CompoundTag();
        access.putUUID("playerId", ids.playerId());
        access.putBoolean("safeAccess", true);
        ListTag accesses = new ListTag();
        accesses.add(access);
        metadata.put(BankStaffingService.SAFE_ACCESS_KEY, accesses);
        return metadata;
    }

    static void bindRoutes(CompoundTag metadata,
                           SafetyDepositBoxOpenAuthorityGameTestSpec.RouteBinding binding,
                           Consumer<String> fail) {
        SafeTellerRoutePosition stop = new SafeTellerRoutePosition(
                binding.tellerPos().getX(), binding.tellerPos().getY(), binding.tellerPos().getZ());
        for (SafeTellerRouteDirection direction : SafeTellerRouteDirection.values()) {
            SafeTellerRoute route = SafeTellerRoute.create(binding.bankId().toString(), VAULT_ID,
                    binding.tellerId().toString(), direction, binding.dimension(), stop, stop,
                    List.of(new SafeTellerRouteStep.Walk(stop)));
            if (SafeTellerRouteNbtStore.saveAndBind(metadata, route).status()
                    != SafeTellerRouteSaveResult.Status.SAVED) {
                fail.accept("could not bind " + direction + " route");
            }
        }
    }

    static BankTellerSafeBoxState.AccountAssignment selectedAssignment(
            SafetyDepositBoxOpenAuthorityGameTestSpec spec) {
        var layout = spec.layout();
        return new BankTellerSafeBoxState.AccountAssignment(
                spec.identities().accountId(), PRIMARY_LABEL, layout.dimension(),
                layout.rowPos().getX(), layout.rowPos().getY(), layout.rowPos().getZ(), 0, VAULT_ID,
                true, false, List.of());
    }

    private static CompoundTag assignment(UUID bankId, UUID accountId,
                                          SafetyDepositBoxOpenAuthorityGameTestSpec.Layout layout,
                                          int doorIndex, String label) {
        CompoundTag assignment = new CompoundTag();
        assignment.putUUID("bankId", bankId);
        assignment.putUUID("accountId", accountId);
        assignment.putString("dimension", layout.dimension());
        assignment.putInt("x", layout.rowPos().getX());
        assignment.putInt("y", layout.rowPos().getY());
        assignment.putInt("z", layout.rowPos().getZ());
        assignment.putInt("doorIndex", doorIndex);
        assignment.putString("boxNumber", label);
        assignment.putBoolean("locked", false);
        return assignment;
    }

    private static ListTag premises(UUID bankId,
                                    SafetyDepositBoxOpenAuthorityGameTestSpec.Layout layout) {
        Bounds bounds = Bounds.around(layout);

        CompoundTag vault = bounds(new CompoundTag(), bounds);
        vault.putString("id", VAULT_ID);
        vault.putString("safeAreaId", "task14-area");
        vault.putString("status", "READY");
        vault.putInt("vaultDoorX", layout.doorMaster().getX());
        vault.putInt("vaultDoorY", layout.doorMaster().getY());
        vault.putInt("vaultDoorZ", layout.doorMaster().getZ());
        vault.putInt("doorIndex", 0);
        vault.put("routeHooks", new ListTag());

        CompoundTag area = bounds(new CompoundTag(), bounds);
        area.putString("id", "task14-area");
        area.putString("premiseId", "task14-premise");
        ListTag vaults = new ListTag();
        vaults.add(vault);
        area.put("vaults", vaults);

        CompoundTag premise = bounds(new CompoundTag(), bounds);
        premise.putString("id", "task14-premise");
        premise.putString("bankId", bankId.toString());
        premise.putString("mode", "PUBLIC");
        premise.putInt("exitX", bounds.min().getX() - 2);
        premise.putInt("exitY", layout.rowPos().getY());
        premise.putInt("exitZ", bounds.min().getZ() - 2);
        premise.putFloat("exitYaw", 0.0F);
        ListTag areas = new ListTag();
        areas.add(area);
        premise.put("safeAreas", areas);
        ListTag premises = new ListTag();
        premises.add(premise);
        return premises;
    }

    private static CompoundTag bounds(CompoundTag tag, Bounds bounds) {
        tag.putString("dimension", bounds.dimension());
        tag.putInt("minX", bounds.min().getX());
        tag.putInt("minY", bounds.min().getY());
        tag.putInt("minZ", bounds.min().getZ());
        tag.putInt("maxX", bounds.max().getX());
        tag.putInt("maxY", bounds.max().getY());
        tag.putInt("maxZ", bounds.max().getZ());
        return tag;
    }

    private record Bounds(String dimension, BlockPos min, BlockPos max) {
        private static Bounds around(SafetyDepositBoxOpenAuthorityGameTestSpec.Layout layout) {
            BlockPos row = layout.rowPos();
            BlockPos door = layout.doorMaster();
            return new Bounds(layout.dimension(),
                    new BlockPos(Math.min(row.getX(), door.getX()) - 8,
                            Math.min(row.getY(), door.getY()) - 2,
                            Math.min(row.getZ(), door.getZ()) - 8),
                    new BlockPos(Math.max(row.getX(), door.getX()) + 8,
                            Math.max(row.getY(), door.getY()) + 8,
                            Math.max(row.getZ(), door.getZ()) + 8));
        }
    }
}
