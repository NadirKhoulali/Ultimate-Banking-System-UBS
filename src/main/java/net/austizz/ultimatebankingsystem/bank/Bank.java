package net.austizz.ultimatebankingsystem.bank;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.BankToUserTransaction;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Bank {
    public static final BigDecimal HUNDRED = new BigDecimal("100");
    private final UUID bankId;
    private String bankName;
    private ConcurrentHashMap<UUID, AccountHolder> BankAccounts;
    private BigDecimal BankReserve;
    private double InterestRate;
    private UUID BankOwner;
    private static final int THIRTY_DAYS_IN_TICKS = 100;
    private int tickCounter = 0; // GEEN static meer



    public Bank (UUID bankId, String BankName, BigDecimal BankReserve, double InterestRate, UUID BankOwnerId) {
        this.bankId = (bankId == null) ? UUID.randomUUID() : bankId;
        this.bankName = BankName;
        this.BankAccounts = new ConcurrentHashMap<>();
        this.BankReserve = BankReserve;
        this.InterestRate = InterestRate;
        this.BankOwner = BankOwnerId;
    }

    public UUID getBankId() {
        return bankId;
    }
    public String getBankName() {
        return bankName;
    }
    public ConcurrentHashMap<UUID, AccountHolder> getBankAccounts() {
        return BankAccounts;
    }
    public UUID getBankOwnerId() {
        return BankOwner;
    }
    public void setBankOwnerId(UUID bankOwnerId) {
        if (bankOwnerId == null) {
            return;
        }
        this.BankOwner = bankOwnerId;
        BankManager.markDirty();
    }
    public BigDecimal getTotalDeposits() {
        BigDecimal total = BigDecimal.ZERO;
        for (AccountHolder accountHolder : this.BankAccounts.values()) {
            total = total.add(accountHolder.getBalance());
        }
        return total;
    }
    public BigDecimal getDeclaredReserve() {
        return BankReserve;
    }
    public AccountHolder getBankAccount(UUID accountId) {
        return BankAccounts.get(accountId);
    }
    public BigDecimal getBankReserve() {
        return this.BankReserve;
    }
    public BigDecimal getMinimumRequiredReserve() {
        return getTotalDeposits()
                .multiply(BigDecimal.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()))
                .setScale(2, RoundingMode.HALF_EVEN);
    }
    public BigDecimal getReserveRatio() {
        BigDecimal deposits = getTotalDeposits();
        if (deposits.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return this.BankReserve.divide(deposits, 6, RoundingMode.HALF_EVEN);
    }
    public BigDecimal getOutstandingLoanBalance() {
        BigDecimal total = BigDecimal.ZERO;
        for (AccountHolder account : this.BankAccounts.values()) {
            if (account == null) {
                continue;
            }
            for (var loan : account.getActiveLoans().values()) {
                if (loan == null || loan.isDefaulted()) {
                    continue;
                }
                total = total.add(loan.getRemainingBalance());
            }
        }
        return total.setScale(2, RoundingMode.HALF_EVEN);
    }
    public BigDecimal getMaxLendableAmount() {
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            var cb = BankManager.getCentralBank(ServerLifecycleHooks.getCurrentServer());
            if (cb != null && this.bankId.equals(cb.getBankId())) {
                return BigDecimal.valueOf(Double.MAX_VALUE);
            }
        }
        BigDecimal reserveRatio = BigDecimal.valueOf(Config.BANK_MIN_RESERVE_RATIO.get());
        BigDecimal lendableFactor = BigDecimal.ONE.subtract(reserveRatio);
        if (lendableFactor.compareTo(BigDecimal.ZERO) < 0) {
            lendableFactor = BigDecimal.ZERO;
        }
        return getTotalDeposits().multiply(lendableFactor).setScale(2, RoundingMode.HALF_EVEN);
    }
    public boolean canIssueLoan(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return getOutstandingLoanBalance().add(amount).compareTo(getMaxLendableAmount()) <= 0;
    }
    public double getInterestRate() {
        return InterestRate;
    }
    public void setReserve(BigDecimal BankReserve) {
        this.BankReserve = BankReserve;
        BankManager.markDirty();
    }
    public void setInterestRate(double InterestRate) {
        double adjusted = InterestRate;
        adjusted = Math.max(Config.MIN_CUSTOM_BANK_INTEREST_RATE.get(), adjusted);
        adjusted = Math.min(Config.MAX_CUSTOM_BANK_INTEREST_RATE.get(), adjusted);

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank != null && !this.bankId.equals(centralBank.getBankId())) {
                var metadata = centralBank.getOrCreateBankMetadata(this.bankId);
                if (!metadata.getBoolean("rateExempt")) {
                    double floor = centralBank.getFederalFundsRate() * Config.SAVINGS_RATE_FLOOR_MULTIPLIER.get();
                    double ceiling = centralBank.getFederalFundsRate() * Config.SAVINGS_RATE_CEILING_MULTIPLIER.get();
                    if (ceiling < floor) {
                        double tmp = floor;
                        floor = ceiling;
                        ceiling = tmp;
                    }
                    adjusted = Math.max(floor, Math.min(ceiling, adjusted));
                }
            }
        }

        this.InterestRate = adjusted;
        BankManager.markDirty();
    }
    public void setBankName(String BankName) {
        this.bankName = BankName;
        BankManager.markDirty();
    }
    public AccountHolder getPlayerAccount(Player player) {
        UltimateBankingSystem.LOGGER.info("Geting player account for " + player.getName());
        for (AccountHolder account : this.BankAccounts.values()) {
            UltimateBankingSystem.LOGGER.info("Player UUID : " + player.getUUID() + " " + "Account UUID: " + account.getPlayerUUID());
            if (account.getPlayerUUID().equals(player.getUUID())) {
                return account;
            }
        };
        return null;
    }
    public boolean AddAccount(AccountHolder AccountHolder) {

        // Only prevent duplicates for the same player AND same account type.
        // (Previously this blocked *any* second account of the same type in the whole bank.)
        for (AccountHolder account : this.BankAccounts.values()) {
            if (account == null) continue;

            if (account.getPlayerUUID() != null
                    && account.getPlayerUUID().equals(AccountHolder.getPlayerUUID())
                    && account.getAccountType() != null
                    && account.getAccountType().equals(AccountHolder.getAccountType())) {
                return false;
            }
        }

        this.BankAccounts.put(AccountHolder.getAccountUUID(), AccountHolder);
        BankManager.markDirty();
        return true;
    }
    public void RemoveAccount(AccountHolder AccountHolder) {
        BigDecimal removedBalance = new BigDecimal("0");
        removedBalance = AccountHolder.getBalance();
        this.BankAccounts.remove(AccountHolder.getAccountUUID());
        this.BankReserve = BankReserve.add(removedBalance);
        BankManager.markDirty();
    }

