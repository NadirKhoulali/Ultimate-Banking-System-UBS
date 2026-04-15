package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.lang.reflect.Method;

final class ClientPayloadInvoker {
    private static final String HANDLERS_CLASS =
            "net.austizz.ultimatebankingsystem.network.ClientPayloadHandlers";

    private ClientPayloadInvoker() {
    }

    static void invoke(String methodName, Object payload) {
        if (FMLEnvironment.dist != Dist.CLIENT || payload == null) {
            return;
        }
        try {
            Class<?> handlers = Class.forName(HANDLERS_CLASS);
            Method method = handlers.getDeclaredMethod(methodName, payload.getClass());
            method.invoke(null, payload);
        } catch (ReflectiveOperationException ex) {
            UltimateBankingSystem.LOGGER.error("[UBS] Failed to invoke client payload handler {}", methodName, ex);
        }
    }
}
