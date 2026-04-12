package net.austizz.ultimatebankingsystem.bank.centralbank;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.payments.ScheduledPayment;
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
    private ConcurrentHashMap<UUID, ScheduledPayment> scheduledPayments;
    private ConcurrentHashMap<String, Boolean> redeemedNoteSerials;
    private ConcurrentHashMap<String, Boolean> redeemedChequeIds;

    public CentralBank() {
        super(new UUID(0,0), "Central Bank", new BigDecimal("0"), 1.2, new UUID(0,0));
        this.banks = new ConcurrentHashMap<>();
        this.banks.put(this.getBankId(), this);
        this.scheduledPayments = new ConcurrentHashMap<>();
        this.redeemedNoteSerials = new ConcurrentHashMap<>();
        this.redeemedChequeIds = new ConcurrentHashMap<>();
    }
    public ConcurrentHashMap<UUID, Bank> getBanks() {
        return banks;
    }
    public void addBank(Bank bank) {
        this.banks.put(bank.getBankId(), bank);
//        NeoForge.EVENT_BUS.register(bank);
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

    public ConcurrentHashMap<UUID, ScheduledPayment> getScheduledPayments() {
        if (this.scheduledPayments == null) {
            this.scheduledPayments = new ConcurrentHashMap<>();
        }
        return scheduledPayments;
    }

    public void addScheduledPayment(ScheduledPayment payment) {
        if (payment == null) {
            return;
        }
        getScheduledPayments().put(payment.getPaymentId(), payment);
        BankManager.markDirty();
    }

    public boolean removeScheduledPayment(UUID paymentId) {
        if (paymentId == null) {
            return false;
        }
        ScheduledPayment removed = getScheduledPayments().remove(paymentId);
        if (removed != null) {
            BankManager.markDirty();
            return true;
        }
        return false;
    }

    public boolean isNoteSerialRedeemed(String serial) {
        if (serial == null || serial.isBlank()) {
            return false;
        }
        return redeemedNoteSerials != null && redeemedNoteSerials.containsKey(serial);
    }

    public void markNoteSerialRedeemed(String serial) {
        if (serial == null || serial.isBlank()) {
            return;
        }
        if (redeemedNoteSerials == null) {
            redeemedNoteSerials = new ConcurrentHashMap<>();
        }
        redeemedNoteSerials.put(serial, Boolean.TRUE);
        BankManager.markDirty();
    }

    public boolean isChequeRedeemed(String chequeId) {
        if (chequeId == null || chequeId.isBlank()) {
            return false;
        }
        return redeemedChequeIds != null && redeemedChequeIds.containsKey(chequeId);
    }

    public void markChequeRedeemed(String chequeId) {
        if (chequeId == null || chequeId.isBlank()) {
            return;
        }
        if (redeemedChequeIds == null) {
            redeemedChequeIds = new ConcurrentHashMap<>();
        }
        redeemedChequeIds.put(chequeId, Boolean.TRUE);
        BankManager.markDirty();
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

        ListTag scheduledPaymentsTag = new ListTag();
        getScheduledPayments().values().forEach(payment -> {
            CompoundTag paymentTag = new CompoundTag();
            payment.save(paymentTag, registries);
            scheduledPaymentsTag.add(paymentTag);
        });
        tag.put("scheduled_payments", scheduledPaymentsTag);

        ListTag noteSerials = new ListTag();
        if (redeemedNoteSerials != null) {
            redeemedNoteSerials.keySet().forEach(serial -> {
                CompoundTag serialTag = new CompoundTag();
                serialTag.putString("serial", serial);
                noteSerials.add(serialTag);
            });
        }
        tag.put("redeemed_note_serials", noteSerials);

        ListTag redeemedCheques = new ListTag();
        if (redeemedChequeIds != null) {
            redeemedChequeIds.keySet().forEach(chequeId -> {
                CompoundTag chequeTag = new CompoundTag();
                chequeTag.putString("chequeId", chequeId);
                redeemedCheques.add(chequeTag);
            });
        }
        tag.put("redeemed_cheque_ids", redeemedCheques);

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

        centralBank.scheduledPayments = new ConcurrentHashMap<>();
        ListTag scheduledPaymentsTag = tag.getList("scheduled_payments", 10);
        for (int i = 0; i < scheduledPaymentsTag.size(); i++) {
            ScheduledPayment payment = ScheduledPayment.load(scheduledPaymentsTag.getCompound(i), registries);
            if (payment != null) {
                centralBank.scheduledPayments.put(payment.getPaymentId(), payment);
            }
        }

        centralBank.redeemedNoteSerials = new ConcurrentHashMap<>();
        ListTag noteSerials = tag.getList("redeemed_note_serials", 10);
        for (int i = 0; i < noteSerials.size(); i++) {
            CompoundTag serialTag = noteSerials.getCompound(i);
            String serial = serialTag.getString("serial");
            if (!serial.isBlank()) {
                centralBank.redeemedNoteSerials.put(serial, Boolean.TRUE);
            }
        }

        centralBank.redeemedChequeIds = new ConcurrentHashMap<>();
        ListTag redeemedCheques = tag.getList("redeemed_cheque_ids", 10);
        for (int i = 0; i < redeemedCheques.size(); i++) {
            CompoundTag chequeTag = redeemedCheques.getCompound(i);
            String chequeId = chequeTag.getString("chequeId");
            if (!chequeId.isBlank()) {
                centralBank.redeemedChequeIds.put(chequeId, Boolean.TRUE);
            }
        }
        return centralBank;
    }


}
