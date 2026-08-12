package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SafePremiseMutationService {
    private static final String PREMISES_KEY = SafeDepositSetupMigration.PREMISES_KEY;
    private static final String ASSIGNMENTS_KEY = SafeDepositSetupMigration.LEGACY_ASSIGNMENTS_KEY;
    private static final String ROUTES_KEY = "safeTellerRoutes";

    private SafePremiseMutationService() {
    }

    public static SafePremiseMutationResult create(Map<String, Object> metadata,
                                                   UUID bankId,
                                                   SafeBlockBounds bounds,
                                                   SafeExitSnapshot exit) {
        PremiseIndex index = premiseIndex(metadata);
        if (index == null) {
            return SafePremiseMutationResult.rejected(
                    "the bank's stored premise data could not be read for a safe overlap check");
        }
        if (bankId == null || !validBounds(bounds) || !validExit(bounds, exit)) {
            return SafePremiseMutationResult.rejected();
        }
        for (PremiseNode premise : index.premises()) {
            if (bounds.overlaps(premise.bounds())) {
                return SafePremiseMutationResult.rejected(
                        "the selection overlaps " + describePremise(premise, bankId));
            }
        }
        for (OpaquePremise blocked : index.opaque()) {
            if (bounds.overlaps(blocked.bounds())) {
                return SafePremiseMutationResult.rejected(
                        "the selection overlaps a stored premise entry with unreadable saved data at "
                                + describeBounds(blocked.bounds()));
            }
        }

        String premiseId;
        do {
            premiseId = UUID.randomUUID().toString();
        } while (index.byId().containsKey(premiseId)
                || index.opaqueIds().contains(premiseId)
                || SafeDepositSetupIds.isMigrationOwnedPremise(premiseId, bankId, bounds));

        Map<String, Object> updated = deepCopyMap(metadata);
        updated.put(SafeDepositSetupMigration.SETUP_VERSION_KEY, SafeDepositSetupMigration.SETUP_VERSION);
        copiedPremises(updated).add(newPremise(premiseId, bankId, bounds, exit));
        return SafePremiseMutationResult.committed(updated);
    }

    public static SafePremiseMutationResult setMode(Map<String, Object> metadata,
                                                    UUID bankId,
                                                    String premiseId,
                                                    SafePremiseMode mode) {
        PremiseIndex index = premiseIndex(metadata);
        PremiseNode selected = selected(index, bankId, premiseId);
        if (selected == null || mode == null) {
            return SafePremiseMutationResult.rejected();
        }

        Map<String, Object> updated = deepCopyMap(metadata);
        copiedPremise(updated, selected.index()).put("mode", mode.name());
        return SafePremiseMutationResult.committed(updated);
    }

    public static SafePremiseMutationResult setExit(Map<String, Object> metadata,
                                                    UUID bankId,
                                                    String premiseId,
                                                    SafeExitSnapshot exit) {
        PremiseIndex index = premiseIndex(metadata);
        PremiseNode selected = selected(index, bankId, premiseId);
        if (selected == null || !validExit(selected.bounds(), exit)) {
            return SafePremiseMutationResult.rejected();
        }

        Map<String, Object> updated = deepCopyMap(metadata);
        Map<String, Object> premise = copiedPremise(updated, selected.index());
        premise.put("exitX", exit.x());
        premise.put("exitY", exit.y());
        premise.put("exitZ", exit.z());
        premise.put("exitYaw", exit.yaw());
        return SafePremiseMutationResult.committed(updated);
    }

    public static List<SafePremiseDeletionPolicy> deletionBlockers(Map<String, Object> metadata,
                                                                   UUID bankId,
                                                                   String premiseId,
                                                                   Set<String> activeVaultIds) {
        return deletionAnalysis(metadata, bankId, premiseId, activeVaultIds).blockers();
    }

    public static SafePremiseMutationResult delete(Map<String, Object> metadata,
                                                   UUID bankId,
                                                   String premiseId,
                                                   Set<String> activeVaultIds) {
        DeletionAnalysis analysis = deletionAnalysis(metadata, bankId, premiseId, activeVaultIds);
        if (!analysis.valid() || !analysis.blockers().isEmpty() || analysis.target() == null) {
            return SafePremiseMutationResult.rejected(analysis.blockers());
        }

        Map<String, Object> updated = deepCopyMap(metadata);
        copiedPremises(updated).remove(analysis.target().index());
        return SafePremiseMutationResult.committed(updated);
    }

    private static DeletionAnalysis deletionAnalysis(Map<String, Object> metadata,
                                                      UUID bankId,
                                                      String premiseId,
                                                      Set<String> activeVaultIds) {
        PremiseIndex index = premiseIndex(metadata);
        PremiseNode target = selected(index, bankId, premiseId);
        if (target == null) {
            return DeletionAnalysis.invalid();
        }

        EnumSet<SafePremiseDeletionPolicy> blockers = EnumSet.noneOf(SafePremiseDeletionPolicy.class);
        if (target.safeAreaCount() > 0) {
            blockers.add(SafePremiseDeletionPolicy.NON_EMPTY);
        }
        if (SafeDepositSetupIds.isMigrationOwnedPremise(target.id(), bankId, target.bounds())) {
            blockers.add(SafePremiseDeletionPolicy.MIGRATION_BACKED);
        }

        DependencyAssessment assignments = assignmentAssessment(metadata, index, target);
        if (assignments.blocked()) {
            blockers.add(SafePremiseDeletionPolicy.ASSIGNED);
        }
        boolean activeValid = activeVaultIds != null;
        if (activeVaultIds == null) {
            blockers.add(SafePremiseDeletionPolicy.ACTIVE);
        } else {
            for (String activeVaultId : activeVaultIds) {
                if (activeVaultId == null || activeVaultId.isBlank()) {
                    blockers.add(SafePremiseDeletionPolicy.ACTIVE);
                    activeValid = false;
                } else if (target.id().equals(activeVaultId) || target.vaultIds().contains(activeVaultId)) {
                    blockers.add(SafePremiseDeletionPolicy.ACTIVE);
                }
            }
        }

        return new DeletionAnalysis(
                target,
                ordered(blockers),
                assignments.valid() && activeValid
        );
    }

    private static DependencyAssessment assignmentAssessment(Map<String, Object> metadata,
                                                              PremiseIndex index,
                                                              PremiseNode target) {
        if (!metadata.containsKey(ASSIGNMENTS_KEY)) {
            return DependencyAssessment.clear();
        }
        Object rawAssignments = metadata.get(ASSIGNMENTS_KEY);
        if (!(rawAssignments instanceof List<?> assignments)) {
            return DependencyAssessment.invalid();
        }

        boolean blocked = false;
        boolean valid = true;
        for (Object rawAssignment : assignments) {
            Map<String, Object> assignment = strictMap(rawAssignment);
            UUID assignmentBankId = assignment == null ? null : uuid(assignment.get("bankId"));
            UUID accountId = assignment == null ? null : uuid(assignment.get("accountId"));
            String dimension = assignment == null ? null : requiredString(assignment.get("dimension"));
            Integer x = assignment == null ? null : integer(assignment.get("x"));
            Integer y = assignment == null ? null : integer(assignment.get("y"));
            Integer z = assignment == null ? null : integer(assignment.get("z"));
            Integer doorIndex = assignment == null ? null : integer(assignment.get("doorIndex"));
            if (assignmentBankId == null || accountId == null || dimension == null
                    || x == null || y == null || z == null || doorIndex == null || doorIndex < 0) {
                blocked = true;
                valid = false;
                continue;
            }

            PremiseNode mapped = null;
            int matchCount = 0;
            for (PremiseNode premise : index.premises()) {
                if (assignmentBankId.equals(premise.bankId())
                        && premise.bounds().contains(dimension, x, y, z)) {
                    mapped = premise;
                    matchCount++;
                }
            }
            if (matchCount != 1) {
                blocked = true;
                valid = false;
            } else if (mapped == target) {
                blocked = true;
            }
        }
        return new DependencyAssessment(blocked, valid);
    }

    private static DependencyAssessment routeAssessment(Map<String, Object> metadata,
                                                         PremiseIndex index,
                                                         PremiseNode target) {
        if (!metadata.containsKey(ROUTES_KEY)) {
            return DependencyAssessment.clear();
        }
        Object rawRoutes = metadata.get(ROUTES_KEY);
        if (!(rawRoutes instanceof List<?> routes)) {
            return DependencyAssessment.invalid();
        }

        boolean blocked = false;
        boolean valid = true;
        Set<String> routeIds = new LinkedHashSet<>();
        for (Object rawRoute : routes) {
            Map<String, Object> route = strictMap(rawRoute);
            String routeId = route == null ? null : identifier(route.get("id"));
            UUID routeBankId = route == null ? null : uuid(route.get("bankId"));
            String vaultId = route == null ? null : identifier(route.get("vaultId"));
            if (routeId == null || routeBankId == null || vaultId == null || !routeIds.add(routeId)) {
                blocked = true;
                valid = false;
                continue;
            }

            PremiseNode mapped = index.byVault().get(new VaultKey(routeBankId, vaultId));
            if (mapped == null) {
                blocked = true;
                valid = false;
            } else if (mapped == target) {
                blocked = true;
            }
        }
        return new DependencyAssessment(blocked, valid);
    }

    private static PremiseIndex premiseIndex(Map<String, Object> metadata) {
        if (metadata == null || !stringKeys(metadata)) {
            return null;
        }
        Object rawPremises = metadata.get(PREMISES_KEY);
        if (rawPremises == null) {
            return new PremiseIndex(List.of(), Map.of(), Map.of(), List.of(), Set.of());
        }
        if (!(rawPremises instanceof List<?> premises)) {
            return null;
        }

        List<PremiseNode> nodes = new ArrayList<>();
        Map<String, PremiseNode> byId = new LinkedHashMap<>();
        Map<VaultKey, PremiseNode> byVault = new LinkedHashMap<>();
        List<OpaquePremise> opaque = new ArrayList<>();
        Set<String> opaqueIds = new LinkedHashSet<>();
        Set<String> safeAreaIds = new LinkedHashSet<>();
        Set<String> vaultIds = new LinkedHashSet<>();
        for (int premiseIndex = 0; premiseIndex < premises.size(); premiseIndex++) {
            Map<String, Object> premise = strictMap(premises.get(premiseIndex));
            SafeBlockBounds bounds = premise == null ? null : bounds(premise);
            if (bounds == null) {
                // Without readable bounds this entry cannot take part in any spatial
                // safety check, so the whole index stays fail-closed.
                return null;
            }
            String id = identifier(premise.get("id"));
            UUID bankId = uuid(premise.get("bankId"));
            boolean duplicateId = id != null && (byId.containsKey(id) || opaqueIds.contains(id));

            Set<String> premiseVaultIds = new LinkedHashSet<>();
            boolean strictValid = id != null && bankId != null && !duplicateId
                    && validStoredPremise(premise, bounds);
            if (strictValid) {
                Object rawSafeAreas = premise.get("safeAreas");
                if (rawSafeAreas instanceof List<?> safeAreas) {
                    for (Object rawSafeArea : safeAreas) {
                        if (!validSafeArea(rawSafeArea, id, bounds, safeAreaIds, vaultIds, premiseVaultIds)) {
                            strictValid = false;
                            break;
                        }
                    }
                } else {
                    strictValid = false;
                }
            }

            if (!strictValid) {
                // Entries that fail strict validation stay out of the mutable index but
                // keep blocking overlapping claims, so one malformed premise can no
                // longer break every premise action for the bank.
                if (duplicateId) {
                    PremiseNode previous = byId.remove(id);
                    if (previous != null) {
                        nodes.remove(previous);
                        byVault.values().removeIf(node -> node == previous);
                        opaque.add(new OpaquePremise(previous.bounds()));
                    }
                }
                if (id != null) {
                    opaqueIds.add(id);
                }
                opaque.add(new OpaquePremise(bounds));
                continue;
            }

            PremiseNode node = new PremiseNode(
                    premiseIndex,
                    id,
                    bankId,
                    bounds,
                    ((List<?>) premise.get("safeAreas")).size(),
                    Set.copyOf(premiseVaultIds)
            );
            nodes.add(node);
            byId.put(id, node);
            for (String vaultId : premiseVaultIds) {
                byVault.put(new VaultKey(bankId, vaultId), node);
            }
        }
        return new PremiseIndex(List.copyOf(nodes), Map.copyOf(byId), Map.copyOf(byVault),
                List.copyOf(opaque), Set.copyOf(opaqueIds));
    }

    private static String describePremise(PremiseNode premise, UUID requestingBankId) {
        StringBuilder text = new StringBuilder("premise ");
        text.append(shortPremiseId(premise.id()));
        text.append(" at ").append(describeBounds(premise.bounds()));
        if (SafeDepositSetupIds.isMigrationOwnedPremise(premise.id(), premise.bankId(), premise.bounds())) {
            text.append(", auto-created from a legacy safe area");
        }
        if (requestingBankId != null && !requestingBankId.equals(premise.bankId())) {
            text.append(", owned by another bank");
        }
        return text.toString();
    }

    private static String shortPremiseId(String id) {
        if (id == null || id.isBlank()) {
            return "(unknown id)";
        }
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private static String describeBounds(SafeBlockBounds bounds) {
        return bounds.dimension()
                + " (" + bounds.minX() + ", " + bounds.minY() + ", " + bounds.minZ()
                + ") to (" + bounds.maxX() + ", " + bounds.maxY() + ", " + bounds.maxZ() + ")";
    }

    private static boolean validStoredPremise(Map<String, Object> premise, SafeBlockBounds bounds) {
        Integer exitX = integer(premise.get("exitX"));
        Integer exitY = integer(premise.get("exitY"));
        Integer exitZ = integer(premise.get("exitZ"));
        Float exitYaw = finiteFloat(premise.get("exitYaw"));
        SafePremiseMode mode = SafePremiseMode.parse(requiredString(premise.get("mode")));
        return validBounds(bounds)
                && exitX != null && exitY != null && exitZ != null && exitYaw != null
                && !bounds.contains(exitX, exitY, exitZ)
                && mode != null;
    }

    private static boolean validSafeArea(Object rawSafeArea,
                                         String premiseId,
                                         SafeBlockBounds premiseBounds,
                                         Set<String> safeAreaIds,
                                         Set<String> allVaultIds,
                                         Set<String> premiseVaultIds) {
        Map<String, Object> safeArea = strictMap(rawSafeArea);
        String safeAreaId = safeArea == null ? null : identifier(safeArea.get("id"));
        String parentId = safeArea == null ? null : identifier(safeArea.get("premiseId"));
        SafeBlockBounds safeAreaBounds = safeArea == null ? null : bounds(safeArea);
        if (safeAreaId == null || !premiseId.equals(parentId) || safeAreaBounds == null
                || !premiseBounds.contains(safeAreaBounds) || !safeAreaIds.add(safeAreaId)) {
            return false;
        }

        Object rawVaults = safeArea.get("vaults");
        if (!(rawVaults instanceof List<?> vaults) || vaults.size() != 1) {
            return false;
        }
        Map<String, Object> vault = strictMap(vaults.get(0));
        String vaultId = vault == null ? null : identifier(vault.get("id"));
        String parentSafeAreaId = vault == null ? null : identifier(vault.get("safeAreaId"));
        String vaultDimension = vault == null ? null : requiredString(vault.get("dimension"));
        SafeVaultSetupStatus status = vault == null
                ? null
                : SafeVaultSetupStatus.parse(requiredString(vault.get("status")));
        if (vaultId == null || !safeAreaId.equals(parentSafeAreaId) || vaultDimension == null
                || !safeAreaBounds.dimension().equals(SafeBlockBounds.normalizeDimension(vaultDimension))
                || status == null || !allVaultIds.add(vaultId) || !validVaultOptionals(vault)
                || !validRouteHooks(vault)) {
            return false;
        }
        premiseVaultIds.add(vaultId);
        return true;
    }

    private static boolean validVaultOptionals(Map<String, Object> vault) {
        String[] keys = {"vaultDoorX", "vaultDoorY", "vaultDoorZ", "doorIndex"};
        for (String key : keys) {
            if (vault.containsKey(key) && integer(vault.get(key)) == null) {
                return false;
            }
        }
        Integer doorIndex = vault.containsKey("doorIndex") ? integer(vault.get("doorIndex")) : null;
        return doorIndex == null || doorIndex >= 0;
    }

    private static boolean validRouteHooks(Map<String, Object> vault) {
        if (!vault.containsKey("routeHooks")) {
            return true;
        }
        Object rawRouteHooks = vault.get("routeHooks");
        if (!(rawRouteHooks instanceof List<?> routeHooks)) {
            return false;
        }
        Set<String> tellerIds = new LinkedHashSet<>();
        for (Object rawHook : routeHooks) {
            Map<String, Object> hook = strictMap(rawHook);
            String tellerId = hook == null ? null : identifier(hook.get("tellerId"));
            String outbound = hook == null ? null : optionalString(hook, "outboundRouteRef");
            String returning = hook == null ? null : optionalString(hook, "returnRouteRef");
            if (tellerId == null
                    || outbound == null || returning == null
                    || (outbound.isBlank() && returning.isBlank()) || !tellerIds.add(tellerId)) {
                return false;
            }
        }
        return true;
    }

    private static PremiseNode selected(PremiseIndex index, UUID bankId, String premiseId) {
        if (index == null || bankId == null || premiseId == null || premiseId.isBlank()) {
            return null;
        }
        PremiseNode selected = index.byId().get(premiseId);
        return selected != null && bankId.equals(selected.bankId()) ? selected : null;
    }

    private static boolean validBounds(SafeBlockBounds bounds) {
        return bounds != null && !bounds.dimension().isBlank();
    }

    private static boolean validExit(SafeBlockBounds bounds, SafeExitSnapshot exit) {
        return validBounds(bounds) && exit != null && !exit.dimension().isBlank()
                && bounds.dimension().equals(exit.dimension())
                && !bounds.contains(exit.x(), exit.y(), exit.z())
                && Float.isFinite(exit.yaw());
    }

    private static SafeBlockBounds bounds(Map<String, Object> source) {
        String dimension = requiredString(source.get("dimension"));
        Integer minX = integer(source.get("minX"));
        Integer minY = integer(source.get("minY"));
        Integer minZ = integer(source.get("minZ"));
        Integer maxX = integer(source.get("maxX"));
        Integer maxY = integer(source.get("maxY"));
        Integer maxZ = integer(source.get("maxZ"));
        if (dimension == null || minX == null || minY == null || minZ == null
                || maxX == null || maxY == null || maxZ == null) {
            return null;
        }
        return new SafeBlockBounds(dimension, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Map<String, Object> newPremise(String premiseId,
                                                  UUID bankId,
                                                  SafeBlockBounds bounds,
                                                  SafeExitSnapshot exit) {
        Map<String, Object> premise = new LinkedHashMap<>();
        premise.put("id", premiseId);
        premise.put("bankId", bankId.toString());
        premise.put("dimension", bounds.dimension());
        premise.put("minX", bounds.minX());
        premise.put("minY", bounds.minY());
        premise.put("minZ", bounds.minZ());
        premise.put("maxX", bounds.maxX());
        premise.put("maxY", bounds.maxY());
        premise.put("maxZ", bounds.maxZ());
        premise.put("exitX", exit.x());
        premise.put("exitY", exit.y());
        premise.put("exitZ", exit.z());
        premise.put("exitYaw", exit.yaw());
        premise.put("mode", SafePremiseMode.PUBLIC.name());
        premise.put("safeAreas", new ArrayList<Map<String, Object>>());
        return premise;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> copiedPremises(Map<String, Object> metadata) {
        Object raw = metadata.get(PREMISES_KEY);
        if (raw instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        List<Map<String, Object>> newList = new ArrayList<>();
        metadata.put(PREMISES_KEY, newList);
        return newList;
    }

    private static Map<String, Object> copiedPremise(Map<String, Object> metadata, int index) {
        return copiedPremises(metadata).get(index);
    }

    private static List<SafePremiseDeletionPolicy> ordered(
            Set<SafePremiseDeletionPolicy> blockers) {
        List<SafePremiseDeletionPolicy> ordered = new ArrayList<>();
        for (SafePremiseDeletionPolicy policy : SafePremiseDeletionPolicy.values()) {
            if (blockers.contains(policy)) {
                ordered.add(policy);
            }
        }
        return List.copyOf(ordered);
    }

    private static UUID uuid(Object raw) {
        String value = requiredString(raw);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Integer integer(Object raw) {
        if (!(raw instanceof Number number)) {
            return null;
        }
        double decimal = number.doubleValue();
        long integral = number.longValue();
        if (!Double.isFinite(decimal) || decimal != integral
                || integral < Integer.MIN_VALUE || integral > Integer.MAX_VALUE) {
            return null;
        }
        return (int) integral;
    }

    private static Float finiteFloat(Object raw) {
        if (!(raw instanceof Number number)) {
            return null;
        }
        float value = number.floatValue();
        return Float.isFinite(value) && Double.isFinite(number.doubleValue()) ? value : null;
    }

    private static String requiredString(Object raw) {
        return raw instanceof String value && !value.isBlank() ? value : null;
    }

    private static String identifier(Object raw) {
        String value = requiredString(raw);
        return value != null && value.equals(value.strip()) ? value : null;
    }

    private static String optionalString(Map<String, Object> source, String key) {
        if (!source.containsKey(key)) {
            return "";
        }
        return source.get(key) instanceof String value ? value : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> strictMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || !stringKeys(map)) {
            return null;
        }
        return (Map<String, Object>) map;
    }

    private static boolean stringKeys(Map<?, ?> map) {
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, deepCopy(value)));
        return copy;
    }

    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> copy.put(key, deepCopy(nested)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(nested -> copy.add(deepCopy(nested)));
            return copy;
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = new LinkedHashSet<>();
            set.forEach(nested -> copy.add(deepCopy(nested)));
            return copy;
        }
        return value;
    }

    private record PremiseNode(int index,
                               String id,
                               UUID bankId,
                               SafeBlockBounds bounds,
                               int safeAreaCount,
                               Set<String> vaultIds) {
    }

    private record PremiseIndex(List<PremiseNode> premises,
                                Map<String, PremiseNode> byId,
                                Map<VaultKey, PremiseNode> byVault,
                                List<OpaquePremise> opaque,
                                Set<String> opaqueIds) {
    }

    private record OpaquePremise(SafeBlockBounds bounds) {
    }

    private record VaultKey(UUID bankId, String vaultId) {
    }

    private record DependencyAssessment(boolean blocked, boolean valid) {
        private static DependencyAssessment clear() {
            return new DependencyAssessment(false, true);
        }

        private static DependencyAssessment invalid() {
            return new DependencyAssessment(true, false);
        }
    }

    private record DeletionAnalysis(PremiseNode target,
                                    List<SafePremiseDeletionPolicy> blockers,
                                    boolean valid) {
        private static DeletionAnalysis invalid() {
            return new DeletionAnalysis(
                    null,
                    List.of(SafePremiseDeletionPolicy.NON_EMPTY),
                    false
            );
        }
    }
}
