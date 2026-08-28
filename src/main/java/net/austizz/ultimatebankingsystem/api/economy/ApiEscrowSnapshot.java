package net.austizz.ultimatebankingsystem.api.economy;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApiStatus.AvailableSince("2.1.0")
public record ApiEscrowSnapshot(
        String escrowId,
        String purpose,
        String status,
        UUID holdingAccountId,
        BigDecimal balance,
        List<UUID> contributorAccountIds,
        Instant createdAt,
        Instant completedAt
) {
    public ApiEscrowSnapshot {
        escrowId = escrowId == null ? "" : escrowId;
        purpose = purpose == null ? "" : purpose;
        status = status == null ? "" : status;
        balance = balance == null ? BigDecimal.ZERO : balance;
        contributorAccountIds = contributorAccountIds == null ? List.of() : List.copyOf(contributorAccountIds);
    }
}
