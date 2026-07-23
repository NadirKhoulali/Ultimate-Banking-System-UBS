package net.austizz.ultimatebankingsystem.api.heist;

import net.austizz.ultimatebankingsystem.api.ApiBlockPosition;
import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

@ApiStatus.AvailableSince("2.0.0")
public record ApiHeistDrillSnapshot(String targetType,
                                    UUID ownerId,
                                    ApiBlockPosition position,
                                    long finishesAtTick,
                                    int jamsRemaining,
                                    boolean jammed,
                                    boolean completed) {
    public ApiHeistDrillSnapshot {
        targetType = targetType == null ? "" : targetType;
        finishesAtTick = Math.max(0L, finishesAtTick);
        jamsRemaining = Math.max(0, jamsRemaining);
    }
}
