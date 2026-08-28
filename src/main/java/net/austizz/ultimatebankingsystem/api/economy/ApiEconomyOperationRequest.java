package net.austizz.ultimatebankingsystem.api.economy;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApiStatus.AvailableSince("2.1.0")
public record ApiEconomyOperationRequest(
        String idempotencyKey,
        ApiEconomyOperationType type,
        ApiEconomyActorType actorType,
        UUID actorPlayerId,
        UUID accountId,
        UUID counterpartyAccountId,
        UUID bankId,
        UUID targetPlayerId,
        String institutionId,
        String escrowId,
        String role,
        BigDecimal amount,
        String reference,
        Map<String, String> metadata,
        List<ApiEconomyTransferLeg> legs
) {
    public ApiEconomyOperationRequest {
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        actorType = actorType == null ? ApiEconomyActorType.PLAYER : actorType;
        institutionId = institutionId == null ? "" : institutionId.trim();
        escrowId = escrowId == null ? "" : escrowId.trim();
        role = role == null ? "" : role.trim();
        amount = amount == null ? BigDecimal.ZERO : amount;
        reference = reference == null ? "" : reference.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        legs = legs == null ? List.of() : List.copyOf(legs);
    }
}
