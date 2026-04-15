package net.austizz.ultimatebankingsystem.api;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UltimateBankingApiImpl implements UltimateBankingApi {
    private static final UUID SHOP_TERMINAL_ID = UUID.nameUUIDFromBytes("ultimatebankingsystem:shop-terminal".getBytes());
    private static final String API_VERSION = "1.2.0";
    private static final int DEFAULT_TRANSACTION_LIMIT = 50;
    private static final int MAX_TRANSACTION_LIMIT = 500;
    private static final List<Integer> SUPPORTED_BILL_DENOMINATIONS = List.of(100, 50, 20, 10, 5, 2, 1);
    private static final List<Integer> SUPPORTED_COIN_DENOMINATIONS_CENTS = List.of(50, 25, 10, 5, 1);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([A-Za-z0-9_\\-]+)%");
    private static final List<String> SUPPORTED_PLACEHOLDERS = List.of(
            "%ubs_player_total_balance%",
            "%ubs_player_total_balance_raw%",
            "%ubs_player_primary_balance%",
            "%ubs_player_primary_balance_raw%",
            "%ubs_player_account_count%",
            "%ubs_player_primary_account_id%",
            "%ubs_player_primary_account_type%",
            "%ubs_player_primary_bank_id%",
            "%ubs_player_primary_bank_name%",
            "%ubs_bank_name%",
            "%ubs_bank_id%",
            "%ubs_bank_status%",
            "%ubs_bank_reserve%",
            "%ubs_bank_reserve_raw%",
            "%ubs_bank_total_deposits%",
            "%ubs_bank_total_deposits_raw%",
            "%ubs_bank_name_<bank-uuid>%",
            "%ubs_bank_status_<bank-uuid>%",
            "%ubs_bank_reserve_<bank-uuid>%",
            "%ubs_bank_reserve_raw_<bank-uuid>%",
            "%ubs_bank_total_deposits_<bank-uuid>%",
            "%ubs_bank_total_deposits_raw_<bank-uuid>%"
    );

    @Override
    public String getApiVersion() {
        return API_VERSION;
    }

    @Override
    public boolean isServerAvailable() {
        return resolveCentralBank() != null;
    }

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

    @Override
    public ApiResult shopPurchase(UUID payerAccountId,
                                  UUID merchantAccountId,
                                  long amount,
                                  String shopName,
                                  String reference) {
        if (amount <= 0) {
            return ApiResult.fail("Amount must be greater than zero", BigDecimal.ZERO);
        }
        if (merchantAccountId == null) {
            return ApiResult.fail("Merchant account is required", BigDecimal.ZERO);
        }
        if (payerAccountId == null) {
            return ApiResult.fail("Payer account is required", BigDecimal.ZERO);
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return ApiResult.fail("Server unavailable", BigDecimal.ZERO);
        }

        AccountHolder payer = resolveAccount(payerAccountId);
        AccountHolder merchant = resolveAccount(merchantAccountId);
        if (payer == null || merchant == null) {
            BigDecimal balance = payer == null ? BigDecimal.ZERO : payer.getBalance();
            return ApiResult.fail("Payer or merchant account not found", balance);
        }

        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return ApiResult.fail("Bank data unavailable", payer.getBalance());
        }
        Bank payerBank = centralBank.getBank(payer.getBankId());
        Bank merchantBank = centralBank.getBank(merchant.getBankId());
        if (payerBank == null || merchantBank == null) {
            return ApiResult.fail("Payer or merchant bank is unavailable", payer.getBalance());
        }
        String payerStatus = resolveBankStatusForTransactions(centralBank, payerBank);
        String merchantStatus = resolveBankStatusForTransactions(centralBank, merchantBank);
        if (blocksTransactions(payerStatus) || blocksTransactions(merchantStatus)) {
            return ApiResult.fail(
                    "Payment blocked by bank status. "
                            + safeBankName(payerBank) + ": " + payerStatus + " | "
                            + safeBankName(merchantBank) + ": " + merchantStatus + ".",
                    payer.getBalance()
            );
        }

        String merchantLabel = (shopName == null || shopName.isBlank()) ? "Payment Terminal" : shopName.trim();
        String normalizedReference = (reference == null || reference.isBlank()) ? "" : reference.trim();
        String description = normalizedReference.isBlank()
                ? "SHOP_PURCHASE:" + merchantLabel
                : "SHOP_PURCHASE:" + merchantLabel + "@" + normalizedReference;
        String selfDescription = normalizedReference.isBlank()
                ? "SHOP_PURCHASE_SELF:" + merchantLabel
                : "SHOP_PURCHASE_SELF:" + merchantLabel + "@" + normalizedReference;

        if (payerAccountId.equals(merchantAccountId)) {
            if (payer.isFrozen()) {
                return ApiResult.fail("Account is frozen", payer.getBalance());
            }
            UserTransaction selfTx = new UserTransaction(
                    payerAccountId,
                    merchantAccountId,
                    BigDecimal.valueOf(amount),
                    LocalDateTime.now(),
                    selfDescription
            );
            payer.addTransaction(selfTx);
            BankManager.markDirty();
            return ApiResult.ok(payer.getBalance());
        }

        UserTransaction tx = new UserTransaction(
                payerAccountId,
                merchantAccountId,
                BigDecimal.valueOf(amount),
                LocalDateTime.now(),
                description
        );
        boolean success = tx.makeTransaction(server);
        if (!success) {
            return ApiResult.fail("Payment failed", payer.getBalance());
        }
        return ApiResult.ok(payer.getBalance());
    }

    @Override
    public ApiItemResult issueBankNote(UUID sourceAccountId, long amountDollars, UUID issuerPlayerId, String issuerName) {
        if (amountDollars <= 0) {
            return ApiItemResult.fail("Amount must be greater than zero");
        }
        AccountHolder account = resolveAccount(sourceAccountId);
        if (account == null) {
            return ApiItemResult.fail("Account not found");
        }
        BigDecimal amount = BigDecimal.valueOf(amountDollars);
        if (!account.RemoveBalance(amount)) {
            return ApiItemResult.fail("Insufficient funds or account unavailable");
        }

        CentralBank centralBank = resolveCentralBank();
        String serial = UUID.randomUUID().toString();
        ItemStack note = new ItemStack(ModItems.BANK_NOTE.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("ubs_note_serial", serial);
        tag.putString("ubs_note_amount", amount.toPlainString());
        tag.putUUID("ubs_note_account", account.getAccountUUID());
        if (issuerPlayerId != null) {
            tag.putUUID("ubs_note_issuer_uuid", issuerPlayerId);
        }
        String resolvedIssuerName = (issuerName == null || issuerName.isBlank()) ? "Unknown" : issuerName.trim();
        tag.putString("ubs_note_issuer_name", resolvedIssuerName);
        tag.putString("ubs_note_source_account", account.getAccountUUID().toString());
        if (centralBank != null) {
            Bank sourceBank = centralBank.getBank(account.getBankId());
            if (sourceBank != null && sourceBank.getBankName() != null && !sourceBank.getBankName().isBlank()) {
                tag.putString("ubs_note_source_bank", sourceBank.getBankName());
            }
        }
        applyCustomTag(note, tag);
        ItemStackDataCompat.setCustomName(note, Component.literal("Bank Note - $" + amount.toPlainString()).withStyle(ChatFormatting.GOLD));

        account.addTransaction(new UserTransaction(
                account.getAccountUUID(),
                UUID.nameUUIDFromBytes("ultimatebankingsystem:note-issuer".getBytes()),
                amount,
                LocalDateTime.now(),
                "API_BANK_NOTE_ISSUED"
        ));
        return ApiItemResult.ok(note, serial, amount);
    }

    @Override
    public ApiItemResult issueCheque(UUID sourceAccountId,
                                     UUID recipientPlayerId,
                                     long amountDollars,
                                     UUID writerPlayerId,
                                     String writerName,
                                     String recipientName) {
        if (recipientPlayerId == null) {
            return ApiItemResult.fail("Recipient id is required");
        }
        if (amountDollars <= 0) {
            return ApiItemResult.fail("Amount must be greater than zero");
        }
        if (writerPlayerId != null && writerPlayerId.equals(recipientPlayerId)) {
            return ApiItemResult.fail("Writer and recipient cannot be the same player");
        }

        AccountHolder account = resolveAccount(sourceAccountId);
        if (account == null) {
            return ApiItemResult.fail("Account not found");
        }
        BigDecimal amount = BigDecimal.valueOf(amountDollars);
        if (!account.RemoveBalance(amount)) {
            return ApiItemResult.fail("Insufficient funds or account unavailable");
        }

        CentralBank centralBank = resolveCentralBank();
        String chequeId = UUID.randomUUID().toString();
        ItemStack cheque = new ItemStack(ModItems.CHEQUE.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("ubs_cheque_id", chequeId);
        tag.putString("ubs_cheque_amount", amount.toPlainString());
        tag.putUUID("ubs_cheque_recipient", recipientPlayerId);
        if (writerPlayerId != null) {
            tag.putUUID("ubs_cheque_writer", writerPlayerId);
        }
        String resolvedWriterName = (writerName == null || writerName.isBlank()) ? "Unknown" : writerName.trim();
        String resolvedRecipientName = (recipientName == null || recipientName.isBlank()) ? "Unknown" : recipientName.trim();
        tag.putString("ubs_cheque_recipient_name", resolvedRecipientName);
        tag.putString("ubs_cheque_writer_name", resolvedWriterName);
        tag.putString("ubs_cheque_source_account", account.getAccountUUID().toString());
        if (centralBank != null) {
            Bank sourceBank = centralBank.getBank(account.getBankId());
            if (sourceBank != null && sourceBank.getBankName() != null && !sourceBank.getBankName().isBlank()) {
                tag.putString("ubs_cheque_source_bank", sourceBank.getBankName());
            }
        }
        applyCustomTag(cheque, tag);
        ItemStackDataCompat.setCustomName(cheque, Component.literal("Cheque - $" + amount.toPlainString()).withStyle(ChatFormatting.GREEN));

        account.addTransaction(new UserTransaction(
                account.getAccountUUID(),
                UUID.nameUUIDFromBytes("ultimatebankingsystem:cheque-write".getBytes()),
                amount,
                LocalDateTime.now(),
                "API_CHEQUE_WRITE:" + recipientPlayerId
        ));
        return ApiItemResult.ok(cheque, chequeId, amount);
    }

    @Override
    public ApiCashResult giveDollarBills(UUID playerId, int denomination, int billCount) {
        if (billCount <= 0) {
            return ApiCashResult.fail("Bill count must be greater than zero", denomination, billCount);
        }
        if (!SUPPORTED_BILL_DENOMINATIONS.contains(denomination)) {
            return ApiCashResult.fail("Unsupported denomination", denomination, billCount);
        }
        ServerPlayer player = resolveOnlinePlayer(playerId);
        if (player == null) {
            return ApiCashResult.fail("Player is not online", denomination, billCount);
        }

        int[] plan = new int[DollarBills.DENOMINATIONS_DESC.length];
        int idx = indexForDenomination(denomination);
        if (idx < 0) {
            return ApiCashResult.fail("Unsupported denomination", denomination, billCount);
        }
        plan[idx] = billCount;
        DollarBills.giveBills(player, plan);
        return ApiCashResult.ok(denomination, billCount);
    }

    @Override
    public ApiCashResult takeDollarBills(UUID playerId, int denomination, int billCount) {
        if (billCount <= 0) {
            return ApiCashResult.fail("Bill count must be greater than zero", denomination, billCount);
        }
        if (!SUPPORTED_BILL_DENOMINATIONS.contains(denomination)) {
            return ApiCashResult.fail("Unsupported denomination", denomination, billCount);
        }
        ServerPlayer player = resolveOnlinePlayer(playerId);
        if (player == null) {
            return ApiCashResult.fail("Player is not online", denomination, billCount);
        }

        int available = countBills(player, denomination);
        if (available < billCount) {
            return ApiCashResult.fail("Not enough matching bills in inventory", denomination, billCount);
        }
        removeBillsByDenomination(player, denomination, billCount);
        return ApiCashResult.ok(denomination, billCount);
    }

    @Override
    public ApiCashResult giveCoins(UUID playerId, int denominationCents, int coinCount) {
        if (coinCount <= 0) {
            return ApiCashResult.fail("Coin count must be greater than zero", denominationCents, coinCount);
        }
        if (!SUPPORTED_COIN_DENOMINATIONS_CENTS.contains(denominationCents)) {
            return ApiCashResult.fail("Unsupported coin denomination (cents)", denominationCents, coinCount);
        }
        ServerPlayer player = resolveOnlinePlayer(playerId);
        if (player == null) {
            return ApiCashResult.fail("Player is not online", denominationCents, coinCount);
        }

        int idx = indexForCashDenominationCents(denominationCents);
        if (idx < 0) {
            return ApiCashResult.fail("Unsupported coin denomination (cents)", denominationCents, coinCount);
        }
        int[] plan = new int[DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length];
        plan[idx] = coinCount;
        DollarBills.giveCash(player, plan);
        return ApiCashResult.ok(denominationCents, coinCount);
    }

    @Override
    public ApiCashResult takeCoins(UUID playerId, int denominationCents, int coinCount) {
        if (coinCount <= 0) {
            return ApiCashResult.fail("Coin count must be greater than zero", denominationCents, coinCount);
        }
        if (!SUPPORTED_COIN_DENOMINATIONS_CENTS.contains(denominationCents)) {
            return ApiCashResult.fail("Unsupported coin denomination (cents)", denominationCents, coinCount);
        }
        ServerPlayer player = resolveOnlinePlayer(playerId);
        if (player == null) {
            return ApiCashResult.fail("Player is not online", denominationCents, coinCount);
        }

        Item coinItem = DollarBills.getCashItemForDenominationCents(denominationCents);
        if (coinItem == null) {
            return ApiCashResult.fail("Unsupported coin denomination (cents)", denominationCents, coinCount);
        }
        int available = countItem(player, coinItem);
        if (available < coinCount) {
            return ApiCashResult.fail("Not enough matching coins in inventory", denominationCents, coinCount);
        }
        removeItem(player, coinItem, coinCount);
        return ApiCashResult.ok(denominationCents, coinCount);
    }

    @Override
    public List<Integer> getSupportedBillDenominations() {
        return SUPPORTED_BILL_DENOMINATIONS;
    }

    @Override
    public List<Integer> getSupportedCoinDenominations() {
        return SUPPORTED_COIN_DENOMINATIONS_CENTS;
    }

    @Override
    public List<ItemStack> createDollarBillStacks(int denomination, int billCount) {
        if (billCount <= 0 || !SUPPORTED_BILL_DENOMINATIONS.contains(denomination)) {
            return List.of();
        }
        Item billItem = DollarBills.getItemForDenomination(denomination);
        if (billItem == null) {
            return List.of();
        }
        int maxStack = billItem.getMaxStackSize();
        int remaining = billCount;
        List<ItemStack> stacks = new ArrayList<>();
        while (remaining > 0) {
            int next = Math.min(maxStack, remaining);
            stacks.add(new ItemStack(billItem, next));
            remaining -= next;
        }
        return List.copyOf(stacks);
    }

    @Override
    public List<ItemStack> createCoinStacks(int denominationCents, int coinCount) {
        if (coinCount <= 0 || !SUPPORTED_COIN_DENOMINATIONS_CENTS.contains(denominationCents)) {
            return List.of();
        }
        Item coinItem = DollarBills.getCashItemForDenominationCents(denominationCents);
        if (coinItem == null) {
            return List.of();
        }
        int maxStack = coinItem.getMaxStackSize();
        int remaining = coinCount;
        List<ItemStack> stacks = new ArrayList<>();
        while (remaining > 0) {
            int next = Math.min(maxStack, remaining);
            stacks.add(new ItemStack(coinItem, next));
            remaining -= next;
        }
        return List.copyOf(stacks);
    }

    @Override
    public int getPlayerBillCount(UUID playerId, int denomination) {
        if (!SUPPORTED_BILL_DENOMINATIONS.contains(denomination)) {
            return 0;
        }
        ServerPlayer player = resolveOnlinePlayer(playerId);
        if (player == null) {
            return 0;
        }
        return countBills(player, denomination);
    }

    @Override
    public int getPlayerCoinCount(UUID playerId, int denominationCents) {
        if (!SUPPORTED_COIN_DENOMINATIONS_CENTS.contains(denominationCents)) {
            return 0;
        }
        ServerPlayer player = resolveOnlinePlayer(playerId);
        if (player == null) {
            return 0;
        }
        Item coinItem = DollarBills.getCashItemForDenominationCents(denominationCents);
        return coinItem == null ? 0 : countItem(player, coinItem);
    }

    @Override
    public int getPlayerCashOnHand(UUID playerId) {
        ServerPlayer player = resolveOnlinePlayer(playerId);
        if (player == null) {
            return 0;
        }
        int totalCents = DollarBills.totalCashValueCents(DollarBills.getAvailableCashCounts(player));
        return totalCents / 100;
    }

    @Override
    public boolean accountExists(UUID accountId) {
        return resolveAccount(accountId) != null;
    }

    @Override
    public boolean bankExists(UUID bankId) {
        if (bankId == null) {
            return false;
        }
        CentralBank centralBank = resolveCentralBank();
        return centralBank != null && centralBank.getBank(bankId) != null;
    }

    @Override
    public Optional<ApiAccountSnapshot> getAccountSnapshot(UUID accountId) {
        return Optional.ofNullable(resolveAccount(accountId)).map(this::toAccountSnapshot);
    }

    @Override
    public Optional<ApiAccountSnapshot> getPrimaryAccountSnapshot(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return Optional.empty();
        }
        AccountHolder primary = findPrimaryAccount(centralBank, playerId);
        return Optional.ofNullable(primary).map(this::toAccountSnapshot);
    }

    @Override
    public List<ApiAccountSnapshot> getPlayerAccounts(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return List.of();
        }
        ConcurrentHashMap<UUID, AccountHolder> accounts = centralBank.SearchForAccount(playerId);
        if (accounts.isEmpty()) {
            return List.of();
        }
        List<ApiAccountSnapshot> snapshots = new ArrayList<>();
        for (AccountHolder account : accounts.values()) {
            if (account != null) {
                snapshots.add(toAccountSnapshot(account));
            }
        }
        snapshots.sort(accountSnapshotComparator());
        return List.copyOf(snapshots);
    }

    @Override
    public List<ApiAccountSnapshot> getBankAccounts(UUID bankId) {
        if (bankId == null) {
            return List.of();
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return List.of();
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null || bank.getBankAccounts() == null || bank.getBankAccounts().isEmpty()) {
            return List.of();
        }
        List<ApiAccountSnapshot> snapshots = new ArrayList<>();
        for (AccountHolder account : bank.getBankAccounts().values()) {
            if (account != null) {
                snapshots.add(toAccountSnapshot(account));
            }
        }
        snapshots.sort(accountSnapshotComparator());
        return List.copyOf(snapshots);
    }

    @Override
    public ApiResult setPrimaryAccount(UUID playerId, UUID accountId) {
        if (playerId == null) {
            return ApiResult.fail("Player id is required", BigDecimal.ZERO);
        }
        if (accountId == null) {
            return ApiResult.fail("Account id is required", BigDecimal.ZERO);
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return ApiResult.fail("Central bank unavailable", BigDecimal.ZERO);
        }
        ConcurrentHashMap<UUID, AccountHolder> accounts = centralBank.SearchForAccount(playerId);
        if (accounts.isEmpty()) {
            return ApiResult.fail("No accounts found for player", BigDecimal.ZERO);
        }
        AccountHolder selected = accounts.get(accountId);
        if (selected == null) {
            return ApiResult.fail("Account does not belong to player", BigDecimal.ZERO);
        }
        for (AccountHolder account : accounts.values()) {
            if (account != null) {
                account.setPrimaryAccount(account.getAccountUUID().equals(accountId));
            }
        }
        return ApiResult.ok(selected.getBalance());
    }

    @Override
    public Optional<ApiBankSnapshot> getBankSnapshot(UUID bankId) {
        if (bankId == null) {
            return Optional.empty();
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return Optional.empty();
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return Optional.empty();
        }
        return Optional.of(toBankSnapshot(centralBank, bank));
    }

    @Override
    public List<ApiBankSnapshot> getBanks() {
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null || centralBank.getBanks() == null || centralBank.getBanks().isEmpty()) {
            return List.of();
        }
        List<ApiBankSnapshot> banks = new ArrayList<>();
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank != null) {
                banks.add(toBankSnapshot(centralBank, bank));
            }
        }
        banks.sort(Comparator.comparing(
                ApiBankSnapshot::bankName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
        ));
        return List.copyOf(banks);
    }

    @Override
    public Optional<ApiTransactionSnapshot> getTransactionSnapshot(UUID transactionId) {
        if (transactionId == null) {
            return Optional.empty();
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return Optional.empty();
        }
        UserTransaction transaction = centralBank.getTransaction(transactionId);
        if (transaction == null) {
            return Optional.empty();
        }
        return Optional.of(toTransactionSnapshot(transaction));
    }

    @Override
    public List<ApiTransactionSnapshot> getAccountTransactions(UUID accountId, int limit) {
        AccountHolder account = resolveAccount(accountId);
        if (account == null || account.getTransactions() == null || account.getTransactions().isEmpty()) {
            return List.of();
        }
        int resolvedLimit = normalizeTransactionLimit(limit);
        List<ApiTransactionSnapshot> snapshots = new ArrayList<>();
        for (UserTransaction tx : account.getTransactions().values()) {
            if (tx != null) {
                snapshots.add(toTransactionSnapshot(tx));
            }
        }
        snapshots.sort(transactionSnapshotComparator());
        if (snapshots.size() > resolvedLimit) {
            snapshots = new ArrayList<>(snapshots.subList(0, resolvedLimit));
        }
        return List.copyOf(snapshots);
    }

    @Override
    public List<ApiTransactionSnapshot> getPlayerTransactions(UUID playerId, int limit) {
        if (playerId == null) {
            return List.of();
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return List.of();
        }
        ConcurrentHashMap<UUID, AccountHolder> accounts = centralBank.SearchForAccount(playerId);
        if (accounts.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<UUID, ApiTransactionSnapshot> uniqueTransactions = new LinkedHashMap<>();
        for (AccountHolder account : accounts.values()) {
            if (account == null || account.getTransactions() == null) {
                continue;
            }
            for (UserTransaction tx : account.getTransactions().values()) {
                if (tx == null || tx.getTransactionUUID() == null) {
                    continue;
                }
                uniqueTransactions.putIfAbsent(tx.getTransactionUUID(), toTransactionSnapshot(tx));
            }
        }
        if (uniqueTransactions.isEmpty()) {
            return List.of();
        }
        List<ApiTransactionSnapshot> snapshots = new ArrayList<>(uniqueTransactions.values());
        snapshots.sort(transactionSnapshotComparator());
        int resolvedLimit = normalizeTransactionLimit(limit);
        if (snapshots.size() > resolvedLimit) {
            snapshots = new ArrayList<>(snapshots.subList(0, resolvedLimit));
        }
        return List.copyOf(snapshots);
    }

    @Override
    public ApiResult getPlayerTotalBalance(UUID playerId) {
        if (playerId == null) {
            return ApiResult.fail("Player id is required", BigDecimal.ZERO);
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return ApiResult.fail("Central bank unavailable", BigDecimal.ZERO);
        }
        var accounts = centralBank.SearchForAccount(playerId);
        if (accounts.isEmpty()) {
            return ApiResult.fail("No accounts found", BigDecimal.ZERO);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (AccountHolder account : accounts.values()) {
            if (account != null && account.getBalance() != null) {
                total = total.add(account.getBalance());
            }
        }
        return ApiResult.ok(total);
    }

    @Override
    public ApiResult getPlayerPrimaryBalance(UUID playerId) {
        if (playerId == null) {
            return ApiResult.fail("Player id is required", BigDecimal.ZERO);
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return ApiResult.fail("Central bank unavailable", BigDecimal.ZERO);
        }
        AccountHolder primary = findPrimaryAccount(centralBank, playerId);
        if (primary == null) {
            return ApiResult.fail("Primary account not found", BigDecimal.ZERO);
        }
        return ApiResult.ok(primary.getBalance());
    }

    @Override
    public int getPlayerAccountCount(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return 0;
        }
        return centralBank.SearchForAccount(playerId).size();
    }

    @Override
    public ApiResult getBankTotalDeposits(UUID bankId) {
        if (bankId == null) {
            return ApiResult.fail("Bank id is required", BigDecimal.ZERO);
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return ApiResult.fail("Central bank unavailable", BigDecimal.ZERO);
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return ApiResult.fail("Bank not found", BigDecimal.ZERO);
        }
        return ApiResult.ok(bank.getTotalDeposits());
    }

    @Override
    public ApiResult getBankReserve(UUID bankId) {
        if (bankId == null) {
            return ApiResult.fail("Bank id is required", BigDecimal.ZERO);
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return ApiResult.fail("Central bank unavailable", BigDecimal.ZERO);
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return ApiResult.fail("Bank not found", BigDecimal.ZERO);
        }
        return ApiResult.ok(bank.getDeclaredReserve());
    }

    @Override
    public String getBankStatus(UUID bankId) {
        if (bankId == null) {
            return "";
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return "";
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return "";
        }
        return resolveBankStatusForTransactions(centralBank, bank);
    }

    @Override
    public String resolvePlaceholder(UUID playerId, String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return "";
        }
        String trimmed = token.trim();
        String normalized = trimmed;
        if (normalized.startsWith("%") && normalized.endsWith("%") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("ubs_")) {
            return "";
        }

        if (playerId != null) {
            AccountHolder primary = findPrimaryAccount(centralBank, playerId);
            if ("ubs_player_total_balance".equals(normalized)) {
                return formatMoneyDisplay(getPlayerTotalBalance(playerId).balanceAfter());
            }
            if ("ubs_player_total_balance_raw".equals(normalized)) {
                return formatMoneyRaw(getPlayerTotalBalance(playerId).balanceAfter());
            }
            if ("ubs_player_primary_balance".equals(normalized)) {
                return formatMoneyDisplay(primary == null ? BigDecimal.ZERO : primary.getBalance());
            }
            if ("ubs_player_primary_balance_raw".equals(normalized)) {
                return formatMoneyRaw(primary == null ? BigDecimal.ZERO : primary.getBalance());
            }
            if ("ubs_player_account_count".equals(normalized)) {
                return String.valueOf(getPlayerAccountCount(playerId));
            }
            if ("ubs_player_primary_account_id".equals(normalized)) {
                return primary == null ? "" : primary.getAccountUUID().toString();
            }
            if ("ubs_player_primary_account_type".equals(normalized)) {
                return primary == null || primary.getAccountType() == null ? "" : primary.getAccountType().label;
            }
            if ("ubs_player_primary_bank_id".equals(normalized)) {
                return primary == null ? "" : primary.getBankId().toString();
            }
            if ("ubs_player_primary_bank_name".equals(normalized)) {
                if (primary == null) {
                    return "";
                }
                Bank bank = centralBank.getBank(primary.getBankId());
                return bank == null ? "" : bank.getBankName();
            }

            if ("ubs_bank_name".equals(normalized)) {
                if (primary == null) {
                    return "";
                }
                Bank bank = centralBank.getBank(primary.getBankId());
                return bank == null ? "" : bank.getBankName();
            }
            if ("ubs_bank_id".equals(normalized)) {
                return primary == null ? "" : primary.getBankId().toString();
            }
            if ("ubs_bank_status".equals(normalized)) {
                return primary == null ? "" : getBankStatus(primary.getBankId());
            }
            if ("ubs_bank_reserve".equals(normalized)) {
                return primary == null ? "" : formatMoneyDisplay(getBankReserve(primary.getBankId()).balanceAfter());
            }
            if ("ubs_bank_reserve_raw".equals(normalized)) {
                return primary == null ? "" : formatMoneyRaw(getBankReserve(primary.getBankId()).balanceAfter());
            }
            if ("ubs_bank_total_deposits".equals(normalized)) {
                return primary == null ? "" : formatMoneyDisplay(getBankTotalDeposits(primary.getBankId()).balanceAfter());
            }
            if ("ubs_bank_total_deposits_raw".equals(normalized)) {
                return primary == null ? "" : formatMoneyRaw(getBankTotalDeposits(primary.getBankId()).balanceAfter());
            }
        }

        if (normalized.startsWith("ubs_bank_name_")) {
            UUID bankId = parseBankIdSuffix(normalized, "ubs_bank_name_");
            if (bankId == null) {
                return "";
            }
            Bank bank = centralBank.getBank(bankId);
            return bank == null ? "" : bank.getBankName();
        }
        if (normalized.startsWith("ubs_bank_status_")) {
            UUID bankId = parseBankIdSuffix(normalized, "ubs_bank_status_");
            return bankId == null ? "" : getBankStatus(bankId);
        }
        if (normalized.startsWith("ubs_bank_reserve_raw_")) {
            UUID bankId = parseBankIdSuffix(normalized, "ubs_bank_reserve_raw_");
            return bankId == null ? "" : formatMoneyRaw(getBankReserve(bankId).balanceAfter());
        }
        if (normalized.startsWith("ubs_bank_reserve_")) {
            UUID bankId = parseBankIdSuffix(normalized, "ubs_bank_reserve_");
            return bankId == null ? "" : formatMoneyDisplay(getBankReserve(bankId).balanceAfter());
        }
        if (normalized.startsWith("ubs_bank_total_deposits_raw_")) {
            UUID bankId = parseBankIdSuffix(normalized, "ubs_bank_total_deposits_raw_");
            return bankId == null ? "" : formatMoneyRaw(getBankTotalDeposits(bankId).balanceAfter());
        }
        if (normalized.startsWith("ubs_bank_total_deposits_")) {
            UUID bankId = parseBankIdSuffix(normalized, "ubs_bank_total_deposits_");
            return bankId == null ? "" : formatMoneyDisplay(getBankTotalDeposits(bankId).balanceAfter());
        }

        return "";
    }

    @Override
    public String resolvePlaceholders(UUID playerId, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String fullToken = "%" + matcher.group(1) + "%";
            String replacement = resolvePlaceholder(playerId, fullToken);
            if (replacement == null || replacement.isEmpty()) {
                replacement = fullToken;
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    @Override
    public List<String> getSupportedPlaceholders() {
        return SUPPORTED_PLACEHOLDERS;
    }

    private ServerPlayer resolveOnlinePlayer(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getPlayerList() == null) {
            return null;
        }
        return server.getPlayerList().getPlayer(playerId);
    }

    private int indexForDenomination(int denomination) {
        for (int i = 0; i < DollarBills.DENOMINATIONS_DESC.length; i++) {
            if (DollarBills.DENOMINATIONS_DESC[i] == denomination) {
                return i;
            }
        }
        return -1;
    }

    private int indexForCashDenominationCents(int denominationCents) {
        for (int i = 0; i < DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length; i++) {
            if (DollarBills.CASH_DENOMINATIONS_CENTS_DESC[i] == denominationCents) {
                return i;
            }
        }
        return -1;
    }

    private int countBills(ServerPlayer player, int denomination) {
        if (player == null) {
            return 0;
        }
        Item target = DollarBills.getItemForDenomination(denomination);
        return target == null ? 0 : countItem(player, target);
    }

    private void removeBillsByDenomination(ServerPlayer player, int denomination, int billCount) {
        if (player == null || billCount <= 0) {
            return;
        }
        Item target = DollarBills.getItemForDenomination(denomination);
        if (target == null) {
            return;
        }
        removeItem(player, target, billCount);
    }

    private int countItem(ServerPlayer player, Item target) {
        if (player == null || target == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() == target) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() == target) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private void removeItem(ServerPlayer player, Item target, int amount) {
        if (player == null || target == null || amount <= 0) {
            return;
        }
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) {
                break;
            }
            if (stack == null || stack.isEmpty() || stack.getItem() != target) {
                continue;
            }
            int remove = Math.min(remaining, stack.getCount());
            stack.shrink(remove);
            remaining -= remove;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (remaining <= 0) {
                break;
            }
            if (stack == null || stack.isEmpty() || stack.getItem() != target) {
                continue;
            }
            int remove = Math.min(remaining, stack.getCount());
            stack.shrink(remove);
            remaining -= remove;
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private void applyCustomTag(ItemStack stack, CompoundTag tag) {
        if (stack == null || stack.isEmpty() || tag == null) {
            return;
        }
        ItemStackDataCompat.setCustomData(stack, tag);
    }

    private ApiAccountSnapshot toAccountSnapshot(AccountHolder account) {
        String accountType = account.getAccountType() == null ? "" : account.getAccountType().name();
        String accountTypeLabel = account.getAccountType() == null ? "" : account.getAccountType().label;
        return new ApiAccountSnapshot(
                account.getAccountUUID(),
                account.getPlayerUUID(),
                account.getBankId(),
                accountType,
                accountTypeLabel,
                account.getBalance() == null ? BigDecimal.ZERO : account.getBalance(),
                account.isPrimaryAccount(),
                account.isFrozen(),
                account.getFrozenReason() == null ? "" : account.getFrozenReason(),
                account.getDateOfCreation()
        );
    }

    private ApiBankSnapshot toBankSnapshot(CentralBank centralBank, Bank bank) {
        BigDecimal declaredReserve = bank.getDeclaredReserve() == null ? BigDecimal.ZERO : bank.getDeclaredReserve();
        BigDecimal totalDeposits = bank.getTotalDeposits() == null ? BigDecimal.ZERO : bank.getTotalDeposits();
        BigDecimal minimumRequiredReserve = bank.getMinimumRequiredReserve() == null
                ? BigDecimal.ZERO
                : bank.getMinimumRequiredReserve();
        BigDecimal reserveRatio = bank.getReserveRatio() == null ? BigDecimal.ZERO : bank.getReserveRatio();
        BigDecimal outstandingLoans = bank.getOutstandingLoanBalance() == null
                ? BigDecimal.ZERO
                : bank.getOutstandingLoanBalance();
        BigDecimal maxLendable = bank.getMaxLendableAmount() == null ? BigDecimal.ZERO : bank.getMaxLendableAmount();
        int accountCount = bank.getBankAccounts() == null ? 0 : bank.getBankAccounts().size();
        return new ApiBankSnapshot(
                bank.getBankId(),
                bank.getBankName(),
                bank.getBankOwnerId(),
                resolveBankStatus(centralBank, bank.getBankId()),
                declaredReserve,
                totalDeposits,
                minimumRequiredReserve,
                reserveRatio,
                outstandingLoans,
                maxLendable,
                bank.getInterestRate(),
                accountCount
        );
    }

    private ApiTransactionSnapshot toTransactionSnapshot(UserTransaction transaction) {
        return new ApiTransactionSnapshot(
                transaction.getTransactionUUID(),
                transaction.getSenderUUID(),
                transaction.getReceiverUUID(),
                transaction.getAmount() == null ? BigDecimal.ZERO : transaction.getAmount(),
                transaction.getTimestamp(),
                transaction.getTransactionDescription() == null ? "" : transaction.getTransactionDescription()
        );
    }

    private Comparator<ApiAccountSnapshot> accountSnapshotComparator() {
        return Comparator
                .comparing(ApiAccountSnapshot::primary, Comparator.reverseOrder())
                .thenComparing(ApiAccountSnapshot::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(snapshot -> snapshot.accountId() == null ? "" : snapshot.accountId().toString());
    }

    private Comparator<ApiTransactionSnapshot> transactionSnapshotComparator() {
        return Comparator
                .comparing(ApiTransactionSnapshot::timestamp, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(snapshot -> snapshot.transactionId() == null ? "" : snapshot.transactionId().toString());
    }

    private int normalizeTransactionLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_TRANSACTION_LIMIT;
        }
        return Math.min(limit, MAX_TRANSACTION_LIMIT);
    }

    private String resolveBankStatus(CentralBank centralBank, UUID bankId) {
        if (centralBank == null || bankId == null) {
            return "";
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        String status = metadata.getString("status");
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveBankStatusForTransactions(CentralBank centralBank, Bank bank) {
        if (centralBank == null || bank == null) {
            return "UNKNOWN";
        }
        if (centralBank.getBankId() != null && centralBank.getBankId().equals(bank.getBankId())) {
            return "ACTIVE";
        }
        String status = resolveBankStatus(centralBank, bank.getBankId());
        return status == null || status.isBlank() ? "ACTIVE" : status;
    }

    private static boolean blocksTransactions(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return "SUSPENDED".equals(normalized)
                || "REVOKED".equals(normalized)
                || "LOCKDOWN".equals(normalized);
    }

    private static String safeBankName(Bank bank) {
        if (bank == null || bank.getBankName() == null || bank.getBankName().isBlank()) {
            return "Unknown Bank";
        }
        return bank.getBankName().trim();
    }

    private AccountHolder resolveAccount(UUID accountId) {
        if (accountId == null) {
            return null;
        }
        var centralBank = resolveCentralBank();
        if (centralBank == null) {
            return null;
        }
        return centralBank.SearchForAccountByAccountId(accountId);
    }

    private CentralBank resolveCentralBank() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        return BankManager.getCentralBank(server);
    }

    private AccountHolder findPrimaryAccount(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) {
            return null;
        }
        ConcurrentHashMap<UUID, AccountHolder> accounts = centralBank.SearchForAccount(playerId);
        if (accounts.isEmpty()) {
            return null;
        }
        for (AccountHolder account : accounts.values()) {
            if (account != null && account.isPrimaryAccount()) {
                return account;
            }
        }
        return accounts.values().stream().filter(a -> a != null).findFirst().orElse(null);
    }

    private UUID parseBankIdSuffix(String normalizedToken, String prefix) {
        if (normalizedToken == null || prefix == null || !normalizedToken.startsWith(prefix)) {
            return null;
        }
        String raw = normalizedToken.substring(prefix.length()).trim();
        if (raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String formatMoneyDisplay(BigDecimal value) {
        String symbol = Config.CURRENCY_SYMBOL.get() == null ? "$" : Config.CURRENCY_SYMBOL.get();
        return symbol + MoneyText.abbreviate(value == null ? BigDecimal.ZERO : value);
    }

    private String formatMoneyRaw(BigDecimal value) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        return amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }
}
