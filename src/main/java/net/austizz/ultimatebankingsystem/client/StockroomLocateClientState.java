package net.austizz.ultimatebankingsystem.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class StockroomLocateClientState {
    private static boolean active;
    private static String dimensionId = "";
    private static int x;
    private static int y;
    private static int z;
    private static int slot;

    private StockroomLocateClientState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static String getDimensionId() {
        return dimensionId == null ? "" : dimensionId;
    }

    public static int getX() {
        return x;
    }

    public static int getY() {
        return y;
    }

    public static int getZ() {
        return z;
    }

    public static int getSlot() {
        return slot;
    }

    public static void set(boolean active, String dimensionId, int x, int y, int z, int slot) {
        StockroomLocateClientState.active = active;
        StockroomLocateClientState.dimensionId = dimensionId == null ? "" : dimensionId;
        StockroomLocateClientState.x = x;
        StockroomLocateClientState.y = y;
        StockroomLocateClientState.z = z;
        StockroomLocateClientState.slot = slot;
    }

    public static void clear() {
        set(false, "", 0, 0, 0, 0);
    }
}
