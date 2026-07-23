package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.network.RfidScannerOpenPayload;
import net.austizz.ultimatebankingsystem.network.RfidTargetSelectPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class RfidTargetSelectionClientState {
    private static boolean active;
    private static String scannerDimensionId = "";
    private static int scannerX;
    private static int scannerY;
    private static int scannerZ;
    private static String targetType = "SUCCESS";
    private static String pin = "";

    private RfidTargetSelectionClientState() {
    }

    public static void start(RfidScannerOpenPayload payload, String type, String sessionPin) {
        if (payload == null) {
            clear();
            return;
        }
        active = true;
        scannerDimensionId = payload.dimensionId();
        scannerX = payload.x();
        scannerY = payload.y();
        scannerZ = payload.z();
        targetType = "FAIL".equalsIgnoreCase(type) ? "FAIL" : "SUCCESS";
        pin = sessionPin == null ? "" : sessionPin;
    }

    public static boolean isActive() {
        return active;
    }

    public static RfidTargetSelectPayload buildPayload(String targetDimensionId, BlockPos targetPos, Direction face) {
        return new RfidTargetSelectPayload(
                scannerDimensionId,
                scannerX,
                scannerY,
                scannerZ,
                targetDimensionId,
                targetPos.getX(),
                targetPos.getY(),
                targetPos.getZ(),
                face == null ? Direction.UP.name() : face.name(),
                targetType,
                pin
        );
    }

    public static String retainedPin(RfidScannerOpenPayload payload) {
        return payload != null && payload.authenticated() && matchesScanner(payload) ? pin : "";
    }

    public static void finishSelection() {
        active = false;
        targetType = "SUCCESS";
    }

    public static void clearSession() {
        clear();
    }

    private static boolean matchesScanner(RfidScannerOpenPayload payload) {
        return payload.dimensionId().equals(scannerDimensionId)
                && payload.x() == scannerX
                && payload.y() == scannerY
                && payload.z() == scannerZ;
    }

    public static void clear() {
        active = false;
        scannerDimensionId = "";
        scannerX = 0;
        scannerY = 0;
        scannerZ = 0;
        targetType = "SUCCESS";
        pin = "";
    }
}
