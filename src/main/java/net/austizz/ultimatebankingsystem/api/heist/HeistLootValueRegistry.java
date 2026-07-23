package net.austizz.ultimatebankingsystem.api.heist;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class HeistLootValueRegistry {
    private static final CopyOnWriteArrayList<HeistLootValueProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private HeistLootValueRegistry() {}

    public static void register(HeistLootValueProvider provider) {
        if (provider != null) PROVIDERS.addIfAbsent(provider);
    }

    public static boolean unregister(HeistLootValueProvider provider) {
        return provider != null && PROVIDERS.remove(provider);
    }

    public static List<HeistLootValueProvider> providers() {
        return List.copyOf(PROVIDERS);
    }
}
