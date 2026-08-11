package net.austizz.ultimatebankingsystem.account;
import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.loan.AccountLoan;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.callback.CallBackManager;
import net.austizz.ultimatebankingsystem.network.DeliveryAlertPayload;
import net.austizz.ultimatebankingsystem.network.ServerNotification;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.util.MoneyText;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class AccountHolder {
    private final UUID accountUUID;
    private final UUID playerUUID;
    private AccountPrincipalType principalType;
    private String principalId;
    private LocalDateTime DateOfCreation;
    private AccountTypes AccountType;
    private String pinCode;
    private BigDecimal balance;
    private final UUID BankId;
    private boolean isPrimaryAccount;
    private ConcurrentHashMap<UUID, UserTransaction> transactions;
    private BigDecimal temporaryWithdrawalLimit;
    private long temporaryWithdrawalLimitExpiresAtGameTime;
    private long temporaryWithdrawalLimitExpiresAtEpochMillis;
    private boolean frozen;
    private String frozenReason;
    private long dailyWithdrawalWindowDay; // Epoch day in server local time
    private BigDecimal dailyWithdrawnAmount;
    private long dailyWithdrawalResetEpochMillis;
    private long dailyOutgoingTransactionDay;
    private BigDecimal dailyOutgoingTransactionAmount;
    private int creditScore;
    private boolean defaulted;
    private ConcurrentHashMap<UUID, AccountLoan> activeLoans;
    private String accountAccessType;
    private String businessLabel;
    private ConcurrentHashMap<UUID, String> accessRoles;
    private ConcurrentHashMap<Integer, CompoundTag> safeBoxSlots;
    private String certificateTier;
    private long certificateMaturityGameTime;
    private boolean certificateLocked;
    private boolean certificateMaturitySettled;
    private double certificateRate;
    private double lastVariableRate;

    private static final long TEMP_WITHDRAWAL_LIMIT_DURATION_TICKS = 24000L;
    private static final ZoneId SERVER_ZONE = ZoneId.systemDefault();
    private static final Comparator<TransactionEntry> TRANSACTION_OLDEST_FIRST = Comparator
            .comparing((TransactionEntry entry) -> entry.transaction().getTimestamp(),
                    Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(TransactionEntry::key, Comparator.nullsFirst(Comparator.naturalOrder()));


    public AccountHolder(UUID playerUUID, BigDecimal balance, AccountTypes accountType, String pinCode, UUID BankId, UUID AccountUUID) {
        this(playerUUID, balance, accountType, pinCode, BankId, AccountUUID,
                AccountPrincipalType.PLAYER, playerUUID == null ? "" : playerUUID.toString());
    }

    private AccountHolder(UUID compatibilityOwnerId,
                          BigDecimal balance,
                          AccountTypes accountType,
                          String pinCode,
                          UUID bankId,
                          UUID accountId,
                          AccountPrincipalType principalType,
                          String principalId) {
        this.accountUUID = accountId == null ? UUID.randomUUID() : accountId;
        this.playerUUID = compatibilityOwnerId;
        this.principalType = principalType == null ? AccountPrincipalType.PLAYER : principalType;
        this.principalId = normalizePrincipalId(principalId, compatibilityOwnerId);
        this.DateOfCreation = LocalDateTime.now();
        this.AccountType = accountType;
        this.pinCode = normalizePin(pinCode);
        this.balance = balance == null ? new  BigDecimal("0") : balance;
        this.BankId = bankId;
        this.isPrimaryAccount = false;
        this.transactions = new ConcurrentHashMap<>();
        this.temporaryWithdrawalLimit = null;
        this.temporaryWithdrawalLimitExpiresAtGameTime = -1L;
        this.temporaryWithdrawalLimitExpiresAtEpochMillis = -1L;
        this.frozen = false;
        this.frozenReason = "";
        this.dailyWithdrawalWindowDay = currentEpochDay();
        this.dailyWithdrawnAmount = BigDecimal.ZERO;
        this.dailyWithdrawalResetEpochMillis = computeNextMidnightEpochMillis();
        this.dailyOutgoingTransactionDay = currentEpochDay();
        this.dailyOutgoingTransactionAmount = BigDecimal.ZERO;
        this.creditScore = Math.max(0, Config.CREDIT_SCORE_DEFAULT.get());
        this.defaulted = false;
        this.activeLoans = new ConcurrentHashMap<>();
        this.accountAccessType = this.principalType == AccountPrincipalType.INSTITUTION
                ? "INSTITUTION"
                : "PERSONAL";
        this.businessLabel = "";
        this.accessRoles = new ConcurrentHashMap<>();
        if (this.principalType == AccountPrincipalType.PLAYER && compatibilityOwnerId != null) {
            this.accessRoles.put(compatibilityOwnerId, "OWNER");
        }
        this.safeBoxSlots = new ConcurrentHashMap<>();
        this.certificateTier = "";
        this.certificateMaturityGameTime = -1L;
        this.certificateLocked = false;
        this.certificateMaturitySettled = false;
        this.certificateRate = 0.0;
        this.lastVariableRate = -1.0;
    }

    public static AccountHolder createInstitutional(String institutionId,
                                                    BigDecimal balance,
                                                    AccountTypes accountType,
                                                    UUID bankId,
                                                    UUID accountId) {
        String normalizedId = normalizeInstitutionId(institutionId);
        if (normalizedId.isEmpty()) {
            throw new IllegalArgumentException("Institution id is required");
        }
        UUID compatibilityOwnerId = UUID.nameUUIDFromBytes(
                ("ultimatebankingsystem:institution:" + normalizedId).getBytes(StandardCharsets.UTF_8));
        return new AccountHolder(
                compatibilityOwnerId,
                balance,
                accountType,
                "",
                bankId,
                accountId,
                AccountPrincipalType.INSTITUTION,
                normalizedId
        );
    }
    // Request all Types of Identification
    public UUID getAccountUUID() {
        return accountUUID;
    }
    public UUID getPlayerUUID() {
        return playerUUID;
    }
    public AccountPrincipalType getPrincipalType() {
        return principalType == null ? AccountPrincipalType.PLAYER : principalType;
    }
    public String getPrincipalId() {
        return normalizePrincipalId(principalId, playerUUID);
    }
    public boolean isInstitutional() {
        return getPrincipalType() == AccountPrincipalType.INSTITUTION;
    }
    public boolean isOwnedByPlayer(UUID playerId) {
        return playerId != null
                && getPrincipalType() == AccountPrincipalType.PLAYER
                && playerId.equals(this.playerUUID);
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
        // Global money-in feedback: whenever an account gains funds, surface a unified alert.
        sendBalanceDeltaAlert(amount, true);
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
        if (!ignoreFreeze && isLockedCertificateAtCurrentGameTime()) {
            return false;
        }
        if (this.balance.compareTo(amount) < 0) {
            return false;
        }
        this.balance = this.balance.subtract(amount);
        UltimateBankingSystem.LOGGER.debug("[UBS] RemoveBalance: ${} from account {}, new balance: ${}", amount, this.accountUUID, this.balance);
        // Global money-out feedback: every successful debit should be visible to the account owner.
        sendBalanceDeltaAlert(amount, false);
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

    public boolean canDebitForSystem(BigDecimal amount) {
        return amount != null
                && amount.compareTo(BigDecimal.ZERO) > 0
                && !this.frozen
                && !isLockedCertificateAtCurrentGameTime()
                && this.balance != null
                && this.balance.compareTo(amount) >= 0;
    }

    public boolean canCreditForSystem(BigDecimal amount) {
        return amount != null
                && amount.compareTo(BigDecimal.ZERO) > 0
                && !this.frozen;
    }

    private void sendBalanceDeltaAlert(BigDecimal amount, boolean incoming) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || this.playerUUID == null || isInstitutional()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(this.playerUUID);
        if (owner == null) {
            return;
        }
        String direction = incoming ? "received" : "spent";
        String legacyMessage = (incoming ? "§a" : "§e")
                + "Account " + direction + ": §6"
                + MoneyText.abbreviateWithDollar(amount)
                + " §7(new balance: §f"
                + MoneyText.abbreviateWithDollar(this.balance)
                + "§7)";
        DeliveryAlertPayload.AlertTone tone = incoming
                ? DeliveryAlertPayload.AlertTone.SUCCESS
                : DeliveryAlertPayload.AlertTone.WARNING;
        ServerNotification.sendLegacy(owner, "Balance", legacyMessage, tone, 3600);
    }
    public synchronized void addTransaction(UserTransaction transaction) {
        if (transaction == null || transaction.getTransactionUUID() == null) {
            return;
        }
        UserTransaction existing = getTransactions().putIfAbsent(transaction.getTransactionUUID(), transaction);
        if (existing != null) {
            return;
        }
        recordDailyOutgoingTransaction(transaction);
        retainNewestTransactions(getTransactions(), configuredTransactionLogLimit());
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

            UltimateBankingSystem.LOGGER.debug("Account terminated for {}", p.getScoreboardName());
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
        this.isPrimaryAccount = isPrimaryAccount && !isInstitutional();
        BankManager.markDirty();
    }

    public boolean isFrozen() {
        return frozen;
    }

    public String getAccountAccessType() {
        return accountAccessType == null ? "PERSONAL" : accountAccessType;
    }

    public void setAccountAccessType(String accountAccessType) {
        if (accountAccessType == null || accountAccessType.isBlank()) {
            this.accountAccessType = "PERSONAL";
        } else {
            this.accountAccessType = accountAccessType.trim().toUpperCase();
        }
        BankManager.markDirty();
    }

    public String getBusinessLabel() {
        return businessLabel == null ? "" : businessLabel;
    }

    public void setBusinessLabel(String businessLabel) {
        this.businessLabel = businessLabel == null ? "" : businessLabel.trim();
        BankManager.markDirty();
    }

    public ConcurrentHashMap<UUID, String> getAccessRoles() {
        if (accessRoles == null) {
            accessRoles = new ConcurrentHashMap<>();
            if (!isInstitutional() && this.playerUUID != null) {
                accessRoles.put(this.playerUUID, "OWNER");
            }
        }
        return accessRoles;
    }

    public void grantAccessRole(UUID playerId, String role) {
        if (playerId == null) {
            return;
        }
        String normalizedRole = role == null || role.isBlank()
                ? "VIEW"
                : role.trim().toUpperCase();
        getAccessRoles().put(playerId, normalizedRole);
        BankManager.markDirty();
    }

    public void revokeAccessRole(UUID playerId) {
        if (playerId == null) {
            return;
        }
        if (!isInstitutional() && playerId.equals(this.playerUUID)) {
            return;
        }
        getAccessRoles().remove(playerId);
        BankManager.markDirty();
    }

    public String getRole(UUID playerId) {
        if (playerId == null) {
            return "";
        }
        if (!isInstitutional() && this.playerUUID != null
                && this.playerUUID.equals(playerId) && !getAccessRoles().containsKey(playerId)) {
            return "OWNER";
        }
        return getAccessRoles().getOrDefault(playerId, "");
    }

    public boolean canView(UUID playerId) {
        String role = getRole(playerId);
        return !role.isEmpty();
    }

    public boolean canDeposit(UUID playerId) {
        String role = getRole(playerId);
        return role.equals("OWNER") || role.equals("MANAGE") || role.equals("DEPOSIT") || role.equals("WITHDRAW");
    }

    public boolean canWithdraw(UUID playerId) {
        String role = getRole(playerId);
        return role.equals("OWNER") || role.equals("MANAGE") || role.equals("WITHDRAW");
    }

    public boolean canManage(UUID playerId) {
        String role = getRole(playerId);
        return role.equals("OWNER") || role.equals("MANAGE");
    }

    public int getSafeBoxSlotCount() {
        return switch (this.AccountType) {
            case CheckingAccount -> Config.SAFEBOX_SLOTS_CHECKING.get();
            case SavingAccount -> Config.SAFEBOX_SLOTS_SAVING.get();
            case MoneyMarketAccount -> Config.SAFEBOX_SLOTS_MONEY_MARKET.get();
            case CertificateAccount -> Config.SAFEBOX_SLOTS_CERTIFICATE.get();
        };
    }

    public ConcurrentHashMap<Integer, CompoundTag> getSafeBoxSlots() {
        if (safeBoxSlots == null) {
            safeBoxSlots = new ConcurrentHashMap<>();
        }
        return safeBoxSlots;
    }

    public boolean depositToSafeBox(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty() || registries == null) {
            return false;
        }
        int maxSlots = Math.max(1, getSafeBoxSlotCount());
        int freeSlot = -1;
        for (int slot = 0; slot < maxSlots; slot++) {
            if (!getSafeBoxSlots().containsKey(slot)) {
                freeSlot = slot;
                break;
            }
        }
        if (freeSlot < 0) {
            return false;
        }

        CompoundTag stackTag = ItemStackDataCompat.saveStack(stack, registries);
        getSafeBoxSlots().put(freeSlot, stackTag);
        BankManager.markDirty();
        return true;
    }

    public ItemStack withdrawFromSafeBox(int slot, HolderLookup.Provider registries) {
        if (slot < 0 || registries == null) {
            return ItemStack.EMPTY;
        }
        CompoundTag stackTag = getSafeBoxSlots().remove(slot);
        if (stackTag == null) {
            return ItemStack.EMPTY;
        }
        ItemStack parsed = ItemStack.parseOptional(registries, stackTag);
        if (parsed.isEmpty()) {
            return ItemStack.EMPTY;
        }
        BankManager.markDirty();
        return parsed;
    }

    public void configureCertificate(String tier, long maturityGameTime, double rate) {
        this.certificateTier = tier == null ? "" : tier.trim().toLowerCase();
        this.certificateMaturityGameTime = maturityGameTime;
        this.certificateRate = rate;
        this.certificateLocked = !this.certificateTier.isBlank() && maturityGameTime > 0;
        this.certificateMaturitySettled = false;
        BankManager.markDirty();
    }

    public String getCertificateTier() {
        return certificateTier == null ? "" : certificateTier;
    }

    public long getCertificateMaturityGameTime() {
        return certificateMaturityGameTime;
    }

    public boolean isCertificateLocked(long gameTime) {
        if (!certificateLocked) {
            return false;
        }
        if (certificateMaturityGameTime <= 0) {
            return false;
        }
        if (gameTime >= certificateMaturityGameTime) {
            certificateLocked = false;
            BankManager.markDirty();
            return false;
        }
        return true;
    }

    private boolean isLockedCertificateAtCurrentGameTime() {
        if (this.AccountType != AccountTypes.CertificateAccount) {
            return false;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return this.certificateLocked;
        }
        var overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return this.certificateLocked;
        }
        return isCertificateLocked(overworld.getGameTime());
    }

    public double getCertificateRate() {
        return certificateRate;
    }

    public boolean isCertificateMaturitySettled() {
        return certificateMaturitySettled;
    }

    public void setCertificateMaturitySettled(boolean certificateMaturitySettled) {
        this.certificateMaturitySettled = certificateMaturitySettled;
        BankManager.markDirty();
    }

    public double getLastVariableRate() {
        return lastVariableRate;
    }

    public void setLastVariableRate(double lastVariableRate) {
        this.lastVariableRate = lastVariableRate;
        BankManager.markDirty();
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
        int configured = Math.max(1, Config.DEFAULT_ATM_WITHDRAWAL_LIMIT.get());
        return BigDecimal.valueOf(configured);
    }

    public BigDecimal getConfiguredDailyWithdrawalLimit() {
        AccountTypes type = this.AccountType;
        if (type == null) {
            return BigDecimal.valueOf(Math.max(1, Config.DAILY_WITHDRAWAL_LIMIT.get()));
        }
        return switch (type) {
            case CheckingAccount -> BigDecimal.valueOf(Math.max(1, Config.DAILY_WITHDRAWAL_LIMIT_CHECKING.get()));
            case SavingAccount -> BigDecimal.valueOf(Math.max(1, Config.DAILY_WITHDRAWAL_LIMIT_SAVING.get()));
            case MoneyMarketAccount -> BigDecimal.valueOf(Math.max(1, Config.DAILY_WITHDRAWAL_LIMIT_MONEY_MARKET.get()));
            case CertificateAccount -> BigDecimal.valueOf(Math.max(1, Config.DAILY_WITHDRAWAL_LIMIT_CERTIFICATE.get()));
            default -> BigDecimal.valueOf(Math.max(1, Config.DAILY_WITHDRAWAL_LIMIT.get()));
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

    public AccountReadSnapshot readOnlySnapshot(long currentGameTime) {
        long nowMillis = System.currentTimeMillis();
        return AccountReadSnapshot.capture(new AccountReadSnapshot.Raw(
                        getConfiguredDailyWithdrawalLimit(), dailyWithdrawalWindowDay,
                        dailyWithdrawnAmount, dailyWithdrawalResetEpochMillis,
                        temporaryWithdrawalLimit, temporaryWithdrawalLimitExpiresAtGameTime,
                        temporaryWithdrawalLimitExpiresAtEpochMillis, certificateLocked,
                        certificateMaturityGameTime),
                currentGameTime, nowMillis, currentEpochDay(), computeNextMidnightEpochMillis());
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

    public long getTemporaryWithdrawalLimitExpiresAtEpochMillis(long currentGameTime) {
        expireTemporaryWithdrawalLimitIfNeeded(currentGameTime);
        return temporaryWithdrawalLimit == null ? -1L : temporaryWithdrawalLimitExpiresAtEpochMillis;
    }

    public boolean setTemporaryWithdrawalLimit(BigDecimal newLimit, long currentGameTime) {
        long expiresAtEpochMillis = System.currentTimeMillis() + (TEMP_WITHDRAWAL_LIMIT_DURATION_TICKS * 50L);
        return setTemporaryWithdrawalLimitUntil(newLimit, currentGameTime, expiresAtEpochMillis);
    }

    public boolean setTemporaryWithdrawalLimitUntil(BigDecimal newLimit, long currentGameTime, long expiresAtEpochMillis) {
        if (newLimit == null || newLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (newLimit.stripTrailingZeros().scale() > 0) {
            return false;
        }
        if (expiresAtEpochMillis <= System.currentTimeMillis()) {
            return false;
        }

        this.temporaryWithdrawalLimit = newLimit;
        long ticksUntilExpiry = Math.max(1L, (expiresAtEpochMillis - System.currentTimeMillis() + 49L) / 50L);
        this.temporaryWithdrawalLimitExpiresAtGameTime = currentGameTime + ticksUntilExpiry;
        this.temporaryWithdrawalLimitExpiresAtEpochMillis = expiresAtEpochMillis;
        BankManager.markDirty();
        return true;
    }

    public void clearTemporaryWithdrawalLimit() {
        if (this.temporaryWithdrawalLimit == null) {
            return;
        }
        this.temporaryWithdrawalLimit = null;
        this.temporaryWithdrawalLimitExpiresAtGameTime = -1L;
        this.temporaryWithdrawalLimitExpiresAtEpochMillis = -1L;
        BankManager.markDirty();
    }

    private void expireTemporaryWithdrawalLimitIfNeeded(long currentGameTime) {
        if (temporaryWithdrawalLimit == null) {
            return;
        }
        long nowMillis = System.currentTimeMillis();
        boolean expiredByClock = temporaryWithdrawalLimitExpiresAtEpochMillis > 0L
                && nowMillis >= temporaryWithdrawalLimitExpiresAtEpochMillis;
        boolean expiredByGameTime = temporaryWithdrawalLimitExpiresAtGameTime >= 0L
                && currentGameTime >= temporaryWithdrawalLimitExpiresAtGameTime;
        if (expiredByClock || expiredByGameTime) {
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

    public Map<UUID, UserTransaction> readOnlyTransactions() {
        if (transactions == null || transactions.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UserTransaction> snapshot = new LinkedHashMap<>();
        transactions.forEach((id, transaction) -> {
            if (id != null && transaction != null) {
                snapshot.put(id, transaction);
            }
        });
        return Map.copyOf(snapshot);
    }

    public synchronized BigDecimal getDailyOutgoingTransactionVolume() {
        if (dailyOutgoingTransactionDay != currentEpochDay() || dailyOutgoingTransactionAmount == null) {
            return BigDecimal.ZERO;
        }
        return dailyOutgoingTransactionAmount.max(BigDecimal.ZERO);
    }

    private void recordDailyOutgoingTransaction(UserTransaction transaction) {
        if (!accountUUID.equals(transaction.getSenderUUID())
                || transaction.getTimestamp() == null
                || transaction.getAmount() == null
                || transaction.getAmount().signum() <= 0) {
            return;
        }
        long today = currentEpochDay();
        long transactionDay = transaction.getTimestamp().toLocalDate().toEpochDay();
        if (transactionDay != today) {
            return;
        }
        if (dailyOutgoingTransactionDay != today || dailyOutgoingTransactionAmount == null) {
            dailyOutgoingTransactionDay = today;
            dailyOutgoingTransactionAmount = BigDecimal.ZERO;
        }
        dailyOutgoingTransactionAmount = dailyOutgoingTransactionAmount.add(transaction.getAmount());
    }

    private static int configuredTransactionLogLimit() {
        try {
            return Math.max(1, Config.ACCOUNT_TRANSACTION_LOG_LIMIT.get());
        } catch (IllegalStateException ignored) {
            return Config.DEFAULT_ACCOUNT_TRANSACTION_LOG_LIMIT;
        }
    }

    private static List<TransactionEntry> newestTransactions(Map<UUID, UserTransaction> source, int limit) {
        int boundedLimit = Math.max(1, limit);
        PriorityQueue<TransactionEntry> retained = new PriorityQueue<>(boundedLimit + 1, TRANSACTION_OLDEST_FIRST);
        if (source != null) {
            source.forEach((key, transaction) -> offerTransaction(retained, boundedLimit, key, transaction));
        }
        List<TransactionEntry> newestFirst = new ArrayList<>(retained);
        newestFirst.sort(TRANSACTION_OLDEST_FIRST.reversed());
        return newestFirst;
    }

    private static void offerTransaction(PriorityQueue<TransactionEntry> retained,
                                         int limit,
                                         UUID key,
                                         UserTransaction transaction) {
        if (key == null || transaction == null) {
            return;
        }
        retained.offer(new TransactionEntry(key, transaction));
        if (retained.size() > limit) {
            retained.poll();
        }
    }

    private static List<TransactionEntry> retainNewestTransactions(ConcurrentHashMap<UUID, UserTransaction> source,
                                                                    int limit) {
        List<TransactionEntry> retained = newestTransactions(source, limit);
        if (source.size() != retained.size()
                || retained.stream().anyMatch(entry -> source.get(entry.key()) != entry.transaction())) {
            source.clear();
            retained.forEach(entry -> source.put(entry.key(), entry.transaction()));
        }
        return retained;
    }

    private record TransactionEntry(UUID key, UserTransaction transaction) {
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putUUID("playerUUID", this.playerUUID);
        tag.putString("principalType", getPrincipalType().name());
        tag.putString("principalId", getPrincipalId());
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
        tag.putString("accountAccessType", getAccountAccessType());
        tag.putString("businessLabel", getBusinessLabel());
        tag.putString("certificateTier", getCertificateTier());
        tag.putLong("certificateMaturityGameTime", this.certificateMaturityGameTime);
        tag.putBoolean("certificateLocked", this.certificateLocked);
        tag.putBoolean("certificateMaturitySettled", this.certificateMaturitySettled);
        tag.putDouble("certificateRate", this.certificateRate);
        tag.putDouble("lastVariableRate", this.lastVariableRate);
        tag.putLong("dailyWithdrawalWindowDay", this.dailyWithdrawalWindowDay);
        tag.putString("dailyWithdrawnAmount", this.dailyWithdrawnAmount.toPlainString());
        tag.putLong("dailyWithdrawalResetEpochMillis", this.dailyWithdrawalResetEpochMillis);
        tag.putLong("dailyOutgoingTransactionDay", this.dailyOutgoingTransactionDay);
        tag.putString("dailyOutgoingTransactionAmount",
                this.dailyOutgoingTransactionAmount == null
                        ? BigDecimal.ZERO.toPlainString()
                        : this.dailyOutgoingTransactionAmount.toPlainString());
        if (this.temporaryWithdrawalLimit != null) {
            tag.putString("temporaryWithdrawalLimit", this.temporaryWithdrawalLimit.toPlainString());
            tag.putLong("temporaryWithdrawalLimitExpiresAtGameTime", this.temporaryWithdrawalLimitExpiresAtGameTime);
            tag.putLong("temporaryWithdrawalLimitExpiresAtEpochMillis", this.temporaryWithdrawalLimitExpiresAtEpochMillis);
        }

        // Transactions
        ListTag txList = new ListTag();
        List<TransactionEntry> retainedTransactions;
        synchronized (this) {
            retainedTransactions = retainNewestTransactions(getTransactions(), configuredTransactionLogLimit());
        }
        for (TransactionEntry entry : retainedTransactions) {
            UserTransaction tx = entry.transaction();

            CompoundTag txTag = new CompoundTag();
            tx.save(txTag, registries);
            // store the key too, in case it diverges from tx.getTransactionUUID()
            txTag.putUUID("mapKey", entry.key());
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

        ListTag roleList = new ListTag();
        for (Map.Entry<UUID, String> roleEntry : getAccessRoles().entrySet()) {
            CompoundTag roleTag = new CompoundTag();
            roleTag.putUUID("playerId", roleEntry.getKey());
            roleTag.putString("role", roleEntry.getValue() == null ? "" : roleEntry.getValue());
            roleList.add(roleTag);
        }
        tag.put("accessRoles", roleList);

        ListTag safeBoxList = new ListTag();
        for (Map.Entry<Integer, CompoundTag> entry : getSafeBoxSlots().entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            CompoundTag slotTag = new CompoundTag();
            slotTag.putInt("slot", entry.getKey());
            slotTag.put("stack", entry.getValue().copy());
            safeBoxList.add(slotTag);
        }
        tag.put("safeBoxSlots", safeBoxList);

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
        AccountPrincipalType principalType = AccountPrincipalType.parse(tag.getString("principalType"));
        String principalId = tag.contains("principalId") ? tag.getString("principalId") : playerUUID.toString();
        AccountHolder account = new AccountHolder(
                playerUUID, balance, accountType, pinCode, BankId, accountUUID, principalType, principalId);
        account.DateOfCreation = LocalDateTime.parse(tag.getString("dateOfCreation"));
        account.isPrimaryAccount = tag.getBoolean("isPrimaryAccount") && !account.isInstitutional();
        account.frozen = tag.getBoolean("frozen");
        account.frozenReason = tag.contains("frozenReason") ? tag.getString("frozenReason") : "";
        account.creditScore = tag.contains("creditScore") ? Math.max(0, tag.getInt("creditScore")) : Math.max(0, Config.CREDIT_SCORE_DEFAULT.get());
        account.defaulted = tag.getBoolean("defaulted");
        account.accountAccessType = tag.contains("accountAccessType") ? tag.getString("accountAccessType") : "PERSONAL";
        account.businessLabel = tag.contains("businessLabel") ? tag.getString("businessLabel") : "";
        account.certificateTier = tag.contains("certificateTier") ? tag.getString("certificateTier") : "";
        account.certificateMaturityGameTime = tag.contains("certificateMaturityGameTime") ? tag.getLong("certificateMaturityGameTime") : -1L;
        account.certificateLocked = tag.getBoolean("certificateLocked");
        account.certificateMaturitySettled = tag.getBoolean("certificateMaturitySettled");
        account.certificateRate = tag.contains("certificateRate") ? tag.getDouble("certificateRate") : 0.0;
        account.lastVariableRate = tag.contains("lastVariableRate") ? tag.getDouble("lastVariableRate") : -1.0;
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
        long today = currentEpochDay();
        boolean hasDailyOutgoingAggregate = tag.contains("dailyOutgoingTransactionDay")
                && tag.contains("dailyOutgoingTransactionAmount");
        account.dailyOutgoingTransactionDay = today;
        account.dailyOutgoingTransactionAmount = BigDecimal.ZERO;
        if (hasDailyOutgoingAggregate && tag.getLong("dailyOutgoingTransactionDay") == today) {
            try {
                account.dailyOutgoingTransactionAmount = new BigDecimal(
                        tag.getString("dailyOutgoingTransactionAmount")).max(BigDecimal.ZERO);
            } catch (NumberFormatException ignored) {
                account.dailyOutgoingTransactionAmount = BigDecimal.ZERO;
            }
        }
        if (tag.contains("temporaryWithdrawalLimit")) {
            try {
                account.temporaryWithdrawalLimit = new BigDecimal(tag.getString("temporaryWithdrawalLimit"));
                account.temporaryWithdrawalLimitExpiresAtGameTime = tag.getLong("temporaryWithdrawalLimitExpiresAtGameTime");
                account.temporaryWithdrawalLimitExpiresAtEpochMillis = tag.contains("temporaryWithdrawalLimitExpiresAtEpochMillis")
                        ? tag.getLong("temporaryWithdrawalLimitExpiresAtEpochMillis")
                        : System.currentTimeMillis() + (TEMP_WITHDRAWAL_LIMIT_DURATION_TICKS * 50L);
            } catch (NumberFormatException ignored) {
                account.temporaryWithdrawalLimit = null;
                account.temporaryWithdrawalLimitExpiresAtGameTime = -1L;
                account.temporaryWithdrawalLimitExpiresAtEpochMillis = -1L;
            }
        }

        // Transactions
        account.transactions = new ConcurrentHashMap<>();
        if (tag.contains("transactions", Tag.TAG_LIST)) {
            ListTag txList = tag.getList("transactions", Tag.TAG_COMPOUND);
            int transactionLimit = configuredTransactionLogLimit();
            PriorityQueue<TransactionEntry> retained = new PriorityQueue<>(
                    transactionLimit + 1, TRANSACTION_OLDEST_FIRST);
            for (int i = 0; i < txList.size(); i++) {
                CompoundTag txTag = txList.getCompound(i);
                UserTransaction tx = UserTransaction.load(txTag, registries);
                if (tx == null) continue;

                UUID key = txTag.hasUUID("mapKey") ? txTag.getUUID("mapKey") : tx.getTransactionUUID();
                offerTransaction(retained, transactionLimit, key, tx);
                if (!hasDailyOutgoingAggregate
                        && account.accountUUID.equals(tx.getSenderUUID())
                        && tx.getTimestamp() != null
                        && tx.getTimestamp().toLocalDate().toEpochDay() == today
                        && tx.getAmount() != null
                        && tx.getAmount().signum() > 0) {
                    account.dailyOutgoingTransactionAmount = account.dailyOutgoingTransactionAmount.add(tx.getAmount());
                }
            }
            retained.forEach(entry -> account.transactions.put(entry.key(), entry.transaction()));
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

        account.accessRoles = new ConcurrentHashMap<>();
        if (tag.contains("accessRoles", Tag.TAG_LIST)) {
            ListTag roleList = tag.getList("accessRoles", Tag.TAG_COMPOUND);
            for (int i = 0; i < roleList.size(); i++) {
                CompoundTag roleTag = roleList.getCompound(i);
                if (!roleTag.hasUUID("playerId")) {
                    continue;
                }
                String role = roleTag.getString("role");
                if (role == null || role.isBlank()) {
                    continue;
                }
                account.accessRoles.put(roleTag.getUUID("playerId"), role.toUpperCase());
            }
        }
        if (!account.isInstitutional() && account.playerUUID != null) {
            account.accessRoles.putIfAbsent(account.playerUUID, "OWNER");
        }

        account.safeBoxSlots = new ConcurrentHashMap<>();
        if (tag.contains("safeBoxSlots", Tag.TAG_LIST)) {
            ListTag safeBoxList = tag.getList("safeBoxSlots", Tag.TAG_COMPOUND);
            for (int i = 0; i < safeBoxList.size(); i++) {
                CompoundTag slotTag = safeBoxList.getCompound(i);
                if (!slotTag.contains("stack", Tag.TAG_COMPOUND)) {
                    continue;
                }
                int slot = slotTag.getInt("slot");
                if (slot < 0) {
                    continue;
                }
                account.safeBoxSlots.put(slot, slotTag.getCompound("stack"));
            }
        }

        return account;
    }

    private static String normalizePrincipalId(String value, UUID fallbackPlayerId) {
        if (value == null || value.isBlank()) {
            return fallbackPlayerId == null ? "" : fallbackPlayerId.toString();
        }
        String normalized = value.trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    private static String normalizeInstitutionId(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.matches("[a-z0-9._:-]{1,160}") ? normalized : "";
    }

    private static final long OUTGOING_TX_WINDOW_MS = 60_000L;

    /**
     * Sliding-window limiter for outgoing transactions for this account.
     * Not persisted; recreated on load (per AccountHolder instance).
     */
    private final ArrayDeque<Long> outgoingTxTimestamps = new ArrayDeque<>();

    /**
     * Try to consume 1 outgoing transaction token.
     *
     * @return true if the transaction is allowed right now.
     */
    public boolean tryConsumeOutgoingTransaction() {
        long now = System.currentTimeMillis();
        int capacity = Math.max(1, Config.TRANSACTIONS_PER_MINUTE.get());
        synchronized (outgoingTxTimestamps) {
            while (!outgoingTxTimestamps.isEmpty()
                    && now - outgoingTxTimestamps.peekFirst() >= OUTGOING_TX_WINDOW_MS) {
                outgoingTxTimestamps.pollFirst();
            }
            if (outgoingTxTimestamps.size() >= capacity) {
                return false;
            }
            outgoingTxTimestamps.addLast(now);
            return true;
        }
    }

    private static boolean isFourDigitPin(String pin) {
        return pin != null && pin.matches("\\d{4}");
    }

    private static String normalizePin(String pin) {
        return isFourDigitPin(pin) ? pin : "";
    }
}
