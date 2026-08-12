package net.austizz.ultimatebankingsystem.bank;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.nbt.CompoundTag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BankLevelService {
    public static final String LEVEL_KEY = "bankLevel";
    public static final String LEVEL_MANUAL_KEY = "bankLevelManual";
    public static final int MAX_LEVEL = 100;

    private static final long SCORE_PER_LEVEL = 25L;
    private static final long DEPOSIT_DOLLARS_PER_SCORE = 1_000L;
    private static final long ACCOUNT_SCORE = 5L;
    private static final int BASE_SAFE_ROW_UNITS = 16;
    private static final int SAFE_ROW_UNITS_PER_LEVEL = 8;
    // Level 1 keeps the historical flat cap of 5 so no pre-leveling bank ends up over capacity.
    private static final int BASE_BANK_TELLER_CAPACITY = 5;
    private static final int BANK_TELLER_CAPACITY_PER_LEVEL = 1;

    private BankLevelService() {
    }

    public static BankLevelSnapshot snapshot(CentralBank centralBank, Bank bank) {
        if (centralBank == null || bank == null) {
            return BankLevelSnapshot.empty();
        }
        return snapshot(bank, centralBank.readBankMetadata(bank.getBankId()));
    }

    public static BankLevelSnapshot snapshot(Bank bank, CompoundTag metadata) {
        if (bank == null) {
            return BankLevelSnapshot.empty();
        }
        CompoundTag safeMetadata = metadata == null ? new CompoundTag() : metadata;
        BigDecimal deposits = bank.getTotalDeposits() == null ? BigDecimal.ZERO : bank.getTotalDeposits();
        int accounts = bank.getBankAccounts() == null ? 0 : bank.getBankAccounts().size();
        long score = bankScore(deposits, accounts);
        int derivedLevel = levelForScore(score);
        boolean manual = safeMetadata.getBoolean(LEVEL_MANUAL_KEY);
        int level = manual && safeMetadata.contains(LEVEL_KEY)
                ? clampLevel(safeMetadata.getInt(LEVEL_KEY))
                : derivedLevel;
        double progress = progressRatioForLevel(score, level);
        long currentFloorScore = requiredScoreForLevel(level);
        long nextScore = level >= MAX_LEVEL ? requiredScoreForLevel(MAX_LEVEL) : requiredScoreForLevel(level + 1);
        return new BankLevelSnapshot(
                level,
                derivedLevel,
                manual,
                deposits.max(BigDecimal.ZERO),
                accounts,
                score,
                currentFloorScore,
                nextScore,
                depositTargetForScore(nextScore),
                accountTargetForScore(nextScore),
                progress,
                safeRowCapacityForLevel(level),
                viewingRoomCapacityForLevel(level),
                bankTellerCapacityForLevel(level),
                buildRoadmap(level)
        );
    }

    public static int effectiveLevel(CentralBank centralBank, Bank bank) {
        return snapshot(centralBank, bank).level();
    }

    public static int safeRowCapacity(CentralBank centralBank, Bank bank) {
        return safeRowCapacityForLevel(effectiveLevel(centralBank, bank));
    }

    public static int safeRowCapacityForLevel(int level) {
        int safeLevel = clampLevel(level);
        return BASE_SAFE_ROW_UNITS + ((safeLevel - 1) * SAFE_ROW_UNITS_PER_LEVEL);
    }

    public static int viewingRoomCapacity(CentralBank centralBank, Bank bank) {
        return viewingRoomCapacityForLevel(effectiveLevel(centralBank, bank));
    }

    public static int viewingRoomCapacityForLevel(int level) {
        return 1 + (clampLevel(level) / 5);
    }

    public static int bankTellerCapacity(CentralBank centralBank, Bank bank) {
        return bankTellerCapacityForLevel(effectiveLevel(centralBank, bank));
    }

    public static int bankTellerCapacityForLevel(int level) {
        int safeLevel = clampLevel(level);
        return BASE_BANK_TELLER_CAPACITY + ((safeLevel - 1) * BANK_TELLER_CAPACITY_PER_LEVEL);
    }

    public static String levelRoadmapReport(CentralBank centralBank, Bank bank) {
        if (centralBank == null || bank == null) {
            return "Bank level roadmap is unavailable.";
        }
        BankLevelSnapshot snapshot = snapshot(centralBank, bank);
        List<String> lines = new ArrayList<>();
        lines.add("Bank Level Roadmap");
        lines.add("@bank_roadmap.enabled=1");
        lines.add("@bank_roadmap.bank_name=" + sanitizeToken(bank.getBankName()));
        lines.add("@bank_roadmap.current_level=" + snapshot.level());
        lines.add("@bank_roadmap.derived_level=" + snapshot.derivedLevel());
        lines.add("@bank_roadmap.manual=" + (snapshot.manual() ? "1" : "0"));
        lines.add("@bank_roadmap.current_deposits=" + snapshot.deposits().setScale(2, RoundingMode.HALF_EVEN).toPlainString());
        lines.add("@bank_roadmap.current_accounts=" + snapshot.accounts());
        lines.add("@bank_roadmap.current_score=" + snapshot.score());
        lines.add("@bank_roadmap.current_level_floor_score=" + snapshot.currentFloorScore());
        lines.add("@bank_roadmap.next_level_score=" + snapshot.nextLevelScore());
        lines.add("@bank_roadmap.next_deposit_target=" + snapshot.nextDepositTargetDollars());
        lines.add("@bank_roadmap.next_account_target=" + snapshot.nextAccountTarget());
        lines.add("@bank_roadmap.progress_ratio=" + String.format(Locale.ROOT, "%.6f", snapshot.progressRatio()));
        lines.add("@bank_roadmap.max_level=" + MAX_LEVEL);
        lines.add("@bank_roadmap.safe_row_capacity=" + snapshot.safeRowCapacity());
        lines.add("@bank_roadmap.viewing_room_capacity=" + snapshot.viewingRoomCapacity());
        lines.add("@bank_roadmap.bank_teller_capacity=" + snapshot.bankTellerCapacity());
        for (String node : snapshot.roadmapNodes()) {
            lines.add("@bank_roadmap.node=" + node);
        }
        lines.add("- Current level: " + snapshot.level()
                + (snapshot.manual() ? " (admin override)" : " (earned)")
                + " | Deposits $" + snapshot.deposits().setScale(2, RoundingMode.HALF_EVEN).toPlainString()
                + " | Accounts " + snapshot.accounts());
        lines.add("- Next target score: " + snapshot.nextLevelScore()
                + " | deposit-only target $" + snapshot.nextDepositTargetDollars()
                + " | account-only target " + snapshot.nextAccountTarget());
        lines.add("- Safe row capacity: " + snapshot.safeRowCapacity() + " row units.");
        lines.add("- Viewing-room capacity: " + snapshot.viewingRoomCapacity() + ".");
        lines.add("- Bank-teller capacity: " + snapshot.bankTellerCapacity() + ".");
        return String.join("\n", lines);
    }

    public static BankLevelResult adminSetBankLevel(CentralBank centralBank, UUID bankId, int requestedLevel) {
        if (centralBank == null || bankId == null) {
            return BankLevelResult.fail("Bank level update failed: missing bank data.");
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return BankLevelResult.fail("Bank not found: " + bankId + ".");
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        int before = snapshot(centralBank, bank).level();
        int target = clampLevel(requestedLevel);
        metadata.putInt(LEVEL_KEY, target);
        metadata.putBoolean(LEVEL_MANUAL_KEY, true);
        centralBank.putBankMetadata(bankId, metadata);
        BankManager.markDirty();
        if (before == target) {
            return BankLevelResult.ok("Bank " + bank.getBankName() + " is already level " + target + ".");
        }
        return BankLevelResult.ok("Bank " + bank.getBankName() + " (" + shortUuid(bankId) + ") level set: " + before + " -> " + target + ".");
    }

    public static BankLevelResult adminAdjustBankLevel(CentralBank centralBank, UUID bankId, int deltaLevels) {
        if (centralBank == null || bankId == null) {
            return BankLevelResult.fail("Bank level update failed: missing bank data.");
        }
        if (deltaLevels == 0) {
            return BankLevelResult.ok("No level change requested.");
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return BankLevelResult.fail("Bank not found: " + bankId + ".");
        }
        int current = snapshot(centralBank, bank).level();
        long raw = (long) current + deltaLevels;
        int target = (int) Math.max(1L, Math.min(MAX_LEVEL, raw));
        if (target == current) {
            return BankLevelResult.fail("Bank " + bank.getBankName() + " is already at level boundary " + current + ".");
        }
        return adminSetBankLevel(centralBank, bankId, target);
    }

    public static BankLevelResult adminClearBankLevelOverride(CentralBank centralBank, UUID bankId) {
        if (centralBank == null || bankId == null) {
            return BankLevelResult.fail("Bank level update failed: missing bank data.");
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return BankLevelResult.fail("Bank not found: " + bankId + ".");
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        if (!metadata.getBoolean(LEVEL_MANUAL_KEY) && !metadata.contains(LEVEL_KEY)) {
            return BankLevelResult.ok("Bank " + bank.getBankName() + " is already using earned level "
                    + snapshot(centralBank, bank).derivedLevel() + ".");
        }

        int before = snapshot(centralBank, bank).level();
        metadata.remove(LEVEL_KEY);
        metadata.putBoolean(LEVEL_MANUAL_KEY, false);
        centralBank.putBankMetadata(bankId, metadata);
        BankManager.markDirty();
        int derived = snapshot(centralBank, bank).derivedLevel();
        return BankLevelResult.ok("Bank " + bank.getBankName() + " (" + shortUuid(bankId)
                + ") level override cleared: " + before + " -> earned level " + derived + ".");
    }

    private static List<String> buildRoadmap(int currentLevel) {
        List<String> nodes = new ArrayList<>(MAX_LEVEL);
        int safeCurrent = clampLevel(currentLevel);
        for (int level = 1; level <= MAX_LEVEL; level++) {
            long score = requiredScoreForLevel(level);
            String state = level < safeCurrent ? "COMPLETED" : (level == safeCurrent ? "CURRENT" : "LOCKED");
            nodes.add(level
                    + "|" + depositTargetForScore(score)
                    + "|" + accountTargetForScore(score)
                    + "|" + safeRowCapacityForLevel(level)
                    + "|" + viewingRoomCapacityForLevel(level)
                    + "|" + bankTellerCapacityForLevel(level)
                    + "|" + state
                    + "|" + encodeUnlocks(unlocksForLevel(level)));
        }
        return nodes;
    }

    private static List<String> unlocksForLevel(int level) {
        int safeLevel = clampLevel(level);
        List<String> unlocks = new ArrayList<>();
        if (safeLevel == 1) {
            unlocks.add("Owner PC banking workspace");
            unlocks.add("Basic safety deposit boxes");
        }
        if (safeLevel == 2) {
            unlocks.add("Extra safe row capacity");
        }
        if (safeLevel == 5) {
            unlocks.add("Deposit-box rent policy tools");
        }
        if (safeLevel == 10) {
            unlocks.add("Locked-box review queue");
        }
        if (safeLevel == 15) {
            unlocks.add("Expanded protected safe areas");
        }
        if (safeLevel == 25) {
            unlocks.add("Vault storage module ready");
        }
        if (safeLevel == 50) {
            unlocks.add("High-capacity safe operations");
        }
        if (safeLevel % 10 == 0) {
            unlocks.add("Milestone capacity expansion");
        }
        return unlocks;
    }

    private static long bankScore(BigDecimal deposits, int accounts) {
        BigDecimal safeDeposits = deposits == null ? BigDecimal.ZERO : deposits.max(BigDecimal.ZERO);
        long depositPoints = safeDeposits
                .divide(BigDecimal.valueOf(DEPOSIT_DOLLARS_PER_SCORE), 0, RoundingMode.DOWN)
                .longValue();
        long accountPoints = Math.max(0L, (long) accounts) * ACCOUNT_SCORE;
        long score = depositPoints + accountPoints;
        return Math.max(0L, Math.min(requiredScoreForLevel(MAX_LEVEL + 1) - 1L, score));
    }

    private static int levelForScore(long score) {
        return clampLevel((int) (Math.max(0L, score) / SCORE_PER_LEVEL) + 1);
    }

    private static long requiredScoreForLevel(int level) {
        int safeLevel = Math.max(1, Math.min(MAX_LEVEL + 1, level));
        return (long) (safeLevel - 1) * SCORE_PER_LEVEL;
    }

    private static long depositTargetForScore(long score) {
        return Math.max(0L, score) * DEPOSIT_DOLLARS_PER_SCORE;
    }

    private static int accountTargetForScore(long score) {
        if (score <= 0L) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, (score + ACCOUNT_SCORE - 1L) / ACCOUNT_SCORE);
    }

    private static double progressRatioForLevel(long score, int level) {
        int safeLevel = clampLevel(level);
        if (safeLevel >= MAX_LEVEL) {
            return 1.0D;
        }
        long floor = requiredScoreForLevel(safeLevel);
        long next = requiredScoreForLevel(safeLevel + 1);
        return Math.max(0.0D, Math.min(1.0D, (score - floor) / (double) Math.max(1L, next - floor)));
    }

    private static int clampLevel(int level) {
        return Math.max(1, Math.min(MAX_LEVEL, level));
    }

    private static String encodeUnlocks(List<String> unlocks) {
        if (unlocks == null || unlocks.isEmpty()) {
            return "-";
        }
        List<String> clean = new ArrayList<>();
        for (String unlock : unlocks) {
            String token = sanitizeToken(unlock).replace(";", ",");
            if (!token.isBlank() && !"-".equals(token)) {
                clean.add(token);
            }
        }
        return clean.isEmpty() ? "-" : String.join(";", clean);
    }

    private static String sanitizeToken(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace('|', '/').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String shortUuid(UUID id) {
        return id == null ? "unknown" : id.toString().substring(0, 8);
    }

    public record BankLevelSnapshot(int level,
                                    int derivedLevel,
                                    boolean manual,
                                    BigDecimal deposits,
                                    int accounts,
                                    long score,
                                    long currentFloorScore,
                                    long nextLevelScore,
                                    long nextDepositTargetDollars,
                                    int nextAccountTarget,
                                    double progressRatio,
                                    int safeRowCapacity,
                                    int viewingRoomCapacity,
                                    int bankTellerCapacity,
                                    List<String> roadmapNodes) {
        private static BankLevelSnapshot empty() {
            return new BankLevelSnapshot(
                    1,
                    1,
                    false,
                    BigDecimal.ZERO,
                    0,
                    0L,
                    0L,
                    SCORE_PER_LEVEL,
                    depositTargetForScore(SCORE_PER_LEVEL),
                    accountTargetForScore(SCORE_PER_LEVEL),
                    0.0D,
                    safeRowCapacityForLevel(1),
                    viewingRoomCapacityForLevel(1),
                    bankTellerCapacityForLevel(1),
                    buildRoadmap(1)
            );
        }
    }

    public record BankLevelResult(boolean success, String message) {
        public static BankLevelResult ok(String message) {
            return new BankLevelResult(true, message == null ? "" : message);
        }

        public static BankLevelResult fail(String message) {
            return new BankLevelResult(false, message == null ? "" : message);
        }
    }
}
