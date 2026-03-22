package net.austizz.ultimatebankingsystem.bank;

import net.austizz.ultimatebankingsystem.account.AccountHolder;

import java.util.UUID;

public class Bank {
    private UUID bankId;
    private String bankName;
    private AccountHolder[] BankAccounts;
    private double BankReserve;
    private double InterestRate;
}
