package net.austizz.ultimatebankingsystem.account.transaction;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Transaction {
    private final UUID senderUUID;
    private final UUID receiverUUID;
    private final UUID transactionUUID;
    private final BigDecimal amount;
    private final LocalDateTime timestamp;
    private final String transactionDescription;


    public Transaction(UUID senderUUID, UUID receiverUUID, BigDecimal amount, LocalDateTime timestamp, String TransactionDescription, UUID transactionUUID) {
        this.senderUUID = senderUUID;
        this.receiverUUID = receiverUUID;
        this.amount = amount;
        this.timestamp = timestamp;
        this.transactionUUID = transactionUUID ;
        this.transactionDescription = TransactionDescription;
    }
    public Transaction(UUID senderUUID, UUID receiverUUID, BigDecimal amount, LocalDateTime timestamp, String TransactionDescription) {
        this.senderUUID = senderUUID;
        this.receiverUUID = receiverUUID;
        this.amount = amount;
        this.timestamp = timestamp;
        this.transactionUUID = UUID.randomUUID();
        this.transactionDescription = TransactionDescription;
    }
    public UUID getSenderUUID() {
        return senderUUID;
    }
    public UUID getReceiverUUID() {
        return receiverUUID;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    public String getTransactionDescription() {
        return transactionDescription;
    }
    public UUID getTransactionUUID() {
        return transactionUUID;
    }

    public boolean makeTransaction(MinecraftServer server) {
        CentralBank centralBank = BankManager.getCentralBank(server);
        AccountHolder sender = centralBank.SearchForAccountByAccountId(senderUUID);
        AccountHolder receiver = centralBank.SearchForAccountByAccountId(receiverUUID);

        if (sender.getBalance().compareTo(amount) <= 0) {
           server.getPlayerList().getPlayer(sender.getPlayerUUID()).sendSystemMessage(Component.literal("§cNot enough balance to perform this transaction!"));
            return false;
        }

        if (!sender.RemoveBalance(amount)) {
            server.getPlayerList().getPlayer(sender.getPlayerUUID()).sendSystemMessage(Component.literal("§cSomething went wrong, Please try again!"));
            return false;
        }
        receiver.AddBalance(amount);

        // Record the transaction on both accounts
        Transaction tx = new Transaction(senderUUID, receiverUUID, amount, timestamp, transactionDescription, transactionUUID);
        sender.addTransaction(tx);
        receiver.addTransaction(tx);

        BankManager.markDirty();
        return true;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putUUID("senderUUID", this.senderUUID);
        tag.putUUID("receiverUUID", this.receiverUUID); // BigDecimal als String opslaan
        tag.putString("amount", this.amount.toString());
        tag.putString("timeStamp", this.timestamp.toString());
        tag.putUUID("transactionUUID", this.transactionUUID);
        tag.putString("transactionDescription", this.transactionDescription);
        return tag;
    }

    public static Transaction  load(CompoundTag tag, HolderLookup.Provider registries) {
        UUID senderUUID = tag.getUUID("senderUUID");
        UUID receiverUUID = tag.getUUID("receiverUUID");
        BigDecimal amount = new BigDecimal(tag.getString("amount"));
        UUID transactionUUID = tag.getUUID("transactionUUID");
        String transactionDescription = tag.getString("transactionDescription");
        LocalDateTime timestamp = LocalDateTime.parse(tag.getString("timeStamp"));
        Transaction transaction = new Transaction(senderUUID, receiverUUID, amount, timestamp, transactionDescription, transactionUUID);
        return transaction;
    }
}
