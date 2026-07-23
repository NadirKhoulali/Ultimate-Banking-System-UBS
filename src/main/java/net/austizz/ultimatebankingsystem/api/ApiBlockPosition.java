package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("2.0.0")
public record ApiBlockPosition(String dimension, int x, int y, int z) {
    public ApiBlockPosition {
        dimension = dimension == null ? "" : dimension.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
