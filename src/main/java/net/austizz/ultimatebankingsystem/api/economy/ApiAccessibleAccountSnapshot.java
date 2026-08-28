package net.austizz.ultimatebankingsystem.api.economy;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApiStatus.AvailableSince("2.1.0")
public record ApiAccessibleAccountSnapshot(
        UUID accountId,
        ApiAccountPrincipalType principalType,
        String principalId,
        UUID playerId,
        UUID bankId,
        String bankName,
        String bankStatus,
        String accountType,
        String accountTypeLabel,
        String businessLabel,
        BigDecimal balance,
        boolean primary,
        boolean frozen,
        String frozenReason,
        Map<UUID, ApiAccountRole> accessRoles,
        ApiAccountRole viewerRole,
        Set<ApiAccountCapability> capabilities,
        LocalDateTime createdAt
) {
    public ApiAccessibleAccountSnapshot {
        principalType = principalType == null ? ApiAccountPrincipalType.PLAYER : principalType;
        principalId = principalId == null ? "" : principalId;
        bankName = bankName == null ? "" : bankName;
        bankStatus = bankStatus == null ? "" : bankStatus;
        accountType = accountType == null ? "" : accountType;
        accountTypeLabel = accountTypeLabel == null ? "" : accountTypeLabel;
        businessLabel = businessLabel == null ? "" : businessLabel;
        balance = balance == null ? BigDecimal.ZERO : balance;
        frozenReason = frozenReason == null ? "" : frozenReason;
        accessRoles = accessRoles == null ? Map.of() : Map.copyOf(accessRoles);
        viewerRole = viewerRole == null ? ApiAccountRole.NONE : viewerRole;
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
}
