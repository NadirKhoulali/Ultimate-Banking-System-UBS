package net.austizz.ultimatebankingsystem.command;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NumismaticsMigrationCommandTreeTest {
    @Test
    void importTreeExposesNumismaticsWizardAndUtilities() throws Exception {
        Class<?> commands = Class.forName(
                "net.austizz.ultimatebankingsystem.command.UBSAdminCommands",
                true, NeoForgeTestClassLoader.get());
        Method factory = commands.getDeclaredMethod("buildUbsRoot");
        factory.setAccessible(true);
        Object builder = factory.invoke(null);
        Object root = builder.getClass().getMethod("build").invoke(builder);

        Object numismatics = child(child(child(root, "admin"), "import"), "numismatics");
        assertEquals(Set.of("open", "status", "world", "file", "report"),
                ((java.util.Collection<?>) numismatics.getClass().getMethod("getChildren").invoke(numismatics))
                        .stream()
                        .map(NumismaticsMigrationCommandTreeTest::nodeName)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    private static Object child(Object node, String name) throws Exception {
        Object child = node.getClass().getMethod("getChild", String.class).invoke(node, name);
        assertNotNull(child, "missing command node " + name);
        return child;
    }

    private static String nodeName(Object node) {
        try {
            return (String) node.getClass().getMethod("getName").invoke(node);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect command node", exception);
        }
    }
}
