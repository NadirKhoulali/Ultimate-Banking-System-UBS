package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("1.2.0")
public interface UltimateBankingApi {
    String getApiVersion();

    @ApiStatus.AvailableSince("1.2.1")
    String formatMoneyRounded(BigDecimal amount);

    @ApiStatus.AvailableSince("1.2.1")
    String formatMoneyRounded(long amount);

    boolean isServerAvailable();

    ApiResult getBalance(UUID accountId);

    ApiResult deposit(UUID accountId, long amount);

    ApiResult withdraw(UUID accountId, long amount);

    ApiResult transfer(UUID senderAccountId, UUID receiverAccountId, long amount);

    ApiTransactionResult deposit(UUID accountId, long amount, String reference);

    ApiTransactionResult deposit(UUID accountId, BigDecimal amount, String reference);

    ApiTransactionResult withdraw(UUID accountId, long amount, String reference);

    ApiTransactionResult withdraw(UUID accountId, BigDecimal amount, String reference);

    ApiTransactionResult transfer(UUID senderAccountId, UUID receiverAccountId, long amount, String reference);

    ApiTransactionResult transfer(UUID senderAccountId, UUID receiverAccountId, BigDecimal amount, String reference);

    ApiTransactionResult depositToPrimary(UUID playerId, long amount, String reference);

    ApiTransactionResult depositToPrimary(UUID playerId, BigDecimal amount, String reference);

    ApiTransactionResult withdrawFromPrimary(UUID playerId, long amount, String reference);

    ApiTransactionResult withdrawFromPrimary(UUID playerId, BigDecimal amount, String reference);

    ApiTransactionResult transferFromPrimary(UUID senderPlayerId, UUID receiverAccountId, long amount, String reference);

    ApiTransactionResult transferFromPrimary(UUID senderPlayerId, UUID receiverAccountId, BigDecimal amount, String reference);

    ApiTransactionResult transferToPrimary(UUID senderAccountId, UUID receiverPlayerId, long amount, String reference);

    ApiTransactionResult transferToPrimary(UUID senderAccountId, UUID receiverPlayerId, BigDecimal amount, String reference);

    Optional<UUID> getPrimaryAccountId(UUID playerId);

    String getAccountStatus(UUID accountId);

    ApiResult validateAccountCanSend(UUID accountId, long amount);

    ApiResult validateAccountCanSend(UUID accountId, BigDecimal amount);

    ApiResult validateAccountCanReceive(UUID accountId);

    @ApiStatus.AvailableSince("1.2.2")
    ApiResult validateAccountCanInteract(UUID accountId, String interactionName);

    @ApiStatus.AvailableSince("1.2.2")
    ApiAlertResult sendAccountAccessDeniedAlert(UUID playerId, UUID accountId, String interactionName, int durationMs);

    ApiAlertResult sendUiAlert(UUID playerId, String title, String message, ApiAlertTone tone, int durationMs);

    ApiAlertResult sendUiAlert(UUID playerId, String title, String message, boolean success, int durationMs, int toneCode);

    ApiAlertResult sendLegacyUiAlert(UUID playerId, String title, String legacyMessage, int durationMs);

    ApiAlertResult sendSuccessUiAlert(UUID playerId, String title, String message, int durationMs);

    ApiAlertResult sendErrorUiAlert(UUID playerId, String title, String message, int durationMs);

    ApiAlertResult sendInfoUiAlert(UUID playerId, String title, String message, int durationMs);

    ApiAlertResult sendWarningUiAlert(UUID playerId, String title, String message, int durationMs);

    List<ApiAlertTone> getSupportedUiAlertTones();

    @ApiStatus.AvailableSince("1.3.0")
    ApiNotificationResult sendNotification(UUID playerId, ApiNotificationRequest request);

    @ApiStatus.AvailableSince("1.3.0")
    ApiNotificationResult dismissNotification(UUID playerId, String notificationId);

    @ApiStatus.AvailableSince("1.3.0")
    ApiNotificationResult clearNotificationChannel(UUID playerId, String channel);

    @ApiStatus.AvailableSince("1.3.0")
    ApiNotificationResult clearNotifications(UUID playerId);

    @ApiStatus.AvailableSince("1.3.0")
    List<ApiNotificationType> getSupportedNotificationTypes();

    @ApiStatus.AvailableSince("1.3.0")
    List<ApiNotificationPriority> getSupportedNotificationPriorities();

    @ApiStatus.AvailableSince("1.3.0")
    List<ApiNotificationPlacement> getSupportedNotificationPlacements();

    @ApiStatus.AvailableSince("1.4.1")
    ApiShopPriceStatistics getItemShopPriceStatistics(ItemStack item, ApiShopPriceScope scope);

    @ApiStatus.AvailableSince("1.4.1")
    ApiShopPriceStatistics getRegularShopPriceStatistics(ItemStack item);

