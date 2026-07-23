package net.austizz.ultimatebankingsystem.command;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class UBSBankingSeedService {
    private static final String SEED_NAMESPACE = "ultimatebankingsystem:demo-seed:";
    private static final String HISTORY_KIND = "economy_hourly_v1";
    private static final long ONE_HOUR_MS = 60L * 60L * 1000L;
    private static final long ONE_DAY_TICKS = 24_000L;

    private UBSBankingSeedService() {
    }

    static SeedResult seedBankingDemo(MinecraftServer server, ServerPlayer actor) {
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return new SeedResult(0, 0, 0, 0, 0, 0, 0, 0, "");
        }

        long gameTime = currentOverworldGameTime(server);
        long nowMillis = System.currentTimeMillis();
        SeedCounters counters = new SeedCounters();
        Map<String, Bank> seededBanks = new LinkedHashMap<>();

        seededBanks.put("aurora", seedBank(
                centralBank,
                counters,
                "aurora",
                "UBS Demo - Aurora Credit Union",
                new BigDecimal("185000"),
                2.25D,
                "ACTIVE",
                "ROLE_BASED",
                "Member credit with conservative reserves.",
                "#62D98A",
                id("owner:aurora")
        ));
        seededBanks.put("pioneer", seedBank(
                centralBank,
                counters,
                "pioneer",
                "UBS Demo - Pioneer Merchant Bank",
                new BigDecimal("265000"),
                2.75D,
                "ACTIVE",
                "PERCENTAGE_SHARES",
                "Inventory loans and retail settlement support.",
                "#5EBBFF",
                id("owner:pioneer")
        ));
        seededBanks.put("atlas", seedBank(
                centralBank,
                counters,
                "atlas",
                "UBS Demo - Atlas Capital",
                new BigDecimal("62000"),
                3.10D,
                "WARNING",
                "FIXED_COFOUNDERS",
                "Aggressive lending with a tight reserve buffer.",
                "#F5C15C",
                id("owner:atlas")
        ));

        String playerSandboxBankName = "";
        if (actor != null) {
            String playerKey = "player:" + actor.getUUID();
            String playerBankName = actor.getName().getString() + " UBS Sandbox Bank";
            Bank playerBank = seedBank(
                    centralBank,
                    counters,
                    playerKey,
                    playerBankName,
                    new BigDecimal("125000"),
                    2.50D,
                    "ACTIVE",
                    "ROLE_BASED",
                    "Player-owned sandbox bank for accepting demo market offers.",
                    "#A78BFA",
                    actor.getUUID()
            );
            seededBanks.put("player", playerBank);
            playerSandboxBankName = playerBank.getBankName();
            seedAccount(counters, playerBank, id(playerKey + ":account:checking"), actor.getUUID(),
                    AccountTypes.CheckingAccount, new BigDecimal("35000"), true, 720, "Sandbox operating account");
            seedAccount(counters, playerBank, id(playerKey + ":account:saving"), actor.getUUID(),
                    AccountTypes.SavingAccount, new BigDecimal("22000"), false, 720, "Sandbox reserve account");
        }

        seedDemoAccounts(counters, seededBanks);
        seedLoanProducts(centralBank, counters, seededBanks);
        seedInterbankOffers(centralBank, counters, seededBanks, gameTime);
        seedInterbankLoans(centralBank, counters, seededBanks, gameTime);
        seedSettlementRows(centralBank, counters, seededBanks, nowMillis);
        seedDashboardHistory(centralBank, counters, nowMillis);

        BankManager.markDirty();
        return new SeedResult(
                counters.banksCreated,
                counters.banksUpdated,
                counters.accountsSeeded,
                counters.loanProductsSeeded,
                counters.offersSeeded,
                counters.loansSeeded,
                counters.settlementsSeeded,
                counters.historyPointsSeeded,
                playerSandboxBankName
        );
    }

    private static Bank seedBank(CentralBank centralBank,
                                 SeedCounters counters,
                                 String key,
                                 String name,
                                 BigDecimal reserve,
                                 double interestRate,
                                 String status,
                                 String ownershipModel,
                                 String motto,
                                 String color,
                                 UUID ownerId) {
        UUID bankId = id("bank:" + key);
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            bank = new Bank(bankId, name, reserve, interestRate, ownerId);
            centralBank.addBank(bank);
            counters.banksCreated++;
        } else {
            bank.setBankName(name);
            bank.setReserve(reserve);
            bank.setBankOwnerId(ownerId);
            counters.banksUpdated++;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putBoolean("ubsSeedData", true);
        metadata.putString("ubsSeedKey", key);
        metadata.putString("status", status);
        metadata.putString("ownershipModel", ownershipModel);
        metadata.putString("motto", motto);
        metadata.putString("color", color);
        metadata.putLong("createdMillis", System.currentTimeMillis() - (long) Math.floorMod(key.hashCode(), 14) * 86_400_000L);
        metadata.putUUID("founder", ownerId);
        metadata.putString("dailyWithdrawn", "0");
        metadata.putLong("dailyWindowDay", -1L);
        metadata.putString("reserveMinRatio", String.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()));
        metadata.putString("dailyCapOverride", "");
        metadata.putString("employees", "");
        metadata.putString("cardIssueFee", "25");
        metadata.putString("cardReplacementFee", "50");
        metadata.putBoolean("rateExempt", true);
        if ("ROLE_BASED".equalsIgnoreCase(ownershipModel)) {
            metadata.putString("roles", ownerId + "=OWNER");
            metadata.putString("shares", "");
            metadata.putString("cofounders", "");
        } else if ("PERCENTAGE_SHARES".equalsIgnoreCase(ownershipModel)) {
            metadata.putString("roles", "");
            metadata.putString("shares", ownerId + "=67;" + id("shareholder:" + key) + "=33");
            metadata.putString("cofounders", "");
        } else if ("FIXED_COFOUNDERS".equalsIgnoreCase(ownershipModel)) {
            metadata.putString("roles", "");
            metadata.putString("shares", "");
            metadata.putString("cofounders", ownerId + ";" + id("cofounder:" + key));
        }
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        bank.setInterestRate(interestRate);
        return bank;
    }

    private static void seedDemoAccounts(SeedCounters counters, Map<String, Bank> banks) {
        Bank aurora = banks.get("aurora");
        if (aurora != null) {
            seedAccount(counters, aurora, id("aurora:account:checking:maya"), id("customer:maya"),
                    AccountTypes.CheckingAccount, new BigDecimal("42500"), true, 760, "Maya's retail operating cash");
            seedAccount(counters, aurora, id("aurora:account:saving:maya"), id("customer:maya"),
                    AccountTypes.SavingAccount, new BigDecimal("68400"), false, 760, "Maya's retained earnings");
            seedAccount(counters, aurora, id("aurora:account:mm:oscar"), id("customer:oscar"),
                    AccountTypes.MoneyMarketAccount, new BigDecimal("31150"), true, 705, "Oscar's seasonal reserve");
        }

        Bank pioneer = banks.get("pioneer");
        if (pioneer != null) {
            seedAccount(counters, pioneer, id("pioneer:account:checking:nora"), id("customer:nora"),
                    AccountTypes.CheckingAccount, new BigDecimal("82750"), true, 790, "Nora's supermarket settlement");
            seedAccount(counters, pioneer, id("pioneer:account:saving:liam"), id("customer:liam"),
                    AccountTypes.SavingAccount, new BigDecimal("54220"), true, 735, "Liam's warehouse reserve");
            seedAccount(counters, pioneer, id("pioneer:account:certificate:liam"), id("customer:liam"),
                    AccountTypes.CertificateAccount, new BigDecimal("18000"), false, 735, "Liam's fixed deposit");
        }

        Bank atlas = banks.get("atlas");
        if (atlas != null) {
            seedAccount(counters, atlas, id("atlas:account:checking:ravi"), id("customer:ravi"),
                    AccountTypes.CheckingAccount, new BigDecimal("96500"), true, 640, "Ravi's expansion cash");
            seedAccount(counters, atlas, id("atlas:account:saving:elin"), id("customer:elin"),
                    AccountTypes.SavingAccount, new BigDecimal("38500"), true, 690, "Elin's franchise reserve");
        }
    }

    private static void seedAccount(SeedCounters counters,
                                    Bank bank,
                                    UUID accountId,
                                    UUID ownerId,
                                    AccountTypes type,
                                    BigDecimal balance,
                                    boolean primary,
                                    int creditScore,
                                    String label) {
        AccountHolder account = new AccountHolder(ownerId, balance, type, "0000", bank.getBankId(), accountId);
        account.setPrimaryAccount(primary);
        account.setCreditScore(creditScore);
        account.setAccountAccessType("BUSINESS");
        account.setBusinessLabel(label);
        bank.getBankAccounts().put(accountId, account);
        counters.accountsSeeded++;
    }

    private static void seedLoanProducts(CentralBank centralBank, SeedCounters counters, Map<String, Bank> banks) {
        putLoanProducts(centralBank, counters, banks.get("aurora"),
                "Starter Shop Credit|25000|5.5|480000;Refit Microloan|8000|4.75|240000");
        putLoanProducts(centralBank, counters, banks.get("pioneer"),
                "Inventory Expansion|75000|6.25|960000;Settlement Bridge|30000|3.95|120000");
        putLoanProducts(centralBank, counters, banks.get("atlas"),
                "High Velocity Growth|120000|9.5|1440000;Short Term Reserve Patch|45000|8.25|240000");
        putLoanProducts(centralBank, counters, banks.get("player"),
                "Sandbox Working Capital|50000|5.0|720000");
    }

    private static void putLoanProducts(CentralBank centralBank, SeedCounters counters, Bank bank, String encodedProducts) {
        if (bank == null) {
            return;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("loanProducts", encodedProducts);
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        counters.loanProductsSeeded += encodedProducts.isBlank() ? 0 : encodedProducts.split(";").length;
    }

    private static void seedInterbankOffers(CentralBank centralBank,
                                            SeedCounters counters,
                                            Map<String, Bank> banks,
                                            long gameTime) {
        putOffer(centralBank, counters, "aurora-offer-1", banks.get("aurora"),
                new BigDecimal("40000"), 4.65D, ONE_DAY_TICKS * 20L, gameTime);
        putOffer(centralBank, counters, "pioneer-offer-1", banks.get("pioneer"),
                new BigDecimal("85000"), 5.10D, ONE_DAY_TICKS * 35L, gameTime);
        putOffer(centralBank, counters, "atlas-offer-1", banks.get("atlas"),
                new BigDecimal("22000"), 7.80D, ONE_DAY_TICKS * 10L, gameTime);
    }

    private static void putOffer(CentralBank centralBank,
                                 SeedCounters counters,
                                 String key,
                                 Bank lender,
                                 BigDecimal amount,
                                 double annualRate,
                                 long termTicks,
                                 long gameTime) {
        if (lender == null) {
            return;
        }
        UUID offerId = id("interbank-offer:" + key);
        CompoundTag offer = new CompoundTag();
        offer.putUUID("id", offerId);
        offer.putBoolean("ubsSeedData", true);
        offer.putUUID("lenderBankId", lender.getBankId());
        offer.putString("amount", amount.toPlainString());
        offer.putDouble("annualRate", annualRate);
        offer.putLong("termTicks", termTicks);
        offer.putLong("createdTick", Math.max(0L, gameTime - (Math.abs(key.hashCode()) % 12_000L)));
        offer.putLong("expiryTick", gameTime + Math.max(termTicks, ONE_DAY_TICKS));
        offer.putString("status", "OPEN");
        centralBank.getInterbankOffers().put(offerId, offer);
        counters.offersSeeded++;
    }

    private static void seedInterbankLoans(CentralBank centralBank,
                                           SeedCounters counters,
                                           Map<String, Bank> banks,
                                           long gameTime) {
        Bank lender = banks.get("aurora");
        Bank borrower = banks.getOrDefault("player", banks.get("atlas"));
        if (lender == null || borrower == null || lender.getBankId().equals(borrower.getBankId())) {
            return;
        }

        UUID loanId = id("interbank-loan:aurora-to-sandbox");
        BigDecimal principal = new BigDecimal("30000");
        BigDecimal remaining = principal.multiply(new BigDecimal("1.0525")).setScale(2, RoundingMode.HALF_EVEN);
        CompoundTag loan = new CompoundTag();
        loan.putUUID("id", loanId);
        loan.putBoolean("ubsSeedData", true);
        loan.putString("type", "INTERBANK");
        loan.putUUID("lenderBankId", lender.getBankId());
        loan.putUUID("borrowerBankId", borrower.getBankId());
        loan.putString("principal", principal.toPlainString());
        loan.putString("remaining", remaining.toPlainString());
        loan.putDouble("annualRate", 5.25D);
        loan.putLong("termTicks", ONE_DAY_TICKS * 30L);
        loan.putLong("createdTick", Math.max(0L, gameTime - ONE_DAY_TICKS * 3L));
        loan.putLong("maturityTick", gameTime + ONE_DAY_TICKS * 27L);
        loan.putLong("paymentIntervalTicks", ONE_DAY_TICKS * 7L);
        loan.putInt("paymentsRemaining", 4);
        loan.putLong("nextDueTick", gameTime + ONE_DAY_TICKS * 4L);
        loan.putString("status", "ACTIVE");
        centralBank.getInterbankLoans().put(loanId, loan);
        counters.loansSeeded++;
    }

    private static void seedSettlementRows(CentralBank centralBank,
                                           SeedCounters counters,
                                           Map<String, Bank> banks,
                                           long nowMillis) {
        putSettlement(centralBank, counters, "settlement:aurora-pioneer", banks.get("aurora"), banks.get("pioneer"),
                new BigDecimal("15500"), "DEMO_RETAIL_SETTLEMENT", true, nowMillis - 42_000L);
        putSettlement(centralBank, counters, "settlement:pioneer-atlas", banks.get("pioneer"), banks.get("atlas"),
                new BigDecimal("6200"), "DEMO_CARD_CLEARING", true, nowMillis - 95_000L);
        putSettlement(centralBank, counters, "settlement:atlas-failed", banks.get("atlas"), banks.get("aurora"),
                new BigDecimal("48000"), "DEMO_INSUFFICIENT_RESERVE", false, nowMillis - 180_000L);
    }

    private static void putSettlement(CentralBank centralBank,
                                      SeedCounters counters,
                                      String key,
                                      Bank from,
                                      Bank to,
                                      BigDecimal amount,
                                      String reason,
                                      boolean success,
                                      long timestampMillis) {
        if (from == null || to == null) {
            return;
        }
        UUID settlementId = id(key);
        CompoundTag entry = new CompoundTag();
        entry.putUUID("id", settlementId);
        entry.putBoolean("ubsSeedData", true);
        entry.putUUID("fromBankId", from.getBankId());
        entry.putUUID("toBankId", to.getBankId());
        entry.putString("amount", amount.toPlainString());
        entry.putLong("timestampMillis", timestampMillis);
        entry.putString("reason", reason);
        entry.putBoolean("success", success);
        if (success) {
            centralBank.getSettlementLedger().put(settlementId, entry);
        } else {
            centralBank.getSettlementSuspense().put(settlementId, entry);
        }
        counters.settlementsSeeded++;
    }

    private static void seedDashboardHistory(CentralBank centralBank, SeedCounters counters, long nowMillis) {
        BigDecimal deposits = totalDeposits(centralBank);
        BigDecimal reserves = totalReserves(centralBank);
        for (int i = 0; i < 8; i++) {
            UUID snapshotId = id("economy-history:" + i);
            BigDecimal depositsPoint = deposits.subtract(BigDecimal.valueOf((7L - i) * 3_200L)).max(BigDecimal.ZERO);
            BigDecimal reservesPoint = reserves.subtract(BigDecimal.valueOf((7L - i) * 1_100L)).max(BigDecimal.ZERO);
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("ubsSeedData", true);
            tag.putString("kind", HISTORY_KIND);
            tag.putLong("capturedAtMillis", nowMillis - (7L - i) * ONE_HOUR_MS);
            tag.putInt("banksTotal", Math.max(0, centralBank.getBanks().size() - 1));
            tag.putInt("activeBanks", countBanksByStatus(centralBank, "ACTIVE"));
            tag.putInt("accountsTotal", totalAccounts(centralBank));
            tag.putString("totalDeposits", depositsPoint.toPlainString());
            tag.putString("totalReserves", reservesPoint.toPlainString());
            tag.putString("moneyCirculating", depositsPoint.toPlainString());
            tag.putInt("onlinePlayers", 0);
            tag.putInt("shopsTotal", 0);
            tag.putLong("shopsRevenueDollars", i * 1250L);
            tag.putInt("issuedCards", i + 2);
            tag.putInt("activeCards", i + 1);
            tag.putInt("blockedCards", i % 2);
            tag.putLong("timestampMillis", tag.getLong("capturedAtMillis"));
            tag.putString("totalCirculation", depositsPoint.toPlainString());
            tag.putString("avgReserveRatio", averageReserveRatio(centralBank).toPlainString());
            tag.putInt("warningOrRestricted", countNonActiveBanks(centralBank));
            tag.putString("totalOutstandingLoans", totalInterbankLoans(centralBank).toPlainString());
            tag.putDouble("federalFundsRate", centralBank.getFederalFundsRate());
            tag.putString("netOmo", "0");
            tag.putLong("settlements24h", centralBank.getSettlementLedger().size());
            centralBank.getReportSnapshots().put(snapshotId, tag);
            counters.historyPointsSeeded++;
        }
    }

    private static BigDecimal averageReserveRatio(CentralBank centralBank) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
                continue;
            }
            BigDecimal deposits = bank.getTotalDeposits();
            BigDecimal ratio = deposits.compareTo(BigDecimal.ZERO) > 0
                    ? bank.getDeclaredReserve().divide(deposits, 6, RoundingMode.HALF_EVEN)
                    : BigDecimal.ONE;
            total = total.add(ratio);
            count++;
        }
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_EVEN)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_EVEN);
    }

    private static int countNonActiveBanks(CentralBank centralBank) {
        int count = 0;
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
                continue;
            }
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
            String status = metadata.getString("status");
            if (status != null && !status.isBlank() && !"ACTIVE".equalsIgnoreCase(status)) {
                count++;
            }
        }
        return count;
    }

    private static BigDecimal totalInterbankLoans(CentralBank centralBank) {
        BigDecimal total = BigDecimal.ZERO;
        for (CompoundTag loan : centralBank.getInterbankLoans().values()) {
            if (loan == null || !"ACTIVE".equalsIgnoreCase(loan.getString("status"))) {
                continue;
            }
            try {
                total = total.add(new BigDecimal(loan.getString("remaining")));
            } catch (NumberFormatException ignored) {
            }
        }
        return total.setScale(2, RoundingMode.HALF_EVEN);
    }

    private static int countBanksByStatus(CentralBank centralBank, String status) {
        int count = 0;
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
                continue;
            }
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
            String bankStatus = metadata.getString("status");
            if (bankStatus == null || bankStatus.isBlank()) {
                bankStatus = "ACTIVE";
            }
            if (status.equalsIgnoreCase(bankStatus)) {
                count++;
            }
        }
        return count;
    }

    private static int totalAccounts(CentralBank centralBank) {
        int total = 0;
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank != null && bank.getBankAccounts() != null) {
                total += bank.getBankAccounts().size();
            }
        }
        return total;
    }

    private static BigDecimal totalDeposits(CentralBank centralBank) {
        BigDecimal total = BigDecimal.ZERO;
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank != null) {
                total = total.add(bank.getTotalDeposits());
            }
        }
        return total;
    }

    private static BigDecimal totalReserves(CentralBank centralBank) {
        BigDecimal total = BigDecimal.ZERO;
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank != null) {
                total = total.add(bank.getDeclaredReserve());
            }
        }
        return total;
    }

    private static long currentOverworldGameTime(MinecraftServer server) {
        if (server == null) {
            return 0L;
        }
        var overworld = server.getLevel(Level.OVERWORLD);
        return overworld == null ? 0L : overworld.getGameTime();
    }

    private static UUID id(String key) {
        return UUID.nameUUIDFromBytes((SEED_NAMESPACE + key).getBytes(StandardCharsets.UTF_8));
    }

    private static final class SeedCounters {
        int banksCreated;
        int banksUpdated;
        int accountsSeeded;
        int loanProductsSeeded;
        int offersSeeded;
        int loansSeeded;
        int settlementsSeeded;
        int historyPointsSeeded;
    }

    record SeedResult(int banksCreated,
                      int banksUpdated,
                      int accountsSeeded,
                      int loanProductsSeeded,
                      int offersSeeded,
                      int loansSeeded,
                      int settlementsSeeded,
                      int historyPointsSeeded,
                      String playerSandboxBankName) {
        int totalSeeded() {
            return banksCreated + banksUpdated + accountsSeeded + loanProductsSeeded
                    + offersSeeded + loansSeeded + settlementsSeeded + historyPointsSeeded;
        }
    }
}
