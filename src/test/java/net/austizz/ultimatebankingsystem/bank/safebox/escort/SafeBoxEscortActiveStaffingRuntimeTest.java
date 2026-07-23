package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import net.austizz.ultimatebankingsystem.bank.owner.setup.SafeStaffReadinessTestSupport;
import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxEscortActiveStaffingRuntimeTest {
    @Test
    void activeRuntimeRechecksLiveAuthorizationAfterFinalSafeAccessRevoke() throws Exception {
        SafeBoxEscortFreshAuthorizationFixture authorization =
                new SafeBoxEscortFreshAuthorizationFixture(
                        SafeStaffReadinessTestSupport.BANK_ID, "vault-1");
        SafeStaffReadinessTestSupport.Scenario scenario =
                SafeStaffReadinessTestSupport.scenario();
        Object metadata = SafeStaffReadinessTestSupport.metadataWithEmployee();
        assertTrue(SafeStaffReadinessTestSupport.staffingMutation(
                "grantSafeAccess", metadata, SafeStaffReadinessTestSupport.EMPLOYEE_ID));

        ActiveRuntime runtime = new ActiveRuntime(authorization, metadata, scenario.readiness());
        assertEquals("STARTED", runtime.start());
        runtime.arrive();
        assertEquals("ACCESS_GRANTED", runtime.grantInspection());
        assertEquals("ALLOWED", runtime.inspect());

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation(
                "revokeSafeAccess", metadata, SafeStaffReadinessTestSupport.EMPLOYEE_ID));

        assertEquals("DENIED_ACTIVE_ESCORT", runtime.inspect(),
                "the next active access inspection must re-run live staffing authorization");
        assertTrue(runtime.authorizationChecks() >= 3,
                "grant plus both access inspections must invoke live authorization");
    }

    private static final class ActiveRuntime {
        private static final String BASE =
                "net.austizz.ultimatebankingsystem.bank.safebox.escort.";
        private final SafeBoxEscortFreshAuthorizationFixture authorization;
        private final Object metadata;
        private final Object readiness;
        private final AtomicInteger authorizationChecks = new AtomicInteger();
        private final Object runtime;
        private String navigationState = "RUNNING";

        private ActiveRuntime(SafeBoxEscortFreshAuthorizationFixture authorization,
                              Object metadata,
                              Object readiness) throws Exception {
            this.authorization = authorization;
            this.metadata = metadata;
            this.readiness = readiness;
            ClassLoader loader = NeoForgeTestClassLoader.get();
            Class<?> navigationType = type("SafeBoxEscortRuntimePorts$Navigation");
            Class<?> effectsType = type("SafeBoxEscortRuntimePorts$Effects");
            Object navigation = Proxy.newProxyInstance(loader, new Class<?>[]{navigationType},
                    (proxy, method, arguments) -> navigation(method));
            Object effects = Proxy.newProxyInstance(loader, new Class<?>[]{effectsType},
                    (proxy, method, arguments) -> effect(method));
            runtime = type("SafeBoxEscortRuntime")
                    .getConstructor(navigationType, effectsType)
                    .newInstance(navigation, effects);
        }

        private String start() throws Exception {
            Object result = type("SafeBoxEscortRuntime")
                    .getMethod("start", type("SafeBoxEscortRuntimeContext"))
                    .invoke(runtime, authorization.context());
            return value(result, "status").toString();
        }

        private void arrive() throws Exception {
            navigationState = "ARRIVED";
            type("SafeBoxEscortRuntime").getMethod("tick", long.class).invoke(runtime, 1L);
        }

        private String grantInspection() throws Exception {
            Object result = type("SafeBoxEscortRuntime").getMethod(
                            "handleTellerInteraction", UUID.class, UUID.class, long.class)
                    .invoke(runtime, authorization.owner, authorization.teller, 2L);
            return result.toString();
        }

        private String inspect() throws Exception {
            Class<?> targetType = type("SafeBoxEscortTarget");
            Class<?> requestType = type("SafeBoxEscortAccessRequest");
            Object request = requestType.getMethod("fromTarget", UUID.class, targetType)
                    .invoke(null, authorization.owner, authorization.target());
            Object result = type("SafeBoxEscortRuntime").getMethod(
                            "inspectAccess", requestType, long.class)
                    .invoke(runtime, request, 2L);
            return result.toString();
        }

        private int authorizationChecks() {
            return authorizationChecks.get();
        }

        private Object navigation(Method method) throws Exception {
            return switch (method.getName()) {
                case "start" -> enumValue("SafeBoxEscortRuntimePorts$NavigationStart", "STARTED");
                case "state" -> enumValue("SafeBoxEscortRuntimePorts$NavigationState", navigationState);
                case "cancel", "forget" -> true;
                default -> null;
            };
        }

        private Object effect(Method method) throws Exception {
            return switch (method.getName()) {
                case "freshlyAuthorized" -> {
                    authorizationChecks.incrementAndGet();
                    yield authorization.liveReadinessAuthorized(metadata, readiness);
                }
                case "acquireDoorHold" -> true;
                default -> null;
            };
        }

        private static Object value(Object target, String accessor) throws Exception {
            return target.getClass().getMethod(accessor).invoke(target);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static Object enumValue(String relativeName, String value) throws Exception {
            return Enum.valueOf((Class<? extends Enum>) type(relativeName), value);
        }

        private static Class<?> type(String relativeName) throws Exception {
            return Class.forName(BASE + relativeName, true, NeoForgeTestClassLoader.get());
        }
    }
}