//    @SubscribeEvent
//    public void onServerTick(ServerTickEvent.Post event) {
//        this.tickCounter++;
//
//        if (this.tickCounter >= THIRTY_DAYS_IN_TICKS) {
//            payInterestAllSavingAccounts();
//            this.tickCounter = 0;
//            BankManager.markDirty(); // Zorg dat de nieuwe stand (0) wordt opgeslagen
//        }
//    }

    public void payInterestAllSavingAccounts() {
        var server = ServerLifecycleHooks.getCurrentServer();
        var centralBank = server == null ? null : BankManager.getCentralBank(server);
        long gameTime = 0L;
        if (server != null && server.getLevel(Level.OVERWORLD) != null) {
            gameTime = server.getLevel(Level.OVERWORLD).getGameTime();
        }

        for (AccountHolder account : this.BankAccounts.values()) {
            if (account == null) {
                continue;
            }
            if (account.getAccountType() == AccountTypes.CertificateAccount) {
                processCertificateMaturity(account, gameTime, server);
                continue;
            }

            if (account.getAccountType() != AccountTypes.SavingAccount
                    && account.getAccountType() != AccountTypes.MoneyMarketAccount) {
                continue;
            }

            double annualRate = resolveEffectiveAnnualRate(account, centralBank);
            if (annualRate <= 0.0) {
                continue;
            }

            int periodsPerYear = account.getAccountType() == AccountTypes.MoneyMarketAccount ? 365 : 12;
            BigDecimal periodicRate = BigDecimal.valueOf(annualRate)
                    .divide(HUNDRED, 10, RoundingMode.HALF_EVEN)
                    .divide(BigDecimal.valueOf(periodsPerYear), 10, RoundingMode.HALF_EVEN);

            BigDecimal payoutAmount = account.getBalance()
                    .multiply(periodicRate)
                    .setScale(2, RoundingMode.HALF_EVEN);

            if (payoutAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (!account.AddBalance(payoutAmount)) {
                continue;
            }

            account.addTransaction(new UserTransaction(
                    this.bankId,
                    account.getAccountUUID(),
                    payoutAmount,
                    LocalDateTime.now(),
                    account.getAccountType() == AccountTypes.MoneyMarketAccount
                            ? "INTEREST: Money Market Compound Payout"
                            : "INTEREST: Savings Compound Payout"
            ));

            if (account.getAccountType() == AccountTypes.MoneyMarketAccount) {
                double previousRate = account.getLastVariableRate();
                if (Math.abs(previousRate - annualRate) > 0.0001D && previousRate >= 0.0D) {
                    ServerPlayer holder = server == null ? null : server.getPlayerList().getPlayer(account.getPlayerUUID());
                    if (holder != null) {
                        holder.sendSystemMessage(Component.literal(
                                "§eMoney Market rate changed: §f" + previousRate + "% §7-> §f" + annualRate + "%"
                        ));
                    }
                }
                account.setLastVariableRate(annualRate);
            }

            if (server != null) {
                ServerPlayer holder = server.getPlayerList().getPlayer(account.getPlayerUUID());
                if (holder != null) {
                    holder.sendSystemMessage(Component.literal(
                            "§aInterest paid: §6$" + payoutAmount.toPlainString()
                                    + " §a(" + annualRate + "% APR) New balance: §f$"
                                    + account.getBalance().toPlainString()
                    ));
                }
            }

            BankManager.markDirty();
        }
    }

    private double resolveEffectiveAnnualRate(AccountHolder account, net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank centralBank) {
        if (account.getAccountType() == AccountTypes.MoneyMarketAccount && centralBank != null) {
            double multiplier = Config.MMA_RATE_MULTIPLIER.get();
            multiplier = Math.max(Config.MIN_CUSTOM_BANK_INTEREST_RATE.get(),
                    Math.min(Config.MAX_CUSTOM_BANK_INTEREST_RATE.get(), multiplier));
            return centralBank.getFederalFundsRate() * multiplier;
        }
        return this.InterestRate;
    }

    private void processCertificateMaturity(AccountHolder account, long gameTime, net.minecraft.server.MinecraftServer server) {
        if (account.getCertificateTier().isBlank()) {
            return;
        }

        boolean locked = account.isCertificateLocked(gameTime);
        if (locked || account.isCertificateMaturitySettled()) {
            return;
        }
        if (account.getCertificateMaturityGameTime() <= 0L || gameTime < account.getCertificateMaturityGameTime()) {
            return;
        }

        BigDecimal principal = account.getBalance();
        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            account.setCertificateMaturitySettled(true);
            return;
        }

        long termTicks = switch (account.getCertificateTier()) {
            case "short" -> Config.CD_SHORT_TERM_TICKS.get();
            case "medium" -> Config.CD_MEDIUM_TERM_TICKS.get();
            case "long" -> Config.CD_LONG_TERM_TICKS.get();
            default -> 24000L;
        };
        BigDecimal years = BigDecimal.valueOf(termTicks)
                .divide(BigDecimal.valueOf(24000D * 365D), 10, RoundingMode.HALF_EVEN);
        BigDecimal interest = principal
                .multiply(BigDecimal.valueOf(account.getCertificateRate())
                        .divide(HUNDRED, 10, RoundingMode.HALF_EVEN))
                .multiply(years)
                .setScale(2, RoundingMode.HALF_EVEN);
        if (interest.compareTo(BigDecimal.ZERO) > 0) {
            account.forceAddBalance(interest);
            account.addTransaction(new UserTransaction(
                    this.bankId,
                    account.getAccountUUID(),
                    interest,
                    LocalDateTime.now(),
                    "CD_MATURITY_PAYOUT:" + account.getCertificateTier()
            ));
        }

        account.setCertificateMaturitySettled(true);
        ServerPlayer holder = server == null ? null : server.getPlayerList().getPlayer(account.getPlayerUUID());
        if (holder != null) {
            holder.sendSystemMessage(Component.literal(
                    "§aYour CD matured. Interest credited: §6$" + interest.toPlainString()
                            + " §a(new balance: §f$" + account.getBalance().toPlainString() + "§a)."
            ));
        }
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("tickCounter", this.tickCounter);
        tag.putUUID("bankId", this.bankId);
        tag.putString("bankName", this.bankName);
        tag.putString("bankReserve", this.BankReserve.toString());
        tag.putDouble("interestRate", this.InterestRate);
        tag.putUUID("bankOwner", this.BankOwner);

        // Accounts opslaan in een ListTag
        ListTag accountsList = new ListTag();
        this.BankAccounts.values().forEach(account -> {
            accountsList.add(account.save(new CompoundTag(), registries));
        });
        tag.put("accounts", accountsList);

        return tag;
    }

    public static Bank load(CompoundTag tag, HolderLookup.Provider registries) {
        Bank bank = new Bank(
                tag.getUUID("bankId"),
                tag.getString("bankName"),
                new BigDecimal(tag.getString("bankReserve")),
                tag.getDouble("interestRate"),
                tag.getUUID("bankOwner")
        );
        if (tag.contains("tickCounter")) {
            bank.tickCounter = tag.getInt("tickCounter"); // Laad de stand terug
        }

        ListTag accountsList = tag.getList("accounts", 10); // 10 = CompoundTag
        for (int i = 0; i < accountsList.size(); i++) {
            AccountHolder acc = AccountHolder.load(accountsList.getCompound(i), registries);
            bank.AddAccount(acc);
        }
        return bank;
    }


}
