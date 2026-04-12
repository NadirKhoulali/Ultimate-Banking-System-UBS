package net.austizz.ultimatebankingsystem.account;

import io.github.bucket4j.Bucket;
import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.loan.AccountLoan;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.callback.CallBackManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class AccountHolder {
    private final UUID accountUUID;
    private final UUID playerUUID;
    private LocalDateTime DateOfCreation;
    private AccountTypes AccountType;
    private String pinCode;
    private BigDecimal balance;
    private final UUID BankId;
    private boolean isPrimaryAccount;
    private ConcurrentHashMap<UUID, UserTransaction> transactions;
    private BigDecimal temporaryWithdrawalLimit;
    private long temporaryWithdrawalLimitExpiresAtGameTime;
    private boolean frozen;
    private String frozenReason;
    private long dailyWithdrawalWindowDay; // Epoch day in server local time
    private BigDecimal dailyWithdrawnAmount;
    private long dailyWithdrawalResetEpochMillis;
    private int creditScore;
    private boolean defaulted;
    private ConcurrentHashMap<UUID, AccountLoan> activeLoans;

    private static final long TEMP_WITHDRAWAL_LIMIT_DURATION_TICKS = 24000L;
    private static final ZoneId SERVER_ZONE = ZoneId.systemDefault();


    public AccountHolder(UUID playerUUID, BigDecimal balance,  AccountTypes accountType, String pinCode, UUID BankId, UUID AccountUUID) {
        this.accountUUID = AccountUUID == null ? UUID.randomUUID() : AccountUUID;
        this.playerUUID = playerUUID;
        this.DateOfCreation = LocalDateTime.now();
        this.AccountType = accountType;
        this.pinCode = normalizePin(pinCode);
        this.balance = balance == null ? new  BigDecimal("0") : balance;
        this.BankId = BankId;
        this.isPrimaryAccount = false;
        this.transactions = new ConcurrentHashMap<>();
        this.temporaryWithdrawalLimit = null;
        this.temporaryWithdrawalLimitExpiresAtGameTime = -1L;
        this.frozen = false;
        this.frozenReason = "";
        this.dailyWithdrawalWindowDay = currentEpochDay();
        this.dailyWithdrawnAmount = BigDecimal.ZERO;
        this.dailyWithdrawalResetEpochMillis = computeNextMidnightEpochMillis();
        this.creditScore = Math.max(0, Config.CREDIT_SCORE_DEFAULT.get());
        this.defaulted = false;
        this.activeLoans = new ConcurrentHashMap<>();
    }
    // Request all Types of Identification
    public UUID getAccountUUID() {
        return accountUUID;
    }
    public UUID getPlayerUUID() {
        return playerUUID;
    }
    public UUID getBankId() {
        return BankId;
    }
    //Request Date of Account Creation
    public LocalDateTime getDateOfCreation() {
        return DateOfCreation;
    }
    // Get type of account e.g. Checking Account
    public AccountTypes getAccountType() {
        return AccountType;
    }
    //Requests Player Balance
    public BigDecimal getBalance() {
        return balance;
    }

    private boolean addBalanceInternal(BigDecimal amount, boolean ignoreFreeze) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (!ignoreFreeze && this.frozen) {
            return false;
        }
        this.balance = this.balance.add(amount);
        BankManager.markDirty();
        return true;
    }

    private boolean removeBalanceInternal(BigDecimal amount, boolean ignoreFreeze) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (!ignoreFreeze && this.frozen) {
            return false;
        }
        if (this.balance.compareTo(amount) < 0) {
            return false;
        }
        this.balance = this.balance.subtract(amount);
        UltimateBankingSystem.LOGGER.debug("[UBS] RemoveBalance: ${} from account {}, new balance: ${}", amount, this.accountUUID, this.balance);
        BankManager.markDirty();
        return true;
    }

    // Adds to Players Balance
    public boolean AddBalance(BigDecimal balance) {
        return addBalanceInternal(balance, false);
    }
    // Removes from Players Balance
    public boolean RemoveBalance(BigDecimal balance) {
        return removeBalanceInternal(balance, false);
    }

    public boolean forceAddBalance(BigDecimal balance) {
        return addBalanceInternal(balance, true);
    }

    public boolean forceRemoveBalance(BigDecimal balance) {
        return removeBalanceInternal(balance, true);
    }
    public void addTransaction(UserTransaction transaction) {
        this.transactions.put(transaction.getTransactionUUID(), transaction);
        BankManager.markDirty();
    }
