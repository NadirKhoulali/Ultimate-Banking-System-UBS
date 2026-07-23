package net.austizz.ultimatebankingsystem.api.heist;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class HeistDoorAdapterRegistry {
    private static final CopyOnWriteArrayList<HeistDoorAdapter> ADAPTERS = new CopyOnWriteArrayList<>();

    private HeistDoorAdapterRegistry() {}

    public static void register(HeistDoorAdapter adapter) {
        if (adapter != null && !ADAPTERS.contains(adapter)) ADAPTERS.add(adapter);
    }

    public static void unregister(HeistDoorAdapter adapter) { ADAPTERS.remove(adapter); }
    public static List<HeistDoorAdapter> adapters() { return List.copyOf(ADAPTERS); }

    public static boolean matches(BlockState state) {
        for (HeistDoorAdapter adapter : ADAPTERS) {
            try { if (adapter.matches(state)) return true; } catch (RuntimeException ignored) {}
        }
        return false;
    }

    public static boolean setBreached(ServerLevel level, BlockPos pos, BlockState state, boolean breached) {
        for (HeistDoorAdapter adapter : ADAPTERS) {
            try {
                if (adapter.matches(state) && adapter.setBreached(level, pos, state, breached)) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }
}
