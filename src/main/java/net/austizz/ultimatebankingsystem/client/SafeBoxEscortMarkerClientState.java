package net.austizz.ultimatebankingsystem.client;

import java.util.Locale;
import java.util.Optional;

public final class SafeBoxEscortMarkerClientState {
    private static final int DOOR_COUNT = 4;
    private static final double DOOR_MARGIN_Y = 0.03D;
    private static final double DOOR_EDGE_MIN = 0.08D;
    private static final double DOOR_EDGE_MAX = 0.92D;
    private static final double FACE_INSET = 0.045D;
    private static final double FACE_OUTSET = 0.035D;
    private static final double ANCHOR_OUTSET = 0.06D;

    private static volatile Snapshot snapshot = Snapshot.inactive();

    private SafeBoxEscortMarkerClientState() {
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static void apply(MarkerUpdate update) {
        snapshot = update == null || !update.active()
                ? Snapshot.inactive()
                : Snapshot.from(update);
    }

    public static void clear() {
        snapshot = Snapshot.inactive();
    }

    public static void onClientDisconnect() {
        clear();
    }

    public static void onLevelAvailabilityChanged(boolean levelAvailable) {
        if (!levelAvailable) {
            clear();
        }
    }

    public static boolean shouldRenderIn(String dimensionId) {
        return snapshot.shouldRenderIn(dimensionId);
    }

    private static String normalizeDimensionId(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return "";
        }
        String normalized = dimensionId.trim().toLowerCase(Locale.ROOT);
        return normalized.indexOf(':') >= 0 ? normalized : "minecraft:" + normalized;
    }

    private static DoorGeometry geometry(int rowX,
                                         int rowY,
                                         int rowZ,
                                         int doorIndex,
                                         Facing facing) {
        double segmentTop = rowY + 1.0D - doorIndex / (double) DOOR_COUNT;
        double segmentBottom = segmentTop - 1.0D / DOOR_COUNT;
        double minY = segmentBottom + DOOR_MARGIN_Y;
        double maxY = segmentTop - DOOR_MARGIN_Y;
        double centerY = (minY + maxY) * 0.5D;
        return switch (facing) {
            case NORTH -> new DoorGeometry(
                    rowX + DOOR_EDGE_MIN, minY, rowZ - FACE_OUTSET,
                    rowX + DOOR_EDGE_MAX, maxY, rowZ + FACE_INSET,
                    rowX + 0.5D, centerY, rowZ - ANCHOR_OUTSET);
            case SOUTH -> new DoorGeometry(
                    rowX + DOOR_EDGE_MIN, minY, rowZ + 1.0D - FACE_INSET,
                    rowX + DOOR_EDGE_MAX, maxY, rowZ + 1.0D + FACE_OUTSET,
                    rowX + 0.5D, centerY, rowZ + 1.0D + ANCHOR_OUTSET);
            case WEST -> new DoorGeometry(
                    rowX - FACE_OUTSET, minY, rowZ + DOOR_EDGE_MIN,
                    rowX + FACE_INSET, maxY, rowZ + DOOR_EDGE_MAX,
                    rowX - ANCHOR_OUTSET, centerY, rowZ + 0.5D);
            case EAST -> new DoorGeometry(
                    rowX + 1.0D - FACE_INSET, minY, rowZ + DOOR_EDGE_MIN,
                    rowX + 1.0D + FACE_OUTSET, maxY, rowZ + DOOR_EDGE_MAX,
                    rowX + 1.0D + ANCHOR_OUTSET, centerY, rowZ + 0.5D);
        };
    }

    public enum Facing {
        NORTH,
        SOUTH,
        WEST,
        EAST
    }

    public record MarkerUpdate(boolean active,
                               String dimensionId,
                               int rowX,
                               int rowY,
                               int rowZ,
                               int doorIndex,
                               String boxLabel) {
        public MarkerUpdate {
            dimensionId = normalizeDimensionId(dimensionId);
            doorIndex = Math.max(0, Math.min(DOOR_COUNT - 1, doorIndex));
            boxLabel = boxLabel == null ? "" : boxLabel.trim();
        }

        public static MarkerUpdate inactive() {
            return new MarkerUpdate(false, "", 0, 0, 0, 0, "");
        }
    }

    public record RenderContext(String dimensionId,
                                boolean chunkLoaded,
                                boolean validRow,
                                Facing facing) {
    }

    public record Snapshot(boolean active,
                           String dimensionId,
                           int rowX,
                           int rowY,
                           int rowZ,
                           int doorIndex,
                           String boxLabel) {
        private static Snapshot inactive() {
            return new Snapshot(false, "", 0, 0, 0, 0, "");
        }

        private static Snapshot from(MarkerUpdate update) {
            return new Snapshot(
                    true,
                    update.dimensionId(),
                    update.rowX(),
                    update.rowY(),
                    update.rowZ(),
                    update.doorIndex(),
                    update.boxLabel()
            );
        }

        public boolean shouldRenderIn(String currentDimensionId) {
            return active
                    && !dimensionId.isEmpty()
                    && dimensionId.equals(normalizeDimensionId(currentDimensionId));
        }

        public Optional<RenderTarget> resolveRenderTarget(RenderContext context) {
            if (context == null
                    || !shouldRenderIn(context.dimensionId())
                    || !context.chunkLoaded()
                    || !context.validRow()
                    || context.facing() == null) {
                return Optional.empty();
            }
            DoorGeometry liveGeometry = SafeBoxEscortMarkerClientState.geometry(
                    rowX, rowY, rowZ, doorIndex, context.facing());
            return Optional.of(new RenderTarget(liveGeometry, boxLabel));
        }
    }

    public record RenderTarget(DoorGeometry geometry, String boxLabel) {
    }

    public record DoorGeometry(double minX,
                               double minY,
                               double minZ,
                               double maxX,
                               double maxY,
                               double maxZ,
                               double anchorX,
                               double anchorY,
                               double anchorZ) {
    }
}
