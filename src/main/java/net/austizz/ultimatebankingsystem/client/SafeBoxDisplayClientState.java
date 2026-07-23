package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.network.SafeBoxDisplayContentsPayload;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SafeBoxDisplayClientState {
    private static final Map<UUID, List<ItemStack>> CONTENTS = new LinkedHashMap<>();

    private SafeBoxDisplayClientState() {
    }

    public static synchronized void apply(SafeBoxDisplayContentsPayload payload) {
        if (payload == null || payload.proxyId() == null) {
            return;
        }
        if (!payload.active()) {
            CONTENTS.remove(payload.proxyId());
            return;
        }
        CONTENTS.put(payload.proxyId(), payload.slots());
    }

    public static synchronized List<ItemStack> contents(UUID proxyId) {
        return proxyId == null ? List.of() : CONTENTS.getOrDefault(proxyId, List.of());
    }

    public static synchronized void clear() {
        CONTENTS.clear();
    }
}
