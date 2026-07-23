package net.austizz.ultimatebankingsystem.bank.safebox.zone;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.EscortBlockPosition;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxArea;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortPhase;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortSession;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortTarget;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxZoneIndexSpatialTest {
    private static final String DIMENSION = "minecraft:overworld";
    private static final UUID BANK_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BANK_B = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID PLAYER = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TELLER = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void atLookupIsBoundedToPlayerChunk() {
        RecordingLookupObserver observer = new RecordingLookupObserver();
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(withDistantPremises(
                zone(BANK_A, "local", bounds(0, 15, 0, 15), exit(-2, 8), List.of())), observer);

        assertEquals(List.of("local"), premiseIds(index.at(DIMENSION, 8, 64, 8)));
        assertEquals(List.of(new Lookup(DIMENSION, 0, 0, 1)), observer.lookups());
    }

    @Test
    void scopeLookupIsBoundedToEscortTargetChunk() {
        SafeBoxZoneRecord.Area vault = new SafeBoxZoneRecord.Area("vault", bounds(4, 7, 4, 7));
        RecordingLookupObserver observer = new RecordingLookupObserver();
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(withDistantPremises(
                zone(BANK_A, "local", bounds(0, 15, 0, 15), exit(-2, 8), List.of(vault))), observer);

        SafeBoxZoneIndex.EscortScope scope = index.scopeFor(escort(BANK_A, 5, 5)).orElseThrow();

        assertEquals("local", scope.premiseId());
        assertEquals("vault", scope.safeAreaId());
        assertEquals(List.of(new Lookup(DIMENSION, 0, 0, 1)), observer.lookups());
    }

    @Test
    void safeExitRevalidationIsBoundedToExitChunk() {
        RecordingLookupObserver observer = new RecordingLookupObserver();
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(withDistantPremises(
                zone(BANK_A, "local", bounds(0, 15, 0, 15), exit(32, 8), List.of())), observer);

        SafeBoxZonePolicy.Decision decision = decide(index, 8, 8, id -> false);

        assertFalse(decision.allowed());
        assertEquals(32, decision.exit().orElseThrow().x());
        assertEquals(List.of(new Lookup(DIMENSION, 0, 0, 1), new Lookup(DIMENSION, 2, 0, 0)),
                observer.lookups());
    }

    @Test
    void negativeAndBoundaryChunkCoordinatesRemainAddressable() {
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(List.of(zone(
                BANK_A, "negative", bounds(-17, 0, 0, 15), exit(2, 8), List.of())));

        for (int x : List.of(-17, -16, -1, 0)) {
            assertEquals(List.of("negative"), premiseIds(index.at(DIMENSION, x, 64, 8)));
        }
        assertTrue(index.at(DIMENSION, -18, 64, 8).isEmpty());
        assertTrue(index.at(DIMENSION, 1, 64, 8).isEmpty());
    }

    @Test
    void negativeZBoundariesUseFloorDivisionForIndexAndQueryChunks() {
        RecordingLookupObserver observer = new RecordingLookupObserver();
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(List.of(
                zone(BANK_A, "z-negative-17", bounds(0, 15, -17, -17), exit(-2, -17), List.of()),
                zone(BANK_A, "z-negative-16", bounds(32, 47, -16, -16), exit(30, -16), List.of()),
                zone(BANK_A, "z-negative-1", bounds(64, 79, -1, -1), exit(62, -1), List.of()),
                zone(BANK_A, "z-zero", bounds(96, 111, 0, 0), exit(94, 0), List.of())), observer);

        assertEquals(List.of("z-negative-17"), premiseIds(index.at(DIMENSION, 8, 64, -17)));
        assertEquals(List.of("z-negative-16"), premiseIds(index.at(DIMENSION, 40, 64, -16)));
        assertEquals(List.of("z-negative-1"), premiseIds(index.at(DIMENSION, 72, 64, -1)));
        assertEquals(List.of("z-zero"), premiseIds(index.at(DIMENSION, 104, 64, 0)));
        assertEquals(List.of(
                new Lookup(DIMENSION, 0, -2, 1), new Lookup(DIMENSION, 2, -1, 1),
                new Lookup(DIMENSION, 4, -1, 1), new Lookup(DIMENSION, 6, 0, 1)), observer.lookups());
    }

    @Test
    void zAxisChunkCrossingRemainsAddressable() {
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(List.of(zone(
                BANK_A, "z-crossing", bounds(0, 15, 15, 16), exit(-2, 15), List.of())));

        assertEquals(List.of("z-crossing"), premiseIds(index.at(DIMENSION, 8, 64, 15)));
        assertEquals(List.of("z-crossing"), premiseIds(index.at(DIMENSION, 8, 64, 16)));
        assertTrue(index.at(DIMENSION, 8, 64, 17).isEmpty());
    }

    @Test
    void dimensionLookupNormalizesWhitespaceAndCase() {
        RecordingLookupObserver observer = new RecordingLookupObserver();
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(List.of(zone(
                BANK_A, "normalized", bounds(0, 15, 0, 15), exit(-2, 8), List.of())), observer);

        assertEquals(List.of("normalized"), premiseIds(index.at("  MINECRAFT:OVERWORLD  ", 8, 64, 8)));
        assertEquals(List.of(new Lookup(DIMENSION, 0, 0, 1)), observer.lookups());
        assertTrue(index.at("minecraft:the_nether", 8, 64, 8).isEmpty());
    }

    @Test
    void safeAreaOutsidePremiseChunkIsIndexedForPresenceAndScope() {
        SafeBoxZoneRecord.Area detachedArea = new SafeBoxZoneRecord.Area(
                "detached-vault", bounds(32, 47, 0, 15));
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(List.of(zone(
                BANK_A, "local", bounds(0, 15, 0, 15), exit(-2, 8), List.of(detachedArea))));

        SafeBoxZoneIndex.Presence presence = index.at(DIMENSION, 40, 64, 8).getFirst();
        assertFalse(presence.insidePremise());
        assertEquals(List.of("detached-vault"), areaIds(presence));
        assertEquals("detached-vault", index.scopeFor(escort(BANK_A, 40, 8)).orElseThrow().safeAreaId());
    }

    @Test
    void constructorDetachesInputAndExposedViewsStayImmutable() {
        SafeBoxZoneRecord.Area vault = new SafeBoxZoneRecord.Area("vault", bounds(4, 7, 4, 7));
        SafeBoxZoneRecord local = zone(
                BANK_A, "local", bounds(0, 15, 0, 15), exit(-2, 8), List.of(vault));
        List<SafeBoxZoneRecord> mutableInput = new ArrayList<>(List.of(local));
        RecordingLookupObserver observer = new RecordingLookupObserver();
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(mutableInput, observer);
        mutableInput.clear();

        List<SafeBoxZoneIndex.Presence> present = index.at(DIMENSION, 5, 64, 5);
        assertSame(local, index.records().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> index.records().clear());
        assertThrows(UnsupportedOperationException.class, present::clear);
        assertThrows(UnsupportedOperationException.class, present.getFirst().safeAreas()::clear);
        assertThrows(UnsupportedOperationException.class, local.safeAreas()::clear);
        assertThrows(UnsupportedOperationException.class, observer.candidateViews().getFirst()::clear);
    }

    @Test
    void premiseAndAreaCoverageDeduplicatesTheRecordCandidate() {
        SafeBoxZoneRecord.Area vault = new SafeBoxZoneRecord.Area("vault", bounds(5, 10, 0, 15));
        RecordingLookupObserver observer = new RecordingLookupObserver();
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(List.of(zone(
                BANK_A, "local", bounds(0, 20, 0, 15), exit(-2, 8), List.of(vault))), observer);

        assertEquals(List.of("local"), premiseIds(index.at(DIMENSION, 7, 64, 8)));
        assertEquals(List.of(new Lookup(DIMENSION, 0, 0, 1)), observer.lookups());
    }

    @Test
    void overlappingLocalZonesRemainSourceOrderedAndFailClosed() {
        SafeBoxZoneRecord first = zone(
                BANK_A, "first", bounds(0, 20, 0, 15), exit(-2, 8), List.of());
        SafeBoxZoneRecord second = zone(
                BANK_B, "second", bounds(10, 30, 0, 15), exit(32, 8), List.of());
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(List.of(first, second));

        assertEquals(List.of("first", "second"), premiseIds(index.at(DIMENSION, 12, 64, 8)));
        SafeBoxZonePolicy.Decision decision = decide(index, 12, 8, id -> id.equals(BANK_A));
        assertFalse(decision.allowed());
        assertEquals(List.of("second"), premiseIds(decision.denied()));
        assertEquals(32, decision.exit().orElseThrow().x());
    }

    private static List<SafeBoxZoneRecord> withDistantPremises(SafeBoxZoneRecord local) {
        List<SafeBoxZoneRecord> records = new ArrayList<>();
        records.add(local);
        for (int index = 0; index < 2_000; index++) {
            int minX = 64 + index * 32;
            records.add(zone(BANK_B, "distant-" + index,
                    bounds(minX, minX + 15, 0, 15), exit(minX - 2, 8), List.of()));
        }
        return records;
    }

    private static SafeBoxZonePolicy.Decision decide(SafeBoxZoneIndex index, int x, int z,
                                                       SafeBoxZonePolicy.StaffAccess access) {
        return SafeBoxZonePolicy.decide(index, DIMENSION, x, 64, z, access, Optional.empty());
    }

    private static SafeBoxEscortSession escort(UUID bankId, int x, int z) {
        SafeBoxEscortTarget target = new SafeBoxEscortTarget(bankId, "vault", UUID.randomUUID(), DIMENSION,
                new EscortBlockPosition(x, 64, z), 0, TELLER);
        return new SafeBoxEscortSession(UUID.randomUUID(), PLAYER, target,
                new SafeBoxArea(DIMENSION, x - 1, 60, z - 1, x + 1, 70, z + 1),
                SafeBoxEscortPhase.OUTBOUND, -1L, null);
    }

    private static SafeBoxZoneRecord zone(UUID bankId, String premiseId, SafeBlockBounds premise,
                                           SafeExitSnapshot exit, List<SafeBoxZoneRecord.Area> areas) {
        return new SafeBoxZoneRecord(
                bankId, premiseId, SafePremiseMode.STAFF_ONLY, premise, exit, areas);
    }

    private static SafeBlockBounds bounds(int minX, int maxX, int minZ, int maxZ) {
        return new SafeBlockBounds(DIMENSION, minX, 60, minZ, maxX, 70, maxZ);
    }

    private static SafeExitSnapshot exit(int x, int z) {
        return new SafeExitSnapshot(DIMENSION, x, 64, z, 0.0F);
    }

    private static List<String> premiseIds(List<SafeBoxZoneIndex.Presence> presences) {
        return presences.stream().map(presence -> presence.record().premiseId()).toList();
    }

    private static List<String> areaIds(SafeBoxZoneIndex.Presence presence) {
        return presence.safeAreas().stream().map(SafeBoxZoneRecord.Area::id).toList();
    }

    private record Lookup(String dimension, int chunkX, int chunkZ, int candidateCount) {
    }

    private static final class RecordingLookupObserver implements SafeBoxZoneIndex.LookupObserver {
        private final List<Lookup> lookups = new ArrayList<>();
        private final List<List<Integer>> candidateViews = new ArrayList<>();

        @Override
        public void onLookup(String dimension, int chunkX, int chunkZ, List<Integer> candidateIndexes) {
            lookups.add(new Lookup(dimension, chunkX, chunkZ, candidateIndexes.size()));
            candidateViews.add(candidateIndexes);
        }

        List<Lookup> lookups() {
            return List.copyOf(lookups);
        }

        List<List<Integer>> candidateViews() {
            return List.copyOf(candidateViews);
        }
    }
}
