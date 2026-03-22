package net.austizz.ultimatebankingsystem.bank;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
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
    public BigDecimal getBankReserve() {
        BigDecimal total = new BigDecimal("0");
        this.BankAccounts.forEach((uuid, accountHolder) -> {
            total.add(accountHolder.getBalance());
        });
        return total;
    }
    public double getInterestRate() {
        return InterestRate;
    }
    public void setReserve(BigDecimal BankReserve) {
        this.BankReserve = BankReserve;
    }
    public void setInterestRate(double InterestRate) {
        this.InterestRate = InterestRate;
    }
    public void setBankName(String BankName) {
        this.bankName = BankName;
    }
    public void AddAccount(AccountHolder AccountHolder) {
        this.BankAccounts.put(AccountHolder.getAccountUUID(), AccountHolder);

    }
}
