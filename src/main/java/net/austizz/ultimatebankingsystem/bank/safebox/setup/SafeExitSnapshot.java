package net.austizz.ultimatebankingsystem.bank.safebox.setup;

public record SafeExitSnapshot(String dimension, int x, int y, int z, float yaw) {
    public SafeExitSnapshot {
        dimension = SafeBlockBounds.normalizeDimension(dimension);
    }
}
