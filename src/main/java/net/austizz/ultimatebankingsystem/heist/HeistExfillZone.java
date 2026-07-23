package net.austizz.ultimatebankingsystem.heist;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared, server-authoritative geometry and visual state for a heist exfill zone. */
public final class HeistExfillZone {
    public static final int MAX_BOUNDARY_POINTS = 64;
    private static final double EDGE_EPSILON = 1.0E-6D;

    private HeistExfillZone() {}

    public enum VisualState {
        HIDDEN,
        IDLE,
        ACTIVE,
        CONTESTED;

        public static VisualState byName(String value) {
            if (value == null || value.isBlank()) return HIDDEN;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return HIDDEN;
            }
        }
    }

    public record Point(double x, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Exfill boundary points must be finite.");
            }
        }
    }

    public record Boundary(List<Point> points) {
        public Boundary {
            points = points == null ? List.of() : List.copyOf(points);
            if (!points.isEmpty() && (points.size() < 3 || points.size() > MAX_BOUNDARY_POINTS)) {
                throw new IllegalArgumentException("An exfill boundary requires 3-64 points.");
            }
        }

        public boolean valid() {
            return points.size() >= 3;
        }

        public boolean contains(double x, double z) {
            if (!valid() || !Double.isFinite(x) || !Double.isFinite(z)) return false;
            boolean inside = false;
            for (int i = 0, previous = points.size() - 1; i < points.size(); previous = i++) {
                Point a = points.get(previous);
                Point b = points.get(i);
                if (onSegment(a, b, x, z)) return true;
                boolean crosses = (a.z() > z) != (b.z() > z);
                if (crosses) {
                    double intersectionX = (b.x() - a.x()) * (z - a.z()) / (b.z() - a.z()) + a.x();
                    if (x < intersectionX) inside = !inside;
                }
            }
            return inside;
        }

        public double centerX() {
            return points.stream().mapToDouble(Point::x).average().orElse(0.0D);
        }

        public double centerZ() {
            return points.stream().mapToDouble(Point::z).average().orElse(0.0D);
        }

        public double perimeterLength() {
            if (!valid()) return 0.0D;
            double length = 0.0D;
            for (int i = 0; i < points.size(); i++) {
                Point a = points.get(i);
                Point b = points.get((i + 1) % points.size());
                length += Math.hypot(b.x() - a.x(), b.z() - a.z());
            }
            return length;
        }

        public String encode() {
            if (!valid()) return "";
            StringBuilder encoded = new StringBuilder(points.size() * 24);
            for (Point point : points) {
                if (!encoded.isEmpty()) encoded.append(';');
                encoded.append(Double.toString(point.x())).append(',').append(Double.toString(point.z()));
            }
            return encoded.toString();
        }

        public static Boundary decode(String encoded) {
            if (encoded == null || encoded.isBlank()) return new Boundary(List.of());
            String[] rows = encoded.split(";", -1);
            if (rows.length < 3 || rows.length > MAX_BOUNDARY_POINTS) return new Boundary(List.of());
            List<Point> points = new ArrayList<>(rows.length);
            try {
                for (String row : rows) {
                    String[] coordinates = row.split(",", -1);
                    if (coordinates.length != 2) return new Boundary(List.of());
                    points.add(new Point(Double.parseDouble(coordinates[0]), Double.parseDouble(coordinates[1])));
                }
            } catch (IllegalArgumentException ignored) {
                return new Boundary(List.of());
            }
            return new Boundary(points);
        }
    }

    public static Boundary rectangle(double minX, double minZ, double maxX, double maxZ) {
        if (!Double.isFinite(minX) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxZ)
                || maxX <= minX || maxZ <= minZ) {
            return new Boundary(List.of());
        }
        return new Boundary(List.of(
                new Point(minX, minZ),
                new Point(maxX, minZ),
                new Point(maxX, maxZ),
                new Point(minX, maxZ)
        ));
    }

    public static Boundary square(double centerX, double centerZ, double halfExtent) {
        if (!Double.isFinite(halfExtent) || halfExtent <= 0.0D) return new Boundary(List.of());
        return rectangle(centerX - halfExtent, centerZ - halfExtent,
                centerX + halfExtent, centerZ + halfExtent);
    }

    public static VisualState visualState(boolean lootArmed, int activeCrew, int crewInside) {
        if (activeCrew <= 0 || crewInside <= 0) return VisualState.IDLE;
        if (!lootArmed || crewInside < activeCrew) return VisualState.CONTESTED;
        return VisualState.ACTIVE;
    }

    private static boolean onSegment(Point a, Point b, double x, double z) {
        double cross = (x - a.x()) * (b.z() - a.z()) - (z - a.z()) * (b.x() - a.x());
        if (Math.abs(cross) > EDGE_EPSILON) return false;
        double dot = (x - a.x()) * (b.x() - a.x()) + (z - a.z()) * (b.z() - a.z());
        if (dot < -EDGE_EPSILON) return false;
        double lengthSquared = Math.pow(b.x() - a.x(), 2.0D) + Math.pow(b.z() - a.z(), 2.0D);
        return dot <= lengthSquared + EDGE_EPSILON;
    }
}
