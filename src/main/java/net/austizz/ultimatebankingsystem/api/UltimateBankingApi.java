package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("1.1.0")
public interface UltimateBankingApi {
    String getApiVersion();

    boolean isServerAvailable();

    ApiResult getBalance(UUID accountId);

    ApiResult deposit(UUID accountId, long amount);

    ApiResult withdraw(UUID accountId, long amount);

    ApiResult transfer(UUID senderAccountId, UUID receiverAccountId, long amount);

    ApiResult shopPurchase(UUID accountId, long amount, String shopName);

    ApiItemResult issueBankNote(UUID sourceAccountId, long amountDollars, UUID issuerPlayerId, String issuerName);

    ApiItemResult issueCheque(UUID sourceAccountId,
                              UUID recipientPlayerId,
                              long amountDollars,
                              UUID writerPlayerId,
                              String writerName,
                              String recipientName);

    ApiCashResult giveDollarBills(UUID playerId, int denomination, int billCount);

    ApiCashResult takeDollarBills(UUID playerId, int denomination, int billCount);

    List<Integer> getSupportedBillDenominations();

    List<ItemStack> createDollarBillStacks(int denomination, int billCount);

    int getPlayerBillCount(UUID playerId, int denomination);

    int getPlayerCashOnHand(UUID playerId);

    boolean accountExists(UUID accountId);

    boolean bankExists(UUID bankId);

    Optional<ApiAccountSnapshot> getAccountSnapshot(UUID accountId);

    Optional<ApiAccountSnapshot> getPrimaryAccountSnapshot(UUID playerId);

    List<ApiAccountSnapshot> getPlayerAccounts(UUID playerId);

    List<ApiAccountSnapshot> getBankAccounts(UUID bankId);

    ApiResult setPrimaryAccount(UUID playerId, UUID accountId);

    Optional<ApiBankSnapshot> getBankSnapshot(UUID bankId);

    List<ApiBankSnapshot> getBanks();

    Optional<ApiTransactionSnapshot> getTransactionSnapshot(UUID transactionId);

    List<ApiTransactionSnapshot> getAccountTransactions(UUID accountId, int limit);

    List<ApiTransactionSnapshot> getPlayerTransactions(UUID playerId, int limit);

    ApiResult getPlayerTotalBalance(UUID playerId);

    ApiResult getPlayerPrimaryBalance(UUID playerId);

    int getPlayerAccountCount(UUID playerId);

    ApiResult getBankTotalDeposits(UUID bankId);

    ApiResult getBankReserve(UUID bankId);

    String getBankStatus(UUID bankId);

    String resolvePlaceholder(UUID playerId, String token);

    String resolvePlaceholders(UUID playerId, String text);

    List<String> getSupportedPlaceholders();
}
