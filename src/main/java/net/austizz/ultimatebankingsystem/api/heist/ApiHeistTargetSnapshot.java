package net.austizz.ultimatebankingsystem.api.heist;

import net.austizz.ultimatebankingsystem.api.ApiBlockBounds;
import net.austizz.ultimatebankingsystem.api.ApiBlockPosition;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.UUID;

@ApiStatus.AvailableSince("2.0.0")
public record ApiHeistTargetSnapshot(UUID bankId,
                                     String bankName,
                                     String premiseId,
                                     ApiBlockBounds bounds,
                                     ApiBlockPosition exit,
                                     float exitYaw,
                                     ApiBlockPosition ownerPc,
                                     ApiBlockPosition vaultDoor,
                                     boolean eligible,
                                     List<String> blockers,
                                     int physicalLootSources,
                                     long bankCooldownRemainingMillis) {
    public ApiHeistTargetSnapshot {
        bankName = bankName == null ? "" : bankName;
        premiseId = premiseId == null ? "" : premiseId;
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        physicalLootSources = Math.max(0, physicalLootSources);
        bankCooldownRemainingMillis = Math.max(0L, bankCooldownRemainingMillis);
    }
}
