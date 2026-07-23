package net.austizz.ultimatebankingsystem.hud;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudPreferencesSavedDataTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void persistsNormalizedPlayerPosition() throws Exception {
        Class<?> dataType = load("net.austizz.ultimatebankingsystem.hud.HudPreferencesSavedData");
        Class<?> tagType = load("net.minecraft.nbt.CompoundTag");
        Class<?> registriesType = load("net.minecraft.core.HolderLookup$Provider");
        UUID playerId = UUID.randomUUID();
        Object data = dataType.getConstructor().newInstance();

        dataType.getMethod("setPosition", UUID.class, String.class)
                .invoke(data, playerId, "MIDDLE_LEFT");
        Object tag = tagType.getConstructor().newInstance();
        Object saved = dataType.getMethod("save", tagType, registriesType)
                .invoke(data, tag, null);
        Method load = dataType.getDeclaredMethod("load", tagType, registriesType);
        load.setAccessible(true);
        Object restored = load.invoke(null, saved, null);

        assertEquals("middle-left", dataType.getMethod("position", UUID.class).invoke(restored, playerId));
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, true, LOADER);
    }
}
