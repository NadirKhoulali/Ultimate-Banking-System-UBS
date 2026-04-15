package net.austizz.ultimatebankingsystem.account.transaction;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
        if (server == null) {
            return false;
        }
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

        if (amount.compareTo(BigDecimal.valueOf(Math.max(1, Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()))) > 0) {
            flagSuspicious(centralBank, sender, receiver, amount, "FLAG_MAX_SINGLE_TRANSACTION");
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

        Bank senderBank = centralBank.getBank(sender.getBankId());
        Bank receiverBank = centralBank.getBank(receiver.getBankId());
        if (senderBank == null || receiverBank == null) {
            return false;
        }
        CompoundTag senderMetadata = centralBank.getOrCreateBankMetadata(senderBank.getBankId());
        BigDecimal bankSingleLimit = senderMetadata.contains("limitSingle")
                ? readDecimal(senderMetadata, "limitSingle")
                : BigDecimal.valueOf(Config.GLOBAL_MAX_SINGLE_TRANSACTION.get());
        if (amount.compareTo(bankSingleLimit) > 0) {
            if (senderPlayer != null) {
                senderPlayer.sendSystemMessage(Component.literal("§cTransfer exceeds this bank's single transaction limit."));
            }
            flagSuspicious(centralBank, sender, receiver, amount, "FLAG_BANK_SINGLE_LIMIT");
            return false;
        }
        String senderStatus = getBankStatus(centralBank, senderBank);
        String receiverStatus = getBankStatus(centralBank, receiverBank);
        if (blocksTransactions(senderStatus) || blocksTransactions(receiverStatus)) {
            if (senderPlayer != null) {
                String senderBankLabel = safeBankName(senderBank);
                String receiverBankLabel = safeBankName(receiverBank);
                senderPlayer.sendSystemMessage(Component.literal(
                        "§cTransfer blocked due to bank status. "
                                + senderBankLabel + ": " + senderStatus + " | "
                                + receiverBankLabel + ": " + receiverStatus + "."
                ));
            }
            flagSuspicious(centralBank, sender, receiver, amount, "FLAG_BANK_STATUS_BLOCK");
            return false;
        }

        BigDecimal senderDailyVolume = computeSenderDailyOutgoing(sender);
        BigDecimal bankDailyPlayerLimit = senderMetadata.contains("limitDailyPlayer")
                ? readDecimal(senderMetadata, "limitDailyPlayer")
                : BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_PLAYER_VOLUME.get());
        if (senderDailyVolume.add(amount).compareTo(bankDailyPlayerLimit) > 0) {
            if (senderPlayer != null) {
                senderPlayer.sendSystemMessage(Component.literal("§cDaily player outgoing transaction volume limit reached."));
            }
            flagSuspicious(centralBank, sender, receiver, amount, "FLAG_DAILY_PLAYER_VOLUME");
            return false;
        }

        BigDecimal bankDailyVolume = computeBankDailyOutgoing(senderBank);
        BigDecimal bankDailyLimit = senderMetadata.contains("limitDailyBank")
                ? readDecimal(senderMetadata, "limitDailyBank")
                : BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_BANK_VOLUME.get());
        if (bankDailyVolume.add(amount).compareTo(bankDailyLimit) > 0) {
            if (senderPlayer != null) {
                senderPlayer.sendSystemMessage(Component.literal("§cDaily bank outgoing volume limit reached."));
            }
            flagSuspicious(centralBank, sender, receiver, amount, "FLAG_DAILY_BANK_VOLUME");
            return false;
        }

        boolean crossBank = !sender.getBankId().equals(receiver.getBankId());
        if (crossBank) {
            BigDecimal reserveAfter = senderBank.getDeclaredReserve().subtract(amount);
            if (reserveAfter.compareTo(senderBank.getMinimumRequiredReserve()) < 0) {
                if (senderPlayer != null) {
                    senderPlayer.sendSystemMessage(Component.literal("§cTransfer blocked: sender bank reserve requirement would be breached."));
                }
                flagSuspicious(centralBank, sender, receiver, amount, "FLAG_RESERVE_REQUIREMENT_BREACH");
                return false;
            }
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

        if (crossBank) {
            senderBank.setReserve(senderBank.getDeclaredReserve().subtract(amount));
            receiverBank.setReserve(receiverBank.getDeclaredReserve().add(amount));
            recordSettlement(centralBank, senderBank, receiverBank, amount, "CROSS_BANK_TX");
        }

        BankManager.markDirty();
        return true;
    }

    private static String getBankStatus(CentralBank centralBank, Bank bank) {
        if (centralBank == null || bank == null) {
            return "UNKNOWN";
        }
        if (centralBank.getBankId() != null && centralBank.getBankId().equals(bank.getBankId())) {
            // Central Bank should always remain operational for core payment/account flows.
            return "ACTIVE";
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        String status = metadata.getString("status");
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        return status.trim().toUpperCase();
    }

    private static boolean blocksTransactions(String status) {
        return "SUSPENDED".equals(status) || "REVOKED".equals(status) || "LOCKDOWN".equals(status);
    }

    private static String safeBankName(Bank bank) {
        if (bank == null || bank.getBankName() == null || bank.getBankName().isBlank()) {
            return "Unknown Bank";
        }
        return bank.getBankName().trim();
    }

    private static BigDecimal computeSenderDailyOutgoing(AccountHolder sender) {
        if (sender == null) {
            return BigDecimal.ZERO;
        }
        LocalDate today = LocalDate.now();
        BigDecimal total = BigDecimal.ZERO;
        for (UserTransaction tx : sender.getTransactions().values()) {
            if (tx == null) {
                continue;
            }
            if (!sender.getAccountUUID().equals(tx.getSenderUUID())) {
                continue;
            }
            if (tx.getTimestamp().toLocalDate().isEqual(today)) {
                total = total.add(tx.getAmount());
            }
        }
        return total.setScale(2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal computeBankDailyOutgoing(Bank bank) {
        if (bank == null) {
            return BigDecimal.ZERO;
        }
        LocalDate today = LocalDate.now();
        BigDecimal total = BigDecimal.ZERO;
        for (AccountHolder account : bank.getBankAccounts().values()) {
            if (account == null) {
                continue;
            }
            for (UserTransaction tx : account.getTransactions().values()) {
                if (tx == null) {
                    continue;
                }
                if (!account.getAccountUUID().equals(tx.getSenderUUID())) {
                    continue;
                }
                if (tx.getTimestamp().toLocalDate().isEqual(today)) {
                    total = total.add(tx.getAmount());
                }
            }
        }
        return total.setScale(2, RoundingMode.HALF_EVEN);
    }

    private static void flagSuspicious(CentralBank centralBank,
                                       AccountHolder sender,
                                       AccountHolder receiver,
                                       BigDecimal amount,
                                       String reason) {
        if (centralBank == null) {
            return;
        }
        CompoundTag entry = new CompoundTag();
        UUID id = UUID.randomUUID();
        entry.putUUID("id", id);
        entry.putLong("timestampMillis", System.currentTimeMillis());
        entry.putUUID("fromBankId", sender.getBankId());
        entry.putUUID("toBankId", receiver.getBankId());
        entry.putString("amount", amount.toPlainString());
        entry.putString("reason", reason);
        entry.putBoolean("success", false);
        centralBank.getSettlementSuspense().put(id, entry);
        BankManager.markDirty();
    }

    private static void recordSettlement(CentralBank centralBank,
                                         Bank fromBank,
                                         Bank toBank,
                                         BigDecimal amount,
                                         String reason) {
        if (centralBank == null || fromBank == null || toBank == null) {
            return;
        }
        CompoundTag entry = new CompoundTag();
        UUID id = UUID.randomUUID();
        entry.putUUID("id", id);
        entry.putLong("timestampMillis", System.currentTimeMillis());
        entry.putUUID("fromBankId", fromBank.getBankId());
        entry.putUUID("toBankId", toBank.getBankId());
        entry.putString("amount", amount.toPlainString());
        entry.putString("reason", reason);
        entry.putBoolean("success", true);
        centralBank.getSettlementLedger().put(id, entry);
        BankManager.markDirty();
    }

    private static BigDecimal readDecimal(CompoundTag tag, String key) {
        if (tag == null || key == null || key.isBlank() || !tag.contains(key)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(tag.getString(key));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
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
