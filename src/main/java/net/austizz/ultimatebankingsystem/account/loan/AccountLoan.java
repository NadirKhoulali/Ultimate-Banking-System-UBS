package net.austizz.ultimatebankingsystem.account.loan;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.math.BigDecimal;
import java.util.UUID;

public final class AccountLoan {
    private final UUID loanId;
    private final UUID lenderBankId;
    private final BigDecimal principal;
    private BigDecimal remainingBalance;
    private final double annualInterestRate;
    private final BigDecimal periodicPayment;
    private final long createdAtGameTime;
    private long nextDueGameTime;
    private final long paymentIntervalTicks;
    private final int totalPayments;
    private int paymentsMade;
    private boolean warnedThisCycle;
    private boolean defaulted;

    public AccountLoan(UUID loanId,
                       UUID lenderBankId,
                       BigDecimal principal,
                       BigDecimal remainingBalance,
                       double annualInterestRate,
                       BigDecimal periodicPayment,
                       long createdAtGameTime,
                       long nextDueGameTime,
                       long paymentIntervalTicks,
                       int totalPayments,
                       int paymentsMade,
                       boolean warnedThisCycle,
                       boolean defaulted) {
        this.loanId = loanId == null ? UUID.randomUUID() : loanId;
        this.lenderBankId = lenderBankId;
        this.principal = principal;
        this.remainingBalance = remainingBalance;
        this.annualInterestRate = annualInterestRate;
        this.periodicPayment = periodicPayment;
        this.createdAtGameTime = createdAtGameTime;
        this.nextDueGameTime = nextDueGameTime;
        this.paymentIntervalTicks = paymentIntervalTicks;
        this.totalPayments = totalPayments;
        this.paymentsMade = paymentsMade;
        this.warnedThisCycle = warnedThisCycle;
        this.defaulted = defaulted;
    }

    public UUID getLoanId() {
        return loanId;
    }

    public UUID getLenderBankId() {
        return lenderBankId;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getRemainingBalance() {
        return remainingBalance;
    }

    public void setRemainingBalance(BigDecimal remainingBalance) {
        this.remainingBalance = remainingBalance;
    }

    public double getAnnualInterestRate() {
        return annualInterestRate;
    }

    public BigDecimal getPeriodicPayment() {
        return periodicPayment;
    }

    public long getCreatedAtGameTime() {
        return createdAtGameTime;
    }

    public long getNextDueGameTime() {
        return nextDueGameTime;
    }

    public void setNextDueGameTime(long nextDueGameTime) {
        this.nextDueGameTime = nextDueGameTime;
    }

    public long getPaymentIntervalTicks() {
        return paymentIntervalTicks;
    }

    public int getTotalPayments() {
        return totalPayments;
    }

    public int getPaymentsMade() {
        return paymentsMade;
    }

    public void setPaymentsMade(int paymentsMade) {
        this.paymentsMade = paymentsMade;
    }

    public boolean isWarnedThisCycle() {
        return warnedThisCycle;
    }

    public void setWarnedThisCycle(boolean warnedThisCycle) {
        this.warnedThisCycle = warnedThisCycle;
    }

    public boolean isDefaulted() {
        return defaulted;
    }

    public void setDefaulted(boolean defaulted) {
        this.defaulted = defaulted;
    }

    public boolean isFullyPaid() {
        return remainingBalance.compareTo(BigDecimal.ZERO) <= 0;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putUUID("loanId", this.loanId);
        if (this.lenderBankId != null) {
            tag.putUUID("lenderBankId", this.lenderBankId);
        }
        tag.putString("principal", this.principal.toPlainString());
        tag.putString("remainingBalance", this.remainingBalance.toPlainString());
        tag.putDouble("annualInterestRate", this.annualInterestRate);
        tag.putString("periodicPayment", this.periodicPayment.toPlainString());
        tag.putLong("createdAtGameTime", this.createdAtGameTime);
        tag.putLong("nextDueGameTime", this.nextDueGameTime);
        tag.putLong("paymentIntervalTicks", this.paymentIntervalTicks);
        tag.putInt("totalPayments", this.totalPayments);
        tag.putInt("paymentsMade", this.paymentsMade);
        tag.putBoolean("warnedThisCycle", this.warnedThisCycle);
        tag.putBoolean("defaulted", this.defaulted);
        return tag;
    }

    public static AccountLoan load(CompoundTag tag, HolderLookup.Provider registries) {
        UUID loanId = tag.getUUID("loanId");
        UUID lenderBankId = tag.hasUUID("lenderBankId") ? tag.getUUID("lenderBankId") : null;
        BigDecimal principal = new BigDecimal(tag.getString("principal"));
        BigDecimal remainingBalance = new BigDecimal(tag.getString("remainingBalance"));
        double annualInterestRate = tag.getDouble("annualInterestRate");
        BigDecimal periodicPayment = new BigDecimal(tag.getString("periodicPayment"));
        long createdAtGameTime = tag.getLong("createdAtGameTime");
        long nextDueGameTime = tag.getLong("nextDueGameTime");
        long paymentIntervalTicks = tag.getLong("paymentIntervalTicks");
        int totalPayments = tag.getInt("totalPayments");
        int paymentsMade = tag.getInt("paymentsMade");
        boolean warnedThisCycle = tag.getBoolean("warnedThisCycle");
        boolean defaulted = tag.getBoolean("defaulted");
        return new AccountLoan(
                loanId,
                lenderBankId,
                principal,
                remainingBalance,
                annualInterestRate,
                periodicPayment,
                createdAtGameTime,
                nextDueGameTime,
                paymentIntervalTicks,
                totalPayments,
                paymentsMade,
                warnedThisCycle,
                defaulted
        );
    }
}
