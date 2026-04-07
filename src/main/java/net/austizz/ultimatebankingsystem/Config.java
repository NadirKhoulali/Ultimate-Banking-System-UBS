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

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
