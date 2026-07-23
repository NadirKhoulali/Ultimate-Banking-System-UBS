package net.austizz.ultimatebankingsystem.api.heist;

import org.jetbrains.annotations.ApiStatus;

import java.util.Set;
import java.util.UUID;

@ApiStatus.AvailableSince("2.0.0")
public record ApiHeistMemberSnapshot(UUID playerId,
                                     String name,
                                     boolean accepted,
                                     boolean ready,
                                     boolean active,
                                     boolean dead,
                                     boolean online,
                                     long scoreCents,
                                     Set<UUID> bagIds) {
    public ApiHeistMemberSnapshot {
        name = name == null ? "" : name;
        scoreCents = Math.max(0L, scoreCents);
        bagIds = bagIds == null ? Set.of() : Set.copyOf(bagIds);
    }
}
