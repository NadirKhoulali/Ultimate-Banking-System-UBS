package net.austizz.ultimatebankingsystem.bank.safebox.zone;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortPhase;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortSession;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortTarget;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SafeBoxZoneIndex {
    private static final int CHUNK_SIZE = 16;
    private static final LookupObserver NO_OP_LOOKUP_OBSERVER =
            (dimension, chunkX, chunkZ, candidateIndexes) -> { };

    private final List<SafeBoxZoneRecord> records;
    private final Map<ChunkKey, List<Integer>> recordIndexesByChunk;
    private final LookupObserver lookupObserver;

    public SafeBoxZoneIndex(List<SafeBoxZoneRecord> records) {
        this(records, NO_OP_LOOKUP_OBSERVER);
    }

    SafeBoxZoneIndex(List<SafeBoxZoneRecord> records, LookupObserver lookupObserver) {
        this.records = records == null ? List.of() : List.copyOf(records);
        this.recordIndexesByChunk = buildSpatialIndex(this.records);
        this.lookupObserver = lookupObserver == null ? NO_OP_LOOKUP_OBSERVER : lookupObserver;
    }

    public List<SafeBoxZoneRecord> records() {
        return records;
    }

    public List<Presence> at(String dimension, int x, int y, int z) {
        List<Presence> matches = new ArrayList<>();
        for (int recordIndex : candidateIndexesAt(dimension, x, z)) {
            SafeBoxZoneRecord record = records.get(recordIndex);
            boolean insidePremise = record.premiseBounds().contains(dimension, x, y, z);
            List<SafeBoxZoneRecord.Area> areas = containingAreas(record, dimension, x, y, z);
            if (insidePremise || !areas.isEmpty()) {
                matches.add(new Presence(record, insidePremise, areas));
            }
        }
        return List.copyOf(matches);
    }

    public List<Presence> premisesAt(String dimension, int x, int y, int z) {
        return at(dimension, x, y, z).stream()
                .filter(Presence::insidePremise)
                .toList();
    }

    public Optional<EscortScope> scopeFor(SafeBoxEscortSession session) {
        if (session == null || !authorizing(session.phase())) {
            return Optional.empty();
        }
        SafeBoxEscortTarget target = session.target();
        List<EscortScope> matches = new ArrayList<>();
        int targetX = target.rowPosition().x();
        int targetZ = target.rowPosition().z();
        for (int recordIndex : candidateIndexesAt(target.dimension(), targetX, targetZ)) {
            SafeBoxZoneRecord record = records.get(recordIndex);
            if (!record.bankId().equals(target.bankId())) {
                continue;
            }
            for (SafeBoxZoneRecord.Area area : containingAreas(record, target.dimension(),
                    targetX, target.rowPosition().y(), targetZ)) {
                matches.add(new EscortScope(record.bankId(), record.premiseId(), area.id()));
            }
        }
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    public Optional<SafeExitSnapshot> safeExitFor(List<Presence> denied) {
        for (Presence presence : denied) {
            SafeExitSnapshot exit = presence.record().exit();
            if (validFor(presence.record(), exit)
                    && at(exit.dimension(), exit.x(), exit.y(), exit.z()).isEmpty()) {
                return Optional.of(exit);
            }
        }
        return Optional.empty();
    }

    private List<Integer> candidateIndexesAt(String dimension, int x, int z) {
        String normalizedDimension = SafeBlockBounds.normalizeDimension(dimension);
        int chunkX = Math.floorDiv(x, CHUNK_SIZE);
        int chunkZ = Math.floorDiv(z, CHUNK_SIZE);
        List<Integer> candidateIndexes = recordIndexesByChunk.getOrDefault(
                new ChunkKey(normalizedDimension, chunkX, chunkZ), List.of());
        lookupObserver.onLookup(normalizedDimension, chunkX, chunkZ, candidateIndexes);
        return candidateIndexes;
    }

    private static Map<ChunkKey, List<Integer>> buildSpatialIndex(List<SafeBoxZoneRecord> records) {
        Map<ChunkKey, Set<Integer>> mutable = new HashMap<>();
        for (int recordIndex = 0; recordIndex < records.size(); recordIndex++) {
            SafeBoxZoneRecord record = records.get(recordIndex);
            indexBounds(mutable, recordIndex, record.premiseBounds());
            for (SafeBoxZoneRecord.Area area : record.safeAreas()) {
                indexBounds(mutable, recordIndex, area.bounds());
            }
        }
        Map<ChunkKey, List<Integer>> immutable = new HashMap<>();
        mutable.forEach((key, indexes) -> immutable.put(key, List.copyOf(indexes)));
        return Map.copyOf(immutable);
    }

    private static void indexBounds(Map<ChunkKey, Set<Integer>> index,
                                    int recordIndex,
                                    SafeBlockBounds bounds) {
        int minChunkX = Math.floorDiv(bounds.minX(), CHUNK_SIZE);
        int maxChunkX = Math.floorDiv(bounds.maxX(), CHUNK_SIZE);
        int minChunkZ = Math.floorDiv(bounds.minZ(), CHUNK_SIZE);
        int maxChunkZ = Math.floorDiv(bounds.maxZ(), CHUNK_SIZE);
        for (int chunkX = minChunkX; ; chunkX++) {
            for (int chunkZ = minChunkZ; ; chunkZ++) {
                ChunkKey key = new ChunkKey(bounds.dimension(), chunkX, chunkZ);
                index.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(recordIndex);
                if (chunkZ == maxChunkZ) {
                    break;
                }
            }
            if (chunkX == maxChunkX) {
                break;
            }
        }
    }

    private static List<SafeBoxZoneRecord.Area> containingAreas(SafeBoxZoneRecord record,
                                                                 String dimension,
                                                                 int x, int y, int z) {
        List<SafeBoxZoneRecord.Area> matches = new ArrayList<>();
        for (SafeBoxZoneRecord.Area area : record.safeAreas()) {
            if (area.bounds().contains(dimension, x, y, z)) {
                matches.add(area);
            }
        }
        return List.copyOf(matches);
    }

    private static boolean authorizing(SafeBoxEscortPhase phase) {
        return phase == SafeBoxEscortPhase.OUTBOUND
                || phase == SafeBoxEscortPhase.AT_VAULT
                || phase == SafeBoxEscortPhase.INSPECTING
                || phase == SafeBoxEscortPhase.WAITING_FOR_EXIT;
    }

    private static boolean validFor(SafeBoxZoneRecord record, SafeExitSnapshot exit) {
        SafeBlockBounds bounds = record.premiseBounds();
        return exit != null && Float.isFinite(exit.yaw())
                && bounds.dimension().equals(exit.dimension())
                && !bounds.contains(exit.dimension(), exit.x(), exit.y(), exit.z());
    }

    public record Presence(SafeBoxZoneRecord record,
                           boolean insidePremise,
                           List<SafeBoxZoneRecord.Area> safeAreas) {
        public Presence {
            safeAreas = safeAreas == null ? List.of() : List.copyOf(safeAreas);
        }
    }

    public record EscortScope(java.util.UUID bankId, String premiseId, String safeAreaId) {
        boolean matchesPremise(SafeBoxZoneRecord record) {
            return bankId.equals(record.bankId()) && premiseId.equals(record.premiseId());
        }
    }

    @FunctionalInterface
    interface LookupObserver {
        void onLookup(String dimension, int chunkX, int chunkZ, List<Integer> candidateIndexes);
    }

    private record ChunkKey(String dimension, int x, int z) {
    }
}
