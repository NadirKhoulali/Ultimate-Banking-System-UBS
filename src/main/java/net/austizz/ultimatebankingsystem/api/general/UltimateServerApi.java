package net.austizz.ultimatebankingsystem.api.general;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0.0")
public interface UltimateServerApi {
    boolean isAvailable();

    ApiServerSnapshot getSnapshot();

    Set<ApiFeature> getAvailableFeatures();

    boolean isFeatureAvailable(ApiFeature feature);

    List<UUID> getOnlinePlayerIds();

    Optional<ApiPlayerPortfolioSnapshot> getPlayerPortfolio(UUID playerId);
}
