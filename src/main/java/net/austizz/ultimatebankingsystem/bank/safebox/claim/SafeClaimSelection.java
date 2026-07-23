package net.austizz.ultimatebankingsystem.bank.safebox.claim;

public record SafeClaimSelection(Corner firstCorner, Corner secondCorner, Exit exit) {
    public record Corner(String dimension, int x, int y, int z) {
    }

    public record Exit(String dimension, int x, int y, int z, float yaw) {
    }
}
