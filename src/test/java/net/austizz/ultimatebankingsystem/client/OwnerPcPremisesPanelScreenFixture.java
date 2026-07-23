package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OwnerPcPremisesPanelScreenFixture {
    private static final ClassLoader NEOFORGE_LOADER = NeoForgeTestClassLoader.get();
    private static final UUID BANK_ID = UUID.fromString("9d48ad1f-45e4-4ed5-a86b-9227b9ccf0d8");
    private static final UUID OPERATION_ID = UUID.fromString("1a4284cb-d85e-43d2-b740-a0f2fb2a8fd2");
    private static final String PREMISE_ID = "premise-main";

    private OwnerPcPremisesPanelScreenFixture() {}

    static LoadedModalScreen loadedPendingModalScreen() throws Exception {
        Class<?> screenType = load("gui.screens.BankOwnerPcScreen");
        Class<?> componentType = minecraft("network.chat.Component");
        Object title = componentType.getMethod("literal", String.class).invoke(null, "Owner PC");
        Object screen = screenType.getConstructor(componentType).newInstance(title);
        setField(screen, "width", 400);
        setField(screen, "height", 300);
        setField(screen, "desktopAuthenticated", true);
        setEnumField(screen, "activeWindow", "BANK_APP");
        invokeNoArgs(screen, "initTaskbarWidgets");
        List<?> children = (List<?>) screenType.getMethod("children").invoke(screen);
        Object startButton = widgetByLabel(children, "Start");
        setField(screen, "premiseDeleteState", loadedPending());
        setEnumField(screen, "bankActionModal", "PREMISE_DELETE_CONFIRM");
        return new LoadedModalScreen(
                screen,
                screenType,
                minecraft("client.gui.components.events.GuiEventListener"),
                startButton);
    }

    static boolean invokeKey(LoadedModalScreen fixture,
                             String methodName,
                             int keyCode,
                             int modifiers) throws Exception {
        return (boolean) fixture.screenType()
                .getMethod(methodName, int.class, int.class, int.class)
                .invoke(fixture.screen(), keyCode, 0, modifiers);
    }

    static void assertPendingOperationRetained(Object screen) throws Exception {
        Object retained = field(screen, "premiseDeleteState").get(screen);
        assertTrue((boolean) retained.getClass().getMethod("pending").invoke(retained));
        assertEquals(OPERATION_ID, retained.getClass().getMethod("operationId").invoke(retained));
        assertEquals("BANK_APP", enumFieldName(screen, "activeWindow"));
        assertEquals("PREMISE_DELETE_CONFIRM", enumFieldName(screen, "bankActionModal"));
    }

    @SuppressWarnings("unchecked")
    static List<Object> modalWidgets(Object screen) throws Exception {
        return (List<Object>) field(screen, "modernBankModalWidgets").get(screen);
    }

    static Object desktopButton(String label) throws Exception {
        Class<?> componentType = minecraft("network.chat.Component");
        Object message = componentType.getMethod("literal", String.class).invoke(null, label);
        Consumer<Object> noOp = ignored -> { };
        return load("gui.widgets.DesktopButton")
                .getConstructor(int.class, int.class, int.class, int.class,
                        componentType, int.class, Consumer.class)
                .newInstance(40, 40, 90, 20, message, 0xFF42C8, noOp);
    }

    static Object desktopEditBox(String hint) throws Exception {
        Class<?> componentType = minecraft("network.chat.Component");
        Object message = componentType.getMethod("literal", String.class).invoke(null, hint);
        return load("gui.widgets.DesktopEditBox")
                .getConstructor(minecraft("client.gui.Font"), int.class, int.class, int.class, int.class,
                        componentType)
                .newInstance(null, 40, 40, 120, 20, message);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> formInputs(Object screen) throws Exception {
        return (Map<String, Object>) field(screen, "activeFormInputs").get(screen);
    }

    @SuppressWarnings("unchecked")
    static void registerModalWidget(Object screen, Object widget) throws Exception {
        ((List<Object>) screen.getClass().getMethod("children").invoke(screen)).add(widget);
        modalWidgets(screen).add(widget);
    }

    static FocusProbe focusProbe(Class<?> listenerType) {
        boolean[] focused = {false};
        AtomicInteger keyPresses = new AtomicInteger();
        AtomicInteger typedCharacters = new AtomicInteger();
        AtomicInteger keyReleases = new AtomicInteger();
        AtomicInteger mouseClicks = new AtomicInteger();
        Object listener = Proxy.newProxyInstance(
                NEOFORGE_LOADER,
                new Class<?>[]{listenerType},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "setFocused" -> {
                        focused[0] = (boolean) args[0];
                        yield null;
                    }
                    case "isFocused" -> focused[0];
                    case "keyPressed" -> {
                        keyPresses.incrementAndGet();
                        yield true;
                    }
                    case "charTyped" -> {
                        typedCharacters.incrementAndGet();
                        yield true;
                    }
                    case "keyReleased" -> {
                        keyReleases.incrementAndGet();
                        yield true;
                    }
                    case "mouseClicked" -> {
                        mouseClicks.incrementAndGet();
                        yield true;
                    }
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
        return new FocusProbe(listener, keyPresses, typedCharacters, keyReleases, mouseClicks);
    }

    static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = field(target, fieldName);
        field.set(target, value);
    }

    static Object fieldValue(Object target, String fieldName) throws Exception {
        return field(target, fieldName).get(target);
    }

    static void setEnumField(Object target, String fieldName, String constantName) throws Exception {
        Field field = field(target, fieldName);
        for (Object constant : field.getType().getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(constantName)) {
                field.set(target, constant);
                return;
            }
        }
        throw new IllegalArgumentException("Unknown enum constant " + constantName);
    }

    static String enumFieldName(Object target, String fieldName) throws Exception {
        return ((Enum<?>) field(target, fieldName).get(target)).name();
    }

    static Class<?> load(String relativeName) throws Exception {
        return Class.forName("net.austizz.ultimatebankingsystem." + relativeName, true, NEOFORGE_LOADER);
    }

    static void invokeNoArgs(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static Object loadedPending() throws Exception {
        return pendingDelete(BANK_ID, PREMISE_ID, OPERATION_ID);
    }

    static Object pendingDelete(UUID bankId, String premiseId, UUID operationId) throws Exception {
        Class<?> lifecycle = load("gui.screens.OwnerPcPremiseDeleteLifecycle");
        Object confirming = lifecycle.getMethod("confirming", UUID.class, String.class)
                .invoke(null, bankId, premiseId);
        Object confirmation = confirming.getClass().getMethod("confirm", UUID.class)
                .invoke(confirming, operationId);
        return confirmation.getClass().getMethod("state").invoke(confirmation);
    }

    private static Object widgetByLabel(List<?> widgets, String label) throws Exception {
        for (Object widget : widgets) {
            if (label.equals(widgetLabel(widget))) {
                return widget;
            }
        }
        throw new IllegalStateException("Missing widget: " + label);
    }

    private static String widgetLabel(Object widget) throws Exception {
        Object message = widget.getClass().getMethod("getMessage").invoke(widget);
        return (String) message.getClass().getMethod("getString").invoke(message);
    }

    private static Field field(Object target, String fieldName) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static Class<?> minecraft(String relativeName) throws Exception {
        return Class.forName("net.minecraft." + relativeName, true, NEOFORGE_LOADER);
    }

    record LoadedModalScreen(Object screen,
                             Class<?> screenType,
                             Class<?> listenerType,
                             Object startButton) {}

    record FocusProbe(Object listener,
                      AtomicInteger keyPresses,
                      AtomicInteger typedCharacters,
                      AtomicInteger keyReleases,
                      AtomicInteger mouseClicks) {}
}
