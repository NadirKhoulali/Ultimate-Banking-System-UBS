package net.austizz.ultimatebankingsystem.account;

import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class AccountHolder {
    private final UUID accountUUID;
    private final UUID playerUUID;
    private final LocalDateTime DateOfCreation;
    private AccountTypes AccountType;
    private String password;
    private BigDecimal balance;
    private final UUID BankId;



    public AccountHolder(Player player, AccountTypes accountType, String password, UUID BankId) {
        this.accountUUID = UUID.randomUUID();
        this.playerUUID = player.getUUID();
        this.DateOfCreation = LocalDateTime.now();
        this.AccountType = accountType;
        this.password = password;
        this.balance = new  BigDecimal("0");
        this.BankId = BankId;
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
    }
    // Removes from Players Balance
    public boolean RemoveBalance(BigDecimal balance) {
        this.balance =  this.balance.subtract(balance);
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
        return true;
    }
}
