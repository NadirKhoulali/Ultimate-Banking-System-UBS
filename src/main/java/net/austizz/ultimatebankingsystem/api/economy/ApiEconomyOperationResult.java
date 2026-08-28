package net.austizz.ultimatebankingsystem.api.economy;

import org.jetbrains.annotations.ApiStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApiStatus.AvailableSince("2.1.0")
public record ApiEconomyOperationResult(
        ApiEconomyOperationStatus status,
        String code,
        String message,
        String idempotencyKey,
        UUID operationId,
        boolean duplicate,
        long revision,
        Instant completedAt,
        List<UUID> transactionIds,
        List<UUID> affectedAccountIds,
        ApiEscrowSnapshot escrow
) {
    public ApiEconomyOperationResult {
        status = status == null ? ApiEconomyOperationStatus.FAILED : status;
        code = code == null ? "UNKNOWN" : code;
        message = message == null ? "" : message;
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey;
        operationId = operationId == null ? UUID.randomUUID() : operationId;
        completedAt = completedAt == null ? Instant.now() : completedAt;
        transactionIds = transactionIds == null ? List.of() : List.copyOf(transactionIds);
        affectedAccountIds = affectedAccountIds == null ? List.of() : List.copyOf(affectedAccountIds);
    }

    public boolean success() {
        return status == ApiEconomyOperationStatus.SUCCEEDED;
    }

    public ApiEconomyOperationResult asDuplicate() {
        return new ApiEconomyOperationResult(status, code, message, idempotencyKey, operationId,
                true, revision, completedAt, transactionIds, affectedAccountIds, escrow);
    }
}
