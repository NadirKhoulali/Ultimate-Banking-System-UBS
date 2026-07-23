package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SafeDepositSetupMigration {
    public static final int SETUP_VERSION = 1;
    public static final String SETUP_VERSION_KEY = "safeDepositSetupVersion";
    public static final String PREMISES_KEY = "safeDepositPremises";
    public static final String LEGACY_AREAS_KEY = "safeDepositAreas";
    public static final String LEGACY_ASSIGNMENTS_KEY = "safeDepositAssignments";

    private SafeDepositSetupMigration() {
    }

    public static boolean migrateLegacy(Map<String, Object> metadata, UUID bankId) {
        if (metadata == null || bankId == null) {
            return false;
        }

        boolean changed = !Integer.valueOf(SETUP_VERSION).equals(
                SafeDepositSetupMaps.integerObject(metadata.get(SETUP_VERSION_KEY)));
        List<Map<String, Object>> existingPremises = mapList(metadata.get(PREMISES_KEY));
        List<Map<String, Object>> validPremises = SafeDepositSetupParser.validPremiseMaps(existingPremises);
        changed |= validPremises.size() != existingPremises.size();
        Reconciliation reconciliation = reconcilePremises(validPremises, legacyBounds(metadata), bankId);
        changed |= reconciliation.changed();

        metadata.put(SETUP_VERSION_KEY, SETUP_VERSION);
        metadata.put(PREMISES_KEY, reconciliation.premises());
        return changed;
    }

    public static SafeDepositSetupSnapshot snapshot(Map<String, Object> metadata) {
        if (metadata == null) {
            return new SafeDepositSetupSnapshot(0, List.of());
        }
        return new SafeDepositSetupSnapshot(
                SafeDepositSetupMaps.integer(metadata.get(SETUP_VERSION_KEY), 0),
                SafeDepositSetupParser.snapshots(mapList(metadata.get(PREMISES_KEY)))
        );
    }

    private static Reconciliation reconcilePremises(List<Map<String, Object>> premises,
                                                     List<SafeBlockBounds> legacyBounds,
                                                     UUID bankId) {
        boolean changed = false;
        List<PremiseEntry> entries = new ArrayList<>();
        Set<String> uniqueIds = SafeDepositSetupMaps.newIdSet();
        for (Map<String, Object> premise : premises) {
            String id = SafeDepositSetupMaps.string(premise.get("id"));
            SafeBlockBounds bounds = SafeBlockBounds.from(premise);
            if (!uniqueIds.add(id)) {
                changed = true;
                continue;
            }
            List<SafePremiseSnapshot> snapshots = SafeDepositSetupParser.snapshots(List.of(premise));
            if (bounds == null || snapshots.size() != 1) {
                changed = true;
                continue;
            }
            entries.add(new PremiseEntry(
                    entries.size(),
                    premise,
                    snapshots.get(0),
                    id,
                    bounds,
                    SafeDepositSetupIds.isMigrationOwnedPremise(id, bankId, bounds)
            ));
        }

        List<SafeBlockBounds> orderedLegacyBounds = new ArrayList<>(new LinkedHashSet<>(legacyBounds));
        Set<SafeBlockBounds> legacyBoundSet = new LinkedHashSet<>(orderedLegacyBounds);
        Map<SafeBlockBounds, Integer> customRepresentationCounts = customRepresentationCounts(
                entries, legacyBoundSet);
        Set<SafeBlockBounds> representedBounds = new LinkedHashSet<>();
        for (SafeBlockBounds bounds : orderedLegacyBounds) {
            if (customRepresentationCounts.getOrDefault(bounds, 0) == 1) {
                representedBounds.add(bounds);
            }
        }

        Map<GeneratedChildKey, GeneratedChild> removedGeneratedChildren = removedGeneratedChildren(
                entries, legacyBoundSet, bankId);
        Map<GeneratedChildKey, List<SafeBlockBounds>> fragmentAssignments = new LinkedHashMap<>();
        for (SafeBlockBounds bounds : orderedLegacyBounds) {
            if (representedBounds.contains(bounds)) {
                continue;
            }
            GeneratedChild soleParent = null;
            int candidateCount = 0;
            for (GeneratedChild candidate : removedGeneratedChildren.values()) {
                if (candidate.bounds().contains(bounds)) {
                    soleParent = candidate;
                    candidateCount++;
                }
            }
            if (candidateCount == 1 && soleParent != null) {
                fragmentAssignments.computeIfAbsent(soleParent.key(), ignored -> new ArrayList<>()).add(bounds);
                representedBounds.add(bounds);
            }
        }

        Map<Integer, Map<String, Object>> reconciledCustomPremises = new LinkedHashMap<>();
        for (PremiseEntry entry : entries) {
            if (entry.migrationOwned()) {
                continue;
            }
            CustomPremiseReconciliation custom = reconcileCustomPremise(
                    entry, removedGeneratedChildren, fragmentAssignments, bankId);
            reconciledCustomPremises.put(entry.index(), custom.premise());
            changed |= custom.changed();
        }

        List<Map<String, Object>> reconciled = new ArrayList<>();
        Set<String> retainedIds = SafeDepositSetupMaps.newIdSet();
        for (PremiseEntry entry : entries) {
            if (entry.migrationOwned()
                    && (!legacyBoundSet.contains(entry.bounds()) || representedBounds.contains(entry.bounds()))) {
                changed = true;
                continue;
            }
            Map<String, Object> retained = entry.migrationOwned()
                    ? entry.premise()
                    : reconciledCustomPremises.get(entry.index());
            retainedIds.add(entry.id());
            reconciled.add(retained);
        }

        for (SafeBlockBounds bounds : orderedLegacyBounds) {
            if (representedBounds.contains(bounds)) {
                continue;
            }
            String premiseId = SafeDepositSetupIds.premiseId(bankId, bounds);
            if (retainedIds.add(premiseId)) {
                reconciled.add(SafeDepositSetupFactory.migrationPremise(premiseId, bankId, bounds));
                changed = true;
            }
        }

        return new Reconciliation(reconciled, changed);
    }

    private static Map<SafeBlockBounds, Integer> customRepresentationCounts(List<PremiseEntry> entries,
                                                                             Set<SafeBlockBounds> legacyBounds) {
        Map<SafeBlockBounds, Integer> counts = new LinkedHashMap<>();
        for (PremiseEntry entry : entries) {
            if (entry.migrationOwned()) {
                continue;
            }
            for (SafeAreaSnapshot safeArea : entry.snapshot().safeAreas()) {
                if (legacyBounds.contains(safeArea.bounds())) {
                    counts.merge(safeArea.bounds(), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private static Map<GeneratedChildKey, GeneratedChild> removedGeneratedChildren(
            List<PremiseEntry> entries,
            Set<SafeBlockBounds> legacyBounds,
            UUID bankId) {
        Map<GeneratedChildKey, GeneratedChild> removed = new LinkedHashMap<>();
        for (PremiseEntry entry : entries) {
            if (entry.migrationOwned()) {
                continue;
            }
            Set<String> validChildIds = SafeDepositSetupMaps.newIdSet();
            for (SafeAreaSnapshot safeArea : entry.snapshot().safeAreas()) {
                validChildIds.add(safeArea.id());
            }
            Object rawSafeAreas = entry.premise().get("safeAreas");
            if (!(rawSafeAreas instanceof List<?> children)) {
                continue;
            }
            for (int childIndex = 0; childIndex < children.size(); childIndex++) {
                Object rawChild = children.get(childIndex);
                if (!(rawChild instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Object> child = SafeDepositSetupMaps.stringKeyMap(rawMap);
                SafeBlockBounds bounds = child == null ? null : SafeBlockBounds.from(child);
                String childId = child == null ? "" : SafeDepositSetupMaps.string(child.get("id"));
                if (bounds == null || !validChildIds.contains(childId)
                        || !entry.id().equals(SafeDepositSetupMaps.string(child.get("premiseId")))
                        || !SafeDepositSetupIds.safeAreaId(bankId, entry.id(), bounds).equals(childId)
                        || legacyBounds.contains(bounds)) {
                    continue;
                }
                GeneratedChildKey key = new GeneratedChildKey(entry.index(), childIndex);
                removed.put(key, new GeneratedChild(key, bounds));
            }
        }
        return removed;
    }

    private static CustomPremiseReconciliation reconcileCustomPremise(
            PremiseEntry entry,
            Map<GeneratedChildKey, GeneratedChild> removedGeneratedChildren,
            Map<GeneratedChildKey, List<SafeBlockBounds>> fragmentAssignments,
            UUID bankId) {
        Object rawSafeAreas = entry.premise().get("safeAreas");
        if (!(rawSafeAreas instanceof List<?> children)) {
            return new CustomPremiseReconciliation(entry.premise(), false);
        }
        boolean changed = false;
        List<Object> reconciledChildren = new ArrayList<>();
        for (int childIndex = 0; childIndex < children.size(); childIndex++) {
            GeneratedChildKey key = new GeneratedChildKey(entry.index(), childIndex);
            if (!removedGeneratedChildren.containsKey(key)) {
                reconciledChildren.add(children.get(childIndex));
                continue;
            }
            changed = true;
            for (SafeBlockBounds fragment : fragmentAssignments.getOrDefault(key, List.of())) {
                reconciledChildren.add(SafeDepositSetupFactory.nestedSafeArea(entry.id(), bankId, fragment));
            }
        }
        if (!changed) {
            return new CustomPremiseReconciliation(entry.premise(), false);
        }
        Map<String, Object> reconciledPremise = new LinkedHashMap<>(entry.premise());
        reconciledPremise.put("safeAreas", reconciledChildren);
        return new CustomPremiseReconciliation(reconciledPremise, true);
    }

    private static List<SafeBlockBounds> legacyBounds(Map<String, Object> metadata) {
        List<SafeBlockBounds> bounds = new ArrayList<>();
        for (Map<String, Object> areaTag : mapList(metadata.get(LEGACY_AREAS_KEY))) {
            SafeBlockBounds areaBounds = SafeBlockBounds.from(areaTag);
            if (areaBounds != null) {
                bounds.add(areaBounds);
            }
        }
        return bounds;
    }

    public static List<Map<String, Object>> mapList(Object raw) {
        return SafeDepositSetupMaps.mapList(raw);
    }

    private record PremiseEntry(int index,
                                Map<String, Object> premise,
                                SafePremiseSnapshot snapshot,
                                String id,
                                SafeBlockBounds bounds,
                                boolean migrationOwned) {
    }

    private record GeneratedChildKey(int premiseIndex, int childIndex) {
    }

    private record GeneratedChild(GeneratedChildKey key, SafeBlockBounds bounds) {
    }

    private record CustomPremiseReconciliation(Map<String, Object> premise, boolean changed) {
    }

    private record Reconciliation(List<Map<String, Object>> premises, boolean changed) {
    }
}
