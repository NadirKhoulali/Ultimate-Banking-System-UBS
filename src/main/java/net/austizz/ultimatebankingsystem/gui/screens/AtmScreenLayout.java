package net.austizz.ultimatebankingsystem.gui.screens;

/** Responsive bounds for the vanilla-scaled ATM screen. */
public record AtmScreenLayout(int width, int height, boolean compact) {
    private static final int TARGET_WIDTH = 420;
    private static final int TARGET_HEIGHT = 300;

    public static AtmScreenLayout forViewport(int viewportWidth, int viewportHeight) {
        int availableWidth = Math.max(240, viewportWidth - 16);
        int availableHeight = Math.max(210, viewportHeight - 16);
        int width = Math.min(TARGET_WIDTH, availableWidth);
        int height = Math.min(TARGET_HEIGHT, availableHeight);
        return new AtmScreenLayout(width, height, width < 360 || height < 260);
    }
}
