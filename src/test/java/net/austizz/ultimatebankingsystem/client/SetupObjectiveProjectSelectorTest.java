package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupObjectiveProjectSelectorTest {
    private static final String STATE_NAME =
            "net.austizz.ultimatebankingsystem.client.ShopSetupObjectiveClientState";
    private static final String BANK_PROJECT_NAME =
            "net.austizz.ultimatebankingsystem.network.BankSetupObjectivesPayload$Project";

    @AfterEach
    void clearState() throws Exception {
        invoke("clear", new Class<?>[0]);
    }

    @Test
    void cyclesBetweenShopAndBankWithoutDroppingEitherProject() throws Exception {
        invoke("set",
                new Class<?>[]{boolean.class, String.class, int.class, int.class,
                        String.class, String.class, List.class},
                true, "Corner Store", 2, 9, "Add stockroom", "Claim storage", List.of());
        invoke("replaceBankProjects", new Class<?>[]{List.class},
                List.of(bankProject("bank-a", "Central Bank", 3, 6, "Install door", "Place a vault door")));

        assertEquals(2, integer("getProjectCount"));
        assertEquals("Store", string("getProjectType"));
        assertEquals("Corner Store", string("getShopName"));

        invoke("cycleProject", new Class<?>[0]);

        assertEquals("Bank", string("getProjectType"));
        assertEquals("Central Bank", string("getShopName"));
        assertEquals(3, integer("getStep"));
        assertTrue(bool("isActive"));
    }

    @Test
    void replacingBankProjectsPreservesShopAndRemovesCompletedBank() throws Exception {
        invoke("set",
                new Class<?>[]{boolean.class, String.class, int.class, int.class,
                        String.class, String.class, List.class},
                true, "Corner Store", 1, 9, "Claim plot", "Select a plot", List.of());
        invoke("replaceBankProjects", new Class<?>[]{List.class},
                List.of(bankProject("bank-a", "Central Bank", 1, 6, "Claim premises", "Select the bank")));
        invoke("replaceBankProjects", new Class<?>[]{List.class}, List.of());

        assertEquals(1, integer("getProjectCount"));
        assertEquals("Store", string("getProjectType"));
        assertTrue(bool("isActive"));

        invoke("clearShopProject", new Class<?>[0]);
        assertFalse(bool("isActive"));
    }

    private static Object bankProject(String id,
                                      String name,
                                      int step,
                                      int total,
                                      String title,
                                      String detail) throws Exception {
        Class<?> projectClass = NeoForgeTestClassLoader.get().loadClass(BANK_PROJECT_NAME);
        Constructor<?> constructor = projectClass.getDeclaredConstructor(
                String.class, String.class, int.class, int.class, String.class, String.class);
        return constructor.newInstance(id, name, step, total, title, detail);
    }

    private static boolean bool(String method) throws Exception {
        return (boolean) invoke(method, new Class<?>[0]);
    }

    private static int integer(String method) throws Exception {
        return (int) invoke(method, new Class<?>[0]);
    }

    private static String string(String method) throws Exception {
        return (String) invoke(method, new Class<?>[0]);
    }

    private static Object invoke(String method, Class<?>[] parameterTypes, Object... args) throws Exception {
        Class<?> stateClass = NeoForgeTestClassLoader.get().loadClass(STATE_NAME);
        Method target = stateClass.getDeclaredMethod(method, parameterTypes);
        return target.invoke(null, args);
    }
}
