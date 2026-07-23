package net.austizz.ultimatebankingsystem.heist;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeistConcurrentProcessStateTest {
    private static final String DIMENSION = "minecraft:overworld";

    @Test
    void concurrentTargetsPersistAndMutateIndependently() throws Exception {
        Types types = types();
        Object session = newSession(types);
        Object thermal = stackTag(types, "thermal");
        Object safe = stackTag(types, "safe");
        Object vaultOne = pos(types, 10, 64, 10);
        Object vaultTwo = pos(types, 30, 64, 30);
        Object safeOne = pos(types, 12, 64, 12);
        Object safeTwo = pos(types, 14, 64, 14);
        Object pcOne = pos(types, 16, 64, 16);
        Object pcTwo = pos(types, 18, 64, 18);

        assertTrue(deployDrill(types, session, "deployDrill", thermal, vaultOne, 1_000L, 2));
        assertTrue(deployDrill(types, session, "deployDrill", thermal, vaultTwo, 1_100L, 1));
        assertTrue(deployDrill(types, session, "deploySafeDrill", safe, safeOne, 800L, 2));
        assertTrue(deployDrill(types, session, "deploySafeDrill", safe, safeTwo, 900L, 1));
        assertTrue((boolean) invoke(session, "beginHack",
                types.stringPosLongLong(), DIMENSION, pcOne, 600L, 0L));
        assertTrue((boolean) invoke(session, "beginHack",
                types.stringPosLongLong(), DIMENSION, pcTwo, 700L, 650L));

        Object vaultOneState = targetState(types, session, "vaultDrill", vaultOne);
        invoke(vaultOneState, "setJammed", new Class<?>[]{long.class}, 500L);
        Object safeOneState = targetState(types, session, "safeDrill", safeOne);
        invoke(safeOneState, "complete", new Class<?>[0]);
        Object pcOneState = targetState(types, session, "activeHack", pcOne);
        invoke(pcOneState, "pauseForRestart", new Class<?>[0]);

        Object restored = load(types, invoke(session, "save", new Class<?>[0]));
        assertEquals(2, ((List<?>) invoke(restored, "vaultDrills", new Class<?>[0])).size());
        assertEquals(2, ((List<?>) invoke(restored, "safeDrills", new Class<?>[0])).size());
        assertEquals(2, ((List<?>) invoke(restored, "activeHacks", new Class<?>[0])).size());
        assertEquals(500L, invoke(targetState(types, restored, "vaultDrill", vaultOne),
                "jammedAtTick", new Class<?>[0]));
        assertEquals(0L, invoke(targetState(types, restored, "vaultDrill", vaultTwo),
                "jammedAtTick", new Class<?>[0]));
        assertEquals(true, invoke(targetState(types, restored, "safeDrill", safeOne),
                "completed", new Class<?>[0]));
        assertEquals(false, invoke(targetState(types, restored, "safeDrill", safeTwo),
                "completed", new Class<?>[0]));
        assertEquals(Long.MAX_VALUE, invoke(targetState(types, restored, "activeHack", pcOne),
                "pausedUntilTick", new Class<?>[0]));
        assertEquals(650L, invoke(targetState(types, restored, "activeHack", pcTwo),
                "pausedUntilTick", new Class<?>[0]));

        invoke(restored, "completeHackWithoutTransfer", types.stringPos(), DIMENSION, pcOne);
        assertNull(targetStateOrNull(types, restored, "activeHack", pcOne));
        assertNotNull(targetState(types, restored, "activeHack", pcTwo));
        assertTrue((boolean) invoke(restored, "isComputerHacked", types.stringPos(), DIMENSION, pcOne));
        assertFalse((boolean) invoke(restored, "isComputerHacked", types.stringPos(), DIMENSION, pcTwo));
        invoke(restored, "removeDrill", types.stringPos(), DIMENSION, vaultOne);
        assertNull(targetStateOrNull(types, restored, "vaultDrill", vaultOne));
        assertNotNull(targetState(types, restored, "vaultDrill", vaultTwo));
    }

    @Test
    void legacySingleTargetFieldsMigrateIntoConcurrentCollections() throws Exception {
        Types types = types();
        Object session = newSession(types);
        Object tag = invoke(session, "save", new Class<?>[0]);
        UUID vaultOwner = UUID.randomUUID();
        UUID safeOwner = UUID.randomUUID();
        Object vault = pos(types, 40, 70, 40);
        Object safe = pos(types, 42, 70, 42);
        Object pc = pos(types, 44, 70, 44);
        Method asLong = types.pos().getMethod("asLong");

        invoke(tag, "remove", new Class<?>[]{String.class}, "vault_drills");
        invoke(tag, "remove", new Class<?>[]{String.class}, "safe_drills");
        invoke(tag, "remove", new Class<?>[]{String.class}, "active_hacks");
        put(types, tag, "drill_owner", vaultOwner);
        putTag(types, tag, "drill_stack", stackTag(types, "legacy-thermal"));
        put(types, tag, "drill_returned", false);
        put(types, tag, "drill_dimension", DIMENSION);
        put(types, tag, "drill_pos", asLong.invoke(vault));
        put(types, tag, "vault_pos", asLong.invoke(vault));
        put(types, tag, "drill_finish", 1_200L);
        put(types, tag, "drill_jams", 2);
        put(types, tag, "safe_drill_owner", safeOwner);
        putTag(types, tag, "safe_drill_stack", stackTag(types, "legacy-safe"));
        put(types, tag, "safe_drill_dimension", DIMENSION);
        put(types, tag, "safe_drill_pos", asLong.invoke(safe));
        put(types, tag, "safe_drill_finish", 900L);
        put(types, tag, "safe_drill_jams", 1);
        put(types, tag, "hack_dimension", DIMENSION);
        put(types, tag, "hack_pos", asLong.invoke(pc));
        put(types, tag, "hack_finish", 700L);
        put(types, tag, "hack_paused", Long.MAX_VALUE);

        Object restored = load(types, tag);
        Object vaultState = targetState(types, restored, "vaultDrill", vault);
        Object safeState = targetState(types, restored, "safeDrill", safe);
        assertEquals(vaultOwner, invoke(vaultState, "ownerId", new Class<?>[0]));
        assertEquals(1_200L, invoke(vaultState, "finishesTick", new Class<?>[0]));
        assertEquals(safeOwner, invoke(safeState, "ownerId", new Class<?>[0]));
        assertEquals(900L, invoke(safeState, "finishesTick", new Class<?>[0]));
        assertEquals(Long.MAX_VALUE, invoke(targetState(types, restored, "activeHack", pc),
                "pausedUntilTick", new Class<?>[0]));
        assertFalse((boolean) invoke(restored, "hasEscrowedDrill", new Class<?>[0]));
    }

    private static boolean deployDrill(Types types, Object session, String method, Object tag,
                                       Object pos, long finish, int jams) throws Exception {
        return (boolean) invoke(session, method,
                new Class<?>[]{UUID.class, types.tag(), String.class, types.pos(), long.class, int.class},
                UUID.randomUUID(), tag, DIMENSION, pos, finish, jams);
    }

    private static Object targetState(Types types, Object session, String method, Object pos) throws Exception {
        Object state = targetStateOrNull(types, session, method, pos);
        assertNotNull(state);
        return state;
    }

    private static Object targetStateOrNull(Types types, Object session, String method, Object pos) throws Exception {
        return invoke(session, method, types.stringPos(), DIMENSION, pos);
    }

    private static Object newSession(Types types) throws Exception {
        UUID leader = UUID.randomUUID();
        return types.session().getConstructor(UUID.class, UUID.class, String.class)
                .newInstance(UUID.randomUUID(), leader, "Leader");
    }

    private static Object load(Types types, Object tag) throws Exception {
        Object session = types.session().getMethod("load", types.tag()).invoke(null, tag);
        assertNotNull(session);
        return session;
    }

    private static Object pos(Types types, int x, int y, int z) throws Exception {
        return types.pos().getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
    }

    private static Object stackTag(Types types, String marker) throws Exception {
        Object tag = types.tag().getConstructor().newInstance();
        put(types, tag, "marker", marker);
        return tag;
    }

    private static void putTag(Types types, Object target, String key, Object value) throws Exception {
        target.getClass().getMethod("put", String.class, types.baseTag()).invoke(target, key, value);
    }

    private static void put(Types types, Object target, String key, Object value) throws Exception {
        Class<?> valueType = value instanceof UUID ? UUID.class
                : value instanceof Boolean ? boolean.class
                : value instanceof Integer ? int.class
                : value instanceof Long ? long.class : String.class;
        String suffix = value instanceof UUID ? "UUID"
                : value instanceof Boolean ? "Boolean"
                : value instanceof Integer ? "Int"
                : value instanceof Long ? "Long" : "String";
        target.getClass().getMethod("put" + suffix, String.class, valueType).invoke(target, key, value);
    }

    private static Object invoke(Object target, String method, Class<?>[] types, Object... args) throws Exception {
        return target.getClass().getMethod(method, types).invoke(target, args);
    }

    private static Types types() throws Exception {
        ClassLoader loader = NeoForgeTestClassLoader.get();
        return new Types(
                Class.forName("net.austizz.ultimatebankingsystem.heist.HeistSession", true, loader),
                Class.forName("net.minecraft.core.BlockPos", true, loader),
                Class.forName("net.minecraft.nbt.CompoundTag", true, loader),
                Class.forName("net.minecraft.nbt.Tag", true, loader));
    }

    private record Types(Class<?> session, Class<?> pos, Class<?> tag, Class<?> baseTag) {
        Class<?>[] stringPos() { return new Class<?>[]{String.class, pos}; }
        Class<?>[] stringPosLongLong() { return new Class<?>[]{String.class, pos, long.class, long.class}; }
    }
}
