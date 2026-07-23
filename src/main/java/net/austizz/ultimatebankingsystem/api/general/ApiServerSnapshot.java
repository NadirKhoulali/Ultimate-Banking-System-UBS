package net.austizz.ultimatebankingsystem.api.general;

import org.jetbrains.annotations.ApiStatus;

import java.util.Set;

@ApiStatus.AvailableSince("2.0.0")
public record ApiServerSnapshot(String apiVersion,
                                int onlinePlayers,
                                int banks,
                                int accounts,
                                int shops,
                                int activeHeists,
                                Set<ApiFeature> features) {
    public ApiServerSnapshot {
        apiVersion = apiVersion == null ? "" : apiVersion;
        onlinePlayers = Math.max(0, onlinePlayers);
        banks = Math.max(0, banks);
        accounts = Math.max(0, accounts);
        shops = Math.max(0, shops);
        activeHeists = Math.max(0, activeHeists);
        features = features == null ? Set.of() : Set.copyOf(features);
    }
}
