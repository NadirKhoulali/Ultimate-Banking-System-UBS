package net.austizz.ultimatebankingsystem;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();



    public static final ModConfigSpec.IntValue TRANSACTIONS_PER_MINUTE = BUILDER
            .comment("The Amount of transactions possible per player per minute")
            .defineInRange("TransactionsPerMinute", 10, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue DEFAULT_ATM_WITHDRAWAL_LIMIT = BUILDER
            .comment("Default maximum amount (in whole dollars) a player can withdraw from an ATM per transaction.")
            .defineInRange("DefaultATMWithdrawalLimit", 500, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue DAILY_WITHDRAWAL_LIMIT = BUILDER
            .comment("Default maximum amount (in whole dollars) a player can withdraw per Minecraft day.")
            .defineInRange("DailyWithdrawalLimit", 2000, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue DAILY_WITHDRAWAL_LIMIT_CHECKING = BUILDER
            .comment("Maximum ATM withdrawal amount per real-world day for Checking accounts.")
            .defineInRange("DailyWithdrawalLimitChecking", 2000, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue DAILY_WITHDRAWAL_LIMIT_SAVING = BUILDER
            .comment("Maximum ATM withdrawal amount per real-world day for Saving accounts.")
            .defineInRange("DailyWithdrawalLimitSaving", 1500, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue DAILY_WITHDRAWAL_LIMIT_MONEY_MARKET = BUILDER
            .comment("Maximum ATM withdrawal amount per real-world day for Money Market accounts.")
            .defineInRange("DailyWithdrawalLimitMoneyMarket", 3000, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue DAILY_WITHDRAWAL_LIMIT_CERTIFICATE = BUILDER
            .comment("Maximum ATM withdrawal amount per real-world day for Certificate accounts.")
            .defineInRange("DailyWithdrawalLimitCertificate", 500, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue AUTOSAVE_INTERVAL_MINUTES = BUILDER
            .comment("How often banking data is marked dirty for autosave (in real minutes).")
            .defineInRange("AutoSaveIntervalMinutes", 5, 1, 120);

    public static final ModConfigSpec.IntValue SAVINGS_INTEREST_INTERVAL_TICKS = BUILDER
            .comment("How often savings interest payout runs (in server ticks). 24000 ticks = one Minecraft day.")
            .defineInRange("SavingsInterestIntervalTicks", 24000, 20, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue LOAN_AUTO_APPROVE_THRESHOLD = BUILDER
            .comment("Maximum principal amount that can be auto-approved for a player loan.")
            .defineInRange("LoanAutoApproveThreshold", 5000, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue LOAN_AUTO_APPROVE_MIN_CREDIT = BUILDER
            .comment("Minimum credit score required for auto-approval.")
            .defineInRange("LoanAutoApproveMinCredit", 550, 0, 2000);

    public static final ModConfigSpec.DoubleValue LOAN_BASE_INTEREST_RATE = BUILDER
            .comment("Base annual loan interest rate used before credit-score adjustment.")
            .defineInRange("LoanBaseInterestRate", 8.0, 0.01, 1000.0);

    public static final ModConfigSpec.DoubleValue LOAN_MIN_INTEREST_RATE = BUILDER
            .comment("Minimum annual loan interest rate after credit-score adjustment.")
            .defineInRange("LoanMinInterestRate", 2.0, 0.01, 1000.0);

    public static final ModConfigSpec.DoubleValue LOAN_MAX_INTEREST_RATE = BUILDER
            .comment("Maximum annual loan interest rate after credit-score adjustment.")
            .defineInRange("LoanMaxInterestRate", 25.0, 0.01, 1000.0);

    public static final ModConfigSpec.IntValue LOAN_TERM_PAYMENTS = BUILDER
            .comment("Default number of scheduled repayments per loan.")
            .defineInRange("LoanTermPayments", 7, 1, 512);

    public static final ModConfigSpec.IntValue LOAN_PAYMENT_INTERVAL_TICKS = BUILDER
            .comment("Ticks between loan repayments (24000 = one Minecraft day).")
            .defineInRange("LoanPaymentIntervalTicks", 24000, 20, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue LOAN_WARNING_BEFORE_DUE_TICKS = BUILDER
            .comment("How many ticks before due date a borrower receives a warning.")
            .defineInRange("LoanWarningBeforeDueTicks", 24000, 20, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CREDIT_SCORE_DEFAULT = BUILDER
            .comment("Default credit score assigned to newly created accounts.")
            .defineInRange("CreditScoreDefault", 500, 0, 2000);

    public static final ModConfigSpec.IntValue CREDIT_SCORE_ON_TIME_BOOST = BUILDER
            .comment("Credit score increase applied after each on-time repayment.")
            .defineInRange("CreditScoreOnTimeBoost", 5, 0, 200);

    public static final ModConfigSpec.IntValue CREDIT_SCORE_MISSED_PENALTY = BUILDER
            .comment("Credit score decrease applied when a repayment is missed.")
            .defineInRange("CreditScoreMissedPenalty", 20, 0, 2000);

    public static final ModConfigSpec.IntValue CREDIT_SCORE_DEFAULT_PENALTY = BUILDER
            .comment("Additional credit score decrease applied when a loan defaults.")
            .defineInRange("CreditScoreDefaultPenalty", 40, 0, 2000);

    public static final ModConfigSpec.BooleanValue ALLOW_BANK_CUSTOM_INTEREST_RATE = BUILDER
            .comment("Allow player made banks to set their own interest rate")
            .define("AllowBankCustomInterestRate", true);

    public static final ModConfigSpec.DoubleValue DEFAULT_SERVER_INTEREST_RATE = BUILDER
            .comment("The default server interest rate across all banks")
            .defineInRange("ServerInterestRate", 1.4, 0.01, Double.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue DEFAULT_FEDERAL_FUNDS_RATE = BUILDER
            .comment(
                    "The interest rate at which depository institutions (banks) lend reserve balances to other depository institutions overnight on an unsecured basis.",
                    "Higher Rates: Increase the cost of borrowing for mortgages, auto loans, and credit cards.",
                    "Lower Rates: Encourage borrowing and spending, but can lower interest earned on savings accounts.")
            .defineInRange("FederalFundsRate", 3.5, 0.00, Double.MAX_VALUE);


    public static final ModConfigSpec.DoubleValue MIN_CUSTOM_BANK_INTEREST_RATE = BUILDER
            .comment(
                    "Minimum allowed interest rate when custom bank interest rates are enabled.",
                    "Used to clamp/validate player-made bank rates.",
                    "Example: 0.50 means 50% of the base value; 1.00 means no change; 1.40 means +40%."
            )
            .defineInRange("MinCustomBankInterestRate", 0.50, 0.01, Double.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue MAX_CUSTOM_BANK_INTEREST_RATE = BUILDER
            .comment(
                    "Maximum allowed interest rate when custom bank interest rates are enabled.",
                    "Used to clamp/validate player-made bank rates.",
                    "Example: 2.00 means 200% of the base value."
            )
            .defineInRange("MaxCustomBankInterestRate", 100.00, 0.01, Double.MAX_VALUE);

    // a list of strings that are treated as resource locations for items
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    public static final ModConfigSpec.ConfigValue<String> CURRENCY_SYMBOL = BUILDER
            .comment("Currency symbol used in textual output.")
            .define("CurrencySymbol", "$");

    public static final ModConfigSpec.ConfigValue<String> CURRENCY_NAME = BUILDER
            .comment("Currency display name used in textual output.")
            .define("CurrencyName", "Dollar");

    public static final ModConfigSpec.BooleanValue HUD_ENABLED_BY_DEFAULT = BUILDER
            .comment("Whether the balance HUD should be enabled by default for players.")
            .define("HudEnabledByDefault", true);

    public static final ModConfigSpec.ConfigValue<String> HUD_CORNER = BUILDER
            .comment("HUD anchor corner: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT.")
            .define("HudCorner", "TOP_RIGHT");

    public static final ModConfigSpec.IntValue HUD_TEXT_COLOR = BUILDER
            .comment("HUD text color as packed RGB integer (example: 0x55FF55).")
            .defineInRange("HudTextColor", 0x55FF55, 0x000000, 0xFFFFFF);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
