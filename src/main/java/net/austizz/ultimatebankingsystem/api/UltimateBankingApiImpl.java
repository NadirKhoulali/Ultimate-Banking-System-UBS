package net.austizz.ultimatebankingsystem.api;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

final class UltimateBankingApiImpl implements UltimateBankingApi {
    private static final UUID SHOP_TERMINAL_ID = UUID.nameUUIDFromBytes("ultimatebankingsystem:shop-terminal".getBytes());

    @Override
    public ApiResult getBalance(UUID accountId) {
        AccountHolder account = resolveAccount(accountId);
        if (account == null) {
            return ApiResult.fail("Account not found", BigDecimal.ZERO);
        }
        return ApiResult.ok(account.getBalance());
    }

    @Override
    public ApiResult deposit(UUID accountId, long amount) {
        if (amount <= 0) {
            return ApiResult.fail("Amount must be greater than zero", BigDecimal.ZERO);
        }
        AccountHolder account = resolveAccount(accountId);
        if (account == null) {
            return ApiResult.fail("Account not found", BigDecimal.ZERO);
        }
        boolean success = account.AddBalance(BigDecimal.valueOf(amount));
        if (!success) {
            return ApiResult.fail("Deposit failed", account.getBalance());
        }
        account.addTransaction(new UserTransaction(
                SHOP_TERMINAL_ID,
                account.getAccountUUID(),
                BigDecimal.valueOf(amount),
                LocalDateTime.now(),
                "API_DEPOSIT"
        ));
        return ApiResult.ok(account.getBalance());
    }

    @Override
    public ApiResult withdraw(UUID accountId, long amount) {
        if (amount <= 0) {
            return ApiResult.fail("Amount must be greater than zero", BigDecimal.ZERO);
        }
        AccountHolder account = resolveAccount(accountId);
        if (account == null) {
            return ApiResult.fail("Account not found", BigDecimal.ZERO);
        }
        boolean success = account.RemoveBalance(BigDecimal.valueOf(amount));
        if (!success) {
            return ApiResult.fail("Insufficient funds or account is unavailable", account.getBalance());
        }
        account.addTransaction(new UserTransaction(
                account.getAccountUUID(),
                SHOP_TERMINAL_ID,
                BigDecimal.valueOf(amount),
                LocalDateTime.now(),
                "API_WITHDRAW"
        ));
        return ApiResult.ok(account.getBalance());
    }

    @Override
    public ApiResult transfer(UUID senderAccountId, UUID receiverAccountId, long amount) {
        if (amount <= 0) {
            return ApiResult.fail("Amount must be greater than zero", BigDecimal.ZERO);
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return ApiResult.fail("Server unavailable", BigDecimal.ZERO);
        }

        AccountHolder sender = resolveAccount(senderAccountId);
        AccountHolder receiver = resolveAccount(receiverAccountId);
        if (sender == null || receiver == null) {
            BigDecimal current = sender == null ? BigDecimal.ZERO : sender.getBalance();
            return ApiResult.fail("Sender or receiver account not found", current);
        }
        if (senderAccountId.equals(receiverAccountId)) {
            return ApiResult.fail("Cannot transfer to the same account", sender.getBalance());
        }

        UserTransaction tx = new UserTransaction(
                senderAccountId,
                receiverAccountId,
                BigDecimal.valueOf(amount),
                LocalDateTime.now(),
                "API_TRANSFER"
        );
        boolean success = tx.makeTransaction(server);
        if (!success) {
            return ApiResult.fail("Transfer failed", sender.getBalance());
        }
        return ApiResult.ok(sender.getBalance());
    }

    @Override
    public ApiResult shopPurchase(UUID accountId, long amount, String shopName) {
        if (amount <= 0) {
            return ApiResult.fail("Amount must be greater than zero", BigDecimal.ZERO);
        }
        AccountHolder account = resolveAccount(accountId);
        if (account == null) {
            return ApiResult.fail("Account not found", BigDecimal.ZERO);
        }

        boolean success = account.RemoveBalance(BigDecimal.valueOf(amount));
        if (!success) {
            return ApiResult.fail("Insufficient funds", account.getBalance());
        }

        String merchant = (shopName == null || shopName.isBlank()) ? "Shop" : shopName.trim();
        account.addTransaction(new UserTransaction(
                account.getAccountUUID(),
                SHOP_TERMINAL_ID,
                BigDecimal.valueOf(amount),
                LocalDateTime.now(),
                "PURCHASE:" + merchant
        ));
        return ApiResult.ok(account.getBalance());
    }

    private AccountHolder resolveAccount(UUID accountId) {
        if (accountId == null) {
            return null;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        var centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return null;
        }
        return centralBank.SearchForAccountByAccountId(accountId);
    }
}
