package net.austizz.ultimatebankingsystem.client;

/** Rendering profile for the tactical exfill ground decal. */
public record HeistExfillBorderProfile(
        double sampleLength,
        double coreWidth,
        double glowWidth,
        double floorSearchAbove,
        double floorSearchBelow,
        double renderDistance,
        int terrainRefreshTicks,
        int idleRgb,
        int activeRgb,
        int contestedRgb,
        int contestedFlashRgb
) {
    public static final HeistExfillBorderProfile TACTICAL = new HeistExfillBorderProfile(
            0.40D,
            0.075D,
            0.23D,
            4.0D,
            5.0D,
            128.0D,
            40,
            0xF4B942,
            0x3EF59A,
            0xFF8A24,
            0xFF334E
    );

    public HeistExfillBorderProfile {
        if (sampleLength <= 0.0D || coreWidth <= 0.0D || glowWidth < coreWidth
                || floorSearchAbove < 0.0D || floorSearchBelow < 0.0D
                || renderDistance <= 0.0D || terrainRefreshTicks < 1) {
            throw new IllegalArgumentException("Invalid heist exfill border profile.");
        }
    }
}
