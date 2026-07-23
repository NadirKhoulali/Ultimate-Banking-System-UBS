package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;
import net.austizz.ultimatebankingsystem.bank.owner.OwnerPcActionPolicy;
import net.austizz.ultimatebankingsystem.bank.owner.OwnerPcMutationContextCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPcActionRequestDispatcherTest {
    private static final UUID BANK_ID = UUID.fromString("10000000-0000-0000-0000-000000000014");
    private static final OwnerPcMutationContextCollector.RememberedPc REMEMBERED =
            new OwnerPcMutationContextCollector.RememberedPc(
                    "minecraft:overworld", 10, 64, 10, "machine-a");
    private static final OwnerPcMutationContextCollector.LoadedPc LIVE_PC =
            new OwnerPcMutationContextCollector.LoadedPc(
                    true, true, OwnerPcMutationContextCollector.BlockKind.OWNER_PC,
                    "machine-a", true, true);
    private static final OwnerPcMutationContextCollector.PlayerLocation NEARBY =
            new OwnerPcMutationContextCollector.PlayerLocation(
                    "minecraft:overworld", 10.5D, 64.5D, 10.5D);

    @ParameterizedTest(name = "denied C2S request is response-only for {0}")
    @MethodSource("invalidContexts")
    void deniedMutationReturnsBeforeRefreshAndPreservesAllState(NamedContext invalid) {
        RecordingPorts ports = new RecordingPorts(invalid.context());
        StateSnapshot before = ports.state.snapshot();

        OwnerPcActionRequestDispatcher.dispatch(payload("SET_MOTTO", "forbidden"), ports);

        assertEquals(before, ports.state.snapshot(), invalid.name());
        assertEquals(0, ports.mutationBoundaryCalls, invalid.name());
        assertEquals(0, ports.refreshBoundaryCalls, invalid.name());
        assertEquals(1, ports.responses.size(), invalid.name());
        assertFalse(ports.responses.getFirst().success(), invalid.name());
    }

    @Test
    void liveMutationCrossesMutationBoundaryExactlyOnceAndThenUsesPureRefresh() {
        OwnerPcActionPolicy.MutationContext live = OwnerPcMutationContextCollector.collect(
                REMEMBERED, LIVE_PC, NEARBY);
        RecordingPorts ports = new RecordingPorts(live);
        StateSnapshot before = ports.state.snapshot();

        OwnerPcActionRequestDispatcher.dispatch(payload("SET_MOTTO", "live-once"), ports);

        assertEquals(1, ports.mutationBoundaryCalls);
        assertEquals(1, ports.refreshBoundaryCalls);
        assertEquals(before.persistenceVersion() + 1, ports.state.persistenceVersion);
        assertEquals("live-once", ports.state.bankMetadata.get("motto"));
        assertEquals(before.accountMetadata(), ports.state.accountMetadata);
        assertEquals(before.accountState(), ports.state.accountState);
        assertEquals(1, ports.responses.size());
        assertTrue(ports.responses.getFirst().success());
    }

    @ParameterizedTest(name = "away read remains state-pure for {0}")
    @MethodSource("readOnlyActions")
    void awayFromPcReadReturnsDataWithoutMutation(String action) {
        RecordingPorts ports = new RecordingPorts(null);
        StateSnapshot before = ports.state.snapshot();

        OwnerPcActionRequestDispatcher.dispatch(payload(action, ""), ports);

        assertEquals(before, ports.state.snapshot());
        assertEquals(0, ports.mutationBoundaryCalls);
        assertEquals(1, ports.refreshBoundaryCalls);
        assertEquals(1, ports.responses.size());
        assertTrue(ports.responses.getFirst().success());
        assertEquals("read snapshot", ports.responses.getFirst().message());
    }

    @Test
    void unknownActionFailsClosedBeforeRefresh() {
        RecordingPorts ports = new RecordingPorts(null);
        StateSnapshot before = ports.state.snapshot();

        OwnerPcActionRequestDispatcher.dispatch(payload("FUTURE_ACTION", ""), ports);

        assertEquals(before, ports.state.snapshot());
        assertEquals(0, ports.mutationBoundaryCalls);
        assertEquals(0, ports.refreshBoundaryCalls);
        assertEquals(1, ports.responses.size());
        assertFalse(ports.responses.getFirst().success());
    }

    private static Stream<NamedContext> invalidContexts() {
        return Stream.of(
                named("absent", null, null, NEARBY),
                named("unloaded", REMEMBERED,
                        new OwnerPcMutationContextCollector.LoadedPc(
                                true, false, OwnerPcMutationContextCollector.BlockKind.UNAVAILABLE,
                                "machine-a", true, true), NEARBY),
                named("wrong block", REMEMBERED,
                        new OwnerPcMutationContextCollector.LoadedPc(
                                true, true, OwnerPcMutationContextCollector.BlockKind.OTHER,
                                "machine-a", true, true), NEARBY),
                named("wrong machine", REMEMBERED,
                        new OwnerPcMutationContextCollector.LoadedPc(
                                true, true, OwnerPcMutationContextCollector.BlockKind.OWNER_PC,
                                "machine-b", true, true), NEARBY),
                named("cross dimension", REMEMBERED, LIVE_PC,
                        new OwnerPcMutationContextCollector.PlayerLocation(
                                "minecraft:the_nether", 10.5D, 64.5D, 10.5D)),
                named("out of range", REMEMBERED, LIVE_PC,
                        new OwnerPcMutationContextCollector.PlayerLocation(
                                "minecraft:overworld", 40.5D, 64.5D, 10.5D)),
                named("powered off", REMEMBERED,
                        new OwnerPcMutationContextCollector.LoadedPc(
                                true, true, OwnerPcMutationContextCollector.BlockKind.OWNER_PC,
                                "machine-a", false, false), NEARBY),
                named("locked", REMEMBERED,
                        new OwnerPcMutationContextCollector.LoadedPc(
                                true, true, OwnerPcMutationContextCollector.BlockKind.OWNER_PC,
                                "machine-a", true, false), NEARBY)
        );
    }

    private static Stream<String> readOnlyActions() {
        return Stream.of(
                "SHOW_INFO", "SHOW_RESERVE", "SHOW_DASHBOARD", "SHOW_ACCOUNTS",
                "SHOW_CDS", "SHOW_LIMITS", "SHOW_ROLES", "SHOW_SHARES",
                "SHOW_COFOUNDERS", "SHOW_EMPLOYEES", "SHOW_LOAN_PRODUCTS",
                "SHOW_LOANS", "SHOW_MARKET", "BANK_LEVEL_ROADMAP", "TELLER_COUNT",
                "ACCOUNT_DETAIL");
    }

    private static NamedContext named(String name,
                                      OwnerPcMutationContextCollector.RememberedPc remembered,
                                      OwnerPcMutationContextCollector.LoadedPc loadedPc,
                                      OwnerPcMutationContextCollector.PlayerLocation player) {
        return new NamedContext(name, OwnerPcMutationContextCollector.collect(remembered, loadedPc, player));
    }

    private static OwnerPcActionRequestDispatcher.Request payload(String action, String arg1) {
        return new OwnerPcActionRequestDispatcher.Request(BANK_ID, action, arg1, "", "", "");
    }

    private static final class RecordingPorts implements OwnerPcActionRequestDispatcher.Ports {
        private final OwnerPcActionPolicy.MutationContext context;
        private final PersistentState state = PersistentState.fixture();
        private final List<OwnerPcActionRequestDispatcher.Response> responses = new ArrayList<>();
        private int mutationBoundaryCalls;
        private int refreshBoundaryCalls;

        private RecordingPorts(OwnerPcActionPolicy.MutationContext context) {
            this.context = context;
        }

        @Override
        public BankOwnerPcService.ActionResult executeDirect(OwnerPcActionRequestDispatcher.Request request) {
            OwnerPcActionPolicy.Decision decision = OwnerPcActionPolicy.authorize(
                    request.action(), OwnerPcActionPolicy.Channel.DIRECT_OWNER_PC, context);
            if (!decision.allowed()) {
                return new BankOwnerPcService.ActionResult(false, decision.message());
            }
            if (decision.action().access() == OwnerPcActionPolicy.Access.READ_ONLY) {
                return new BankOwnerPcService.ActionResult(true, "read snapshot");
            }
            mutationBoundaryCalls++;
            state.bankMetadata.put("motto", request.arg1());
            state.persistenceVersion++;
            return new BankOwnerPcService.ActionResult(true, "updated");
        }

        @Override
        public void sendResponse(OwnerPcActionRequestDispatcher.Response response) {
            responses.add(response);
        }

        @Override
        public void refreshBankData(UUID bankId) {
            assertEquals(BANK_ID, bankId);
            refreshBoundaryCalls++;
        }
    }

    private static final class PersistentState {
        private final Map<String, String> bankMetadata = new LinkedHashMap<>();
        private final Map<String, String> accountMetadata = new LinkedHashMap<>();
        private final Map<String, String> accountState = new LinkedHashMap<>();
        private int persistenceVersion;

        private static PersistentState fixture() {
            PersistentState state = new PersistentState();
            state.bankMetadata.put("status", "ACTIVE");
            state.bankMetadata.put("motto", "before");
            state.bankMetadata.put("dailyWithdrawn", "41.00");
            state.accountMetadata.put("roles", "owner=OWNER");
            state.accountMetadata.put("updatedAt", "1234");
            state.accountState.put("balance", "900.00");
            state.accountState.put("dailyWithdrawnAmount", "75.00");
            state.accountState.put("temporaryWithdrawalLimitExpiresAtEpochMillis", "99");
            state.persistenceVersion = 7;
            return state;
        }

        private StateSnapshot snapshot() {
            return new StateSnapshot(Map.copyOf(bankMetadata), Map.copyOf(accountMetadata),
                    Map.copyOf(accountState), persistenceVersion);
        }
    }

    private record StateSnapshot(Map<String, String> bankMetadata,
                                 Map<String, String> accountMetadata,
                                 Map<String, String> accountState,
                                 int persistenceVersion) {
    }

    private record NamedContext(String name, OwnerPcActionPolicy.MutationContext context) {
        @Override
        public String toString() {
            return name;
        }
    }
}
