package net.austizz.ultimatebankingsystem.api.heist;

import net.austizz.ultimatebankingsystem.api.ApiBlockPosition;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("2.0.0")
public record ApiHeistHackSnapshot(ApiBlockPosition position,
                                   long finishesAtTick,
                                   long pausedUntilTick,
                                   boolean waitingForRestart) {
    public ApiHeistHackSnapshot {
        finishesAtTick = Math.max(0L, finishesAtTick);
        pausedUntilTick = Math.max(0L, pausedUntilTick);
    }
}
