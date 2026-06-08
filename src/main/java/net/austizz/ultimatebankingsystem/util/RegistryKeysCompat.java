package net.austizz.ultimatebankingsystem.util;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class RegistryKeysCompat {
    // Resolve registry keys reflectively so the mod works on obfuscated Forge runtime jars
    // even when direct mapped method/field links differ.
    public static final ResourceKey<Registry<Level>> DIMENSION_REGISTRY_KEY =
            createRegistryKeyCompat(new ResourceLocation("minecraft", "dimension"));

    // Same compatibility path for creative tab registry key.
    public static final ResourceKey<Registry<CreativeModeTab>> CREATIVE_MODE_TAB_REGISTRY_KEY =
            createRegistryKeyCompat(new ResourceLocation("minecraft", "creative_mode_tab"));

    private RegistryKeysCompat() {
    }

    @SuppressWarnings("unchecked")
    private static <T> ResourceKey<Registry<T>> createRegistryKeyCompat(ResourceLocation location) {
        try {
            Method m = findResourceKeyFactory(1);
            return (ResourceKey<Registry<T>>) m.invoke(null, location);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to resolve registry key factory method for " + location, ex);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> ResourceKey<T> createValueKey(ResourceKey<? extends Registry<T>> registryKey, ResourceLocation id) {
        try {
            Method m = findResourceKeyFactory(2);
            return (ResourceKey<T>) m.invoke(null, registryKey, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to resolve value key factory method for " + id, ex);
        }
    }

    private static Method findResourceKeyFactory(int parameterCount) throws NoSuchMethodException {
        for (Method m : ResourceKey.class.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            if (!ResourceKey.class.isAssignableFrom(m.getReturnType())) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (parameterCount == 1) {
                if (params.length == 1 && params[0] == ResourceLocation.class) {
                    m.setAccessible(true);
                    return m;
                }
                continue;
            }
            if (parameterCount == 2) {
                if (params.length == 2 && params[0] == ResourceKey.class && params[1] == ResourceLocation.class) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new NoSuchMethodException("No compatible ResourceKey factory method found (arity=" + parameterCount + ")");
    }
}
