package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxEscortMarkerClientStateTest {
    public static void main(String[] args) {
        SafeBoxEscortMarkerClientStateTest test = new SafeBoxEscortMarkerClientStateTest();
        run("dimension-mismatch", test::dimensionMismatchSuppressesRendering);
        run("all-facings-live-geometry", test::allFacingsComputeLiveExactDoorGeometry);
        run("invalid-unloaded-suppression", test::invalidUnloadedAndUnknownFacingSuppressRendering);
        run("stale-disconnect-clear", test::disconnectAndLevelNullTransitionsClearStaleMarker);
        run("exact-box-label", test::renderTargetCarriesExactBoxLabel);
        System.out.println("SUMMARY marker state scenarios passed=5 failed=0");
    }

    @AfterEach
    void clearClientMarker() {
        SafeBoxEscortMarkerClientState.clear();
    }

    @Test
    void dimensionMismatchSuppressesRendering() {
        SafeBoxEscortMarkerClientState.apply(update(1));
        SafeBoxEscortMarkerClientState.Snapshot snapshot = SafeBoxEscortMarkerClientState.snapshot();

        assertTrue(snapshot.shouldRenderIn("minecraft:overworld"));
        assertFalse(snapshot.shouldRenderIn("minecraft:the_nether"));
        assertTrue(snapshot.resolveRenderTarget(context(
                "minecraft:the_nether", true, true,
                SafeBoxEscortMarkerClientState.Facing.NORTH)).isEmpty());
    }

    @Test
    void allFacingsComputeLiveExactDoorGeometry() {
        SafeBoxEscortMarkerClientState.apply(update(1));
        SafeBoxEscortMarkerClientState.Snapshot snapshot = SafeBoxEscortMarkerClientState.snapshot();

        SafeBoxEscortMarkerClientState.DoorGeometry north = geometry(
                snapshot, SafeBoxEscortMarkerClientState.Facing.NORTH);
        SafeBoxEscortMarkerClientState.DoorGeometry south = geometry(
                snapshot, SafeBoxEscortMarkerClientState.Facing.SOUTH);
        SafeBoxEscortMarkerClientState.DoorGeometry west = geometry(
                snapshot, SafeBoxEscortMarkerClientState.Facing.WEST);
        SafeBoxEscortMarkerClientState.DoorGeometry east = geometry(
                snapshot, SafeBoxEscortMarkerClientState.Facing.EAST);

        assertEquals(-4.06D, north.anchorZ(), 0.00001D);
        assertEquals(-2.94D, south.anchorZ(), 0.00001D);
        assertEquals(19.94D, west.anchorX(), 0.00001D);
        assertEquals(21.06D, east.anchorX(), 0.00001D);
        assertEquals(80.53D, north.minY(), 0.00001D);
        assertEquals(80.72D, north.maxY(), 0.00001D);
        assertNotSame(north, geometry(snapshot, SafeBoxEscortMarkerClientState.Facing.NORTH),
                "geometry must be derived for the live facing, not retained in the snapshot");
    }

    @Test
    void invalidUnloadedAndUnknownFacingSuppressRendering() {
        SafeBoxEscortMarkerClientState.apply(update(1));
        SafeBoxEscortMarkerClientState.Snapshot snapshot = SafeBoxEscortMarkerClientState.snapshot();

        assertTrue(snapshot.resolveRenderTarget(context(
                "minecraft:overworld", false, true,
                SafeBoxEscortMarkerClientState.Facing.NORTH)).isEmpty());
        assertTrue(snapshot.resolveRenderTarget(context(
                "minecraft:overworld", true, false,
                SafeBoxEscortMarkerClientState.Facing.NORTH)).isEmpty());
        assertTrue(snapshot.resolveRenderTarget(context(
                "minecraft:overworld", true, true, null)).isEmpty());
    }

    @Test
    void disconnectAndLevelNullTransitionsClearStaleMarker() {
        SafeBoxEscortMarkerClientState.apply(update(1));
        SafeBoxEscortMarkerClientState.onClientDisconnect();
        assertFalse(SafeBoxEscortMarkerClientState.snapshot().active());

        SafeBoxEscortMarkerClientState.apply(update(1));
        SafeBoxEscortMarkerClientState.onLevelAvailabilityChanged(true);
        assertTrue(SafeBoxEscortMarkerClientState.snapshot().active());
        SafeBoxEscortMarkerClientState.onLevelAvailabilityChanged(false);
        assertFalse(SafeBoxEscortMarkerClientState.snapshot().active());
    }

    @Test
    void renderTargetCarriesExactBoxLabel() {
        SafeBoxEscortMarkerClientState.apply(update(2));

        SafeBoxEscortMarkerClientState.RenderTarget target =
                SafeBoxEscortMarkerClientState.snapshot().resolveRenderTarget(context(
                        "minecraft:overworld", true, true,
                        SafeBoxEscortMarkerClientState.Facing.EAST)).orElseThrow();

        assertEquals("SDB-0042", target.boxLabel());
        assertEquals(80.28D, target.geometry().minY(), 0.00001D);
        assertEquals(80.47D, target.geometry().maxY(), 0.00001D);
    }

    private static SafeBoxEscortMarkerClientState.MarkerUpdate update(int doorIndex) {
        return new SafeBoxEscortMarkerClientState.MarkerUpdate(
                true, "minecraft:overworld", 20, 80, -4, doorIndex, "SDB-0042");
    }

    private static SafeBoxEscortMarkerClientState.RenderContext context(
            String dimensionId,
            boolean chunkLoaded,
            boolean validRow,
            SafeBoxEscortMarkerClientState.Facing facing) {
        return new SafeBoxEscortMarkerClientState.RenderContext(
                dimensionId, chunkLoaded, validRow, facing);
    }

    private static SafeBoxEscortMarkerClientState.DoorGeometry geometry(
            SafeBoxEscortMarkerClientState.Snapshot snapshot,
            SafeBoxEscortMarkerClientState.Facing facing) {
        return snapshot.resolveRenderTarget(context(
                "minecraft:overworld", true, true, facing)).orElseThrow().geometry();
    }

    private static void run(String scenario, Runnable action) {
        action.run();
        SafeBoxEscortMarkerClientState.clear();
        System.out.println("PASS " + scenario);
    }
}