    @ApiStatus.AvailableSince("1.4.1")
    ApiShopPriceStatistics getAllShelfPriceStatistics(ItemStack item);

    @ApiStatus.AvailableSince("1.4.1")
    ApiShopPriceStatistics getNonCreativeShelfPriceStatistics(ItemStack item);

    @ApiStatus.AvailableSince("1.4.1")
    ApiShopPriceStatistics getCreativeShelfPriceStatistics(ItemStack item);

    ApiResult shopPurchase(UUID accountId, long amount, String shopName);
    ApiResult shopPurchase(UUID payerAccountId, UUID merchantAccountId, long amount, String shopName, String reference);

    ApiItemResult issueBankNote(UUID sourceAccountId, long amountDollars, UUID issuerPlayerId, String issuerName);

    ApiItemResult issueCheque(UUID sourceAccountId,
                              UUID recipientPlayerId,
                              long amountDollars,
                              UUID writerPlayerId,
                              String writerName,
                              String recipientName);

    ApiCashResult giveDollarBills(UUID playerId, int denomination, int billCount);

    ApiCashResult takeDollarBills(UUID playerId, int denomination, int billCount);

    ApiCashResult giveCoins(UUID playerId, int denominationCents, int coinCount);

    ApiCashResult takeCoins(UUID playerId, int denominationCents, int coinCount);

    List<Integer> getSupportedBillDenominations();

    List<Integer> getSupportedCoinDenominations();

    List<ItemStack> createDollarBillStacks(int denomination, int billCount);

    List<ItemStack> createCoinStacks(int denominationCents, int coinCount);

    int getPlayerBillCount(UUID playerId, int denomination);

    int getPlayerCoinCount(UUID playerId, int denominationCents);

    int getPlayerCashOnHand(UUID playerId);

    int getPlayerCashOnHandCents(UUID playerId);

    boolean accountExists(UUID accountId);

    boolean bankExists(UUID bankId);

    Optional<ApiAccountSnapshot> getAccountSnapshot(UUID accountId);

    Optional<ApiAccountSnapshot> getPrimaryAccountSnapshot(UUID playerId);

    List<ApiAccountSnapshot> getPlayerAccounts(UUID playerId);

    List<UUID> getPlayerAccountIds(UUID playerId);

    List<ApiAccountSnapshot> getBankAccounts(UUID bankId);

    ApiResult setPrimaryAccount(UUID playerId, UUID accountId);

    boolean playerHasAnyAccount(UUID playerId);

    boolean playerHasPrimaryAccount(UUID playerId);

    boolean playerHasAvailableAccount(UUID playerId);

    boolean playerHasAvailablePrimaryAccount(UUID playerId);

    boolean playerHasFrozenAccount(UUID playerId);

    boolean playerOwnsAccount(UUID playerId, UUID accountId);

    boolean playerOwnsBank(UUID playerId, UUID bankId);

    @ApiStatus.AvailableSince("2.0.0")
    boolean playerOwnsAnyBank(UUID playerId);

    @ApiStatus.AvailableSince("2.0.0")
    List<ApiBankSnapshot> getPlayerOwnedBanks(UUID playerId);

    @ApiStatus.AvailableSince("2.0.0")
    boolean shopExists(UUID shopId);

    @ApiStatus.AvailableSince("2.0.0")
    boolean playerOwnsShop(UUID playerId, UUID shopId);

    @ApiStatus.AvailableSince("2.0.0")
    boolean playerOwnsAnyShop(UUID playerId);

    @ApiStatus.AvailableSince("2.0.0")
    List<UUID> getPlayerOwnedShopIds(UUID playerId);

    boolean accountBelongsToBank(UUID accountId, UUID bankId);

    boolean accountIsFrozen(UUID accountId);

    boolean accountIsPrimary(UUID accountId);

    boolean accountCanSend(UUID accountId, long amount);

    boolean accountCanSend(UUID accountId, BigDecimal amount);

    boolean accountCanReceive(UUID accountId);

    boolean primaryAccountCanSend(UUID playerId, long amount);

    boolean primaryAccountCanSend(UUID playerId, BigDecimal amount);

    boolean primaryAccountCanReceive(UUID playerId);

    boolean bankAcceptsTransactions(UUID bankId);

    Optional<ApiBankSnapshot> getBankSnapshot(UUID bankId);

    List<ApiBankSnapshot> getBanks();

    Optional<ApiTransactionSnapshot> getTransactionSnapshot(UUID transactionId);

    List<ApiTransactionSnapshot> getAccountTransactions(UUID accountId, int limit);

    List<ApiTransactionSnapshot> getPlayerTransactions(UUID playerId, int limit);

    boolean hasPlayerEverStolen(UUID playerId);

    List<UUID> getPlayersStolenFrom(UUID playerId);

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
