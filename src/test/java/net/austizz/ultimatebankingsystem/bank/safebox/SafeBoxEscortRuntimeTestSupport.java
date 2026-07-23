package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.EscortBlockPosition;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxArea;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortAccessRequest;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntime;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntimeContext;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntimePorts;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortTarget;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePosition;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SafeBoxEscortRuntimeTestSupport {
    static final UUID SESSION = id(1);
    static final UUID PLAYER = id(2);
    static final UUID TELLER = id(3);
    static final UUID BANK = id(4);
    static final UUID ACCOUNT = id(5);

    private SafeBoxEscortRuntimeTestSupport() {
    }

    static Fixture fixture() {
        return new Fixture();
    }

    static SafeBoxEscortRuntimeContext context() {
        return context(SESSION, PLAYER, TELLER);
    }

    static SafeBoxEscortRuntimeContext context(UUID session, UUID player, UUID teller) {
        return new SafeBoxEscortRuntimeContext(
                session, player, target(teller),
                new SafeBoxArea("minecraft:overworld", 0, 50, 0, 40, 80, 40),
                new SafeBoxArea("minecraft:overworld", 10, 60, 10, 20, 70, 20),
                new SafeBoxEscortRuntimeContext.Exit("minecraft:overworld", -2, 64, -2, 90.0F),
                new EscortBlockPosition(18, 64, 18),
                route(teller, SafeTellerRouteDirection.OUTBOUND),
                route(teller, SafeTellerRouteDirection.RETURN), "Box A-1");
    }

    static SafeBoxEscortTarget target() {
        return target(TELLER);
    }

    static SafeBoxEscortTarget target(UUID teller) {
        return new SafeBoxEscortTarget(BANK, "vault-a", ACCOUNT, "minecraft:overworld",
                new EscortBlockPosition(12, 64, 14), 3, teller);
    }

    static SafeBoxEscortAccessRequest accessRequest(UUID player, SafeBoxEscortTarget target) {
        return SafeBoxEscortAccessRequest.fromTarget(player, target);
    }

    static SafeTellerRoute route(UUID teller, SafeTellerRouteDirection direction) {
        SafeTellerRoutePosition start = new SafeTellerRoutePosition(1, 64, 1);
        SafeTellerRoutePosition finish = new SafeTellerRoutePosition(18, 64, 18);
        return SafeTellerRoute.create(BANK.toString(), "vault-a", teller.toString(), direction,
                "minecraft:overworld", start, finish, List.of(new SafeTellerRouteStep.Walk(finish)));
    }

    static long count(List<String> values, String expected) {
        return values.stream().filter(expected::equals).count();
    }

    static UUID id(long value) {
        return new UUID(0L, value);
    }

    enum Call {
        START, STATE, TICK, CANCEL, FORGET,
        ACQUIRE, RELEASE, GRANT, REVOKE, SHOW, CLEAR, EJECT
    }

    static final class Fixture {
        final SafeBoxEscortRuntimeFaultPlan faults = new SafeBoxEscortRuntimeFaultPlan();
        final FakeNavigation navigation = new FakeNavigation(faults);
        final FakeEffects effects = new FakeEffects(faults);
        final SafeBoxEscortRuntime runtime = new SafeBoxEscortRuntime(navigation, effects);

        void outboundArrived(long tick) {
            runtime.start(context());
            navigation.arrive(SESSION);
            runtime.tick(tick);
        }

        void inspectingAt(long tick) {
            outboundArrived(tick);
            runtime.handleTellerInteraction(PLAYER, TELLER, tick);
        }

        void waitingAt(long tick) {
            inspectingAt(tick);
            runtime.handleTellerInteraction(PLAYER, TELLER, tick + 1);
        }
    }

    static final class FakeNavigation implements SafeBoxEscortRuntimePorts.Navigation {
        final Map<UUID, SafeBoxEscortRuntimePorts.NavigationState> states = new HashMap<>();
        final List<String> events = new ArrayList<>();
        private final SafeBoxEscortRuntimeFaultPlan faults;
        SafeBoxEscortRuntimePorts.NavigationStart nextStart =
                SafeBoxEscortRuntimePorts.NavigationStart.STARTED;
        boolean mutateBeforeStartFault = true;

        FakeNavigation(SafeBoxEscortRuntimeFaultPlan faults) {
            this.faults = faults;
        }

        @Override
        public SafeBoxEscortRuntimePorts.NavigationStart start(UUID session,
                                                               UUID teller,
                                                               UUID player,
                                                               SafeBoxArea premiseBounds,
                                                               SafeTellerRoute route) {
            events.add("start:" + route.direction());
            if (mutateBeforeStartFault) {
                states.put(session, SafeBoxEscortRuntimePorts.NavigationState.RUNNING);
            }
            faults.hit(Call.START);
            states.put(session, SafeBoxEscortRuntimePorts.NavigationState.RUNNING);
            SafeBoxEscortRuntimePorts.NavigationStart result = nextStart;
            nextStart = SafeBoxEscortRuntimePorts.NavigationStart.STARTED;
            if (result != SafeBoxEscortRuntimePorts.NavigationStart.STARTED) {
                states.remove(session);
            }
            return result;
        }

        @Override
        public SafeBoxEscortRuntimePorts.NavigationState state(UUID session) {
            faults.hit(Call.STATE);
            return states.getOrDefault(session, SafeBoxEscortRuntimePorts.NavigationState.MISSING);
        }

        @Override
        public void tick() {
            faults.hit(Call.TICK);
        }

        @Override
        public boolean cancel(UUID session) {
            events.add("cancel");
            if (faults.refused(Call.CANCEL)) {
                return false;
            }
            if (states.get(session) != SafeBoxEscortRuntimePorts.NavigationState.RUNNING) {
                faults.hit(Call.CANCEL);
                return false;
            }
            states.put(session, SafeBoxEscortRuntimePorts.NavigationState.CANCELLED);
            faults.hit(Call.CANCEL);
            return true;
        }

        @Override
        public boolean forget(UUID session) {
            events.add("forget");
            if (faults.refused(Call.FORGET)) {
                return false;
            }
            SafeBoxEscortRuntimePorts.NavigationState current = states.get(session);
            if (current == null || current == SafeBoxEscortRuntimePorts.NavigationState.RUNNING) {
                faults.hit(Call.FORGET);
                return false;
            }
            states.remove(session);
            faults.hit(Call.FORGET);
            return true;
        }

        void arrive(UUID session) {
            states.put(session, SafeBoxEscortRuntimePorts.NavigationState.ARRIVED);
        }
    }

    static final class FakeEffects implements SafeBoxEscortRuntimePorts.Effects {
        final List<String> events = new ArrayList<>();
        private final SafeBoxEscortRuntimeFaultPlan faults;
        boolean authorized = true;
        boolean holdResult = true;

        FakeEffects(SafeBoxEscortRuntimeFaultPlan faults) {
            this.faults = faults;
        }

        @Override
        public boolean freshlyAuthorized(SafeBoxEscortRuntimeContext context) {
            events.add("auth");
            return authorized;
        }

        @Override
        public boolean acquireDoorHold(SafeBoxEscortRuntimeContext context) {
            events.add("hold+");
            faults.hit(Call.ACQUIRE);
            return holdResult;
        }

        @Override
        public void releaseDoorHold(SafeBoxEscortRuntimeContext context) {
            events.add("hold-");
            faults.hit(Call.RELEASE);
        }

        @Override
        public void grantAccess(SafeBoxEscortRuntimeContext context) {
            events.add("access+");
            faults.hit(Call.GRANT);
        }

        @Override
        public void revokeAccess(SafeBoxEscortRuntimeContext context) {
            events.add("access-");
            faults.hit(Call.REVOKE);
        }

        @Override
        public void showMarker(SafeBoxEscortRuntimeContext context) {
            events.add("marker+");
            faults.hit(Call.SHOW);
        }

        @Override
        public void clearMarker(SafeBoxEscortRuntimeContext context) {
            events.add("marker-");
            faults.hit(Call.CLEAR);
        }

        @Override
        public void eject(SafeBoxEscortRuntimeContext context) {
            events.add("eject");
            faults.hit(Call.EJECT);
        }
    }
}
