package net.austizz.ultimatebankingsystem.bank.centralbank;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.BankToBankTransaction;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.payments.ScheduledPayment;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.common.MinecraftForge;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CentralBank extends Bank{
    private static final int TELLER_VARIANT_MALE = 0;
    private static final int TELLER_VARIANT_FEMALE = 1;

    private ConcurrentHashMap<UUID, Bank> banks;
    private ConcurrentHashMap<UUID, ScheduledPayment> scheduledPayments;
    private ConcurrentHashMap<String, Boolean> redeemedNoteSerials;
    private ConcurrentHashMap<String, Boolean> redeemedChequeIds;
    private double federalFundsRate;
    private ConcurrentHashMap<UUID, CompoundTag> bankMetadata;
    private ConcurrentHashMap<UUID, CompoundTag> bankApplications;
    private ConcurrentHashMap<UUID, CompoundTag> bankAppeals;
    private ConcurrentHashMap<UUID, CompoundTag> openMarketOperations;
    private ConcurrentHashMap<UUID, CompoundTag> settlementLedger;
    private ConcurrentHashMap<UUID, CompoundTag> settlementSuspense;
    private ConcurrentHashMap<UUID, CompoundTag> interbankOffers;
    private ConcurrentHashMap<UUID, CompoundTag> interbankLoans;
    private ConcurrentHashMap<UUID, CompoundTag> reportSnapshots;
    private ConcurrentHashMap<UUID, CompoundTag> issuedCreditCards;
    private int nextBankTellerVariant;

    public CentralBank() {
        super(new UUID(0,0), "Central Bank", new BigDecimal("0"), 1.2, new UUID(0,0));
        this.banks = new ConcurrentHashMap<>();
        this.banks.put(this.getBankId(), this);
        this.scheduledPayments = new ConcurrentHashMap<>();
        this.redeemedNoteSerials = new ConcurrentHashMap<>();
        this.redeemedChequeIds = new ConcurrentHashMap<>();
        this.federalFundsRate = Config.DEFAULT_FEDERAL_FUNDS_RATE.get();
        this.bankMetadata = new ConcurrentHashMap<>();
        this.bankApplications = new ConcurrentHashMap<>();
        this.bankAppeals = new ConcurrentHashMap<>();
        this.openMarketOperations = new ConcurrentHashMap<>();
        this.settlementLedger = new ConcurrentHashMap<>();
        this.settlementSuspense = new ConcurrentHashMap<>();
        this.interbankOffers = new ConcurrentHashMap<>();
        this.interbankLoans = new ConcurrentHashMap<>();
        this.reportSnapshots = new ConcurrentHashMap<>();
        this.issuedCreditCards = new ConcurrentHashMap<>();
        this.nextBankTellerVariant = TELLER_VARIANT_MALE;
    }
    public ConcurrentHashMap<UUID, Bank> getBanks() {
        return banks;
    }
    public void addBank(Bank bank) {
        this.banks.put(bank.getBankId(), bank);
//        MinecraftForge.EVENT_BUS.register(bank);
        BankManager.markDirty();
    }
    public boolean removeBank(Bank bank) {
        if (bank.getBankId().equals(this.getBankId())) {
            return false;
        }
        this.banks.remove(bank.getBankId());
        if (this.bankMetadata != null) {
            this.bankMetadata.remove(bank.getBankId());
        }
        BankManager.markDirty();
        return true;
    }
    public Bank getBank(UUID uuid) {
        return this.banks.get(uuid);
    }
    public Bank getBankByName(String bankName) {
        if (bankName == null || bankName.isBlank()) {
            return null;
        }
        String normalized = bankName.trim();
        return this.banks.values().stream()
                .filter(bank -> bank != null && bank.getBankName() != null)
                .filter(bank -> bank.getBankName().equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
    }
    public ConcurrentHashMap<UUID, AccountHolder> SearchForAccount(UUID playerId) {
        ConcurrentHashMap<UUID, AccountHolder> result = new ConcurrentHashMap<>();
        if (playerId == null) {
            return result;
        }

        // TEMPORARY SOLUTION: THIS IS NOT OPTIMIZED CODE
        for (Bank bank : this.banks.values()) {
            if (bank == null || bank.getBankAccounts() == null) {
                continue;
            }
            for (AccountHolder account : bank.getBankAccounts().values()){
                if (account != null && playerId.equals(account.getPlayerUUID())) {
                    result.put(account.getAccountUUID(), account);
                }
            }
        }
        return result;
    }
    public AccountHolder SearchForAccountByAccountId(UUID accountId) {
        if (accountId == null) {
            return null;
        }
        // TEMPORARY SOLUTION: THIS IS NOT OPTIMIZED CODE
        for (Bank bank : this.banks.values()) {
            if (bank == null || bank.getBankAccounts() == null) {
                continue;
            }
            for (AccountHolder account : bank.getBankAccounts().values()){
                if (account != null && accountId.equals(account.getAccountUUID())) {
                    return account;
                }
            }
        }
        return null;
    }
    public UserTransaction getTransaction(UUID transactionID) {
        if (transactionID == null) {
            return null;
        }
        // TEMPORARY SOLUTION: THIS IS NOT OPTIMIZED CODE
        for (Bank bank : this.banks.values()) {
            if (bank == null || bank.getBankAccounts() == null) {
                continue;
            }
            for (AccountHolder account : bank.getBankAccounts().values()) {
                if (account == null || account.getTransactions() == null) {
                    continue;
                }
                UserTransaction tx = account.getTransactions().get(transactionID);
                if (tx != null) {
                    return tx;
                }
            }
        }
        return null;
    }

    public synchronized boolean settle(BankToBankTransaction transaction) {
        if (transaction == null || transaction.getAmount() == null
                || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        Bank senderBank = getBank(transaction.getSenderBankUUID());
        Bank receiverBank = getBank(transaction.getReceiverBankUUID());
        if (senderBank == null || receiverBank == null) {
            recordSettlementSuspense(transaction, "Missing sender or receiver bank");
            return false;
        }
        if (senderBank.getDeclaredReserve().compareTo(transaction.getAmount()) < 0) {
            recordSettlementSuspense(transaction, "Insufficient sender reserve");
            return false;
        }

        senderBank.setReserve(senderBank.getDeclaredReserve().subtract(transaction.getAmount()));
        receiverBank.setReserve(receiverBank.getDeclaredReserve().add(transaction.getAmount()));
        recordSettlementLedger(transaction, "SETTLED");
        BankManager.markDirty();
        return true;
    }

    private void recordSettlementLedger(BankToBankTransaction transaction, String reason) {
        UUID entryId = transaction.getTransactionUUID() == null ? UUID.randomUUID() : transaction.getTransactionUUID();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", entryId);
        tag.putUUID("fromBankId", transaction.getSenderBankUUID());
        tag.putUUID("toBankId", transaction.getReceiverBankUUID());
        tag.putString("amount", transaction.getAmount().toPlainString());
        tag.putLong("timestampMillis", System.currentTimeMillis());
        tag.putString("reason", reason == null ? "" : reason);
        tag.putBoolean("success", true);
        getSettlementLedger().put(entryId, tag);
        trimTagMap(getSettlementLedger(), Math.max(1, Config.CLEARING_LEDGER_LIMIT.get()));
    }

    private void recordSettlementSuspense(BankToBankTransaction transaction, String reason) {
        UUID entryId = transaction.getTransactionUUID() == null ? UUID.randomUUID() : transaction.getTransactionUUID();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", entryId);
        tag.putUUID("fromBankId", transaction.getSenderBankUUID());
        tag.putUUID("toBankId", transaction.getReceiverBankUUID());
        tag.putString("amount", transaction.getAmount().toPlainString());
        tag.putLong("timestampMillis", System.currentTimeMillis());
        tag.putString("reason", reason == null ? "" : reason);
        tag.putBoolean("success", false);
        getSettlementSuspense().put(entryId, tag);
        trimTagMap(getSettlementSuspense(), Math.max(1, Config.CLEARING_LEDGER_LIMIT.get()));
        BankManager.markDirty();
    }

    private static void trimTagMap(ConcurrentHashMap<UUID, CompoundTag> map, int maxSize) {
        if (map == null || map.size() <= maxSize || maxSize < 1) {
            return;
        }
        var ordered = map.entrySet().stream()
                .sorted(java.util.Comparator.comparingLong(entry -> entry.getValue().getLong("timestampMillis")))
                .toList();
        int removeCount = map.size() - maxSize;
        for (int i = 0; i < removeCount && i < ordered.size(); i++) {
            map.remove(ordered.get(i).getKey());
        }
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

    public double getFederalFundsRate() {
        return federalFundsRate;
    }

    public boolean setFederalFundsRate(double rate) {
        if (Double.isNaN(rate) || Double.isInfinite(rate)) {
            return false;
        }
        if (rate < Config.MIN_FEDERAL_FUNDS_RATE.get() || rate > Config.MAX_FEDERAL_FUNDS_RATE.get()) {
            return false;
        }
        this.federalFundsRate = rate;
        BankManager.markDirty();
        return true;
    }

    public ConcurrentHashMap<UUID, CompoundTag> getBankMetadata() {
        if (this.bankMetadata == null) {
            this.bankMetadata = new ConcurrentHashMap<>();
        }
        return this.bankMetadata;
    }

    public CompoundTag getOrCreateBankMetadata(UUID bankId) {
        if (bankId == null) {
            return new CompoundTag();
        }
        return getBankMetadata().computeIfAbsent(bankId, id -> {
            CompoundTag meta = new CompoundTag();
            meta.putString("status", "ACTIVE");
            meta.putString("ownershipModel", "SOLE");
            meta.putString("motto", "");
            meta.putString("color", "#55AAFF");
            meta.putBoolean("rateExempt", false);
            meta.putLong("nextLicenseFeeTick", 0L);
            meta.putString("dailyWithdrawn", "0");
            meta.putLong("dailyWindowDay", 0L);
            meta.putString("reserveMinRatio", String.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()));
            meta.putString("cardIssueFee", "25");
            meta.putString("cardReplacementFee", "50");
            return meta;
        });
    }

    public void putBankMetadata(UUID bankId, CompoundTag metadata) {
        if (bankId == null || metadata == null) {
            return;
        }
        getBankMetadata().put(bankId, metadata);
        BankManager.markDirty();
    }

    public ConcurrentHashMap<UUID, CompoundTag> getBankApplications() {
        if (bankApplications == null) {
            bankApplications = new ConcurrentHashMap<>();
        }
        return bankApplications;
    }

    public ConcurrentHashMap<UUID, CompoundTag> getBankAppeals() {
        if (bankAppeals == null) {
            bankAppeals = new ConcurrentHashMap<>();
        }
        return bankAppeals;
    }

    public ConcurrentHashMap<UUID, CompoundTag> getOpenMarketOperations() {
        if (openMarketOperations == null) {
            openMarketOperations = new ConcurrentHashMap<>();
        }
        return openMarketOperations;
    }

    public ConcurrentHashMap<UUID, CompoundTag> getSettlementLedger() {
        if (settlementLedger == null) {
            settlementLedger = new ConcurrentHashMap<>();
        }
        return settlementLedger;
    }

    public ConcurrentHashMap<UUID, CompoundTag> getSettlementSuspense() {
        if (settlementSuspense == null) {
            settlementSuspense = new ConcurrentHashMap<>();
        }
        return settlementSuspense;
    }

    public ConcurrentHashMap<UUID, CompoundTag> getInterbankOffers() {
        if (interbankOffers == null) {
            interbankOffers = new ConcurrentHashMap<>();
        }
        return interbankOffers;
    }

    public ConcurrentHashMap<UUID, CompoundTag> getInterbankLoans() {
        if (interbankLoans == null) {
            interbankLoans = new ConcurrentHashMap<>();
        }
        return interbankLoans;
    }

    public ConcurrentHashMap<UUID, CompoundTag> getReportSnapshots() {
        if (reportSnapshots == null) {
            reportSnapshots = new ConcurrentHashMap<>();
        }
        return reportSnapshots;
    }

    public ConcurrentHashMap<UUID, CompoundTag> getIssuedCreditCards() {
        if (issuedCreditCards == null) {
            issuedCreditCards = new ConcurrentHashMap<>();
        }
        return issuedCreditCards;
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

    public synchronized int claimNextBankTellerVariant() {
        int variant = this.nextBankTellerVariant == TELLER_VARIANT_FEMALE
                ? TELLER_VARIANT_FEMALE
                : TELLER_VARIANT_MALE;
        this.nextBankTellerVariant = variant == TELLER_VARIANT_MALE
                ? TELLER_VARIANT_FEMALE
                : TELLER_VARIANT_MALE;
        BankManager.markDirty();
        return variant;
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

        tag.putDouble("federal_funds_rate", this.federalFundsRate);
        tag.put("bank_metadata", saveTagMap(getBankMetadata()));
        tag.put("bank_applications", saveTagMap(getBankApplications()));
        tag.put("bank_appeals", saveTagMap(getBankAppeals()));
        tag.put("open_market_operations", saveTagMap(getOpenMarketOperations()));
        tag.put("settlement_ledger", saveTagMap(getSettlementLedger()));
        tag.put("settlement_suspense", saveTagMap(getSettlementSuspense()));
        tag.put("interbank_offers", saveTagMap(getInterbankOffers()));
        tag.put("interbank_loans", saveTagMap(getInterbankLoans()));
        tag.put("report_snapshots", saveTagMap(getReportSnapshots()));
        tag.put("issued_credit_cards", saveTagMap(getIssuedCreditCards()));
        tag.putInt("next_bank_teller_variant", this.nextBankTellerVariant);

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

        centralBank.federalFundsRate = tag.contains("federal_funds_rate")
                ? tag.getDouble("federal_funds_rate")
                : Config.DEFAULT_FEDERAL_FUNDS_RATE.get();
        centralBank.bankMetadata = loadTagMap(tag.getList("bank_metadata", 10));
        centralBank.bankApplications = loadTagMap(tag.getList("bank_applications", 10));
        centralBank.bankAppeals = loadTagMap(tag.getList("bank_appeals", 10));
        centralBank.openMarketOperations = loadTagMap(tag.getList("open_market_operations", 10));
        centralBank.settlementLedger = loadTagMap(tag.getList("settlement_ledger", 10));
        centralBank.settlementSuspense = loadTagMap(tag.getList("settlement_suspense", 10));
        centralBank.interbankOffers = loadTagMap(tag.getList("interbank_offers", 10));
        centralBank.interbankLoans = loadTagMap(tag.getList("interbank_loans", 10));
        centralBank.reportSnapshots = loadTagMap(tag.getList("report_snapshots", 10));
        centralBank.issuedCreditCards = loadTagMap(tag.getList("issued_credit_cards", 10));
        centralBank.nextBankTellerVariant = tag.contains("next_bank_teller_variant")
                ? tag.getInt("next_bank_teller_variant")
                : TELLER_VARIANT_MALE;
        return centralBank;
    }

    private static ListTag saveTagMap(ConcurrentHashMap<UUID, CompoundTag> map) {
        ListTag list = new ListTag();
        if (map == null) {
            return list;
        }
        map.forEach((id, value) -> {
            if (id == null || value == null) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.put("data", value.copy());
            list.add(entry);
        });
        return list;
    }

    private static ConcurrentHashMap<UUID, CompoundTag> loadTagMap(ListTag list) {
        ConcurrentHashMap<UUID, CompoundTag> map = new ConcurrentHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("id") || !entry.contains("data", 10)) {
                continue;
            }
            map.put(entry.getUUID("id"), entry.getCompound("data"));
        }
        return map;
    }


}
