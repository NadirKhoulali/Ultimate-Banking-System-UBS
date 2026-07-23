package net.austizz.ultimatebankingsystem.api.general;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@ApiStatus.AvailableSince("2.0.0")
public record ApiPlayerPortfolioSnapshot(UUID playerId,
                                         int accountCount,
                                         UUID primaryAccountId,
                                         BigDecimal totalBalance,
                                         List<UUID> ownedBankIds,
                                         List<UUID> accessibleBankIds,
                                         List<UUID> ownedShopIds,
                                         List<UUID> accessibleShopIds,
                                         UUID currentHeistSessionId) {
    public ApiPlayerPortfolioSnapshot {
        accountCount = Math.max(0, accountCount);
        totalBalance = totalBalance == null ? BigDecimal.ZERO : totalBalance;
        ownedBankIds = ownedBankIds == null ? List.of() : List.copyOf(ownedBankIds);
        accessibleBankIds = accessibleBankIds == null ? List.of() : List.copyOf(accessibleBankIds);
        ownedShopIds = ownedShopIds == null ? List.of() : List.copyOf(ownedShopIds);
        accessibleShopIds = accessibleShopIds == null ? List.of() : List.copyOf(accessibleShopIds);
    }
}
