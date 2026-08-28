package net.austizz.ultimatebankingsystem.api.economy;

import org.jetbrains.annotations.ApiStatus;

import java.time.Instant;
import java.util.UUID;

@ApiStatus.AvailableSince("2.1.0")
public record ApiEconomySnapshotRequest(
        UUID viewerPlayerId,
        boolean includeAllAccounts,
        boolean includeTransactions,
        Instant transactionsSince,
        int transactionLimit
) {
    public ApiEconomySnapshotRequest {
        transactionLimit = Math.max(0, Math.min(50_000, transactionLimit));
    }

    public static ApiEconomySnapshotRequest forPlayer(UUID playerId) {
        return new ApiEconomySnapshotRequest(playerId, false, true,
                Instant.now().minusSeconds(366L * 24L * 60L * 60L), 10_000);
    }

    public static ApiEconomySnapshotRequest reconciliation() {
        return new ApiEconomySnapshotRequest(null, true, true,
                Instant.now().minusSeconds(366L * 24L * 60L * 60L), 50_000);
    }
}
