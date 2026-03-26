package net.austizz.ultimatebankingsystem.account;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.callback.CallBackManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


public class AccountHolder {
    private final UUID accountUUID;
    private final UUID playerUUID;
    private LocalDateTime DateOfCreation;
    private AccountTypes AccountType;
    private String password;
    private BigDecimal balance;
    private final UUID BankId;
    private boolean isPrimaryAccount;



    public AccountHolder(UUID playerUUID, BigDecimal balance,  AccountTypes accountType, String password, UUID BankId, UUID AccountUUID) {
        this.accountUUID = AccountUUID == null ? UUID.randomUUID() : AccountUUID;
        this.playerUUID = playerUUID;
        this.DateOfCreation = LocalDateTime.now();
        this.AccountType = accountType;
        this.password = password;
        this.balance = balance == null ? new  BigDecimal("0") : balance;
        this.BankId = BankId;
        this.isPrimaryAccount = false;
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
    public void AddBalance(BigDecimal balance) {
        this.balance = this.balance.add(balance);
        BankManager.markDirty();
    }
    // Removes from Players Balance
    public boolean RemoveBalance(BigDecimal balance) {
        this.balance =  this.balance.subtract(balance);
        BankManager.markDirty();
        return true;

    }
    public boolean sendMoney(AccountHolder accountHolder, BigDecimal amount) {
        if (this.balance.compareTo(amount) >= 0) {
            ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(accountUUID).sendSystemMessage(Component.literal("Amount is not valid!"));
            return false;
        }

        if (this.RemoveBalance(amount)){
            accountHolder.AddBalance(amount);
        }
        BankManager.markDirty();
        return true;
    }

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
        // voeg andere velden toe...
        return tag;
    }

    public static AccountHolder load(CompoundTag tag, HolderLookup.Provider registries) {
        UUID uuid = tag.getUUID("playerUUID");
        BigDecimal balance = new BigDecimal(tag.getString("balance"));
        AccountTypes accountType = AccountTypes.valueOf(tag.getString("AccountType"));
        String password = tag.getString("password");
        UUID accountUUID = tag.getUUID("accountUUID");
        UUID BankId = tag.getUUID("BankId");
        UUID playerUUID = tag.getUUID("playerUUID");
        AccountHolder account = new AccountHolder(playerUUID, balance, accountType, password, BankId, accountUUID);
        account.DateOfCreation = LocalDateTime.parse(tag.getString("dateOfCreation"));
        account.isPrimaryAccount = tag.getBoolean("isPrimaryAccount");
        return account;
    }
}
