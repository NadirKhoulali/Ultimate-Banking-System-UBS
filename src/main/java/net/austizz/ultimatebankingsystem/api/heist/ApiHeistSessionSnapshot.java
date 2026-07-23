package net.austizz.ultimatebankingsystem.api.heist;

import net.austizz.ultimatebankingsystem.api.ApiBlockBounds;
import net.austizz.ultimatebankingsystem.api.ApiBlockPosition;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApiStatus.AvailableSince("2.0.0")
public record ApiHeistSessionSnapshot(UUID sessionId,
                                      UUID leaderId,
                                      UUID bankId,
                                      String bankName,
                                      String premiseId,
                                      ApiBlockBounds premiseBounds,
                                      ApiBlockPosition exit,
                                      float exitYaw,
                                      String phase,
                                      long createdAtMillis,
                                      long countdownEndsAtTick,
                                      long startedAtMillis,
                                      long startedAtTick,
                                      long deadlineTick,
                                      long extractionStartedTick,
                                      long totalLootCents,
                                      boolean lootArmed,
                                      boolean alarmed,
                                      String alarmReason,
                                      List<ApiHeistMemberSnapshot> members,
                                      List<ApiHeistDrillSnapshot> drills,
                                      List<ApiHeistHackSnapshot> hacks,
                                      int completedComputerHacks,
                                      Set<String> breachedTargets,
                                      Set<UUID> cancelVotes) {
    public ApiHeistSessionSnapshot {
        bankName = bankName == null ? "" : bankName;
        premiseId = premiseId == null ? "" : premiseId;
        phase = phase == null ? "PLANNING" : phase;
        alarmReason = alarmReason == null ? "" : alarmReason;
        totalLootCents = Math.max(0L, totalLootCents);
        members = members == null ? List.of() : List.copyOf(members);
        drills = drills == null ? List.of() : List.copyOf(drills);
        hacks = hacks == null ? List.of() : List.copyOf(hacks);
        completedComputerHacks = Math.max(0, completedComputerHacks);
        breachedTargets = breachedTargets == null ? Set.of() : Set.copyOf(breachedTargets);
        cancelVotes = cancelVotes == null ? Set.of() : Set.copyOf(cancelVotes);
    }
}
