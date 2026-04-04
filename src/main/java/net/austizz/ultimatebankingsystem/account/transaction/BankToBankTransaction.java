package net.austizz.ultimatebankingsystem.account.transaction;

import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class BankToBankTransaction extends UserTransaction {

    public BankToBankTransaction(UUID senderBankUUID, UUID receiverBankUUID, BigDecimal amount, LocalDateTime timestamp, String transactionDescription, UUID transactionUUID) {
        super(senderBankUUID, receiverBankUUID, amount, timestamp, transactionDescription, transactionUUID);
    }

    public BankToBankTransaction(UUID senderBankUUID, UUID receiverBankUUID, BigDecimal amount, LocalDateTime timestamp, String transactionDescription) {
        super(senderBankUUID, receiverBankUUID, amount, timestamp, transactionDescription);
    }

    public UUID getSenderBankUUID() {
        return getSenderUUID();
    }

    public UUID getReceiverBankUUID() {
        return getReceiverUUID();
    }

    @Override
    public boolean makeTransaction(MinecraftServer server) {
        if (getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        CentralBank centralBank = BankManager.getCentralBank(server);
        Bank senderBank = centralBank.getBank(getSenderBankUUID());
        Bank receiverBank = centralBank.getBank(getReceiverBankUUID());

        if (senderBank == null || receiverBank == null) {
            return false;
        }

        BigDecimal senderReserve = senderBank.getBankReserve();
        if (senderReserve.compareTo(getAmount()) < 0) {
            return false;
        }

        senderBank.setReserve(senderReserve.subtract(getAmount()));
        receiverBank.setReserve(receiverBank.getBankReserve().add(getAmount()));

        BankManager.markDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putUUID("senderBankUUID", getSenderBankUUID());
        tag.putUUID("receiverBankUUID", getReceiverBankUUID());
        tag.putString("amount", getAmount().toString());
        tag.putString("timeStamp", getTimestamp().toString());
        tag.putUUID("transactionUUID", getTransactionUUID());
        tag.putString("transactionDescription", getTransactionDescription());
        return tag;
    }

    public static BankToBankTransaction load(CompoundTag tag, HolderLookup.Provider registries) {
        UUID senderBankUUID = tag.getUUID("senderBankUUID");
        UUID receiverBankUUID = tag.getUUID("receiverBankUUID");
        BigDecimal amount = new BigDecimal(tag.getString("amount"));
        UUID transactionUUID = tag.getUUID("transactionUUID");
        String transactionDescription = tag.getString("transactionDescription");
        LocalDateTime timestamp = LocalDateTime.parse(tag.getString("timeStamp"));
        return new BankToBankTransaction(senderBankUUID, receiverBankUUID, amount, timestamp, transactionDescription, transactionUUID);
    }
}
