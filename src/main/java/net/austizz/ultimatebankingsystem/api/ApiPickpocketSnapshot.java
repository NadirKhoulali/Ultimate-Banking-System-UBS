package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

@ApiStatus.AvailableSince("1.3.0")
public record ApiPickpocketSnapshot(
        UUID thiefPlayerId,
        UUID victimPlayerId,
        String victimNameSnapshot,
        long timestampMillis,
        String stolenStackSummary
) {}
