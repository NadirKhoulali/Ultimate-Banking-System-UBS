package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SafeDepositSetupParser {
    private SafeDepositSetupParser() {
    }

    static List<Map<String, Object>> validPremiseMaps(List<Map<String, Object>> source) {
        List<Map<String, Object>> premises = new ArrayList<>();
        for (Map<String, Object> premise : source) {
            if (premiseSnapshot(premise) != null) {
                premises.add(premise);
            }
        }
        return premises;
    }

    static List<SafePremiseSnapshot> snapshots(List<Map<String, Object>> source) {
        List<SafePremiseSnapshot> premises = new ArrayList<>();
        for (Map<String, Object> premise : source) {
            SafePremiseSnapshot snapshot = premiseSnapshot(premise);
            if (snapshot != null) {
                premises.add(snapshot);
            }
        }
        return premises;
    }

    static boolean validExit(Map<String, Object> premise) {
        SafeBlockBounds bounds = SafeBlockBounds.from(premise);
        Integer exitX = SafeDepositSetupMaps.integerObject(premise.get("exitX"));
        Integer exitY = SafeDepositSetupMaps.integerObject(premise.get("exitY"));
        Integer exitZ = SafeDepositSetupMaps.integerObject(premise.get("exitZ"));
        float yaw = SafeDepositSetupMaps.floatValue(premise.get("exitYaw"), Float.NaN);
        return bounds != null
                && exitX != null && exitY != null && exitZ != null
                && !bounds.contains(exitX, exitY, exitZ)
                && Float.isFinite(yaw)
                && SafePremiseMode.parse(SafeDepositSetupMaps.string(premise.get("mode"))) != null;
    }

    private static SafePremiseSnapshot premiseSnapshot(Map<String, Object> premise) {
        if (premise == null || SafeDepositSetupMaps.string(premise.get("id")).isBlank()) {
            return null;
        }
        SafeBlockBounds bounds = SafeBlockBounds.from(premise);
        if (bounds == null || !validExit(premise)) {
            return null;
        }
        String id = SafeDepositSetupMaps.string(premise.get("id"));
        Object rawSafeAreas = premise.get("safeAreas");
        if (!(rawSafeAreas instanceof List<?> rawSafeAreaEntries)) {
            return null;
        }
        List<SafeAreaSnapshot> safeAreas = new ArrayList<>();
        for (Map<String, Object> safeArea : SafeDepositSetupMaps.mapList(rawSafeAreas)) {
            SafeAreaSnapshot snapshot = safeAreaSnapshot(safeArea, id);
            if (snapshot != null) {
                if (!bounds.contains(snapshot.bounds())) {
                    return null;
                }
                safeAreas.add(snapshot);
            }
        }
        if (!rawSafeAreaEntries.isEmpty() && safeAreas.isEmpty()) {
            return null;
        }
        SafePremiseMode mode = SafePremiseMode.parse(SafeDepositSetupMaps.string(premise.get("mode")));
        if (mode == null) {
            return null;
        }
        SafeExitSnapshot exit = new SafeExitSnapshot(
                bounds.dimension(),
                SafeDepositSetupMaps.integer(premise.get("exitX"), 0),
                SafeDepositSetupMaps.integer(premise.get("exitY"), 0),
                SafeDepositSetupMaps.integer(premise.get("exitZ"), 0),
                SafeDepositSetupMaps.floatValue(premise.get("exitYaw"), 0.0F)
        );
        return new SafePremiseSnapshot(id, SafeDepositSetupMaps.string(premise.get("bankId")), bounds, exit,
                mode, safeAreas);
    }

    private static SafeAreaSnapshot safeAreaSnapshot(Map<String, Object> safeArea, String premiseId) {
        if (safeArea == null || SafeDepositSetupMaps.string(safeArea.get("id")).isBlank()
                || !premiseId.equals(SafeDepositSetupMaps.string(safeArea.get("premiseId")))) {
            return null;
        }
        SafeBlockBounds bounds = SafeBlockBounds.from(safeArea);
        if (bounds == null) {
            return null;
        }
        String id = SafeDepositSetupMaps.string(safeArea.get("id"));
        List<SafeVaultSnapshot> vaults = new ArrayList<>();
        for (Map<String, Object> vault : SafeDepositSetupMaps.mapList(safeArea.get("vaults"))) {
            SafeVaultSnapshot snapshot = vaultSnapshot(vault, id);
            if (snapshot != null) {
                vaults.add(snapshot);
            }
        }
        return vaults.size() == 1 ? new SafeAreaSnapshot(id, premiseId, bounds, vaults) : null;
    }

    private static SafeVaultSnapshot vaultSnapshot(Map<String, Object> vault, String safeAreaId) {
        if (vault == null || SafeDepositSetupMaps.string(vault.get("id")).isBlank()
                || !safeAreaId.equals(SafeDepositSetupMaps.string(vault.get("safeAreaId")))) {
            return null;
        }
        SafeVaultSetupStatus status = SafeVaultSetupStatus.parse(SafeDepositSetupMaps.string(vault.get("status")));
        if (status == null) {
            return null;
        }
        return new SafeVaultSnapshot(
                SafeDepositSetupMaps.string(vault.get("id")),
                safeAreaId,
                SafeDepositSetupMaps.string(vault.get("dimension")),
                status,
                SafeDepositSetupMaps.optionalInt(vault.get("vaultDoorX")),
                SafeDepositSetupMaps.optionalInt(vault.get("vaultDoorY")),
                SafeDepositSetupMaps.optionalInt(vault.get("vaultDoorZ")),
                SafeDepositSetupMaps.optionalInt(vault.get("doorIndex")),
                routeHooks(vault.get("routeHooks"))
        );
    }

    private static List<SafeTellerRouteHook> routeHooks(Object raw) {
        List<SafeTellerRouteHook> hooks = new ArrayList<>();
        for (Map<String, Object> hook : SafeDepositSetupMaps.mapList(raw)) {
            SafeTellerRouteHook snapshot = routeHook(hook);
            if (snapshot != null) {
                hooks.add(snapshot);
            }
        }
        return hooks;
    }

    private static SafeTellerRouteHook routeHook(Map<String, Object> hook) {
        String tellerId = SafeDepositSetupMaps.string(hook.get("tellerId"));
        String outboundRouteRef = SafeDepositSetupMaps.string(hook.get("outboundRouteRef"));
        String returnRouteRef = SafeDepositSetupMaps.string(hook.get("returnRouteRef"));
        Boolean bankBound = SafeDepositSetupMaps.booleanObject(hook.get("bankBound"));
        if (tellerId.isBlank() || bankBound == null
                || (outboundRouteRef.isBlank() && returnRouteRef.isBlank())) {
            return null;
        }
        return new SafeTellerRouteHook(tellerId, bankBound, outboundRouteRef, returnRouteRef);
    }
}
