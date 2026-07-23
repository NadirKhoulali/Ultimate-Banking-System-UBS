package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.util.Locale;
import java.util.Map;

public record SafeBlockBounds(String dimension,
                              int minX,
                              int minY,
                              int minZ,
                              int maxX,
                              int maxY,
                              int maxZ) {
    public SafeBlockBounds {
        dimension = normalizeDimension(dimension);
        int cleanMinX = Math.min(minX, maxX);
        int cleanMinY = Math.min(minY, maxY);
        int cleanMinZ = Math.min(minZ, maxZ);
        int cleanMaxX = Math.max(minX, maxX);
        int cleanMaxY = Math.max(minY, maxY);
        int cleanMaxZ = Math.max(minZ, maxZ);
        minX = cleanMinX;
        minY = cleanMinY;
        minZ = cleanMinZ;
        maxX = cleanMaxX;
        maxY = cleanMaxY;
        maxZ = cleanMaxZ;
    }

    public static SafeBlockBounds from(Map<String, Object> map) {
        if (map == null || blank(map.get("dimension")) || !hasBounds(map)) {
            return null;
        }
        Integer minX = integer(map.get("minX"));
        Integer minY = integer(map.get("minY"));
        Integer minZ = integer(map.get("minZ"));
        Integer maxX = integer(map.get("maxX"));
        Integer maxY = integer(map.get("maxY"));
        Integer maxZ = integer(map.get("maxZ"));
        if (minX == null || minY == null || minZ == null || maxX == null || maxY == null || maxZ == null) {
            return null;
        }
        return new SafeBlockBounds(string(map.get("dimension")), minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean contains(String dimension, int x, int y, int z) {
        return this.dimension.equals(normalizeDimension(dimension))
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean contains(SafeBlockBounds other) {
        return other != null
                && dimension.equals(other.dimension)
                && other.minX >= minX && other.maxX <= maxX
                && other.minY >= minY && other.maxY <= maxY
                && other.minZ >= minZ && other.maxZ <= maxZ;
    }

    public boolean overlaps(SafeBlockBounds other) {
        return other != null
                && dimension.equals(other.dimension)
                && minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public static String normalizeDimension(String raw) {
        return raw == null || raw.isBlank() ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasBounds(Map<String, Object> map) {
        return map.containsKey("minX") && map.containsKey("minY") && map.containsKey("minZ")
                && map.containsKey("maxX") && map.containsKey("maxY") && map.containsKey("maxZ");
    }

    private static boolean blank(Object value) {
        return string(value).isBlank();
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(string(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
