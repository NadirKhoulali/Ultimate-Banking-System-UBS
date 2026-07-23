package net.austizz.ultimatebankingsystem.heist;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class HeistDeferredExitStateTest {
    @Test
    void deferredCrewExitSurvivesSaveAndIsConsumedOnce() throws Exception {
        ClassLoader loader = NeoForgeTestClassLoader.get();
        Class<?> savedDataType = Class.forName(
                "net.austizz.ultimatebankingsystem.heist.HeistSavedData", true, loader);
        Class<?> exitType = Class.forName(
                "net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot", true, loader);
        Class<?> compoundTagType = Class.forName("net.minecraft.nbt.CompoundTag", true, loader);
        Class<?> registriesType = Class.forName("net.minecraft.core.HolderLookup$Provider", true, loader);

        Object data = savedDataType.getConstructor().newInstance();
        UUID playerId = UUID.randomUUID();
        Object exit = exitType.getConstructor(String.class, int.class, int.class, int.class, float.class)
                .newInstance("minecraft:the_nether", 14, 72, -9, 135.0F);
        savedDataType.getMethod("deferExit", UUID.class, exitType).invoke(data, playerId, exit);

        Object tag = compoundTagType.getConstructor().newInstance();
        savedDataType.getMethod("save", compoundTagType, registriesType).invoke(data, tag, null);
        Object restored = savedDataType.getMethod("load", compoundTagType, registriesType)
                .invoke(null, tag, null);

        Method take = savedDataType.getMethod("takeDeferredExit", UUID.class);
        Object restoredExit = take.invoke(restored, playerId);
        assertNotNull(restoredExit);
        assertEquals("minecraft:the_nether", exitType.getMethod("dimension").invoke(restoredExit));
        assertEquals(14, exitType.getMethod("x").invoke(restoredExit));
        assertEquals(72, exitType.getMethod("y").invoke(restoredExit));
        assertEquals(-9, exitType.getMethod("z").invoke(restoredExit));
        assertEquals(135.0F, exitType.getMethod("yaw").invoke(restoredExit));
        assertNull(take.invoke(restored, playerId));
    }
}
