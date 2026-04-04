package net.austizz.ultimatebankingsystem.account;

import io.github.bucket4j.Bucket;
import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.callback.CallBackManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class AccountHolder {
    private final UUID accountUUID;
    private final UUID playerUUID;
    private LocalDateTime DateOfCreation;
    private AccountTypes AccountType;
    private String password;
    private BigDecimal balance;
    private final UUID BankId;
    private boolean isPrimaryAccount;
    private ConcurrentHashMap<UUID, UserTransaction> transactions;


    public AccountHolder(UUID playerUUID, BigDecimal balance,  AccountTypes accountType, String password, UUID BankId, UUID AccountUUID) {
        this.accountUUID = AccountUUID == null ? UUID.randomUUID() : AccountUUID;
        this.playerUUID = playerUUID;
        this.DateOfCreation = LocalDateTime.now();
        this.AccountType = accountType;
        this.password = password;
        this.balance = balance == null ? new  BigDecimal("0") : balance;
        this.BankId = BankId;
        this.isPrimaryAccount = false;
        this.transactions = new ConcurrentHashMap<>();
    }
    // Request all Types of Identification
    public UUID getAccountUUID() {
        return accountUUID;
    }
    public UUID getPlayerUUID() {
        return playerUUID;
    }
    public UUID getBankId() {
        return BankId;
    }
    //Request Date of Account Creation
    public LocalDateTime getDateOfCreation() {
        return DateOfCreation;
    }
    // Get type of account e.g. Checking Account
    public AccountTypes getAccountType() {
        return AccountType;
    }
    //Requests Player Balance
    public BigDecimal getBalance() {
        return balance;
    }
    // Adds to Players Balance
    public boolean AddBalance(BigDecimal balance) {
        if(balance.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        this.balance = this.balance.add(balance);
        BankManager.markDirty();
        return true;
    }
    // Removes from Players Balance
    public boolean RemoveBalance(BigDecimal balance) {

        if(this.balance.compareTo(balance) <= 0) {
            return false;
        }
        if(balance.compareTo(BigDecimal.ZERO) <= 0 ) {
            return false;
        }
        this.balance =  this.balance.subtract(balance);
        BankManager.markDirty();
        return true;

    }
    public void addTransaction(UserTransaction transaction) {
        this.transactions.put(transaction.getTransactionUUID(), transaction);
    }
//    public boolean sendMoney(AccountHolder accountHolder, BigDecimal amount) {
//        if (this.balance.compareTo(amount) <= 0) {
//            ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerUUID).sendSystemMessage(Component.literal("Amount is not valid!"));
//            return false;
//        }
//
//        if (!this.RemoveBalance(amount)){
//            return false;
//        }
//        accountHolder.AddBalance(amount);
//        BankManager.markDirty();
//        return true;
//    }

    public void RequestAccountTermination(ServerPlayer player) {
        // Maak de callback aan: wat moet er gebeuren als ze op 'JA' klikken?
        String yesCallbackId = CallBackManager.createCallback(p -> {
            // HIER KOMT JE LOGICA OM HET ACCOUNT TE VERWIJDEREN
            MinecraftServer server = player.getServer();
            CentralBank centralBank = BankManager.getCentralBank(server);
            Bank bank = centralBank.getBank(this.BankId);

            if (bank == null) {
                p.sendSystemMessage(Component.literal("Bank not found!"));
                return;
            }

            bank.RemoveAccount(this);

            p.sendSystemMessage(Component.literal("Your account has been successfully terminated. Your balance has been transferred to the bank.")
                    .withStyle(ChatFormatting.DARK_RED));

            System.out.println("Account terminated for: " + p.getScoreboardName());
        });

        // Het bericht opbouwen
        player.sendSystemMessage(Component.literal("Are you sure you want to terminate your account?\n")
                .append(Component.literal("By Agreeing to terminate your account, your Balance will \nremain with the bank permanently!\n\n")
                        .withStyle(ChatFormatting.GRAY))

                // De "JA" knop
                .append(Component.literal("[Yes, I Agree] ")
                        .setStyle(Style.EMPTY
                                .withBold(true)
                                .withColor(ChatFormatting.RED)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ubs_action " + yesCallbackId))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to permanently delete"))))
                )

                // The "NO" button (no callback needed, just dismisses or sends a simple message)
                .append(Component.literal(" [No, I Disagree]")
                        .setStyle(Style.EMPTY
                                .withBold(true)
                                .withColor(ChatFormatting.GREEN)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ubs_action " + yesCallbackId +  " cancel")))) // Optioneel
        );
    }
    public boolean isPrimaryAccount() {
        return isPrimaryAccount;
    }
    public void setPrimaryAccount(boolean isPrimaryAccount) {
        this.isPrimaryAccount = isPrimaryAccount;
        BankManager.markDirty();
    }

    /**
     * Returns the transaction map, ensuring it is always non-null.
     * Key = transaction UUID.
     */
    public ConcurrentHashMap<UUID, UserTransaction> getTransactions() {
        if (transactions == null) {
            transactions = new ConcurrentHashMap<>();
        }
        return transactions;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putUUID("playerUUID", this.playerUUID);
        tag.putString("balance", this.balance.toString()); // BigDecimal als String opslaan
        tag.putUUID("BankId", this.BankId);
        tag.putBoolean("isPrimaryAccount", isPrimaryAccount);
        tag.putString("AccountType", this.AccountType.name());
        tag.putUUID("accountUUID", this.accountUUID);
        tag.putString("dateOfCreation", this.DateOfCreation.toString());
        tag.putString("password", this.password);
        tag.putUUID("playerUUID", this.playerUUID);

        // Transactions
        ListTag txList = new ListTag();
        for (Map.Entry<UUID, UserTransaction> entry : getTransactions().entrySet()) {
            UserTransaction tx = entry.getValue();
            if (tx == null) continue;

            CompoundTag txTag = new CompoundTag();
            tx.save(txTag, registries);
            // store the key too, in case it diverges from tx.getTransactionUUID()
            txTag.putUUID("mapKey", entry.getKey());
            txList.add(txTag);
        }
        tag.put("transactions", txList);

        // voeg andere velden toe...
        return tag;
    }

    public static AccountHolder load(CompoundTag tag, HolderLookup.Provider registries) {
        BigDecimal balance = new BigDecimal(tag.getString("balance"));
        AccountTypes accountType = AccountTypes.valueOf(tag.getString("AccountType"));
        String password = tag.getString("password");
        UUID accountUUID = tag.getUUID("accountUUID");
        UUID BankId = tag.getUUID("BankId");
        UUID playerUUID = tag.getUUID("playerUUID");
        AccountHolder account = new AccountHolder(playerUUID, balance, accountType, password, BankId, accountUUID);
        account.DateOfCreation = LocalDateTime.parse(tag.getString("dateOfCreation"));
        account.isPrimaryAccount = tag.getBoolean("isPrimaryAccount");

        // Transactions
        account.transactions = new ConcurrentHashMap<>();
        if (tag.contains("transactions", Tag.TAG_LIST)) {
            ListTag txList = tag.getList("transactions", Tag.TAG_COMPOUND);
            for (int i = 0; i < txList.size(); i++) {
                CompoundTag txTag = txList.getCompound(i);
                UserTransaction tx = UserTransaction.load(txTag, registries);
                if (tx == null) continue;

                UUID key = txTag.hasUUID("mapKey") ? txTag.getUUID("mapKey") : tx.getTransactionUUID();
                account.transactions.put(key, tx);
            }
        }

        return account;
    }

    private static final int OUTGOING_TX_CAPACITY = Config.TRANSACTIONS_PER_MINUTE.get();
    private static final Duration OUTGOING_TX_REFILL_PERIOD = Duration.ofMinutes(1);

    /**
     * Rate limiter for outgoing transactions for this account.
     * Not persisted; recreated on load (per AccountHolder instance).
     */
    private final Bucket outgoingTxBucket = Bucket.builder()
            .addLimit(limit -> limit.capacity(OUTGOING_TX_CAPACITY)
                    .refillIntervally(OUTGOING_TX_CAPACITY, OUTGOING_TX_REFILL_PERIOD))
            .build();

    /**
     * Try to consume 1 outgoing transaction token.
     *
     * @return true if the transaction is allowed right now.
     */
    public boolean tryConsumeOutgoingTransaction() {
        return outgoingTxBucket.tryConsume(1);
    }
}
