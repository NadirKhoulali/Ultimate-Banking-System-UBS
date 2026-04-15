package net.austizz.ultimatebankingsystem;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();



    public static final ForgeConfigSpec.IntValue TRANSACTIONS_PER_MINUTE = BUILDER
            .comment("The Amount of transactions possible per player per minute")
            .defineInRange("TransactionsPerMinute", 10, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue PAYMENT_TERMINAL_FEEDBACK_TICKS = BUILDER
            .comment("Legacy terminal feedback setting. Terminal payment lock/feedback is currently fixed to 2 seconds.")
            .defineInRange("PaymentTerminalFeedbackTicks", 40, 20, 20 * 30);

    public static final ForgeConfigSpec.IntValue DEFAULT_ATM_WITHDRAWAL_LIMIT = BUILDER
            .comment("Default maximum amount (in whole dollars) a player can withdraw from an ATM per transaction.")
            .defineInRange("DefaultATMWithdrawalLimit", 500, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue DAILY_WITHDRAWAL_LIMIT = BUILDER
            .comment("Default maximum amount (in whole dollars) a player can withdraw per Minecraft day.")
            .defineInRange("DailyWithdrawalLimit", 2000, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue DAILY_WITHDRAWAL_LIMIT_CHECKING = BUILDER
            .comment("Maximum ATM withdrawal amount per real-world day for Checking accounts.")
            .defineInRange("DailyWithdrawalLimitChecking", 2000, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue DAILY_WITHDRAWAL_LIMIT_SAVING = BUILDER
            .comment("Maximum ATM withdrawal amount per real-world day for Saving accounts.")
            .defineInRange("DailyWithdrawalLimitSaving", 1500, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue DAILY_WITHDRAWAL_LIMIT_MONEY_MARKET = BUILDER
            .comment("Maximum ATM withdrawal amount per real-world day for Money Market accounts.")
            .defineInRange("DailyWithdrawalLimitMoneyMarket", 3000, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue DAILY_WITHDRAWAL_LIMIT_CERTIFICATE = BUILDER
            .comment("Maximum ATM withdrawal amount per real-world day for Certificate accounts.")
            .defineInRange("DailyWithdrawalLimitCertificate", 500, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue AUTOSAVE_INTERVAL_MINUTES = BUILDER
            .comment("How often banking data is marked dirty for autosave (in real minutes).")
            .defineInRange("AutoSaveIntervalMinutes", 5, 1, 120);

    public static final ForgeConfigSpec.IntValue SAVINGS_INTEREST_INTERVAL_TICKS = BUILDER
            .comment("How often savings interest payout runs (in server ticks). 24000 ticks = one Minecraft day.")
            .defineInRange("SavingsInterestIntervalTicks", 24000, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue LOAN_AUTO_APPROVE_THRESHOLD = BUILDER
            .comment("Maximum principal amount that can be auto-approved for a player loan.")
            .defineInRange("LoanAutoApproveThreshold", 5000, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue LOAN_AUTO_APPROVE_MIN_CREDIT = BUILDER
            .comment("Minimum credit score required for auto-approval.")
            .defineInRange("LoanAutoApproveMinCredit", 550, 0, 2000);

    public static final ForgeConfigSpec.DoubleValue LOAN_BASE_INTEREST_RATE = BUILDER
            .comment("Base annual loan interest rate used before credit-score adjustment.")
            .defineInRange("LoanBaseInterestRate", 8.0, 0.01, 1000.0);

    public static final ForgeConfigSpec.DoubleValue LOAN_MIN_INTEREST_RATE = BUILDER
            .comment("Minimum annual loan interest rate after credit-score adjustment.")
            .defineInRange("LoanMinInterestRate", 2.0, 0.01, 1000.0);

    public static final ForgeConfigSpec.DoubleValue LOAN_MAX_INTEREST_RATE = BUILDER
            .comment("Maximum annual loan interest rate after credit-score adjustment.")
            .defineInRange("LoanMaxInterestRate", 25.0, 0.01, 1000.0);

    public static final ForgeConfigSpec.IntValue LOAN_TERM_PAYMENTS = BUILDER
            .comment("Default number of scheduled repayments per loan.")
            .defineInRange("LoanTermPayments", 7, 1, 512);

    public static final ForgeConfigSpec.IntValue LOAN_PAYMENT_INTERVAL_TICKS = BUILDER
            .comment("Ticks between loan repayments (24000 = one Minecraft day).")
            .defineInRange("LoanPaymentIntervalTicks", 24000, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue LOAN_WARNING_BEFORE_DUE_TICKS = BUILDER
            .comment("How many ticks before due date a borrower receives a warning.")
            .defineInRange("LoanWarningBeforeDueTicks", 24000, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue CREDIT_SCORE_DEFAULT = BUILDER
            .comment("Default credit score assigned to newly created accounts.")
            .defineInRange("CreditScoreDefault", 500, 0, 2000);

    public static final ForgeConfigSpec.IntValue CREDIT_SCORE_ON_TIME_BOOST = BUILDER
            .comment("Credit score increase applied after each on-time repayment.")
            .defineInRange("CreditScoreOnTimeBoost", 5, 0, 200);

    public static final ForgeConfigSpec.IntValue CREDIT_SCORE_MISSED_PENALTY = BUILDER
            .comment("Credit score decrease applied when a repayment is missed.")
            .defineInRange("CreditScoreMissedPenalty", 20, 0, 2000);

    public static final ForgeConfigSpec.IntValue CREDIT_SCORE_DEFAULT_PENALTY = BUILDER
            .comment("Additional credit score decrease applied when a loan defaults.")
            .defineInRange("CreditScoreDefaultPenalty", 40, 0, 2000);

    public static final ForgeConfigSpec.BooleanValue ALLOW_BANK_CUSTOM_INTEREST_RATE = BUILDER
            .comment("Allow player made banks to set their own interest rate")
            .define("AllowBankCustomInterestRate", true);

    public static final ForgeConfigSpec.DoubleValue DEFAULT_SERVER_INTEREST_RATE = BUILDER
            .comment("The default server interest rate across all banks")
            .defineInRange("ServerInterestRate", 1.4, 0.01, Double.MAX_VALUE);

    public static final ForgeConfigSpec.DoubleValue DEFAULT_FEDERAL_FUNDS_RATE = BUILDER
            .comment(
                    "The interest rate at which depository institutions (banks) lend reserve balances to other depository institutions overnight on an unsecured basis.",
                    "Higher Rates: Increase the cost of borrowing for mortgages, auto loans, and credit cards.",
                    "Lower Rates: Encourage borrowing and spending, but can lower interest earned on savings accounts.")
            .defineInRange("FederalFundsRate", 3.5, 0.00, Double.MAX_VALUE);

    public static final ForgeConfigSpec.DoubleValue MIN_FEDERAL_FUNDS_RATE = BUILDER
            .comment("Minimum allowed Federal Funds Rate.")
            .defineInRange("MinFederalFundsRate", 0.00, 0.00, Double.MAX_VALUE);

    public static final ForgeConfigSpec.DoubleValue MAX_FEDERAL_FUNDS_RATE = BUILDER
            .comment("Maximum allowed Federal Funds Rate.")
            .defineInRange("MaxFederalFundsRate", 50.00, 0.01, Double.MAX_VALUE);

    public static final ForgeConfigSpec.DoubleValue BANK_MIN_RESERVE_RATIO = BUILDER
            .comment("Minimum reserve ratio target for player banks (0.10 = 10%).")
            .defineInRange("BankMinReserveRatio", 0.10, 0.0, 1.0);

    public static final ForgeConfigSpec.IntValue BANK_RESERVE_GRACE_TICKS = BUILDER
            .comment("Grace period before automatic restriction/suspension when reserve checks fail.")
            .defineInRange("BankReserveGraceTicks", 72000, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue BANK_CHARTER_FEE = BUILDER
            .comment("One-time non-refundable charter fee when creating a player bank.")
            .defineInRange("BankCharterFee", 10000, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue BANK_ANNUAL_LICENSE_FEE = BUILDER
            .comment("Recurring annual license fee charged to player banks.")
            .defineInRange("BankAnnualLicenseFee", 2000, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue BANK_ANNUAL_LICENSE_INTERVAL_TICKS = BUILDER
            .comment("Tick interval between annual license fee collections.")
            .defineInRange("BankAnnualLicenseIntervalTicks", 24000 * 365, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.DoubleValue BANK_PROFIT_TAX_RATE = BUILDER
            .comment("Tax rate applied to positive bank reserve growth during tax collection (0.05 = 5%).")
            .defineInRange("BankProfitTaxRate", 0.05, 0.0, 1.0);

    public static final ForgeConfigSpec.IntValue BANK_TAX_INTERVAL_TICKS = BUILDER
            .comment("Tick interval for automatic bank tax collection.")
            .defineInRange("BankTaxIntervalTicks", 24000, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue OMO_HISTORY_LIMIT = BUILDER
            .comment("Maximum retained open market operation history entries.")
            .defineInRange("OpenMarketHistoryLimit", 20, 1, 5000);

    public static final ForgeConfigSpec.IntValue CLEARING_LEDGER_LIMIT = BUILDER
            .comment("Maximum retained central clearing ledger entries.")
            .defineInRange("ClearingLedgerLimit", 50, 1, 20000);

    public static final ForgeConfigSpec.DoubleValue SAVINGS_RATE_FLOOR_MULTIPLIER = BUILDER
            .comment("Dynamic savings floor multiplier applied to Federal Funds Rate.")
            .defineInRange("SavingsRateFloorMultiplier", 0.50, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue SAVINGS_RATE_CEILING_MULTIPLIER = BUILDER
            .comment("Dynamic savings ceiling multiplier applied to Federal Funds Rate.")
            .defineInRange("SavingsRateCeilingMultiplier", 3.00, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue MMA_RATE_MULTIPLIER = BUILDER
            .comment("Money Market Account rate multiplier against Federal Funds Rate.")
            .defineInRange("MoneyMarketRateMultiplier", 0.85, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue BANK_DAILY_LIQUIDITY_RATIO = BUILDER
            .comment("Per-bank daily withdrawal cap ratio against current reserve.")
            .defineInRange("BankDailyLiquidityRatio", 0.20, 0.0, 1.0);

    public static final ForgeConfigSpec.IntValue WITHDRAWAL_QUEUE_EXPIRY_TICKS = BUILDER
            .comment("Expiry for queued withdrawals when reserves are insufficient.")
            .defineInRange("WithdrawalQueueExpiryTicks", 24000, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue BANK_RUN_WINDOW_TICKS = BUILDER
            .comment("Rolling window size for bank-run detection.")
            .defineInRange("BankRunWindowTicks", 100, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.DoubleValue BANK_RUN_THRESHOLD_RATIO = BUILDER
            .comment("Withdrawal ratio threshold to trigger bank-run lockdown.")
            .defineInRange("BankRunThresholdRatio", 0.30, 0.0, 1.0);

    public static final ForgeConfigSpec.IntValue BANK_RUN_LOCKDOWN_TICKS = BUILDER
            .comment("Automatic lockdown duration after bank-run detection.")
            .defineInRange("BankRunLockdownTicks", 300, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue HEIST_DURATION_TICKS = BUILDER
            .comment("Coming Soon: bank heist duration in ticks (currently unused).")
            .defineInRange("HeistDurationTicks", 600, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue HEIST_MIN_PLAYERS = BUILDER
            .comment("Coming Soon: minimum nearby players required to start a heist (currently unused).")
            .defineInRange("HeistMinPlayers", 2, 1, 64);

    public static final ForgeConfigSpec.DoubleValue HEIST_SUCCESS_CHANCE = BUILDER
            .comment("Coming Soon: heist success probability from 0.0 to 1.0 (currently unused).")
            .defineInRange("HeistSuccessChance", 0.45, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue HEIST_PAYOUT_RATIO = BUILDER
            .comment("Coming Soon: percentage of target reserve paid out on successful heist (currently unused).")
            .defineInRange("HeistPayoutRatio", 0.10, 0.0, 1.0);

    public static final ForgeConfigSpec.IntValue HEIST_COOLDOWN_TICKS = BUILDER
            .comment("Coming Soon: cooldown (wanted state) applied after failed heist (currently unused).")
            .defineInRange("HeistCooldownTicks", 24000, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.BooleanValue PLAYER_BANKS_ENABLED = BUILDER
            .comment("Enable player-owned bank creation.")
            .define("PlayerBanksEnabled", true);

    public static final ForgeConfigSpec.BooleanValue PLAYER_BANKS_REQUIRE_ADMIN_APPROVAL = BUILDER
            .comment("Require admin approval before player bank creation.")
            .define("PlayerBanksRequireAdminApproval", false);

    public static final ForgeConfigSpec.IntValue PLAYER_BANKS_MIN_BALANCE = BUILDER
            .comment("Minimum balance required to apply for player-bank creation.")
            .defineInRange("PlayerBanksMinBalance", 5000, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue PLAYER_BANKS_MIN_PLAYTIME_HOURS = BUILDER
            .comment("Minimum playtime in hours required for player-bank creation.")
            .defineInRange("PlayerBanksMinPlaytimeHours", 0, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue PLAYER_BANKS_CREATION_FEE = BUILDER
            .comment("Creation fee deducted when a player bank is approved.")
            .defineInRange("PlayerBanksCreationFee", 2500, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue PLAYER_BANKS_MAX_BANKS_PER_PLAYER = BUILDER
            .comment("Maximum number of player banks one player may own.")
            .defineInRange("PlayerBanksMaxPerPlayer", 3, 1, 100);

    public static final ForgeConfigSpec.IntValue PLAYER_BANKS_NAME_MAX_LENGTH = BUILDER
            .comment("Maximum character length for player bank names.")
            .defineInRange("PlayerBanksNameMaxLength", 32, 3, 128);

    public static final ForgeConfigSpec.IntValue PLAYER_BANKS_CREATION_COOLDOWN_HOURS = BUILDER
            .comment("Cooldown in hours between player bank creation attempts.")
            .defineInRange("PlayerBanksCreationCooldownHours", 24, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue GLOBAL_MAX_SINGLE_TRANSACTION = BUILDER
            .comment("Global maximum amount for a single transaction.")
            .defineInRange("GlobalMaxSingleTransaction", 50000, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue GLOBAL_MAX_DAILY_PLAYER_VOLUME = BUILDER
            .comment("Global maximum outgoing transaction volume per player per day.")
            .defineInRange("GlobalMaxDailyPlayerVolume", 200000, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue GLOBAL_MAX_DAILY_BANK_VOLUME = BUILDER
            .comment("Global maximum outgoing transaction volume per bank per day.")
            .defineInRange("GlobalMaxDailyBankVolume", 1000000, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue SAFEBOX_SLOTS_CHECKING = BUILDER
            .comment("Safe deposit slot count for checking accounts.")
            .defineInRange("SafeBoxSlotsChecking", 9, 1, 54);

    public static final ForgeConfigSpec.IntValue SAFEBOX_SLOTS_SAVING = BUILDER
            .comment("Safe deposit slot count for saving accounts.")
            .defineInRange("SafeBoxSlotsSaving", 18, 1, 54);

    public static final ForgeConfigSpec.IntValue SAFEBOX_SLOTS_MONEY_MARKET = BUILDER
            .comment("Safe deposit slot count for money market accounts.")
            .defineInRange("SafeBoxSlotsMoneyMarket", 27, 1, 54);

    public static final ForgeConfigSpec.IntValue SAFEBOX_SLOTS_CERTIFICATE = BUILDER
            .comment("Safe deposit slot count for certificate accounts.")
            .defineInRange("SafeBoxSlotsCertificate", 9, 1, 54);

    public static final ForgeConfigSpec.IntValue CD_SHORT_TERM_TICKS = BUILDER
            .comment("Certificate short-term maturity in ticks.")
            .defineInRange("CDShortTermTicks", 72000, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue CD_MEDIUM_TERM_TICKS = BUILDER
            .comment("Certificate medium-term maturity in ticks.")
            .defineInRange("CDMediumTermTicks", 144000, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue CD_LONG_TERM_TICKS = BUILDER
            .comment("Certificate long-term maturity in ticks.")
            .defineInRange("CDLongTermTicks", 288000, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.DoubleValue CD_SHORT_RATE = BUILDER
            .comment("Certificate short-term APR.")
            .defineInRange("CDShortRate", 3.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue CD_MEDIUM_RATE = BUILDER
            .comment("Certificate medium-term APR.")
            .defineInRange("CDMediumRate", 4.5, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue CD_LONG_RATE = BUILDER
            .comment("Certificate long-term APR.")
            .defineInRange("CDLongRate", 6.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue CD_EARLY_PENALTY_FACTOR_SHORT = BUILDER
            .comment("Early withdrawal penalty factor for short CD tier.")
            .defineInRange("CDEarlyPenaltyFactorShort", 0.50, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue CD_EARLY_PENALTY_FACTOR_MEDIUM = BUILDER
            .comment("Early withdrawal penalty factor for medium CD tier.")
            .defineInRange("CDEarlyPenaltyFactorMedium", 0.50, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue CD_EARLY_PENALTY_FACTOR_LONG = BUILDER
            .comment("Early withdrawal penalty factor for long CD tier.")
            .defineInRange("CDEarlyPenaltyFactorLong", 0.50, 0.0, 1.0);


    public static final ForgeConfigSpec.DoubleValue MIN_CUSTOM_BANK_INTEREST_RATE = BUILDER
            .comment(
                    "Minimum allowed interest rate when custom bank interest rates are enabled.",
                    "Used to clamp/validate player-made bank rates.",
                    "Example: 0.50 means 50% of the base value; 1.00 means no change; 1.40 means +40%."
            )
            .defineInRange("MinCustomBankInterestRate", 0.50, 0.01, Double.MAX_VALUE);

    public static final ForgeConfigSpec.DoubleValue MAX_CUSTOM_BANK_INTEREST_RATE = BUILDER
            .comment(
                    "Maximum allowed interest rate when custom bank interest rates are enabled.",
                    "Used to clamp/validate player-made bank rates.",
                    "Example: 2.00 means 200% of the base value."
            )
            .defineInRange("MaxCustomBankInterestRate", 100.00, 0.01, Double.MAX_VALUE);

    // a list of strings that are treated as resource locations for items
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    public static final ForgeConfigSpec.ConfigValue<String> CURRENCY_SYMBOL = BUILDER
            .comment("Currency symbol used in textual output.")
            .define("CurrencySymbol", "$");

    public static final ForgeConfigSpec.ConfigValue<String> CURRENCY_NAME = BUILDER
            .comment("Currency display name used in textual output.")
            .define("CurrencyName", "Dollar");

    public static final ForgeConfigSpec.BooleanValue HUD_ENABLED_BY_DEFAULT = BUILDER
            .comment("Whether the balance HUD should be enabled by default for players.")
            .define("HudEnabledByDefault", true);

    public static final ForgeConfigSpec.ConfigValue<String> HUD_CORNER = BUILDER
            .comment("HUD anchor corner: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT.")
            .define("HudCorner", "TOP_RIGHT");

    public static final ForgeConfigSpec.IntValue HUD_TEXT_COLOR = BUILDER
            .comment("HUD text color as packed RGB integer (example: 0x55FF55).")
            .defineInRange("HudTextColor", 0x55FF55, 0x000000, 0xFFFFFF);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
