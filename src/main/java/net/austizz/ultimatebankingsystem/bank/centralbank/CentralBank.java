package net.austizz.ultimatebankingsystem.bank.centralbank;

import net.austizz.ultimatebankingsystem.bank.Bank;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CentralBank extends Bank{
    private ConcurrentHashMap<UUID, Bank> banks;

    public CentralBank() {
        super(new UUID(0,0), "Central Bank", new BigDecimal("0"), 1.2, new UUID(0,0));
        this.setReserve(this.getBankReserve());
        this.banks = new ConcurrentHashMap<>();

    }
    public ConcurrentHashMap<UUID, Bank> getBanks() {
        return banks;
    }
    public void addBank(Bank bank) {
        this.banks.put(bank.getBankId(), bank);
    }
    public void removeBank(Bank bank) {
        this.banks.remove(bank.getBankId());
    }
    public Bank getBank(UUID uuid) {
        return this.banks.get(uuid);
    }
}
