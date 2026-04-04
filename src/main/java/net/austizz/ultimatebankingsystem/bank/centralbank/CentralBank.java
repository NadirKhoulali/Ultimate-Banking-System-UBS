package net.austizz.ultimatebankingsystem.bank.centralbank;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CentralBank extends Bank{
    private ConcurrentHashMap<UUID, Bank> banks;

    public CentralBank() {
        super(new UUID(0,0), "Central Bank", new BigDecimal("0"), 1.2, new UUID(0,0));
        this.banks = new ConcurrentHashMap<>();
        this.banks.put(this.getBankId(), this);
    }
    public ConcurrentHashMap<UUID, Bank> getBanks() {
        return banks;
    }
    public void addBank(Bank bank) {
        this.banks.put(bank.getBankId(), bank);
        NeoForge.EVENT_BUS.register(bank);
        BankManager.markDirty();
    }
    public boolean removeBank(Bank bank) {
        if (bank.getBankId().equals(this.getBankId())) {
            return false;
        }
        this.banks.remove(bank.getBankId());
        BankManager.markDirty();
        return true;
    }
    public Bank getBank(UUID uuid) {
        return this.banks.get(uuid);
    }
    public Bank getBankByName(String bankName) {
        return this.banks.values().stream().filter(bank -> bank.getBankName().equals(bankName)).findFirst().orElse(null);
    }
    public ConcurrentHashMap<UUID, AccountHolder> SearchForAccount(UUID playerId) {
        ConcurrentHashMap<UUID, AccountHolder> result = new ConcurrentHashMap<>();

        // TEMPORARY SOLUTION: THIS IS NOT OPTIMIZED CODE
        for (Bank bank : this.banks.values()) {
            for (AccountHolder account : bank.getBankAccounts().values()){
                if (account.getPlayerUUID().equals(playerId)) {
                    result.put(account.getAccountUUID(), account);
                }
            }
        }
        return result;
    }
    public AccountHolder SearchForAccountByAccountId(UUID accountId) {
        // TEMPORARY SOLUTION: THIS IS NOT OPTIMIZED CODE
        for (Bank bank : this.banks.values()) {
            for (AccountHolder account : bank.getBankAccounts().values()){
                if (account.getAccountUUID().equals(accountId)) {
                    return account;
                }
            }
        }
        return null;
    }
    public UserTransaction getTransaction(UUID transactionID) {
        // TEMPORARY SOLUTION: THIS IS NOT OPTIMIZED CODE
        for (Bank bank : this.banks.values()) {
            for (AccountHolder account : bank.getBankAccounts().values()) {
                UserTransaction tx = account.getTransactions().get(transactionID);
                if (tx != null) {
                    return tx;
                }
            }
        }
        return null;
    }


    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        // Sla eerst de basisgegevens van de Central Bank zelf op (via de super methode in Bank)
        super.save(tag, registries);

        // Sla alle banken in de ConcurrentHashMap op
        ListTag banksList = new ListTag();
        this.banks.values().forEach(bank -> {
            // Voorkom oneindige recursie: sla de Central Bank zelf niet NOGMAALS op in de lijst
            if (!bank.getBankId().equals(this.getBankId())) {
                banksList.add(bank.save(new CompoundTag(), registries));
            }
        });
        tag.put("sub_banks", banksList);

        return tag;
    }

    public static CentralBank load(CompoundTag tag, HolderLookup.Provider registries) {
        CentralBank centralBank = new CentralBank();

        // Restore base fields saved by Bank.save(...)
        if (tag.contains("bankName")) {
            centralBank.setBankName(tag.getString("bankName"));
        }
        if (tag.contains("bankReserve")) {
            centralBank.setReserve(new BigDecimal(tag.getString("bankReserve")));
        }
        if (tag.contains("interestRate")) {
            centralBank.setInterestRate(tag.getDouble("interestRate"));
        }
        // bankOwner has no setter; ignore for now

        // Restore the central bank's own accounts saved by super.save(...)
        ListTag centralAccounts = tag.getList("accounts", 10); // 10 = CompoundTag
        for (int i = 0; i < centralAccounts.size(); i++) {
            AccountHolder acc = AccountHolder.load(centralAccounts.getCompound(i), registries);
            centralBank.AddAccount(acc);
        }

        // Restore sub-banks
        ListTag banksList = tag.getList("sub_banks", 10);
        for (int i = 0; i < banksList.size(); i++) {
            Bank loadedBank = Bank.load(banksList.getCompound(i), registries);
            centralBank.addBank(loadedBank);
        }
        return centralBank;
    }


}
