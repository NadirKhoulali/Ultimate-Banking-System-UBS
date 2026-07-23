package net.austizz.ultimatebankingsystem.api.general;

import net.austizz.ultimatebankingsystem.api.ApiResult;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;
import net.austizz.ultimatebankingsystem.api.bank.ApiBankManagementSnapshot;
import net.austizz.ultimatebankingsystem.api.heist.ApiHeistSessionSnapshot;
import net.austizz.ultimatebankingsystem.api.internal.ApiInternals;
import net.austizz.ultimatebankingsystem.api.shop.ApiShopManagementSnapshot;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApiStatus.Internal
public final class UltimateServerApiImpl implements UltimateServerApi {
    private static final Set<ApiFeature> FEATURES = Set.copyOf(EnumSet.allOf(ApiFeature.class));

    @Override
    public boolean isAvailable() {
        return ApiInternals.server() != null && ApiInternals.centralBank() != null;
    }

    @Override
    public ApiServerSnapshot getSnapshot() {
        MinecraftServer server = ApiInternals.server();
        CentralBank centralBank = ApiInternals.centralBank();
        if (server == null || centralBank == null) {
            return new ApiServerSnapshot(UltimateBankingApiProvider.get().getApiVersion(), 0, 0, 0, 0, 0, FEATURES);
        }
        int accounts = centralBank.getBanks().values().stream()
                .filter(java.util.Objects::nonNull)
                .mapToInt(bank -> bank.getBankAccounts().size())
                .sum();
        return new ApiServerSnapshot(UltimateBankingApiProvider.get().getApiVersion(),
                server.getPlayerCount(), centralBank.getBanks().size(), accounts,
                UltimateBankingApiProvider.shops().getShops().size(),
                UltimateBankingApiProvider.heists().getActiveSessions().size(), FEATURES);
    }

    @Override
    public Set<ApiFeature> getAvailableFeatures() {
        return FEATURES;
    }

    @Override
    public boolean isFeatureAvailable(ApiFeature feature) {
        return feature != null && FEATURES.contains(feature);
    }

    @Override
    public List<UUID> getOnlinePlayerIds() {
        MinecraftServer server = ApiInternals.server();
        if (server == null) return List.of();
        return server.getPlayerList().getPlayers().stream().map(player -> player.getUUID()).toList();
    }

    @Override
    public Optional<ApiPlayerPortfolioSnapshot> getPlayerPortfolio(UUID playerId) {
        if (!isAvailable() || playerId == null) return Optional.empty();
        List<ApiBankManagementSnapshot> ownedBanks = UltimateBankingApiProvider.banks().getOwnedBanks(playerId);
        List<ApiBankManagementSnapshot> accessibleBanks = UltimateBankingApiProvider.banks().getAccessibleBanks(playerId);
        List<ApiShopManagementSnapshot> ownedShops = UltimateBankingApiProvider.shops().getOwnedShops(playerId);
        List<ApiShopManagementSnapshot> accessibleShops = UltimateBankingApiProvider.shops().getAccessibleShops(playerId);
        ApiResult total = UltimateBankingApiProvider.get().getPlayerTotalBalance(playerId);
        UUID sessionId = UltimateBankingApiProvider.heists().getPlayerSession(playerId)
                .map(ApiHeistSessionSnapshot::sessionId).orElse(null);
        return Optional.of(new ApiPlayerPortfolioSnapshot(playerId,
                UltimateBankingApiProvider.get().getPlayerAccountCount(playerId),
                UltimateBankingApiProvider.get().getPrimaryAccountId(playerId).orElse(null),
                total.success() ? total.balanceAfter() : BigDecimal.ZERO,
                ownedBanks.stream().map(ApiBankManagementSnapshot::bankId).toList(),
                accessibleBanks.stream().map(ApiBankManagementSnapshot::bankId).toList(),
                ownedShops.stream().map(ApiShopManagementSnapshot::shopId).toList(),
                accessibleShops.stream().map(ApiShopManagementSnapshot::shopId).toList(), sessionId));
    }
}
