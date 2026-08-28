package net.austizz.ultimatebankingsystem.api.placeholder;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;
import net.austizz.ultimatebankingsystem.api.bank.ApiBankManagementSnapshot;
import net.austizz.ultimatebankingsystem.api.shop.ApiShopManagementSnapshot;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Optional NeoEssentials leaderboard registrations backed by live UBS data. */
public final class UbsLeaderboardIntegrations {
    private record Board(String id, String title, boolean higherIsBetter,
                         Function<MinecraftServer, Map<UUID, Number>> values,
                         Function<Number, String> formatter) {}

    private UbsLeaderboardIntegrations() {
    }

    public static void install() {
        try {
            Class<?> api = Class.forName("com.zerog.neoessentials.leaderboard.LeaderboardAPI");
            Class<?> definitionType = Class.forName("com.zerog.neoessentials.leaderboard.LeaderboardDefinition");
            Class<?> providerType = Class.forName("com.zerog.neoessentials.leaderboard.StatProvider");
            Constructor<?> definitionConstructor = definitionType.getConstructor(
                    String.class, String.class, String.class, boolean.class);
            Method register = api.getMethod("registerBoard", definitionType, providerType);

            UltimateBankingSystem.LOGGER.info("NeoEssentials detected; registering UBS leaderboard boards");

            for (Board board : boards()) {
                Object definition = definitionConstructor.newInstance(
                        // Do not assign an exemption permission here. NeoEssentials treats
                        // permission-bearing players, including operators, as excluded from
                        // the board. UBS boards must show operators normally; server owners
                        // can hide a player through NeoEssentials permissions if desired.
                        board.id(), board.title(), null, board.higherIsBetter());
                Object provider = Proxy.newProxyInstance(
                        providerType.getClassLoader(), new Class<?>[]{providerType},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "getAllValues" -> board.values().apply(
                                    args != null && args.length > 0 && args[0] instanceof MinecraftServer server
                                            ? server : null);
                            case "formatValue" -> board.formatter().apply(
                                    args != null && args.length > 0 && args[0] instanceof Number number
                                            ? number : 0L);
                            default -> defaultValue(method.getReturnType());
                        });
                register.invoke(null, definition, provider);
            }
            UltimateBankingSystem.LOGGER.info("Registered {} UBS leaderboards with NeoEssentials", boards().size());
        } catch (ClassNotFoundException ignored) {
            // NeoEssentials is an optional server-side integration.
        } catch (ReflectiveOperationException | RuntimeException exception) {
            UltimateBankingSystem.LOGGER.warn("Could not register UBS leaderboards with NeoEssentials", exception);
        }
    }

    private static List<Board> boards() {
        return List.of(
                new Board("ubs_player_balance", "UBS Player Wealth", true,
                        UbsLeaderboardIntegrations::playerBalances, UbsLeaderboardIntegrations::formatCents),
                new Board("ubs_player_accounts", "UBS Most Banked Players", true,
                        UbsLeaderboardIntegrations::playerAccountCounts, UbsLeaderboardIntegrations::formatCount),
                new Board("ubs_player_businesses", "UBS Business Builders", true,
                        UbsLeaderboardIntegrations::playerBusinessCounts, UbsLeaderboardIntegrations::formatCount),
                new Board("ubs_shop_revenue", "UBS Top Shop Operators by Revenue", true,
                        UbsLeaderboardIntegrations::shopRevenue, UbsLeaderboardIntegrations::formatCents),
                new Board("ubs_shop_level", "UBS Top Shop Operators by Level", true,
                        UbsLeaderboardIntegrations::shopLevels, UbsLeaderboardIntegrations::formatCount),
                new Board("ubs_shop_claims", "UBS Largest Shops by Claimed Areas", true,
                        UbsLeaderboardIntegrations::shopClaims, UbsLeaderboardIntegrations::formatCount),
                new Board("ubs_bank_deposits", "UBS Banks by Customer Deposits", true,
                        UbsLeaderboardIntegrations::bankDeposits, UbsLeaderboardIntegrations::formatCents),
                new Board("ubs_bank_accounts", "UBS Banks by Customer Accounts", true,
                        UbsLeaderboardIntegrations::bankAccounts, UbsLeaderboardIntegrations::formatCount),
                new Board("ubs_bank_reserves", "UBS Banks by Declared Reserves", true,
                        UbsLeaderboardIntegrations::bankReserves, UbsLeaderboardIntegrations::formatCents)
        );
    }

    private static Map<UUID, Number> playerBalances(MinecraftServer server) {
        Map<UUID, BigDecimal> totals = new LinkedHashMap<>();
        for (Bank bank : allBanks(server)) {
            for (AccountHolder account : bank.getBankAccounts().values()) {
                if (account == null || account.isInstitutional() || account.getPlayerUUID() == null) {
                    continue;
                }
                totals.merge(account.getPlayerUUID(), nonNegative(account.getBalance()), BigDecimal::add);
            }
        }
        Map<UUID, Number> result = new LinkedHashMap<>();
        totals.forEach((player, amount) -> result.put(player, cents(amount)));
        return result;
    }

    private static Map<UUID, Number> playerAccountCounts(MinecraftServer server) {
        Map<UUID, Number> result = new LinkedHashMap<>();
        for (Bank bank : allBanks(server)) {
            for (AccountHolder account : bank.getBankAccounts().values()) {
                if (account == null || account.isInstitutional() || account.getPlayerUUID() == null) {
                    continue;
                }
                result.merge(account.getPlayerUUID(), 1L, (left, right) -> left.longValue() + right.longValue());
            }
        }
        return result;
    }

    private static Map<UUID, Number> playerBusinessCounts(MinecraftServer server) {
        Map<UUID, Number> result = new LinkedHashMap<>();
        for (ApiShopManagementSnapshot shop : UltimateBankingApiProvider.shops().getShops()) {
            if (shop.ownerId() != null) result.merge(shop.ownerId(), 1L, UbsLeaderboardIntegrations::sum);
        }
        for (ApiBankManagementSnapshot bank : UltimateBankingApiProvider.banks().getBanks()) {
            if (!bank.centralBank() && bank.ownerId() != null) result.merge(bank.ownerId(), 1L, UbsLeaderboardIntegrations::sum);
        }
        return result;
    }

    private static Map<UUID, Number> shopRevenue(MinecraftServer server) {
        Map<UUID, Number> result = new LinkedHashMap<>();
        for (ApiShopManagementSnapshot shop : UltimateBankingApiProvider.shops().getShops()) {
            if (shop.ownerId() == null) continue;
            result.merge(shop.ownerId(), cents(BigDecimal.valueOf(shop.revenueDollars())), UbsLeaderboardIntegrations::max);
        }
        return result;
    }

    private static Map<UUID, Number> shopLevels(MinecraftServer server) {
        Map<UUID, Number> result = new LinkedHashMap<>();
        for (ApiShopManagementSnapshot shop : UltimateBankingApiProvider.shops().getShops()) {
            if (shop.ownerId() == null) continue;
            result.merge(shop.ownerId(), (long) shop.level(), UbsLeaderboardIntegrations::max);
        }
        return result;
    }

    private static Map<UUID, Number> shopClaims(MinecraftServer server) {
        Map<UUID, Number> result = new LinkedHashMap<>();
        for (ApiShopManagementSnapshot shop : UltimateBankingApiProvider.shops().getShops()) {
            if (shop.ownerId() == null) continue;
            result.merge(shop.ownerId(), (long) shop.claimRegions(), UbsLeaderboardIntegrations::sum);
        }
        return result;
    }

    private static Map<UUID, Number> bankDeposits(MinecraftServer server) {
        Map<UUID, Number> result = new LinkedHashMap<>();
        for (ApiBankManagementSnapshot bank : UltimateBankingApiProvider.banks().getBanks()) {
            if (bank.centralBank() || bank.ownerId() == null) continue;
            result.merge(bank.ownerId(), cents(bank.totalDeposits()), UbsLeaderboardIntegrations::sum);
        }
        return result;
    }

    private static Map<UUID, Number> bankAccounts(MinecraftServer server) {
        Map<UUID, Number> result = new LinkedHashMap<>();
        for (ApiBankManagementSnapshot bank : UltimateBankingApiProvider.banks().getBanks()) {
            if (bank.centralBank() || bank.ownerId() == null) continue;
            result.merge(bank.ownerId(), (long) bank.accountCount(), UbsLeaderboardIntegrations::sum);
        }
        return result;
    }

    private static Map<UUID, Number> bankReserves(MinecraftServer server) {
        Map<UUID, Number> result = new LinkedHashMap<>();
        for (ApiBankManagementSnapshot bank : UltimateBankingApiProvider.banks().getBanks()) {
            if (bank.centralBank() || bank.ownerId() == null) continue;
            result.merge(bank.ownerId(), cents(bank.reserve()), UbsLeaderboardIntegrations::sum);
        }
        return result;
    }

    private static List<Bank> allBanks(MinecraftServer server) {
        CentralBank centralBank = server == null ? null : BankManager.getCentralBank(server);
        if (centralBank == null) {
            return List.of();
        }

        // Central Bank owns the player accounts created through /account open. The
        // child-bank map contains only player-created banks, so omitting the central
        // bank makes every player leaderboard empty on a fresh server.
        List<Bank> banks = new java.util.ArrayList<>();
        banks.add(centralBank);
        // CentralBank also keeps itself in this map. Do not collect that same bank
        // twice, otherwise every central-bank account is reflected twice on player
        // balance and account-count leaderboards.
        centralBank.getBanks().forEach((bankId, bank) -> {
            if (bank != null && !centralBank.getBankId().equals(bankId)) {
                banks.add(bank);
            }
        });
        return List.copyOf(banks);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    private static long cents(BigDecimal amount) {
        return nonNegative(amount).movePointRight(2).longValue();
    }

    private static Number sum(Number left, Number right) {
        return left.longValue() + right.longValue();
    }

    private static Number max(Number left, Number right) {
        return Math.max(left.longValue(), right.longValue());
    }

    private static String formatCents(Number value) {
        return String.format("$%,d.%02d", value.longValue() / 100L, Math.abs(value.longValue() % 100L));
    }

    private static String formatCount(Number value) {
        return String.format("%,d", value.longValue());
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }
}
