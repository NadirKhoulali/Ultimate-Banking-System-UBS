package net.austizz.ultimatebankingsystem.account;

import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.minecraft.world.entity.player.Player;

import java.time.LocalDateTime;
import java.util.UUID;

public class AccountHolder {
    private UUID accountUUID;
    private UUID playerUUID;
    private LocalDateTime DateOfCreation;
    private AccountTypes AccountType;
    private String name;
    private String surname;
    private String password;
    private double balance;
    private UUID BankId;



    public AccountHolder(Player player, AccountTypes accountType, String name, String surname, String password) {
        this.accountUUID = UUID.randomUUID();
        this.playerUUID = player.getUUID();
        this.DateOfCreation = LocalDateTime.now();
        this.AccountType = accountType;
        this.name = name;
        this.surname = surname;
        this.password = password;
        this.balance = 0;
    }
}
