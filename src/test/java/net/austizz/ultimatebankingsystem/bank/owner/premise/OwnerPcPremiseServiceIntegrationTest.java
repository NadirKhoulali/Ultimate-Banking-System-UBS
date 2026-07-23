package net.austizz.ultimatebankingsystem.bank.owner.premise;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPcPremiseServiceIntegrationTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();
    private static final String OWNER_PREMISE =
            "net.austizz.ultimatebankingsystem.bank.owner.premise.";
    private static final String NETWORK = "net.austizz.ultimatebankingsystem.network.";
    private static final String SAFE_SETUP =
            "net.austizz.ultimatebankingsystem.bank.safebox.setup.";
    private static final String SAFE_CLAIM =
            "net.austizz.ultimatebankingsystem.bank.safebox.claim.";
    private static final UUID BANK_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000701");
    private static final UUID OPERATION_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000701");
    private static final UUID DENIED_OPERATION_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000702");
    private static final UUID FAILED_OPERATION_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000703");
    private static final String PREMISE_ID = "premise-task-seven";

    @Test
    void serverOwnsClaimCoordinatesAndRevalidatesAuthority() throws Exception {
        Class<?> service = loadRequired(
                OWNER_PREMISE + "OwnerPcPremiseService",
                "Task 7 requires the server-authoritative OwnerPcPremiseService");
        UUID unsolicitedOperationId = (UUID) service
                .getField("UNSOLICITED_OPERATION_ID").get(null);
        assertEquals(new UUID(0L, 0L), unsolicitedOperationId);
        assertFalse(Arrays.stream(network("OwnerPcPremiseActionPayload").getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .anyMatch(name -> name.contains("dimension") || name.contains("coordinate")
                        || name.equals("x") || name.equals("y") || name.equals("z")
                        || name.contains("yaw")),
                "the C2S action payload must carry intent and identifiers only");

        FakePorts ports = new FakePorts(metadata(), Access.ownerAccess());
        Object started = execute(service, ports, action("START_CLAIM", "", null));
        assertTrue(success(started));
        assertEquals(1, ports.sessionStarts);
        assertEquals("PREMISE_CREATE", ports.lastPurpose);
        assertEquals(0, ports.commits);

        Object serverSelection = selection(
                corner("minecraft:overworld", 100, 64, 100),
                corner("minecraft:overworld", 110, 70, 110),
                exit("minecraft:overworld", 99, 64, 105, 90.0F));
        ports.access = ports.access.locked();
        Object denied = applyObserved(service, ports, "PREMISE_CREATE", "", serverSelection);
        assertFalse(success(denied), "authority must be re-read when the deferred tool applies");
        assertEquals(unsolicitedOperationId, value(denied, "operationId"));
        assertEquals(0, ports.commits);
        assertEquals(0, ports.invalidations);

        ports.access = Access.ownerAccess();
        Map<String, Object> original = ports.metadata;
        Object applied = applyObserved(service, ports, "PREMISE_CREATE", "", serverSelection);

        assertTrue(success(applied));
        assertEquals(unsolicitedOperationId, value(applied, "operationId"));
        assertEquals(1, ports.commits);
        assertEquals(1, ports.invalidations);
        assertNotEquals(original, ports.metadata);
        assertEquals(List.of(), premises(original));
        Map<String, Object> created = premises(ports.metadata).get(0);
        assertEquals("minecraft:overworld", created.get("dimension"));
        assertEquals(100, created.get("minX"));
        assertEquals(64, created.get("minY"));
        assertEquals(100, created.get("minZ"));
        assertEquals(110, created.get("maxX"));
        assertEquals(70, created.get("maxY"));
        assertEquals(110, created.get("maxZ"));
        assertEquals(99, created.get("exitX"));
        assertEquals(64, created.get("exitY"));
        assertEquals(105, created.get("exitZ"));
        assertEquals(90.0F, created.get("exitYaw"));
        assertEquals("PUBLIC", created.get("mode"));
        assertEquals(List.of(), created.get("safeAreas"));
        assertEquals(created.get("id"), value(applied, "premiseId"));
    }

    @Test
    void directResultsEchoOperationIdAcrossSuccessDenialAndFailure() throws Exception {
        Class<?> service = loadRequired(
                OWNER_PREMISE + "OwnerPcPremiseService",
                "Task 7 requires the server-authoritative OwnerPcPremiseService");

        FakePorts successPorts = new FakePorts(metadataWithPremise(), Access.ownerAccess());
        Object succeeded = execute(service, successPorts,
                action(OPERATION_ID, "SET_MODE", PREMISE_ID, "STAFF_ONLY"));
        assertTrue(success(succeeded));
        assertEquals(OPERATION_ID, value(succeeded, "operationId"));
        Object response = succeeded.getClass().getMethod("response").invoke(succeeded);
        assertEquals(OPERATION_ID, value(response, "operationId"));

        FakePorts deniedPorts = new FakePorts(metadataWithPremise(), Access.ownerAccess().locked());
        Object denied = execute(service, deniedPorts,
                action(DENIED_OPERATION_ID, "SET_MODE", PREMISE_ID, "STAFF_ONLY"));
        assertFalse(success(denied));
        assertEquals(DENIED_OPERATION_ID, value(denied, "operationId"));

        FakePorts failedPorts = new FakePorts(metadataWithPremise(), Access.ownerAccess());
        Object failed = execute(service, failedPorts,
                action(FAILED_OPERATION_ID, "START_EXIT_EDIT", "stale-premise", null));
        assertFalse(success(failed));
        assertEquals(FAILED_OPERATION_ID, value(failed, "operationId"));
    }

    @Test
    void liveAuthorityAndConcurrentSessionDenialsHaveNoSideEffects() throws Exception {
        Class<?> service = loadRequired(
                OWNER_PREMISE + "OwnerPcPremiseService",
                "Task 7 requires the server-authoritative OwnerPcPremiseService");
        List<Access> denied = List.of(
                Access.ownerAccess().withoutBank(),
                Access.ownerAccess().withoutActiveComputer(),
                Access.ownerAccess().poweredOff(),
                Access.ownerAccess().locked(),
                Access.ordinaryEmployee(),
                Access.safeAccessOnly(),
                Access.wrongBankOwner());
        for (Access access : denied) {
            FakePorts ports = new FakePorts(metadataWithPremise(), access);
            String before = ports.metadata.toString();
            Object result = execute(service, ports, action("SET_MODE", PREMISE_ID, "STAFF_ONLY"));
            assertFalse(success(result), access.toString());
            assertEquals(0, ports.commits, access.toString());
            assertEquals(0, ports.invalidations, access.toString());
            assertEquals(before, ports.metadata.toString(), access.toString());
        }

        FakePorts admin = new FakePorts(metadataWithPremise(), Access.levelThreeAccess());
        Object changed = execute(service, admin, action("SET_MODE", PREMISE_ID, "STAFF_ONLY"));
        assertTrue(success(changed));
        assertEquals("STAFF_ONLY", premises(admin.metadata).get(0).get("mode"));
        assertEquals(1, admin.commits);
        assertEquals(1, admin.invalidations);

        FakePorts concurrent = new FakePorts(metadata(), Access.ownerAccess());
        concurrent.sessionAllowed = false;
        Object result = execute(service, concurrent, action("START_CLAIM", "", null));
        assertFalse(success(result));
        assertEquals(1, concurrent.sessionStarts);
        assertEquals(0, concurrent.commits);
        assertEquals(0, concurrent.invalidations);
    }

    @Test
    void staleIdsInsideExitAndFreshDeleteBlockersFailClosed() throws Exception {
        Class<?> service = loadRequired(
                OWNER_PREMISE + "OwnerPcPremiseService",
                "Task 7 requires the server-authoritative OwnerPcPremiseService");
        FakePorts ports = new FakePorts(metadataWithPremise(), Access.ownerAccess());

        Object staleExit = execute(
                service, ports, action("START_EXIT_EDIT", "stale-premise", null));
        assertFalse(success(staleExit));
        assertEquals(0, ports.sessionStarts);

        Object inside = selection(null, null,
                exit("minecraft:overworld", 5, 65, 5, -30.0F));
        Object invalidExit = applyObserved(
                service, ports, "PREMISE_EXIT_EDIT", PREMISE_ID, inside);
        assertFalse(success(invalidExit));
        assertEquals(0, ports.commits);
        assertEquals(0, ports.invalidations);

        assignments(ports.metadata).add(map(
                "bankId", BANK_ID.toString(),
                "accountId", "20000000-0000-0000-0000-000000000701",
                "dimension", "minecraft:overworld",
                "x", 5,
                "y", 65,
                "z", 5,
                "doorIndex", 0));
        Object blockedDelete = execute(service, ports, action("DELETE", PREMISE_ID, null));
        assertFalse(success(blockedDelete));
        assertTrue(message(blockedDelete).contains("ASSIGNED"));
        assertEquals(0, ports.commits,
                "a blocker introduced after a panel snapshot must prevent the live delete");
        assertEquals(1, premises(ports.metadata).size());

        Object staleDelete = execute(
                service, ports, action("DELETE", "stale-premise", null));
        assertFalse(success(staleDelete));
        assertEquals(0, ports.commits);
    }

    @Test
    void activeVaultSnapshotIsImmutableInsideSerializedMutation() throws Exception {
        Class<?> service = loadRequired(
                OWNER_PREMISE + "OwnerPcPremiseService",
                "Task 7 requires the server-authoritative OwnerPcPremiseService");
        FakePorts ports = new FakePorts(metadataWithPremise(), Access.ownerAccess());
        ports.activeVaultIds.add("vault-unrelated");

        Object deleted = execute(service, ports, action("DELETE", PREMISE_ID, null));

        assertTrue(success(deleted));
        assertEquals(1, ports.serializedMutations);
        assertEquals(1, ports.commits);
        assertEquals(1, ports.invalidations);
        assertEquals(Set.of("vault-unrelated"), ports.activeVaultIds);
        assertTrue(premises(ports.metadata).isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> ports.lastActiveSnapshot.add("vault-spoof"));
    }

    private static Object execute(Class<?> service, FakePorts ports, Object payload)
            throws Exception {
        return service.getMethod("execute",
                        ownerPremise("OwnerPcPremiseService$Ports"),
                        network("OwnerPcPremiseActionPayload"))
                .invoke(null, ports.proxy(), payload);
    }

    private static Object applyObserved(Class<?> service,
                                        FakePorts ports,
                                        String purpose,
                                        String premiseId,
                                        Object selection) throws Exception {
        Class<?> purposeType = safeClaim("SafeClaimToolPurpose");
        return service.getMethod("applyObservedSelection",
                        ownerPremise("OwnerPcPremiseService$Ports"),
                        UUID.class,
                        purposeType,
                        String.class,
                        safeClaim("SafeClaimSelection"))
                .invoke(null, ports.proxy(), BANK_ID, enumValue(purposeType, purpose),
                        premiseId, selection);
    }

    private static Object action(String action, String premiseId, String mode) throws Exception {
        return action(OPERATION_ID, action, premiseId, mode);
    }

    private static Object action(UUID operationId,
                                 String action,
                                 String premiseId,
                                 String mode) throws Exception {
        Class<?> actionType = network("OwnerPcPremiseActionPayload$Action");
        Class<?> modeType = safeSetup("SafePremiseMode");
        return network("OwnerPcPremiseActionPayload")
                .getConstructor(UUID.class, UUID.class, actionType, String.class, modeType)
                .newInstance(BANK_ID, operationId, enumValue(actionType, action), premiseId,
                        enumValue(modeType, mode));
    }

    private static Object corner(String dimension, int x, int y, int z) throws Exception {
        return safeClaim("SafeClaimSelection$Corner")
                .getConstructor(String.class, int.class, int.class, int.class)
                .newInstance(dimension, x, y, z);
    }

    private static Object exit(String dimension, int x, int y, int z, float yaw)
            throws Exception {
        return safeClaim("SafeClaimSelection$Exit")
                .getConstructor(String.class, int.class, int.class, int.class, float.class)
                .newInstance(dimension, x, y, z, yaw);
    }

    private static Object selection(Object first, Object second, Object exit) throws Exception {
        return safeClaim("SafeClaimSelection")
                .getConstructor(
                        safeClaim("SafeClaimSelection$Corner"),
                        safeClaim("SafeClaimSelection$Corner"),
                        safeClaim("SafeClaimSelection$Exit"))
                .newInstance(first, second, exit);
    }

    private static boolean success(Object result) throws Exception {
        return (boolean) value(result, "success");
    }

    private static String message(Object result) throws Exception {
        return (String) value(result, "message");
    }

    private static Object value(Object target, String method) throws Exception {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static Class<?> loadRequired(String name, String message) {
        try {
            return Class.forName(name, true, LOADER);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError(message, exception);
        }
    }

    private static Class<?> ownerPremise(String name) throws Exception {
        return Class.forName(OWNER_PREMISE + name, true, LOADER);
    }

    private static Class<?> network(String name) throws Exception {
        return Class.forName(NETWORK + name, true, LOADER);
    }

    private static Class<?> safeSetup(String name) throws Exception {
        return Class.forName(SAFE_SETUP + name, true, LOADER);
    }

    private static Class<?> safeClaim(String name) throws Exception {
        return Class.forName(SAFE_CLAIM + name, true, LOADER);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> enumType, String name) {
        return name == null ? null : Enum.valueOf((Class) enumType, name);
    }

    private static Map<String, Object> metadata() {
        return map(
                "safeDepositSetupVersion", 1,
                "safeDepositPremises", new ArrayList<Map<String, Object>>(),
                "safeDepositAssignments", new ArrayList<Map<String, Object>>(),
                "safeTellerRoutes", new ArrayList<Map<String, Object>>(),
                "sentinel", "preserve-me");
    }

    private static Map<String, Object> metadataWithPremise() {
        Map<String, Object> metadata = metadata();
        premises(metadata).add(map(
                "id", PREMISE_ID,
                "bankId", BANK_ID.toString(),
                "dimension", "minecraft:overworld",
                "minX", 0,
                "minY", 60,
                "minZ", 0,
                "maxX", 10,
                "maxY", 70,
                "maxZ", 10,
                "exitX", -1,
                "exitY", 64,
                "exitZ", 5,
                "exitYaw", 0.0F,
                "mode", "PUBLIC",
                "safeAreas", new ArrayList<Map<String, Object>>()));
        return metadata;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> premises(Map<String, Object> metadata) {
        return (List<Map<String, Object>>) metadata.get("safeDepositPremises");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> assignments(Map<String, Object> metadata) {
        return (List<Map<String, Object>>) metadata.get("safeDepositAssignments");
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            map.put((String) values[index], values[index + 1]);
        }
        return map;
    }

    private static final class FakePorts {
        private Map<String, Object> metadata;
        private Access access;
        private boolean sessionAllowed = true;
        private final Set<String> activeVaultIds = new LinkedHashSet<>();
        private Set<String> lastActiveSnapshot = Set.of();
        private int sessionStarts;
        private int serializedMutations;
        private int commits;
        private int invalidations;
        private String lastPurpose = "";

        private FakePorts(Map<String, Object> metadata, Access access) {
            this.metadata = metadata;
            this.access = access;
        }

        private Object proxy() throws Exception {
            Class<?> portsType = ownerPremise("OwnerPcPremiseService$Ports");
            return Proxy.newProxyInstance(LOADER, new Class<?>[]{portsType},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "authority" -> authority();
                        case "startSession" -> startSession(args);
                        case "withMutation" -> withMutation(args);
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private Object authority() throws Exception {
            Constructor<?> constructor = ownerPremise("OwnerPcPremiseService$Authority")
                    .getConstructors()[0];
            return constructor.newInstance(metadata, access.bankExists, access.activeComputer,
                    access.poweredOn, access.unlocked, access.owner, access.levelThree);
        }

        private Object startSession(Object[] args) throws Exception {
            sessionStarts++;
            lastPurpose = ((Enum<?>) args[1]).name();
            Class<?> resultType = ownerPremise("OwnerPcPremiseService$SessionResult");
            return resultType.getConstructors()[0].newInstance(
                    sessionAllowed,
                    sessionAllowed ? "Premise claim tool started."
                            : "A claim tool is already active.");
        }

        @SuppressWarnings("unchecked")
        private Object withMutation(Object[] args) throws Exception {
            serializedMutations++;
            lastActiveSnapshot = Set.copyOf(activeVaultIds);
            Consumer<Map<String, Object>> commit = updated -> {
                commits++;
                invalidations++;
                metadata = updated;
            };
            Method apply = ownerPremise("OwnerPcPremiseService$Mutation")
                    .getMethod("apply",
                            ownerPremise("OwnerPcPremiseService$Authority"),
                            Set.class,
                            Consumer.class);
            return apply.invoke(args[3], authority(), lastActiveSnapshot, commit);
        }
    }

    private record Access(boolean bankExists,
                          boolean activeComputer,
                          boolean poweredOn,
                          boolean unlocked,
                          boolean owner,
                          boolean levelThree) {
        private static Access ownerAccess() {
            return new Access(true, true, true, true, true, false);
        }

        private static Access levelThreeAccess() {
            return new Access(true, true, true, true, false, true);
        }

        private static Access ordinaryEmployee() {
            return new Access(true, true, true, true, false, false);
        }

        private static Access safeAccessOnly() {
            return ordinaryEmployee();
        }

        private static Access wrongBankOwner() {
            return ordinaryEmployee();
        }

        private Access withoutBank() {
            return new Access(false, activeComputer, poweredOn, unlocked, owner, levelThree);
        }

        private Access withoutActiveComputer() {
            return new Access(bankExists, false, poweredOn, unlocked, owner, levelThree);
        }

        private Access poweredOff() {
            return new Access(bankExists, activeComputer, false, unlocked, owner, levelThree);
        }

        private Access locked() {
            return new Access(bankExists, activeComputer, poweredOn, false, owner, levelThree);
        }
    }
}
