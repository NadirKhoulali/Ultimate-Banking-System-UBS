package net.austizz.ultimatebankingsystem.account.transaction;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class UserTransaction {
    private final UUID senderUUID;
    private final UUID receiverUUID;
    private final UUID transactionUUID;
    private final BigDecimal amount;
    private final LocalDateTime timestamp;
    private final String transactionDescription;



    public UserTransaction(UUID senderUUID, UUID receiverUUID, BigDecimal amount, LocalDateTime timestamp, String TransactionDescription, UUID transactionUUID) {
        this.senderUUID = senderUUID;
        this.receiverUUID = receiverUUID;
        this.amount = amount;
        this.timestamp = timestamp;
        this.transactionUUID = transactionUUID ;
        this.transactionDescription = TransactionDescription;
    }
    public UserTransaction(UUID senderUUID, UUID receiverUUID, BigDecimal amount, LocalDateTime timestamp, String TransactionDescription) {
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
        if (centralBank == null) {
            return false;
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        AccountHolder sender = centralBank.SearchForAccountByAccountId(senderUUID);
        AccountHolder receiver = centralBank.SearchForAccountByAccountId(receiverUUID);

        if (sender == null || receiver == null) {
            return false;
        }

        ServerPlayer senderPlayer = server.getPlayerList().getPlayer(sender.getPlayerUUID());

        if (sender.isFrozen()) {
            if (senderPlayer != null) {
                String reason = sender.getFrozenReason();
                senderPlayer.sendSystemMessage(Component.literal(
                        "§cThis account is frozen." + (reason.isEmpty() ? "" : " Reason: " + reason)
                ));
            }
            return false;
        }

        if (receiver.isFrozen()) {
            if (senderPlayer != null) {
                senderPlayer.sendSystemMessage(Component.literal("§cTransfer failed: the destination account is frozen."));
            }
            return false;
        }

        // Per-account rate limit (outgoing)
        if (!sender.tryConsumeOutgoingTransaction()) {
            if (senderPlayer != null) {
                senderPlayer.sendSystemMessage(Component.literal("§cYou're sending transactions too fast. Please wait a moment."));
            }
            return false;
        }

        Player player = senderPlayer;

        if (player != null && sender.getBalance().compareTo(amount) < 0) {
            player.sendSystemMessage(Component.literal("§cNot enough balance to perform this transaction!"));
            return false;
        }

        if (!sender.RemoveBalance(amount)) {
            if (player != null) {
                player.sendSystemMessage(Component.literal("§cSomething went wrong, Please try again!"));
            }
            return false;
        }

        receiver.AddBalance(amount);

        // Record the transaction on both accounts
        sender.addTransaction(this);
        receiver.addTransaction(this);

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

    public static UserTransaction load(CompoundTag tag, HolderLookup.Provider registries) {
        UUID senderUUID = tag.getUUID("senderUUID");
        UUID receiverUUID = tag.getUUID("receiverUUID");
        BigDecimal amount = new BigDecimal(tag.getString("amount"));
        UUID transactionUUID = tag.getUUID("transactionUUID");
        String transactionDescription = tag.getString("transactionDescription");
        LocalDateTime timestamp = LocalDateTime.parse(tag.getString("timeStamp"));
        UserTransaction transaction = new UserTransaction(senderUUID, receiverUUID, amount, timestamp, transactionDescription, transactionUUID);
        return transaction;
    }
}
