package net.austizz.ultimatebankingsystem.command;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UBSAdminPremiseCommandTreeTest {
    @Test
    void premiseAdminTreeExposesAllManagementActions() throws Exception {
        Class<?> commands = Class.forName(
                "net.austizz.ultimatebankingsystem.command.UBSAdminCommands",
                true, NeoForgeTestClassLoader.get());
        Method factory = commands.getDeclaredMethod("buildAdminBankPremiseLiteral");
        factory.setAccessible(true);
        Object builder = factory.invoke(null);
        Object premise = builder.getClass().getMethod("build").invoke(builder);
        Collection<?> children = (Collection<?>) premise.getClass()
                .getMethod("getChildren").invoke(premise);

        assertEquals(Set.of("list", "info", "add", "delete", "mode", "exit", "cancel"),
                children.stream()
                        .map(UBSAdminPremiseCommandTreeTest::nodeName)
                        .collect(Collectors.toSet()));
    }

    @Test
    void premiseCommandsAreAttachedUnderUbsAdminBank() throws Exception {
        Class<?> commands = Class.forName(
                "net.austizz.ultimatebankingsystem.command.UBSAdminCommands",
                true, NeoForgeTestClassLoader.get());
        Method factory = commands.getDeclaredMethod("buildUbsRoot");
        factory.setAccessible(true);
        Object builder = factory.invoke(null);
        Object root = builder.getClass().getMethod("build").invoke(builder);

        Object admin = child(root, "admin");
        Object bank = child(admin, "bank");
        assertNotNull(child(bank, "premise"));
    }

    @Test
    void premiseIdsCopyToClipboardAndExplainTheAction() throws Exception {
        ensureMinecraftBootstrap();
        Class<?> commands = Class.forName(
                "net.austizz.ultimatebankingsystem.command.UBSAdminCommands",
                true, NeoForgeTestClassLoader.get());
        Method factory = commands.getDeclaredMethod("clickablePremiseId", String.class);
        factory.setAccessible(true);
        Object component = factory.invoke(null, "premise-42");
        Object style = component.getClass().getMethod("getStyle").invoke(component);
        Object click = style.getClass().getMethod("getClickEvent").invoke(style);
        Object hover = style.getClass().getMethod("getHoverEvent").invoke(style);

        assertNotNull(click);
        assertEquals("COPY_TO_CLIPBOARD",
                ((Enum<?>) click.getClass().getMethod("getAction").invoke(click)).name());
        assertEquals("premise-42", click.getClass().getMethod("getValue").invoke(click));
        assertEquals(Boolean.TRUE, style.getClass().getMethod("isUnderlined").invoke(style));
        assertNotNull(hover);
        assertTrue(hover.toString().contains("Click to copy premise ID"));
    }

    private static void ensureMinecraftBootstrap() throws Exception {
        ClassLoader loader = NeoForgeTestClassLoader.get();
        Class<?> loadingModList = Class.forName(
                "net.neoforged.fml.loading.LoadingModList", true, loader);
        if (loadingModList.getMethod("get").invoke(null) == null) {
            loadingModList.getMethod(
                            "of", List.class, List.class, List.class, List.class, Map.class)
                    .invoke(null, List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        Class.forName("net.minecraft.SharedConstants", true, loader)
                .getMethod("tryDetectVersion")
                .invoke(null);
        Class.forName("net.minecraft.server.Bootstrap", true, loader)
                .getMethod("bootStrap")
                .invoke(null);
    }

    private static String nodeName(Object node) {
        try {
            return (String) node.getClass().getMethod("getName").invoke(node);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect command node", exception);
        }
    }

    private static Object child(Object node, String name) throws Exception {
        Object child = node.getClass().getMethod("getChild", String.class).invoke(node, name);
        assertNotNull(child, "missing command node " + name);
        return child;
    }
}
