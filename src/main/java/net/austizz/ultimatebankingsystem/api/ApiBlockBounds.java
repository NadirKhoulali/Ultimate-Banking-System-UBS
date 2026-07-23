package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("2.0.0")
public record ApiBlockBounds(String dimension,
                             int minX,
                             int minY,
                             int minZ,
                             int maxX,
                             int maxY,
                             int maxZ) {
    public ApiBlockBounds {
        dimension = dimension == null ? "" : dimension.trim().toLowerCase(java.util.Locale.ROOT);
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

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public boolean contains(ApiBlockPosition position) {
        return position != null
                && dimension.equals(position.dimension())
                && position.x() >= minX && position.x() <= maxX
                && position.y() >= minY && position.y() <= maxY
                && position.z() >= minZ && position.z() <= maxZ;
    }
}
