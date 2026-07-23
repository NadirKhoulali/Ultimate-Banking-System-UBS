package net.austizz.ultimatebankingsystem.claim;

public record ClaimOutline(String dimensionId,
                           String type,
                           String ownerId,
                           String ownerName,
                           int minX,
                           int minY,
                           int minZ,
                           int maxX,
                           int maxY,
                           int maxZ) {
    public ClaimOutline {
        dimensionId = clean(dimensionId, 96);
        type = clean(type, 48);
        ownerId = clean(ownerId, 36);
        ownerName = clean(ownerName, 72);
        int x1 = Math.min(minX, maxX);
        int y1 = Math.min(minY, maxY);
        int z1 = Math.min(minZ, maxZ);
        int x2 = Math.max(minX, maxX);
        int y2 = Math.max(minY, maxY);
        int z2 = Math.max(minZ, maxZ);
        minX = x1;
        minY = y1;
        minZ = z1;
        maxX = x2;
        maxY = y2;
        maxZ = z2;
    }

    public ClaimOutline(String dimensionId,
                        String type,
                        String ownerName,
                        int minX,
                        int minY,
                        int minZ,
                        int maxX,
                        int maxY,
                        int maxZ) {
        this(dimensionId, type, "", ownerName,
                minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean ownedBy(java.util.UUID playerId) {
        return playerId != null && playerId.toString().equalsIgnoreCase(ownerId);
    }

    public boolean near(int x, int y, int z, int range) {
        long dx = x < minX ? (long) minX - x : Math.max(0L, (long) x - maxX);
        long dy = y < minY ? (long) minY - y : Math.max(0L, (long) y - maxY);
        long dz = z < minZ ? (long) minZ - z : Math.max(0L, (long) z - maxZ);
        long safeRange = Math.max(0L, range);
        return dx * dx + dy * dy + dz * dz <= safeRange * safeRange;
    }

    private static String clean(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
