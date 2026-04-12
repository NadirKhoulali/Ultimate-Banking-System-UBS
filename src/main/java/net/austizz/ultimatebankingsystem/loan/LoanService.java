package net.austizz.ultimatebankingsystem.loan;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.loan.AccountLoan;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LoanService {
    private LoanService() {}

    public record LoanQuote(UUID accountId,
                            UUID borrowerPlayerId,
                            UUID lenderBankId,
                            BigDecimal principal,
                            double annualInterestRate,
                            BigDecimal totalRepayable,
                            BigDecimal periodicPayment,
                            int totalPayments,
                            long paymentIntervalTicks,
                            long firstDueGameTime,
                            boolean requiresAdminApproval,
                            String approvalReason) {
    }

    private static final ConcurrentHashMap<UUID, LoanQuote> PENDING_ADMIN_APPROVALS = new ConcurrentHashMap<>();

    public static LoanQuote createQuote(AccountHolder account, BigDecimal principal, long currentGameTime) {
        int payments = Math.max(1, Config.LOAN_TERM_PAYMENTS.get());
        long interval = Math.max(20, Config.LOAN_PAYMENT_INTERVAL_TICKS.get());
        double rate = computeRateFromCredit(account.getCreditScore());

        BigDecimal interestMultiplier = BigDecimal.valueOf(rate)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN);
        BigDecimal totalRepayable = principal
                .multiply(BigDecimal.ONE.add(interestMultiplier))
                .setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal periodicPayment = totalRepayable
                .divide(BigDecimal.valueOf(payments), 2, RoundingMode.HALF_EVEN);

        boolean requiresAdmin = principal.compareTo(BigDecimal.valueOf(Config.LOAN_AUTO_APPROVE_THRESHOLD.get())) > 0
                || account.getCreditScore() < Config.LOAN_AUTO_APPROVE_MIN_CREDIT.get();
        String reason = requiresAdmin
                ? "Above auto-approval threshold or credit score below required minimum."
                : "";

        return new LoanQuote(
                account.getAccountUUID(),
                account.getPlayerUUID(),
                account.getBankId(),
                principal,
                rate,
                totalRepayable,
                periodicPayment,
                payments,
                interval,
                currentGameTime + interval,
                requiresAdmin,
                reason
        );
    }

    public static double computeRateFromCredit(int creditScore) {
        double base = Config.LOAN_BASE_INTEREST_RATE.get();
        double adjusted = base - ((creditScore - 500) / 100.0);
        double min = Config.LOAN_MIN_INTEREST_RATE.get();
        double max = Config.LOAN_MAX_INTEREST_RATE.get();
        if (adjusted < min) {
            return min;
        }
        if (adjusted > max) {
            return max;
        }
        return adjusted;
    }

    public static AccountLoan issueLoan(MinecraftServer server, LoanQuote quote) {
        if (server == null || quote == null) {
            return null;
        }

        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return null;
        }

        AccountHolder account = centralBank.SearchForAccountByAccountId(quote.accountId());
        if (account == null) {
            return null;
        }

        boolean added = account.AddBalance(quote.principal());
        if (!added) {
            return null;
        }

        AccountLoan loan = new AccountLoan(
                UUID.randomUUID(),
                quote.lenderBankId(),
                quote.principal(),
                quote.totalRepayable(),
                quote.annualInterestRate(),
                quote.periodicPayment(),
                quote.firstDueGameTime() - quote.paymentIntervalTicks(),
                quote.firstDueGameTime(),
                quote.paymentIntervalTicks(),
                quote.totalPayments(),
                0,
                false,
                false
        );
        account.addLoan(loan);
        account.setDefaulted(false);
        if (account.isFrozen() && "Loan default".equalsIgnoreCase(account.getFrozenReason())) {
            account.unfreeze();
        }
        account.addTransaction(new UserTransaction(
                quote.lenderBankId(),
                quote.accountId(),
                quote.principal(),
                LocalDateTime.now(),
                "LOAN_DISBURSEMENT"
        ));
        BankManager.markDirty();
        return loan;
    }

    public static void queueAdminApproval(LoanQuote quote) {
        if (quote == null) {
            return;
        }
        PENDING_ADMIN_APPROVALS.put(quote.borrowerPlayerId(), quote);
    }

    public static LoanQuote getPendingApproval(UUID borrowerPlayerId) {
        return borrowerPlayerId == null ? null : PENDING_ADMIN_APPROVALS.get(borrowerPlayerId);
    }

    public static List<LoanQuote> listPendingApprovals() {
        return PENDING_ADMIN_APPROVALS.values().stream()
                .sorted(Comparator.comparing(LoanQuote::borrowerPlayerId))
                .toList();
    }

    public static AccountLoan approvePending(MinecraftServer server, UUID borrowerPlayerId) {
        LoanQuote quote = borrowerPlayerId == null ? null : PENDING_ADMIN_APPROVALS.remove(borrowerPlayerId);
        if (quote == null) {
            return null;
        }
        return issueLoan(server, quote);
    }

    public static boolean denyPending(UUID borrowerPlayerId) {
        if (borrowerPlayerId == null) {
            return false;
        }
        return PENDING_ADMIN_APPROVALS.remove(borrowerPlayerId) != null;
    }

    public static void processRepayments(MinecraftServer server, long currentGameTime) {
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }

        long warningTicks = Math.max(20, Config.LOAN_WARNING_BEFORE_DUE_TICKS.get());
        for (Bank bank : centralBank.getBanks().values()) {
            for (AccountHolder account : bank.getBankAccounts().values()) {
                if (account == null) {
                    continue;
                }
                List<UUID> toRemove = new ArrayList<>();
                for (AccountLoan loan : account.getActiveLoans().values()) {
                    if (loan == null || loan.isDefaulted()) {
                        continue;
                    }

                    long nextDue = loan.getNextDueGameTime();
                    if (!loan.isWarnedThisCycle()
                            && currentGameTime >= Math.max(0L, nextDue - warningTicks)
                            && currentGameTime < nextDue) {
                        ServerPlayer borrower = server.getPlayerList().getPlayer(account.getPlayerUUID());
                        if (borrower != null) {
                            borrower.sendSystemMessage(Component.literal(
                                    "§eLoan payment due soon: §6$" + loan.getPeriodicPayment().toPlainString()
                                            + " §ein " + (nextDue - currentGameTime) + " ticks."
                            ));
                        }
                        loan.setWarnedThisCycle(true);
                        BankManager.markDirty();
                    }

                    if (currentGameTime < nextDue) {
                        continue;
                    }

                    BigDecimal due = loan.getPeriodicPayment();
                    if (due.compareTo(loan.getRemainingBalance()) > 0) {
                        due = loan.getRemainingBalance();
                    }

                    if (account.RemoveBalance(due)) {
                        loan.setRemainingBalance(loan.getRemainingBalance().subtract(due));
                        loan.setPaymentsMade(loan.getPaymentsMade() + 1);
                        loan.setNextDueGameTime(nextDue + loan.getPaymentIntervalTicks());
                        loan.setWarnedThisCycle(false);
                        account.adjustCreditScore(Config.CREDIT_SCORE_ON_TIME_BOOST.get());
                        account.addTransaction(new UserTransaction(
                                account.getAccountUUID(),
                                loan.getLenderBankId() == null ? account.getBankId() : loan.getLenderBankId(),
                                due,
                                LocalDateTime.now(),
                                "LOAN_REPAYMENT"
                        ));
                        if (loan.isFullyPaid()) {
                            toRemove.add(loan.getLoanId());
                            ServerPlayer borrower = server.getPlayerList().getPlayer(account.getPlayerUUID());
                            if (borrower != null) {
                                borrower.sendSystemMessage(Component.literal("§aLoan fully repaid. Great work."));
                            }
                        }
                        BankManager.markDirty();
                        continue;
                    }

                    account.adjustCreditScore(-Config.CREDIT_SCORE_MISSED_PENALTY.get());
                    account.adjustCreditScore(-Config.CREDIT_SCORE_DEFAULT_PENALTY.get());
                    account.setDefaulted(true);
                    account.freeze("Loan default");
                    loan.setDefaulted(true);
                    BankManager.markDirty();

                    ServerPlayer borrower = server.getPlayerList().getPlayer(account.getPlayerUUID());
                    if (borrower != null) {
                        borrower.sendSystemMessage(Component.literal(
                                "§cLoan defaulted: you missed a payment of $" + due.toPlainString() + "."
                        ));
                    }

                    for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                        if (online.hasPermissions(3)) {
                            online.sendSystemMessage(Component.literal(
                                    "§c[UBS] Loan default: " + account.getPlayerUUID() + " on account " + account.getAccountUUID()
                            ));
                        }
                    }
                }

                for (UUID loanId : toRemove) {
                    account.removeLoan(loanId);
                }
            }
        }
    }
}
