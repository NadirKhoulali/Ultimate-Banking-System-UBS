package net.austizz.ultimatebankingsystem.api.economy;

import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.1.0")
public interface UltimateEconomyApi {
    String getApiVersion();

    ApiEconomySnapshot snapshot(ApiEconomySnapshotRequest request);

    Optional<ApiEconomyOperationResult> findOperation(String idempotencyKey);

    ApiEconomyOperationResult execute(ApiEconomyOperationRequest request);
}
