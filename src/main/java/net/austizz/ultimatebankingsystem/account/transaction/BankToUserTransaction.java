package net.austizz.ultimatebankingsystem.account.transaction;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class BankToUserTransaction extends UserTransaction {

    public BankToUserTransaction(UUID senderBankUUID, UUID receiverAccountUUID, BigDecimal amount, LocalDateTime timestamp, String transactionDescription, UUID transactionUUID) {
        super(senderBankUUID, receiverAccountUUID, amount, timestamp, transactionDescription, transactionUUID);
    }

    public BankToUserTransaction(UUID senderBankUUID, UUID receiverAccountUUID, BigDecimal amount, LocalDateTime timestamp, String transactionDescription) {
        super(senderBankUUID, receiverAccountUUID, amount, timestamp, transactionDescription);
    }

    public UUID getSenderBankUUID() {
        return getSenderUUID();
    }

    public UUID getReceiverAccountUUID() {
        return getReceiverUUID();
    }

    @Override
    public boolean makeTransaction(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        if (getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return false;
        }
        Bank senderBank = centralBank.getBank(getSenderBankUUID());
        AccountHolder receiver = centralBank.SearchForAccountByAccountId(getReceiverAccountUUID());

        if (senderBank == null || receiver == null) {
            return false;
        }

        BigDecimal senderReserve = senderBank.getBankReserve();
        if (senderReserve.compareTo(getAmount()) < 0) {
            return false;
        }

        senderBank.setReserve(senderReserve.subtract(getAmount()));

        if (!receiver.AddBalance(getAmount())) {
            // Roll back reserve mutation if crediting fails.
            senderBank.setReserve(senderReserve);
            return false;
        }

        receiver.addTransaction(this);
        BankManager.markDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putUUID("senderBankUUID", getSenderBankUUID());
        tag.putUUID("receiverAccountUUID", getReceiverAccountUUID());
        tag.putString("amount", getAmount().toString());
        tag.putString("timeStamp", getTimestamp().toString());
        tag.putUUID("transactionUUID", getTransactionUUID());
        tag.putString("transactionDescription", getTransactionDescription());
        return tag;
    }

    public static BankToUserTransaction load(CompoundTag tag, HolderLookup.Provider registries) {
        UUID senderBankUUID = tag.getUUID("senderBankUUID");
        UUID receiverAccountUUID = tag.getUUID("receiverAccountUUID");
        BigDecimal amount = new BigDecimal(tag.getString("amount"));
        UUID transactionUUID = tag.getUUID("transactionUUID");
        String transactionDescription = tag.getString("transactionDescription");
        LocalDateTime timestamp = LocalDateTime.parse(tag.getString("timeStamp"));
        return new BankToUserTransaction(senderBankUUID, receiverAccountUUID, amount, timestamp, transactionDescription, transactionUUID);
    }
}
