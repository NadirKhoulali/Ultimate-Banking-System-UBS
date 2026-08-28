package net.austizz.ultimatebankingsystem.api.placeholder;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/** Optional integrations for placeholder providers that may not be installed. */
public final class UbsPlaceholderIntegrations {
    private static final List<String> KEYS = List.of(
            "ubs_balance", "ubs_balance_raw", "ubs_total_balance", "ubs_total_balance_raw",
            "ubs_primary_balance", "ubs_primary_balance_raw", "ubs_primary_account_id",
            "ubs_primary_account_type", "ubs_primary_bank_id", "ubs_primary_bank_name",
            "ubs_account_count", "ubs_has_account", "ubs_has_primary_account",
            "ubs_cash_balance", "ubs_cash_balance_raw", "ubs_account_status",
            "ubs_owned_bank_count", "ubs_owns_bank", "ubs_owned_shop_count", "ubs_owns_shop",
            "ubs_server_bank_count", "ubs_server_shop_count"
    );

    private UbsPlaceholderIntegrations() {
    }

    public static void install() {
        UbsLeaderboardIntegrations.install();
        try {
            Class<?> facade = Class.forName("com.zerog.neoessentials.api.PlaceholderAPI");
            Method register = findRegistrationMethod(facade);
            if (register == null) {
                UltimateBankingSystem.LOGGER.warn("NeoEssentials detected, but its placeholder registration API is unavailable");
                return;
            }
            Class<?> providerType = register.getParameterTypes()[1];
            for (String key : KEYS) {
                Object provider = Proxy.newProxyInstance(
                        providerType.getClassLoader(), new Class<?>[]{providerType},
                        (proxy, method, args) -> {
                            if (!"onRequest".equals(method.getName())) {
                                return defaultValue(method.getReturnType());
                            }
                            ServerPlayer player = args != null && args.length > 0 && args[0] instanceof ServerPlayer
                                    ? (ServerPlayer) args[0] : null;
                            return player == null ? null : UltimateBankingApiProvider.get()
                                    .resolvePlaceholder(player.getUUID(), key);
                        });
                register.invoke(null, key, provider);
            }
            UltimateBankingSystem.LOGGER.info("Registered {} UBS live placeholders with NeoEssentials", KEYS.size());
        } catch (ClassNotFoundException ignored) {
            // NeoEssentials is an optional server-side integration.
        } catch (ReflectiveOperationException | RuntimeException exception) {
            UltimateBankingSystem.LOGGER.warn("Could not register UBS placeholders with NeoEssentials", exception);
        }
    }

    private static Method findRegistrationMethod(Class<?> facade) {
        for (Method method : facade.getMethods()) {
            if ("registerPlaceholder".equals(method.getName()) && method.getParameterCount() == 2
                    && method.getParameterTypes()[0] == String.class
                    && method.getParameterTypes()[1].isInterface()) {
                return method;
            }
        }
        return null;
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
