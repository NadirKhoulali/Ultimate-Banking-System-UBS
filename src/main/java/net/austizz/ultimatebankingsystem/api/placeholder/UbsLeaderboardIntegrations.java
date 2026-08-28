package net.austizz.ultimatebankingsystem.api.placeholder;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;
import net.austizz.ultimatebankingsystem.api.bank.ApiBankManagementSnapshot;
import net.austizz.ultimatebankingsystem.api.shop.ApiShopManagementSnapshot;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Optional bridge to NeoEssentials' named, non-player leaderboard API. */
public final class UbsLeaderboardIntegrations {
    private static final String API = "com.zerog.neoessentials.leaderboard.LeaderboardAPI";
    private static final String DEFINITION = "com.zerog.neoessentials.leaderboard.LeaderboardDefinition";
    private static final String PROVIDER = "com.zerog.neoessentials.leaderboard.NamedStatProvider";
    private static final String ENTRY = PROVIDER + "$NamedEntry";
    private static final String STAT_PROVIDER = "com.zerog.neoessentials.leaderboard.StatProvider";
    private static final List<String> BOARD_IDS = List.of("ubs_shop_revenue", "ubs_shop_level", "ubs_shop_momentum", "ubs_bank_deposits", "ubs_bank_customers", "ubs_bank_reserves");
    private static final List<String> PLAYER_BOARD_IDS = List.of("ubs_player_balance", "ubs_player_accounts", "ubs_player_businesses");
    private static final List<String> OLD_BOARD_IDS = List.of("ubs_shop_claims", "ubs_bank_accounts");

    private UbsLeaderboardIntegrations() {}

    public static void install() {
        try {
            Class<?> api = Class.forName(API);
            Class<?> definitionType = Class.forName(DEFINITION);
            Class<?> entryType = Class.forName(ENTRY);
            Constructor<?> entryConstructor = entryType.getDeclaredConstructor(String.class, Number.class);
            entryConstructor.setAccessible(true);
            Constructor<?> definitionConstructor = definitionType.getDeclaredConstructor(String.class, String.class, String.class, boolean.class, int.class, String.class, String.class, String.class);
            Class<?> statProviderType = Class.forName(STAT_PROVIDER);
            Method register = api.getMethod("registerBoard", definitionType, statProviderType);
            Method unregister = api.getMethod("unregisterBoard", String.class);
            for (String id : BOARD_IDS) unregister.invoke(null, id);
            for (String id : PLAYER_BOARD_IDS) unregister.invoke(null, id);
            for (String id : OLD_BOARD_IDS) unregister.invoke(null, id);

            int registered = 0;
            for (Board board : boards()) {
                try {
                    Object definition = definitionConstructor.newInstance(board.id(), board.title(), null, true, 60,
                            "{rankColor}{medal} #{rank} &f{name} &8- &a{value}",
                            "&b" + board.title() + " &7(Page {page}/{totalPages})", board.icon());
                    Class<?> providerType = board.kind() == Kind.PLAYER ? statProviderType : Class.forName(PROVIDER);
                    InvocationHandler handler = board.kind() == Kind.PLAYER
                            ? (proxy, method, args) -> switch (method.getName()) {
                                case "getAllValues" -> playerValues(serverArg(args), board);
                                case "formatValue" -> formatValue(args, board);
                                case "toString" -> "UBS " + board.id() + " provider";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == (args == null ? null : args[0]);
                                default -> defaultValue(method.getReturnType());
                            }
                            : (proxy, method, args) -> switch (method.getName()) {
                                case "getAllNamedValues" -> namedValues(serverArg(args), board, entryConstructor);
                                case "getAllValues" -> Map.of();
                                case "formatValue" -> formatValue(args, board);
                                case "toString" -> "UBS " + board.id() + " provider";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == (args == null ? null : args[0]);
                                default -> defaultValue(method.getReturnType());
                            };
                    Object provider = Proxy.newProxyInstance(providerType.getClassLoader(), new Class<?>[]{providerType}, handler);
                    register.invoke(null, definition, provider);
                    registered++;
                } catch (Throwable failure) {
                    UltimateBankingSystem.LOGGER.warn("Could not register UBS leaderboard {}.", board.id(), failure);
                }
            }
            UltimateBankingSystem.LOGGER.info("Registered {} named UBS leaderboards with NeoEssentials.", registered);
        } catch (ClassNotFoundException ignored) {
            // NeoEssentials is optional.
        } catch (Throwable failure) {
            UltimateBankingSystem.LOGGER.warn("Could not register UBS leaderboards with NeoEssentials.", failure);
        }
    }

    private static MinecraftServer serverArg(Object[] args) {
        return args != null && args.length > 0 && args[0] instanceof MinecraftServer server ? server : null;
    }

    public static void uninstall() {
        try {
            Method unregister = Class.forName(API).getMethod("unregisterBoard", String.class);
            for (String id : BOARD_IDS) unregister.invoke(null, id);
            for (String id : PLAYER_BOARD_IDS) unregister.invoke(null, id);
        } catch (ClassNotFoundException ignored) {
            // NeoEssentials is optional.
        } catch (Throwable failure) {
            UltimateBankingSystem.LOGGER.debug("Could not unregister UBS leaderboards.", failure);
        }
    }

