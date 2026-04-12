package net.austizz.ultimatebankingsystem.bank;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BankRegulationService {
    private BankRegulationService() {}

    private static long lastAuditTick = -1L;
    private static long lastSnapshotTick = -1L;

    private record EmployeeSalary(String role, BigDecimal salary) {}

    public static void process(MinecraftServer server, long gameTime) {
        if (server == null) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }

        expireInterbankOffers(centralBank, gameTime);
        processCentralDiscountLoans(server, centralBank, gameTime);
        processInterbankLoanMaturities(server, centralBank, gameTime);
        processAnnualLicenseRenewals(server, centralBank, gameTime);
        processEmployeeSalaries(server, centralBank, gameTime);
        processBankTaxes(server, centralBank, gameTime);
        processReserveAudits(server, centralBank, gameTime);
        enforceSavingsRateBands(server, centralBank, gameTime);
        processReportSnapshots(centralBank, gameTime);
    }

    private static void expireInterbankOffers(CentralBank centralBank, long gameTime) {
        for (Map.Entry<UUID, CompoundTag> entry : centralBank.getInterbankOffers().entrySet()) {
            CompoundTag offer = entry.getValue();
            if (!"OPEN".equalsIgnoreCase(offer.getString("status"))) {
                continue;
            }
            if (offer.contains("expiryTick") && offer.getLong("expiryTick") < gameTime) {
                offer.putString("status", "EXPIRED");
                centralBank.getInterbankOffers().put(entry.getKey(), offer);
                BankManager.markDirty();
            }
        }
    }

    private static void processCentralDiscountLoans(MinecraftServer server, CentralBank centralBank, long gameTime) {
        for (Map.Entry<UUID, CompoundTag> entry : centralBank.getInterbankLoans().entrySet()) {
            UUID loanId = entry.getKey();
            CompoundTag loan = entry.getValue();
            if (!"ACTIVE".equalsIgnoreCase(loan.getString("status"))) {
                continue;
            }
            if (!"CB_DISCOUNT".equalsIgnoreCase(loan.getString("type"))) {
                continue;
            }
            if (!loan.contains("nextDueTick") || gameTime < loan.getLong("nextDueTick")) {
                continue;
            }

            Bank borrower = centralBank.getBank(readUuid(loan, "bankId"));
            if (borrower == null) {
                loan.putString("status", "DEFAULTED");
                loan.putString("defaultReason", "Borrower bank missing");
                centralBank.getInterbankLoans().put(loanId, loan);
                BankManager.markDirty();
                continue;
            }

            BigDecimal remaining = readDecimal(loan, "remaining");
            BigDecimal periodic = readDecimal(loan, "periodicPayment");
            if (remaining.compareTo(BigDecimal.ZERO) <= 0 || periodic.compareTo(BigDecimal.ZERO) <= 0) {
                loan.putString("status", "PAID");
                centralBank.getInterbankLoans().put(loanId, loan);
                BankManager.markDirty();
                continue;
            }

            BigDecimal due = periodic.compareTo(remaining) > 0 ? remaining : periodic;
            BigDecimal reserve = borrower.getDeclaredReserve();
            if (reserve.compareTo(due) >= 0) {
                borrower.setReserve(reserve.subtract(due));
                centralBank.setReserve(centralBank.getDeclaredReserve().add(due));
                remaining = remaining.subtract(due).setScale(2, RoundingMode.HALF_EVEN);
                int paymentsRemaining = Math.max(0, loan.getInt("paymentsRemaining") - 1);
                loan.putInt("paymentsRemaining", paymentsRemaining);
                loan.putString("remaining", remaining.toPlainString());
                loan.putLong("nextDueTick", gameTime + Math.max(20L, loan.getLong("paymentIntervalTicks")));
                recordSettlement(centralBank, borrower.getBankId(), centralBank.getBankId(), due, "CB_LOAN_REPAYMENT:" + loanId, true);
                if (remaining.compareTo(BigDecimal.ZERO) <= 0 || paymentsRemaining <= 0) {
                    loan.putString("status", "PAID");
                    notifyOwner(server, borrower, "§aCentral Bank loan " + shortId(loanId) + " has been fully repaid.");
                }
            } else {
                if (reserve.compareTo(BigDecimal.ZERO) > 0) {
                    borrower.setReserve(BigDecimal.ZERO);
                    centralBank.setReserve(centralBank.getDeclaredReserve().add(reserve));
                    remaining = remaining.subtract(reserve).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN);
                    loan.putString("remaining", remaining.toPlainString());
                    recordSettlement(centralBank, borrower.getBankId(), centralBank.getBankId(), reserve, "CB_LOAN_PARTIAL_REPAYMENT:" + loanId, false);
                }
                loan.putString("status", "DEFAULTED");
                loan.putString("defaultReason", "Insufficient reserve for due payment");
                CompoundTag metadata = centralBank.getOrCreateBankMetadata(borrower.getBankId());
                metadata.putString("status", "SUSPENDED");
                metadata.putString("suspendReason", "Defaulted Central Bank loan");
                centralBank.putBankMetadata(borrower.getBankId(), metadata);
                notifyOwner(server, borrower, "§cYour bank defaulted on Central Bank loan " + shortId(loanId) + ".");
            }

            centralBank.getInterbankLoans().put(loanId, loan);
            BankManager.markDirty();
        }
    }

    private static void processInterbankLoanMaturities(MinecraftServer server, CentralBank centralBank, long gameTime) {
        for (Map.Entry<UUID, CompoundTag> entry : centralBank.getInterbankLoans().entrySet()) {
            UUID loanId = entry.getKey();
            CompoundTag loan = entry.getValue();
            if (!"ACTIVE".equalsIgnoreCase(loan.getString("status"))) {
                continue;
            }
            if (!"INTERBANK".equalsIgnoreCase(loan.getString("type"))) {
                continue;
            }
            if (!loan.contains("maturityTick") || gameTime < loan.getLong("maturityTick")) {
                continue;
            }

            Bank lender = centralBank.getBank(readUuid(loan, "lenderBankId"));
            Bank borrower = centralBank.getBank(readUuid(loan, "borrowerBankId"));
            if (lender == null || borrower == null) {
                loan.putString("status", "DEFAULTED");
                loan.putString("defaultReason", "Missing lender or borrower bank");
                centralBank.getInterbankLoans().put(loanId, loan);
                BankManager.markDirty();
                continue;
            }

            BigDecimal remaining = readDecimal(loan, "remaining");
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                loan.putString("status", "REPAID");
                centralBank.getInterbankLoans().put(loanId, loan);
                BankManager.markDirty();
                continue;
            }

            BigDecimal borrowerReserve = borrower.getDeclaredReserve();
            BigDecimal repay = borrowerReserve.min(remaining);
            if (repay.compareTo(BigDecimal.ZERO) > 0) {
                borrower.setReserve(borrowerReserve.subtract(repay));
                lender.setReserve(lender.getDeclaredReserve().add(repay));
                remaining = remaining.subtract(repay).setScale(2, RoundingMode.HALF_EVEN);
                loan.putString("remaining", remaining.toPlainString());
                recordSettlement(centralBank, borrower.getBankId(), lender.getBankId(), repay, "INTERBANK_LOAN_REPAYMENT:" + loanId, remaining.compareTo(BigDecimal.ZERO) <= 0);
            }

            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                loan.putString("status", "REPAID");
                notifyOwner(server, lender, "§aInter-bank loan " + shortId(loanId) + " has been repaid.");
                notifyOwner(server, borrower, "§aInter-bank loan " + shortId(loanId) + " has been settled.");
            } else {
                loan.putString("status", "DEFAULTED");
                loan.putString("defaultReason", "Borrower reserve shortfall");
                notifyOwner(server, lender, "§cInter-bank loan " + shortId(loanId) + " defaulted. Shortfall: $" + remaining.toPlainString());
                notifyOwner(server, borrower, "§cYour bank defaulted on inter-bank loan " + shortId(loanId) + ".");
            }
            centralBank.getInterbankLoans().put(loanId, loan);
            BankManager.markDirty();
        }
    }

    private static void processAnnualLicenseRenewals(MinecraftServer server, CentralBank centralBank, long gameTime) {
        long interval = Math.max(20L, Config.BANK_ANNUAL_LICENSE_INTERVAL_TICKS.get());
        BigDecimal fee = BigDecimal.valueOf(Math.max(0, Config.BANK_ANNUAL_LICENSE_FEE.get()));
        long warningLead = 7L * 24000L;

        for (Bank bank : centralBank.getBanks().values()) {
            if (bank.getBankId().equals(centralBank.getBankId())) {
                continue;
            }
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
            long nextDue = metadata.contains("nextLicenseFeeTick")
                    ? metadata.getLong("nextLicenseFeeTick")
                    : gameTime + interval;
            if (!metadata.contains("nextLicenseFeeTick")) {
                metadata.putLong("nextLicenseFeeTick", nextDue);
                centralBank.putBankMetadata(bank.getBankId(), metadata);
            }

            if (gameTime >= (nextDue - warningLead) && !metadata.getBoolean("licenseWarningSent")) {
                notifyOwner(server, bank, "§eAnnual license fee due soon for " + bank.getBankName()
                        + ". Fee: $" + fee.toPlainString() + " at tick " + nextDue + ".");
                metadata.putBoolean("licenseWarningSent", true);
                centralBank.putBankMetadata(bank.getBankId(), metadata);
            }

            if (gameTime < nextDue) {
                continue;
            }

            if (bank.getDeclaredReserve().compareTo(fee) >= 0) {
                bank.setReserve(bank.getDeclaredReserve().subtract(fee));
                centralBank.setReserve(centralBank.getDeclaredReserve().add(fee));
                metadata.putLong("nextLicenseFeeTick", gameTime + interval);
                metadata.putBoolean("licenseWarningSent", false);
                metadata.remove("licenseGraceEndTick");
                if ("WARNING".equalsIgnoreCase(metadata.getString("status"))) {
                    metadata.putString("status", "ACTIVE");
                }
                centralBank.putBankMetadata(bank.getBankId(), metadata);
                recordSettlement(centralBank, bank.getBankId(), centralBank.getBankId(), fee, "LICENSE_FEE", true);
                notifyOwner(server, bank, "§aAnnual license fee paid for " + bank.getBankName() + ".");
                continue;
            }

            long graceEnd = metadata.contains("licenseGraceEndTick")
                    ? metadata.getLong("licenseGraceEndTick")
                    : gameTime + Math.max(20L, Config.BANK_RESERVE_GRACE_TICKS.get());
            metadata.putLong("licenseGraceEndTick", graceEnd);
            if (!"SUSPENDED".equalsIgnoreCase(metadata.getString("status"))) {
                metadata.putString("status", "WARNING");
            }
            if (gameTime >= graceEnd) {
                metadata.putString("status", "SUSPENDED");
                metadata.putString("suspendReason", "Unpaid annual license renewal fee");
                notifyOwner(server, bank, "§cYour bank was suspended for unpaid annual license fee.");
            } else {
                notifyOwner(server, bank, "§eInsufficient reserve for annual license fee. Grace until tick " + graceEnd + ".");
            }
            centralBank.putBankMetadata(bank.getBankId(), metadata);
        }
    }

    private static void processEmployeeSalaries(MinecraftServer server, CentralBank centralBank, long gameTime) {
        long salaryInterval = 24000L;
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank.getBankId().equals(centralBank.getBankId())) {
                continue;
            }
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
            String encoded = metadata.getString("employees");
            if (encoded == null || encoded.isBlank()) {
                continue;
            }
            long lastSalaryTick = metadata.contains("lastSalaryTick") ? metadata.getLong("lastSalaryTick") : Long.MIN_VALUE;
            if (lastSalaryTick != Long.MIN_VALUE && (gameTime - lastSalaryTick) < salaryInterval) {
                continue;
            }

            Map<UUID, EmployeeSalary> employees = decodeEmployees(encoded);
            if (employees.isEmpty()) {
                metadata.putLong("lastSalaryTick", gameTime);
                centralBank.putBankMetadata(bank.getBankId(), metadata);
                continue;
            }

            for (Map.Entry<UUID, EmployeeSalary> entry : employees.entrySet()) {
                UUID employeeId = entry.getKey();
                EmployeeSalary employee = entry.getValue();
                BigDecimal salary = employee.salary();
                if (salary.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                if (bank.getDeclaredReserve().compareTo(salary) < 0) {
                    notifyOwner(server, bank, "§eSalary deferred for " + shortId(employeeId) + " due to low reserve.");
                    continue;
                }

                AccountHolder destination = findEmployeeSalaryAccount(centralBank, bank, employeeId);
                if (destination == null) {
                    notifyOwner(server, bank, "§eSalary skipped for " + shortId(employeeId) + " (no destination account).");
                    continue;
                }

                bank.setReserve(bank.getDeclaredReserve().subtract(salary));
                destination.forceAddBalance(salary);
                destination.addTransaction(new UserTransaction(
                        bank.getBankId(),
                        destination.getAccountUUID(),
                        salary,
                        LocalDateTime.now(),
                        "EMPLOYEE_SALARY:" + bank.getBankName() + ":" + employee.role()
                ));
                notifyOwner(server, bank, "§aPaid salary $" + salary.toPlainString() + " to " + shortId(employeeId) + ".");
                ServerPlayer onlineEmployee = server.getPlayerList().getPlayer(employeeId);
                if (onlineEmployee != null) {
                    onlineEmployee.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§aSalary received from " + bank.getBankName() + ": $" + salary.toPlainString()
                    ));
                }
            }

            metadata.putLong("lastSalaryTick", gameTime);
            centralBank.putBankMetadata(bank.getBankId(), metadata);
        }
    }

    private static void processBankTaxes(MinecraftServer server, CentralBank centralBank, long gameTime) {
        long taxInterval = Math.max(20L, Config.BANK_TAX_INTERVAL_TICKS.get());
        BigDecimal taxRate = BigDecimal.valueOf(Config.BANK_PROFIT_TAX_RATE.get());
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank.getBankId().equals(centralBank.getBankId())) {
                continue;
            }
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
            long lastTaxTick = metadata.contains("lastTaxTick") ? metadata.getLong("lastTaxTick") : Long.MIN_VALUE;
            if (lastTaxTick != Long.MIN_VALUE && (gameTime - lastTaxTick) < taxInterval) {
                continue;
            }

            BigDecimal previousReserve = metadata.contains("lastTaxReserve")
                    ? readDecimal(metadata, "lastTaxReserve")
                    : bank.getDeclaredReserve();
            BigDecimal currentReserve = bank.getDeclaredReserve();
            BigDecimal profit = currentReserve.subtract(previousReserve).max(BigDecimal.ZERO);
            BigDecimal tax = profit.multiply(taxRate).setScale(2, RoundingMode.HALF_EVEN);
            if (tax.compareTo(BigDecimal.ZERO) > 0 && bank.getDeclaredReserve().compareTo(tax) >= 0) {
                bank.setReserve(bank.getDeclaredReserve().subtract(tax));
                centralBank.setReserve(centralBank.getDeclaredReserve().add(tax));
                recordSettlement(centralBank, bank.getBankId(), centralBank.getBankId(), tax, "BANK_TAX", true);
                notifyOwner(server, bank, "§eBank tax collected: $" + tax.toPlainString());
            }
            metadata.putLong("lastTaxTick", gameTime);
            metadata.putString("lastTaxReserve", bank.getDeclaredReserve().toPlainString());
            centralBank.putBankMetadata(bank.getBankId(), metadata);
        }
    }

    private static void processReserveAudits(MinecraftServer server, CentralBank centralBank, long gameTime) {
        long interval = 24000L;
        if (lastAuditTick >= 0L && (gameTime - lastAuditTick) < interval) {
            return;
        }
        lastAuditTick = gameTime;

        BigDecimal minRatio = BigDecimal.valueOf(Config.BANK_MIN_RESERVE_RATIO.get());
        long grace = Math.max(20L, Config.BANK_RESERVE_GRACE_TICKS.get());
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank.getBankId().equals(centralBank.getBankId())) {
                continue;
            }
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
            BigDecimal deposits = bank.getTotalDeposits();
            BigDecimal reserve = bank.getDeclaredReserve();
            BigDecimal ratio = deposits.compareTo(BigDecimal.ZERO) > 0
                    ? reserve.divide(deposits, 6, RoundingMode.HALF_EVEN)
                    : BigDecimal.ONE;
            if (ratio.compareTo(minRatio) < 0) {
                long breachStart = metadata.contains("reserveBreachStartTick")
                        ? metadata.getLong("reserveBreachStartTick")
                        : gameTime;
                metadata.putLong("reserveBreachStartTick", breachStart);
                if ((gameTime - breachStart) >= grace) {
                    if (!"SUSPENDED".equalsIgnoreCase(metadata.getString("status"))
                            && !"REVOKED".equalsIgnoreCase(metadata.getString("status"))) {
                        metadata.putString("status", "RESTRICTED");
                    }
                } else if (!"SUSPENDED".equalsIgnoreCase(metadata.getString("status"))
                        && !"REVOKED".equalsIgnoreCase(metadata.getString("status"))) {
                    metadata.putString("status", "WARNING");
                }
                notifyOwner(server, bank, "§eReserve audit warning: ratio "
                        + ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_EVEN).toPlainString()
                        + "% is below minimum.");
            } else {
                metadata.remove("reserveBreachStartTick");
                if ("WARNING".equalsIgnoreCase(metadata.getString("status"))
                        || "RESTRICTED".equalsIgnoreCase(metadata.getString("status"))) {
                    metadata.putString("status", "ACTIVE");
                    notifyOwner(server, bank, "§aReserve audit: your bank returned to compliance.");
                }
            }
            centralBank.putBankMetadata(bank.getBankId(), metadata);
        }
    }

    private static void enforceSavingsRateBands(MinecraftServer server, CentralBank centralBank, long gameTime) {
        double federal = centralBank.getFederalFundsRate();
        double floor = federal * Config.SAVINGS_RATE_FLOOR_MULTIPLIER.get();
        double ceiling = federal * Config.SAVINGS_RATE_CEILING_MULTIPLIER.get();
        long grace = 24000L;

        for (Bank bank : centralBank.getBanks().values()) {
            if (bank.getBankId().equals(centralBank.getBankId())) {
                continue;
            }
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
            if (metadata.getBoolean("rateExempt")) {
                metadata.remove("rateOutOfBandStartTick");
                centralBank.putBankMetadata(bank.getBankId(), metadata);
                continue;
            }

            double rate = bank.getInterestRate();
            boolean outOfBand = rate < floor || rate > ceiling;
            if (!outOfBand) {
                metadata.remove("rateOutOfBandStartTick");
                centralBank.putBankMetadata(bank.getBankId(), metadata);
                continue;
            }

            long start = metadata.contains("rateOutOfBandStartTick")
                    ? metadata.getLong("rateOutOfBandStartTick")
                    : gameTime;
            metadata.putLong("rateOutOfBandStartTick", start);
            if ((gameTime - start) >= grace) {
                double clamped = Math.max(floor, Math.min(ceiling, rate));
                bank.setInterestRate(clamped);
                metadata.remove("rateOutOfBandStartTick");
                notifyOwner(server, bank, "§eSavings rate was clamped to " + clamped
                        + "% to match federal rate band.");
            } else {
                notifyOwner(server, bank, "§eSavings rate is outside federal band. Adjust within one in-game day.");
            }
            centralBank.putBankMetadata(bank.getBankId(), metadata);
        }
    }

    private static void processReportSnapshots(CentralBank centralBank, long gameTime) {
        long interval = 24000L;
        if (lastSnapshotTick >= 0L && (gameTime - lastSnapshotTick) < interval) {
            return;
        }
        lastSnapshotTick = gameTime;

        BigDecimal totalCirculation = BigDecimal.ZERO;
        BigDecimal totalReserves = centralBank.getDeclaredReserve();
        int activeBanks = 0;
        for (Bank bank : centralBank.getBanks().values()) {
            if (!bank.getBankId().equals(centralBank.getBankId())) {
                activeBanks++;
                totalReserves = totalReserves.add(bank.getDeclaredReserve());
            }
            for (AccountHolder account : bank.getBankAccounts().values()) {
                totalCirculation = totalCirculation.add(account.getBalance());
            }
        }
        CompoundTag snapshot = new CompoundTag();
        snapshot.putLong("timestampMillis", System.currentTimeMillis());
        snapshot.putLong("gameTime", gameTime);
        snapshot.putString("totalCirculation", totalCirculation.toPlainString());
        snapshot.putString("totalReserves", totalReserves.toPlainString());
        snapshot.putInt("activeBanks", activeBanks);
        snapshot.putDouble("federalFundsRate", centralBank.getFederalFundsRate());
        centralBank.getReportSnapshots().put(UUID.randomUUID(), snapshot);
        trimTagMap(centralBank.getReportSnapshots(), 400);
        BankManager.markDirty();
    }

    private static void recordSettlement(CentralBank centralBank,
                                         UUID fromBankId,
                                         UUID toBankId,
                                         BigDecimal amount,
                                         String reason,
                                         boolean success) {
        CompoundTag settlement = new CompoundTag();
        UUID id = UUID.randomUUID();
        settlement.putUUID("id", id);
        settlement.putLong("timestampMillis", System.currentTimeMillis());
        settlement.putUUID("fromBankId", fromBankId);
        settlement.putUUID("toBankId", toBankId);
        settlement.putString("amount", amount.toPlainString());
        settlement.putString("reason", reason == null ? "" : reason);
        settlement.putBoolean("success", success);
        if (success) {
            centralBank.getSettlementLedger().put(id, settlement);
            trimTagMap(centralBank.getSettlementLedger(), Math.max(1, Config.CLEARING_LEDGER_LIMIT.get()));
        } else {
            centralBank.getSettlementSuspense().put(id, settlement);
            trimTagMap(centralBank.getSettlementSuspense(), Math.max(1, Config.CLEARING_LEDGER_LIMIT.get()));
        }
        BankManager.markDirty();
    }

    private static void trimTagMap(Map<UUID, CompoundTag> map, int maxSize) {
        if (map.size() <= maxSize) {
            return;
        }
        List<Map.Entry<UUID, CompoundTag>> ordered = new ArrayList<>(map.entrySet());
        ordered.sort(Comparator.comparingLong(entry -> entry.getValue().getLong("timestampMillis")));
        int removeCount = map.size() - maxSize;
        for (int i = 0; i < removeCount && i < ordered.size(); i++) {
            map.remove(ordered.get(i).getKey());
        }
    }

    private static Map<UUID, EmployeeSalary> decodeEmployees(String encoded) {
        Map<UUID, EmployeeSalary> result = new java.util.HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            String raw = entry.trim();
            if (raw.isBlank() || !raw.contains("=") || !raw.contains(":")) {
                continue;
            }
            String[] idAndData = raw.split("=", 2);
            String[] roleAndSalary = idAndData[1].split(":", 2);
            if (roleAndSalary.length < 2) {
                continue;
            }
            try {
                UUID playerId = UUID.fromString(idAndData[0].trim());
                String role = roleAndSalary[0].trim().toUpperCase(Locale.ROOT);
                BigDecimal salary = new BigDecimal(roleAndSalary[1].trim());
                if (salary.compareTo(BigDecimal.ZERO) >= 0) {
                    result.put(playerId, new EmployeeSalary(role, salary));
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private static AccountHolder findEmployeeSalaryAccount(CentralBank centralBank, Bank bank, UUID playerId) {
        if (centralBank == null || bank == null || playerId == null) {
            return null;
        }
        for (AccountHolder account : bank.getBankAccounts().values()) {
            if (playerId.equals(account.getPlayerUUID()) && account.getAccountType() == net.austizz.ultimatebankingsystem.accountTypes.AccountTypes.CheckingAccount) {
                return account;
            }
        }
        var all = centralBank.SearchForAccount(playerId);
        if (all.isEmpty()) {
            return null;
        }
        for (AccountHolder account : all.values()) {
            if (account.isPrimaryAccount()) {
                return account;
            }
        }
        return all.values().iterator().next();
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

    private static UUID readUuid(CompoundTag tag, String key) {
        if (tag == null || key == null || key.isBlank() || !tag.hasUUID(key)) {
            return null;
        }
        return tag.getUUID(key);
    }

    private static String shortId(UUID id) {
        if (id == null) {
            return "unknown";
        }
        String raw = id.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }

    private static void notifyOwner(MinecraftServer server, Bank bank, String message) {
        if (server == null || bank == null) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(bank.getBankOwnerId());
        if (owner != null) {
            owner.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        }
    }
}
