package net.austizz.ultimatebankingsystem.bank;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.handler.Response;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Bank {
    private UUID bankId;
    private String bankName;
    private ConcurrentHashMap<UUID, AccountHolder> BankAccounts;
    private BigDecimal BankReserve;
    private double InterestRate;
    private UUID BankOwner;

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
    public AccountHolder getBankAccount(UUID accountId) {
        return BankAccounts.get(accountId);
    }
    public BigDecimal getBankReserve() {
        BigDecimal total = BigDecimal.ZERO;
        for (AccountHolder accountHolder : this.BankAccounts.values()) {
            total = total.add(accountHolder.getBalance());
        }
        return total;
    }
    public double getInterestRate() {
        return InterestRate;
    }
    public void setReserve(BigDecimal BankReserve) {
        this.BankReserve = BankReserve;
        BankManager.markDirty();
    }
    public void setInterestRate(double InterestRate) {
        this.InterestRate = InterestRate;
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

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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

        ListTag accountsList = tag.getList("accounts", 10); // 10 = CompoundTag
        for (int i = 0; i < accountsList.size(); i++) {
            AccountHolder acc = AccountHolder.load(accountsList.getCompound(i), registries);
            bank.AddAccount(acc);
        }
        return bank;
    }


}
