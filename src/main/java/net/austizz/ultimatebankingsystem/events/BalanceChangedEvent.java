package net.austizz.ultimatebankingsystem.events;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.math.BigDecimal;

public class BalanceChangedEvent extends Event {
    private final AccountHolder account;
    private final BigDecimal newBalance;

    private final BigDecimal changeAmount;
    private final boolean isPositiveNumber;

    public BalanceChangedEvent(AccountHolder account, BigDecimal newBalance, BigDecimal changeAmount , boolean isPositiveNumber) {
        this.account = account;
        this.newBalance = newBalance;
        this.changeAmount = changeAmount;
        this.isPositiveNumber = isPositiveNumber;
    }

    public AccountHolder getAccount() { return account; }
    public BigDecimal getNewBalance() { return newBalance; }
    public BigDecimal getChangeAmount() { return changeAmount; }
    public boolean isPositiveNumber() { return isPositiveNumber; }
}
