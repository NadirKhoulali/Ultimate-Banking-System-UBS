package net.austizz.ultimatebankingsystem.bank.safebox;

import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;

final class SafetyDepositBoxOpenAuthorityGameTestClock {
    private static final Field TICK_COUNT = tickCountField();

    private SafetyDepositBoxOpenAuthorityGameTestClock() {
    }

    static void advanceTo(MinecraftServer server, long tick) {
        if (tick < server.getTickCount() || tick > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("server tick must advance within integer range");
        }
        try {
            TICK_COUNT.setInt(server, (int) tick);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to advance the GameTest server clock", exception);
        }
    }

    private static Field tickCountField() {
        try {
            Field field = MinecraftServer.class.getDeclaredField("tickCount");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
