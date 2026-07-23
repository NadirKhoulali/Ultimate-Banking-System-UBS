package net.austizz.ultimatebankingsystem.api.bank;

import net.austizz.ultimatebankingsystem.api.ApiManagementResult;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0.0")
public interface UltimateBankManagementApi {
    boolean isAvailable();

    List<ApiBankManagementSnapshot> getBanks();

    Optional<ApiBankManagementSnapshot> getBank(UUID bankId);

    Optional<ApiBankManagementSnapshot> findBank(String nameOrId);

    List<ApiBankManagementSnapshot> getOwnedBanks(UUID playerId);

    List<ApiBankManagementSnapshot> getAccessibleBanks(UUID playerId);

    boolean playerOwnsBank(UUID playerId, UUID bankId);

    boolean playerOwnsAnyBank(UUID playerId);

    boolean playerCanAccessBank(UUID playerId, UUID bankId);

    boolean playerCanManageSafeArea(UUID playerId, UUID bankId);

    boolean playerCanAccessProtectedSafeArea(UUID playerId, UUID bankId);

    boolean isBankUnderAttack(UUID bankId);

    ApiBankStaffingSnapshot getStaffing(UUID bankId);

    ApiSafeDepositSetupSnapshot getSafeDepositSetup(UUID bankId);

    ApiManagementResult setEmployeeSafeAccess(UUID actorId, UUID bankId, UUID employeeId, boolean allowed);

    ApiManagementResult setInterestRate(UUID actorId, UUID bankId, double annualPercent);
}
