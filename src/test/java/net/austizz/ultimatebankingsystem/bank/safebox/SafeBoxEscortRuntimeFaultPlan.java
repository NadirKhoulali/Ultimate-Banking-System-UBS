package net.austizz.ultimatebankingsystem.bank.safebox;

import java.util.EnumMap;
import java.util.Map;

import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.Call;

final class SafeBoxEscortRuntimeFaultPlan {
    private final Map<Call, Integer> remaining = new EnumMap<>(Call.class);
    private final Map<Call, Integer> delays = new EnumMap<>(Call.class);
    private final Map<Call, Integer> refusals = new EnumMap<>(Call.class);
    private final Map<Call, Integer> calls = new EnumMap<>(Call.class);

    void once(Call call) {
        onInvocation(call, calls(call) + 1);
    }

    void onInvocation(Call call, int invocation) {
        remaining.put(call, 1);
        delays.put(call, Math.max(0, invocation - calls(call) - 1));
    }

    void always(Call call) {
        remaining.put(call, -1);
        delays.put(call, 0);
    }

    void refuseOnce(Call call) {
        refusals.put(call, 1);
    }

    void refuseAlways(Call call) {
        refusals.put(call, -1);
    }

    void clear(Call call) {
        remaining.remove(call);
        delays.remove(call);
        refusals.remove(call);
    }

    int calls(Call call) {
        return calls.getOrDefault(call, 0);
    }

    boolean refused(Call call) {
        int left = refusals.getOrDefault(call, 0);
        if (left == 0) {
            return false;
        }
        calls.merge(call, 1, Integer::sum);
        if (left > 0) {
            refusals.put(call, left - 1);
        }
        return true;
    }

    void hit(Call call) {
        calls.merge(call, 1, Integer::sum);
        int delay = delays.getOrDefault(call, 0);
        if (delay > 0) {
            delays.put(call, delay - 1);
            return;
        }
        int left = remaining.getOrDefault(call, 0);
        if (left == 0) {
            return;
        }
        if (left > 0) {
            remaining.put(call, left - 1);
        }
        throw new IllegalStateException("injected " + call);
    }
}
