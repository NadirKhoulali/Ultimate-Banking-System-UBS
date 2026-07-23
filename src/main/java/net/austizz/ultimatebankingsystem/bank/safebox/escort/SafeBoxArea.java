package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import java.util.Locale;

public record SafeBoxArea(String dimension,
                          int minX,
                          int minY,
                          int minZ,
                          int maxX,
                          int maxY,
                          int maxZ) {
    public SafeBoxArea {
        dimension = normalize(dimension);
        int lowX = Math.min(minX, maxX);
        int lowY = Math.min(minY, maxY);
        int lowZ = Math.min(minZ, maxZ);
        int highX = Math.max(minX, maxX);
        int highY = Math.max(minY, maxY);
        int highZ = Math.max(minZ, maxZ);
        minX = lowX;
        minY = lowY;
        minZ = lowZ;
        maxX = highX;
        maxY = highY;
        maxZ = highZ;
    }

    public boolean contains(String candidateDimension, EscortBlockPosition position) {
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        if (!dimension.equals(normalize(candidateDimension))) {
            return false;
        }
        return position.x() >= minX && position.x() <= maxX
                && position.y() >= minY && position.y() <= maxY
                && position.z() >= minZ && position.z() <= maxZ;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("dimension must not be blank");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