    private static Map<String, Object> namedValues(MinecraftServer server, Board board, Constructor<?> entryConstructor) throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        if (server == null) return values;
        if (board.kind() == Kind.SHOP) {
            for (ApiShopManagementSnapshot shop : UltimateBankingApiProvider.shops().getShops()) {
                if (shop == null || shop.shopId() == null) continue;
                long value = switch (board.metric()) {
                    case REVENUE, MOMENTUM -> cents(shop.revenueDollars());
                    case LEVEL -> shop.level();
                    default -> 0L;
                };
                values.put(shop.shopId().toString(), entryConstructor.newInstance(displayName(shop.name()), value));
            }
        } else {
            for (ApiBankManagementSnapshot bank : UltimateBankingApiProvider.banks().getBanks()) {
                if (bank == null || bank.bankId() == null) continue;
                long value = switch (board.metric()) {
                    case DEPOSITS -> cents(bank.totalDeposits());
                    case CUSTOMERS -> bank.accountCount();
                    case RESERVES -> cents(bank.reserve());
                    default -> 0L;
                };
                values.put(bank.bankId().toString(), entryConstructor.newInstance(displayName(bank.name()), value));
            }
        }
        return values;
    }

    private static Map<UUID, Number> playerValues(MinecraftServer server, Board board) {
        Map<UUID, Number> values = new LinkedHashMap<>();
        if (board.metric() == Metric.WEALTH) {
            Map<UUID, BigDecimal> totals = new LinkedHashMap<>();
            for (Bank bank : allBanks(server)) {
                for (AccountHolder account : bank.getBankAccounts().values()) {
                    if (account == null || account.isInstitutional() || account.getPlayerUUID() == null) continue;
                    totals.merge(account.getPlayerUUID(), account.getBalance(), BigDecimal::add);
                }
            }
            totals.forEach((player, amount) -> values.put(player, cents(amount)));
        } else if (board.metric() == Metric.BANKING) {
            for (Bank bank : allBanks(server)) {
                for (AccountHolder account : bank.getBankAccounts().values()) {
                    if (account == null || account.isInstitutional() || account.getPlayerUUID() == null) continue;
                    values.merge(account.getPlayerUUID(), 1L, (left, right) -> left.longValue() + right.longValue());
                }
            }
        } else {
            for (ApiShopManagementSnapshot shop : UltimateBankingApiProvider.shops().getShops()) {
                if (shop.ownerId() != null) values.merge(shop.ownerId(), 1L, (left, right) -> left.longValue() + right.longValue());
            }
            for (ApiBankManagementSnapshot bank : UltimateBankingApiProvider.banks().getBanks()) {
                if (!bank.centralBank() && bank.ownerId() != null) values.merge(bank.ownerId(), 1L, (left, right) -> left.longValue() + right.longValue());
            }
        }
        return values;
    }

    private static List<Bank> allBanks(MinecraftServer server) {
        CentralBank centralBank = server == null ? null : BankManager.getCentralBank(server);
        if (centralBank == null) return List.of();
        List<Bank> banks = new java.util.ArrayList<>();
        banks.add(centralBank);
        centralBank.getBanks().forEach((id, bank) -> {
            if (bank != null && !centralBank.getBankId().equals(id)) banks.add(bank);
        });
        return banks;
    }

    private static List<Board> boards() {
        return List.of(
                new Board("ubs_player_balance", "Wealthiest Players", "minecraft:emerald", Kind.PLAYER, Metric.WEALTH),
                new Board("ubs_player_accounts", "Most Active Bankers", "minecraft:paper", Kind.PLAYER, Metric.BANKING),
                new Board("ubs_player_businesses", "Business Tycoons", "minecraft:gold_block", Kind.PLAYER, Metric.BUSINESS),
                new Board("ubs_shop_revenue", "Top Shops by Revenue", "minecraft:emerald", Kind.SHOP, Metric.REVENUE),
                new Board("ubs_shop_level", "Top Shops by Level", "minecraft:emerald", Kind.SHOP, Metric.LEVEL),
                new Board("ubs_shop_momentum", "Fastest Growing Shops", "minecraft:clock", Kind.SHOP, Metric.MOMENTUM),
                new Board("ubs_bank_deposits", "Banks by Deposits", "minecraft:gold_ingot", Kind.BANK, Metric.DEPOSITS),
                new Board("ubs_bank_customers", "Most Popular Banks", "minecraft:player_head", Kind.BANK, Metric.CUSTOMERS),
                new Board("ubs_bank_reserves", "Banks by Reserves", "minecraft:gold_block", Kind.BANK, Metric.RESERVES)
        );
    }

    private static Object formatValue(Object[] args, Board board) {
        if (args == null || args.length == 0 || !(args[0] instanceof Number number)) {
            return board.metric().isMoney() ? "$0.00" : "0";
        }
        long value = number.longValue();
        if (!board.metric().isMoney()) {
            return number.toString();
        }
        long dollars = value / 100L;
        long cents = Math.abs(value % 100L);
        return String.format("$%,d.%02d", dollars, cents);
    }

    private static long cents(long dollars) {
        return dollars <= 0 ? 0 : dollars > Long.MAX_VALUE / 100 ? Long.MAX_VALUE : dollars * 100;
    }

    private static long cents(BigDecimal dollars) {
        if (dollars == null || dollars.signum() <= 0) return 0L;
        try {
            return dollars.movePointRight(2).longValueExact();
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static String displayName(String name) {
        return name == null || name.isBlank() ? "Unnamed" : name.trim();
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

    private enum Kind { PLAYER, SHOP, BANK }
    private enum Metric {
        WEALTH, BANKING, BUSINESS, REVENUE, LEVEL, MOMENTUM, DEPOSITS, CUSTOMERS, RESERVES;

        private boolean isMoney() {
            return this == WEALTH || this == REVENUE || this == MOMENTUM
                    || this == DEPOSITS || this == RESERVES;
        }
    }
    private record Board(String id, String title, String icon, Kind kind, Metric metric) {}
}
