package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("1.0.0")
public interface UltimateBankingApi {
    ApiResult getBalance(UUID accountId);

    ApiResult deposit(UUID accountId, long amount);

    ApiResult withdraw(UUID accountId, long amount);

    ApiResult transfer(UUID senderAccountId, UUID receiverAccountId, long amount);

    ApiResult shopPurchase(UUID accountId, long amount, String shopName);
}
