package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SafeDepositSetupFactory {
    private SafeDepositSetupFactory() {
    }

    static Map<String, Object> migrationPremise(String premiseId, UUID bankId, SafeBlockBounds bounds) {
        Map<String, Object> safeArea = nestedSafeArea(premiseId, bankId, bounds);

        Map<String, Object> premise = new LinkedHashMap<>();
        premise.put("id", premiseId);
        premise.put("bankId", bankId.toString());
        writeBounds(premise, bounds);
        premise.put("exitX", bounds.minX() - 1);
        premise.put("exitY", bounds.minY());
        premise.put("exitZ", bounds.minZ());
        premise.put("exitYaw", 0.0F);
        premise.put("mode", SafePremiseMode.PUBLIC.name());
        premise.put("safeAreas", new ArrayList<>(List.of(safeArea)));
        return premise;
    }

    static Map<String, Object> nestedSafeArea(String premiseId, UUID bankId, SafeBlockBounds bounds) {
        Map<String, Object> safeArea = safeArea(premiseId, bankId, bounds);
        safeArea.put("vaults", new ArrayList<>(List.of(placeholderVault(safeArea, bankId, bounds))));
        return safeArea;
    }

    private static Map<String, Object> safeArea(String premiseId, UUID bankId, SafeBlockBounds bounds) {
        Map<String, Object> safeArea = new LinkedHashMap<>();
        safeArea.put("id", SafeDepositSetupIds.safeAreaId(bankId, premiseId, bounds));
        safeArea.put("premiseId", premiseId);
        writeBounds(safeArea, bounds);
        safeArea.put("vaults", new ArrayList<Map<String, Object>>());
        return safeArea;
    }

    private static Map<String, Object> placeholderVault(Map<String, Object> safeArea,
                                                        UUID bankId,
                                                        SafeBlockBounds bounds) {
        Map<String, Object> vault = new LinkedHashMap<>();
        String safeAreaId = SafeDepositSetupMaps.string(safeArea.get("id"));
        vault.put("id", SafeDepositSetupIds.vaultId(bankId, safeAreaId));
        vault.put("safeAreaId", safeAreaId);
        vault.put("dimension", bounds.dimension());
        vault.put("status", SafeVaultSetupStatus.SETUP_PENDING.name());
        vault.put("routeStatus", "UNWIRED");
        vault.put("routeHooks", new ArrayList<Map<String, Object>>());
        return vault;
    }

    private static void writeBounds(Map<String, Object> target, SafeBlockBounds bounds) {
        target.put("dimension", bounds.dimension());
        target.put("minX", bounds.minX());
        target.put("minY", bounds.minY());
        target.put("minZ", bounds.minZ());
        target.put("maxX", bounds.maxX());
        target.put("maxY", bounds.maxY());
        target.put("maxZ", bounds.maxZ());
    }
}