//    public boolean sendMoney(AccountHolder accountHolder, BigDecimal amount) {
//        if (this.balance.compareTo(amount) <= 0) {
//            ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerUUID).sendSystemMessage(Component.literal("Amount is not valid!"));
//            return false;
//        }
//
//        if (!this.RemoveBalance(amount)){
//            return false;
//        }
//        accountHolder.AddBalance(amount);
//        BankManager.markDirty();
//        return true;
//    }

    public void RequestAccountTermination(ServerPlayer player) {
        // Maak de callback aan: wat moet er gebeuren als ze op 'JA' klikken?
        String yesCallbackId = CallBackManager.createCallback(p -> {
            // HIER KOMT JE LOGICA OM HET ACCOUNT TE VERWIJDEREN
            MinecraftServer server = player.getServer();
            CentralBank centralBank = BankManager.getCentralBank(server);
            Bank bank = centralBank.getBank(this.BankId);

            if (bank == null) {
                p.sendSystemMessage(Component.literal("Bank not found!"));
                return;
            }

            bank.RemoveAccount(this);

            p.sendSystemMessage(Component.literal("Your account has been successfully terminated. Your balance has been transferred to the bank.")
                    .withStyle(ChatFormatting.DARK_RED));

            System.out.println("Account terminated for: " + p.getScoreboardName());
        });

        // Het bericht opbouwen
        player.sendSystemMessage(Component.literal("Are you sure you want to terminate your account?\n")
                .append(Component.literal("By Agreeing to terminate your account, your Balance will \nremain with the bank permanently!\n\n")
                        .withStyle(ChatFormatting.GRAY))

                // De "JA" knop
                .append(Component.literal("[Yes, I Agree] ")
                        .setStyle(Style.EMPTY
                                .withBold(true)
                                .withColor(ChatFormatting.RED)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ubs_action " + yesCallbackId))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to permanently delete"))))
                )

                // The "NO" button (no callback needed, just dismisses or sends a simple message)
                .append(Component.literal(" [No, I Disagree]")
                        .setStyle(Style.EMPTY
                                .withBold(true)
                                .withColor(ChatFormatting.GREEN)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ubs_action " + yesCallbackId +  " cancel")))) // Optioneel
        );
    }
    public boolean isPrimaryAccount() {
        return isPrimaryAccount;
    }
    public void setPrimaryAccount(boolean isPrimaryAccount) {
        this.isPrimaryAccount = isPrimaryAccount;
        BankManager.markDirty();
    }

    public boolean isFrozen() {
        return frozen;
    }

    public String getFrozenReason() {
        return frozenReason == null ? "" : frozenReason;
    }

    public void freeze(String reason) {
        this.frozen = true;
        this.frozenReason = reason == null ? "" : reason.trim();
        BankManager.markDirty();
    }

    public void unfreeze() {
        if (!this.frozen && getFrozenReason().isEmpty()) {
            return;
        }
        this.frozen = false;
        this.frozenReason = "";
        BankManager.markDirty();
    }

    public boolean hasPin() {
        return isFourDigitPin(this.pinCode);
    }

    public boolean matchesPin(String candidatePin) {
        if (!hasPin() || candidatePin == null) {
            return false;
        }
        return this.pinCode.equals(candidatePin);
    }

    public boolean setPin(String newPin) {
        if (!isFourDigitPin(newPin)) {
            return false;
        }
        this.pinCode = newPin;
        BankManager.markDirty();
        return true;
    }

    public BigDecimal getConfiguredWithdrawalLimit() {
        if (this.AccountType == AccountTypes.CheckingAccount) {
            return BigDecimal.valueOf(Integer.MAX_VALUE);
        }
        return BigDecimal.valueOf(Config.DEFAULT_ATM_WITHDRAWAL_LIMIT.get());
    }

    public BigDecimal getConfiguredDailyWithdrawalLimit() {
        return switch (this.AccountType) {
            case CheckingAccount -> BigDecimal.valueOf(Integer.MAX_VALUE);
            case SavingAccount -> BigDecimal.valueOf(Config.DAILY_WITHDRAWAL_LIMIT_SAVING.get());
            case MoneyMarketAccount -> BigDecimal.valueOf(Config.DAILY_WITHDRAWAL_LIMIT_MONEY_MARKET.get());
            case CertificateAccount -> BigDecimal.valueOf(Config.DAILY_WITHDRAWAL_LIMIT_CERTIFICATE.get());
            default -> BigDecimal.valueOf(Config.DAILY_WITHDRAWAL_LIMIT.get());
        };
    }

    public BigDecimal getEffectiveWithdrawalLimit(long currentGameTime) {
        expireTemporaryWithdrawalLimitIfNeeded(currentGameTime);
        return temporaryWithdrawalLimit != null ? temporaryWithdrawalLimit : getConfiguredWithdrawalLimit();
    }

    public BigDecimal getDailyWithdrawnAmount() {
        syncDailyWithdrawalWindow();
        return dailyWithdrawnAmount;
    }

    public BigDecimal getRemainingDailyWithdrawalLimit() {
        BigDecimal remaining = getConfiguredDailyWithdrawalLimit().subtract(getDailyWithdrawnAmount());
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    public boolean canWithdrawToday(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return amount.compareTo(getRemainingDailyWithdrawalLimit()) <= 0;
    }

    public void registerDailyWithdrawal(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        syncDailyWithdrawalWindow();
        this.dailyWithdrawnAmount = this.dailyWithdrawnAmount.add(amount);
        BankManager.markDirty();
    }

    public void rollbackDailyWithdrawal(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        syncDailyWithdrawalWindow();
        this.dailyWithdrawnAmount = this.dailyWithdrawnAmount.subtract(amount);
        if (this.dailyWithdrawnAmount.compareTo(BigDecimal.ZERO) < 0) {
            this.dailyWithdrawnAmount = BigDecimal.ZERO;
        }
        BankManager.markDirty();
    }

    public long getDailyWithdrawalResetEpochMillis() {
        syncDailyWithdrawalWindow();
        return dailyWithdrawalResetEpochMillis;
    }

    public int getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(int creditScore) {
        this.creditScore = Math.max(0, creditScore);
        BankManager.markDirty();
    }

    public void adjustCreditScore(int delta) {
        setCreditScore(this.creditScore + delta);
    }

    public boolean isDefaulted() {
        return defaulted;
    }

    public void setDefaulted(boolean defaulted) {
        this.defaulted = defaulted;
        BankManager.markDirty();
    }

    public ConcurrentHashMap<UUID, AccountLoan> getActiveLoans() {
        if (activeLoans == null) {
            activeLoans = new ConcurrentHashMap<>();
        }
        return activeLoans;
    }

    public void addLoan(AccountLoan loan) {
        if (loan == null) {
            return;
        }
        getActiveLoans().put(loan.getLoanId(), loan);
        BankManager.markDirty();
    }

    public void removeLoan(UUID loanId) {
        if (loanId == null) {
            return;
        }
        getActiveLoans().remove(loanId);
        BankManager.markDirty();
    }

    public BigDecimal getTemporaryWithdrawalLimitIfActive(long currentGameTime) {
        expireTemporaryWithdrawalLimitIfNeeded(currentGameTime);
        return temporaryWithdrawalLimit;
    }

    public long getTemporaryWithdrawalLimitExpiresAtGameTime(long currentGameTime) {
        expireTemporaryWithdrawalLimitIfNeeded(currentGameTime);
        return temporaryWithdrawalLimit == null ? -1L : temporaryWithdrawalLimitExpiresAtGameTime;
    }

    public boolean setTemporaryWithdrawalLimit(BigDecimal newLimit, long currentGameTime) {
        if (newLimit == null || newLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (newLimit.stripTrailingZeros().scale() > 0) {
            return false;
        }

        this.temporaryWithdrawalLimit = newLimit;
        this.temporaryWithdrawalLimitExpiresAtGameTime = currentGameTime + TEMP_WITHDRAWAL_LIMIT_DURATION_TICKS;
        BankManager.markDirty();
        return true;
    }

    public void clearTemporaryWithdrawalLimit() {
        if (this.temporaryWithdrawalLimit == null) {
            return;
        }
        this.temporaryWithdrawalLimit = null;
        this.temporaryWithdrawalLimitExpiresAtGameTime = -1L;
        BankManager.markDirty();
    }

    private void expireTemporaryWithdrawalLimitIfNeeded(long currentGameTime) {
        if (temporaryWithdrawalLimit == null) {
            return;
        }
        if (currentGameTime >= temporaryWithdrawalLimitExpiresAtGameTime) {
            clearTemporaryWithdrawalLimit();
        }
    }

    private void syncDailyWithdrawalWindow() {
        long dayIndex = currentEpochDay();
        long nextReset = computeNextMidnightEpochMillis();
        if (dayIndex == this.dailyWithdrawalWindowDay
                && this.dailyWithdrawalResetEpochMillis > System.currentTimeMillis()) {
            return;
        }

        this.dailyWithdrawalWindowDay = dayIndex;
        this.dailyWithdrawnAmount = BigDecimal.ZERO;
        this.dailyWithdrawalResetEpochMillis = nextReset;
        BankManager.markDirty();
    }

    private static long currentEpochDay() {
        return LocalDate.now(SERVER_ZONE).toEpochDay();
    }

    private static long computeNextMidnightEpochMillis() {
        ZonedDateTime nextMidnight = LocalDate.now(SERVER_ZONE).plusDays(1).atStartOfDay(SERVER_ZONE);
        return nextMidnight.toInstant().toEpochMilli();
    }

    /**
     * Returns the transaction map, ensuring it is always non-null.
     * Key = transaction UUID.
     */
    public ConcurrentHashMap<UUID, UserTransaction> getTransactions() {
        if (transactions == null) {
            transactions = new ConcurrentHashMap<>();
        }
        return transactions;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putUUID("playerUUID", this.playerUUID);
        tag.putString("balance", this.balance.toString()); // BigDecimal als String opslaan
        tag.putUUID("BankId", this.BankId);
        tag.putBoolean("isPrimaryAccount", isPrimaryAccount);
        tag.putString("AccountType", this.AccountType.name());
        tag.putUUID("accountUUID", this.accountUUID);
        tag.putString("dateOfCreation", this.DateOfCreation.toString());
        tag.putString("pinCode", this.pinCode == null ? "" : this.pinCode);
        tag.putUUID("playerUUID", this.playerUUID);
        tag.putBoolean("frozen", this.frozen);
        tag.putString("frozenReason", getFrozenReason());
        tag.putInt("creditScore", this.creditScore);
        tag.putBoolean("defaulted", this.defaulted);
        tag.putLong("dailyWithdrawalWindowDay", this.dailyWithdrawalWindowDay);
        tag.putString("dailyWithdrawnAmount", this.dailyWithdrawnAmount.toPlainString());
        tag.putLong("dailyWithdrawalResetEpochMillis", this.dailyWithdrawalResetEpochMillis);
        if (this.temporaryWithdrawalLimit != null) {
            tag.putString("temporaryWithdrawalLimit", this.temporaryWithdrawalLimit.toPlainString());
            tag.putLong("temporaryWithdrawalLimitExpiresAtGameTime", this.temporaryWithdrawalLimitExpiresAtGameTime);
        }

        // Transactions
        ListTag txList = new ListTag();
        for (Map.Entry<UUID, UserTransaction> entry : getTransactions().entrySet()) {
            UserTransaction tx = entry.getValue();
            if (tx == null) continue;

            CompoundTag txTag = new CompoundTag();
            tx.save(txTag, registries);
            // store the key too, in case it diverges from tx.getTransactionUUID()
            txTag.putUUID("mapKey", entry.getKey());
            txList.add(txTag);
        }
        tag.put("transactions", txList);

        ListTag loanList = new ListTag();
        for (AccountLoan loan : getActiveLoans().values()) {
            if (loan == null) {
                continue;
            }
            CompoundTag loanTag = new CompoundTag();
            loan.save(loanTag, registries);
            loanList.add(loanTag);
        }
        tag.put("activeLoans", loanList);

        // voeg andere velden toe...
        return tag;
    }

    public static AccountHolder load(CompoundTag tag, HolderLookup.Provider registries) {
        BigDecimal balance = new BigDecimal(tag.getString("balance"));
        AccountTypes accountType = AccountTypes.valueOf(tag.getString("AccountType"));
        String pinCode = "";
        if (tag.contains("pinCode")) {
            pinCode = tag.getString("pinCode");
        } else if (tag.contains("password")) {
            // Legacy migration: old worlds saved the value under "password".
            String legacyPassword = tag.getString("password");
            if (isFourDigitPin(legacyPassword)) {
                pinCode = legacyPassword;
            }
        }
        UUID accountUUID = tag.getUUID("accountUUID");
        UUID BankId = tag.getUUID("BankId");
        UUID playerUUID = tag.getUUID("playerUUID");
        AccountHolder account = new AccountHolder(playerUUID, balance, accountType, pinCode, BankId, accountUUID);
        account.DateOfCreation = LocalDateTime.parse(tag.getString("dateOfCreation"));
        account.isPrimaryAccount = tag.getBoolean("isPrimaryAccount");
        account.frozen = tag.getBoolean("frozen");
        account.frozenReason = tag.contains("frozenReason") ? tag.getString("frozenReason") : "";
        account.creditScore = tag.contains("creditScore") ? Math.max(0, tag.getInt("creditScore")) : Math.max(0, Config.CREDIT_SCORE_DEFAULT.get());
        account.defaulted = tag.getBoolean("defaulted");
        account.dailyWithdrawalWindowDay = tag.contains("dailyWithdrawalWindowDay") ? tag.getLong("dailyWithdrawalWindowDay") : currentEpochDay();
        if (tag.contains("dailyWithdrawnAmount")) {
            try {
                account.dailyWithdrawnAmount = new BigDecimal(tag.getString("dailyWithdrawnAmount"));
            } catch (NumberFormatException ignored) {
                account.dailyWithdrawnAmount = BigDecimal.ZERO;
            }
        }
        account.dailyWithdrawalResetEpochMillis = tag.contains("dailyWithdrawalResetEpochMillis")
                ? tag.getLong("dailyWithdrawalResetEpochMillis")
                : computeNextMidnightEpochMillis();
        if (tag.contains("temporaryWithdrawalLimit")) {
            try {
                account.temporaryWithdrawalLimit = new BigDecimal(tag.getString("temporaryWithdrawalLimit"));
                account.temporaryWithdrawalLimitExpiresAtGameTime = tag.getLong("temporaryWithdrawalLimitExpiresAtGameTime");
            } catch (NumberFormatException ignored) {
                account.temporaryWithdrawalLimit = null;
                account.temporaryWithdrawalLimitExpiresAtGameTime = -1L;
            }
        }

        // Transactions
        account.transactions = new ConcurrentHashMap<>();
        if (tag.contains("transactions", Tag.TAG_LIST)) {
            ListTag txList = tag.getList("transactions", Tag.TAG_COMPOUND);
            for (int i = 0; i < txList.size(); i++) {
                CompoundTag txTag = txList.getCompound(i);
                UserTransaction tx = UserTransaction.load(txTag, registries);
                if (tx == null) continue;

                UUID key = txTag.hasUUID("mapKey") ? txTag.getUUID("mapKey") : tx.getTransactionUUID();
                account.transactions.put(key, tx);
            }
        }

        account.activeLoans = new ConcurrentHashMap<>();
        if (tag.contains("activeLoans", Tag.TAG_LIST)) {
            ListTag loanList = tag.getList("activeLoans", Tag.TAG_COMPOUND);
            for (int i = 0; i < loanList.size(); i++) {
                AccountLoan loan = AccountLoan.load(loanList.getCompound(i), registries);
                if (loan == null) {
                    continue;
                }
                account.activeLoans.put(loan.getLoanId(), loan);
            }
        }

        return account;
    }

    private static final int OUTGOING_TX_CAPACITY = Config.TRANSACTIONS_PER_MINUTE.get();
    private static final Duration OUTGOING_TX_REFILL_PERIOD = Duration.ofMinutes(1);

    /**
     * Rate limiter for outgoing transactions for this account.
     * Not persisted; recreated on load (per AccountHolder instance).
     */
    private final Bucket outgoingTxBucket = Bucket.builder()
            .addLimit(limit -> limit.capacity(OUTGOING_TX_CAPACITY)
                    .refillIntervally(OUTGOING_TX_CAPACITY, OUTGOING_TX_REFILL_PERIOD))
            .build();

    /**
     * Try to consume 1 outgoing transaction token.
     *
     * @return true if the transaction is allowed right now.
     */
    public boolean tryConsumeOutgoingTransaction() {
        return outgoingTxBucket.tryConsume(1);
    }

    private static boolean isFourDigitPin(String pin) {
        return pin != null && pin.matches("\\d{4}");
    }

    private static String normalizePin(String pin) {
        return isFourDigitPin(pin) ? pin : "";
    }
}
