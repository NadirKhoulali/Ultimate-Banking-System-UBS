package net.austizz.ultimatebankingsystem.client.renderer;

public final class SafeBoxDisplayLayout {
    private SafeBoxDisplayLayout() {
    }

    public static int layerCount(int inventorySlots) {
        return Math.max(1, (Math.max(1, inventorySlots) + 8) / 9);
    }

    public static Position position(int slot, int inventorySlots, double trayHeight) {
        int safeSlot = Math.max(0, slot);
        int layer = safeSlot / 9;
        int local = safeSlot % 9;
        int column = local % 3;
        int row = local / 3;
        int layers = layerCount(inventorySlots);
        double baseY = Math.min(0.08D, Math.max(0.055D, trayHeight * 0.45D));
        double layerStep = layers <= 1
                ? 0.0D
                : Math.min(0.12D, Math.max(0.08D,
                (Math.max(0.16D, trayHeight) - 0.15D) / (layers - 1)));
        return new Position(
                -0.23D + column * 0.23D,
                baseY + layer * layerStep,
                -0.19D + row * 0.19D,
                layer
        );
    }

    public record Position(double x, double y, double z, int layer) {
    }
}
