package net.austizz.ultimatebankingsystem.api.economy;

import org.jetbrains.annotations.ApiStatus;

import java.time.Instant;
import java.util.List;

@ApiStatus.AvailableSince("2.1.0")
public record ApiEconomySnapshot(
        String apiVersion,
        long revision,
        Instant generatedAt,
        List<ApiAccessibleAccountSnapshot> accounts,
        List<ApiEconomyTransactionSnapshot> transactions,
        List<ApiEscrowSnapshot> escrows
) {
    public ApiEconomySnapshot {
        apiVersion = apiVersion == null ? "" : apiVersion;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
        escrows = escrows == null ? List.of() : List.copyOf(escrows);
    }
}
