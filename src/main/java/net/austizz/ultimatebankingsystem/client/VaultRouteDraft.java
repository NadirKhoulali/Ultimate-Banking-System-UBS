package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteFace;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteValidator;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteEditorPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRoutePosition;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteSavePayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteStepPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

final class VaultRouteDraft {
    private final List<OwnerPcVaultRouteStepPayload> steps = new ArrayList<>();
    private String dimension = "";
    private OwnerPcVaultRoutePosition start = OwnerPcVaultRoutePosition.ZERO;
    private OwnerPcVaultRoutePosition finish = OwnerPcVaultRoutePosition.ZERO;
    private boolean startSet;
    private boolean finishSet;

    void load(OwnerPcVaultRouteEditorPayload payload) {
        clear();
        if (payload.hasRoute()) {
            dimension = payload.dimension();
            start = payload.start();
            finish = payload.finish();
            startSet = true;
            finishSet = true;
        }
        steps.addAll(payload.steps());
    }

    void clear() {
        dimension = "";
        start = OwnerPcVaultRoutePosition.ZERO;
        finish = OwnerPcVaultRoutePosition.ZERO;
        startSet = false;
        finishSet = false;
        steps.clear();
    }

    boolean acceptsDimension(String candidate) {
        return dimension.isEmpty() || dimension.equals(candidate);
    }

    void establishDimension(String candidate) {
        if (dimension.isEmpty()) {
            dimension = candidate;
        }
    }

    void setStart(OwnerPcVaultRoutePosition position) {
        start = position;
        startSet = true;
    }

    void setFinish(OwnerPcVaultRoutePosition position) {
        finish = position;
        finishSet = true;
    }

    boolean insertWalk(int index, OwnerPcVaultRoutePosition target) {
        return insert(index, new OwnerPcVaultRouteStepPayload.Walk(target));
    }

    boolean insertWait(int index, int durationTicks) {
        return insert(index, new OwnerPcVaultRouteStepPayload.Wait(clampWait(durationTicks)));
    }

    boolean insertRedstone(int index,
                           OwnerPcVaultRoutePosition target,
                           SafeTellerRouteFace face,
                           int strength,
                           int durationTicks) {
        return insert(index, new OwnerPcVaultRouteStepPayload.Redstone(
                target,
                face == null ? SafeTellerRouteFace.UP : face,
                clampStrength(strength),
                clampRedstoneDuration(durationTicks)));
    }

    boolean insertRfid(int index, OwnerPcVaultRoutePosition scanner) {
        return insert(index, new OwnerPcVaultRouteStepPayload.Rfid(scanner));
    }

    boolean replaceOrInsertWalk(int index, OwnerPcVaultRoutePosition target) {
        return replaceOrInsert(index, new OwnerPcVaultRouteStepPayload.Walk(target));
    }

    boolean replaceOrInsertRedstone(int index,
                                    OwnerPcVaultRoutePosition target,
                                    SafeTellerRouteFace face,
                                    int strength,
                                    int durationTicks) {
        return replaceOrInsert(index, new OwnerPcVaultRouteStepPayload.Redstone(
                target,
                face == null ? SafeTellerRouteFace.UP : face,
                clampStrength(strength),
                clampRedstoneDuration(durationTicks)));
    }

    boolean replaceOrInsertRfid(int index, OwnerPcVaultRoutePosition scanner) {
        return replaceOrInsert(index, new OwnerPcVaultRouteStepPayload.Rfid(scanner));
    }

    private boolean insert(int index, OwnerPcVaultRouteStepPayload step) {
        if (steps.size() >= SafeTellerRouteValidator.MAX_STEPS) {
            return false;
        }
        steps.add(Math.max(0, Math.min(index, steps.size())), step);
        return true;
    }

    private boolean replaceOrInsert(int index, OwnerPcVaultRouteStepPayload step) {
        if (index >= 0 && index < steps.size()) {
            steps.set(index, step);
            return true;
        }
        return insert(steps.size(), step);
    }

    void updateWait(int index, int durationTicks) {
        if (index >= 0 && index < steps.size()
                && steps.get(index) instanceof OwnerPcVaultRouteStepPayload.Wait) {
            steps.set(index, new OwnerPcVaultRouteStepPayload.Wait(clampWait(durationTicks)));
        }
    }

    void updateRedstone(int index, int strength, int durationTicks) {
        if (index >= 0 && index < steps.size()
                && steps.get(index) instanceof OwnerPcVaultRouteStepPayload.Redstone redstone) {
            steps.set(index, new OwnerPcVaultRouteStepPayload.Redstone(
                    redstone.target(), redstone.face(), clampStrength(strength),
                    clampRedstoneDuration(durationTicks)));
        }
    }

    void deleteStep(int index) {
        if (index >= 0 && index < steps.size()) {
            steps.remove(index);
        }
    }

    void moveStep(int index, int delta) {
        int target = index + delta;
        if (index >= 0 && index < steps.size() && target >= 0 && target < steps.size()) {
            Collections.swap(steps, index, target);
        }
    }

    OwnerPcVaultRouteSavePayload toSavePayload(VaultRouteEditorClientState.Identity identity,
                                                UUID editSessionId) {
        if (editSessionId == null) {
            throw new IllegalStateException("Route edit session is no longer valid. Reopen it at the Owner PC.");
        }
        if (identity == null || dimension.isBlank() || !startSet || !finishSet) {
            throw new IllegalStateException("dimension, start, and finish are required");
        }
        return new OwnerPcVaultRouteSavePayload(
                editSessionId, identity.bankId(), identity.vaultId(), identity.tellerId(),
                identity.direction(), dimension, start, finish, steps);
    }

    String dimension() {
        return dimension;
    }

    OwnerPcVaultRoutePosition start() {
        return start;
    }

    OwnerPcVaultRoutePosition finish() {
        return finish;
    }

    boolean hasStart() {
        return startSet;
    }

    boolean hasFinish() {
        return finishSet;
    }

    List<OwnerPcVaultRouteStepPayload> steps() {
        return List.copyOf(steps);
    }

    private static int clampWait(int value) {
        return Math.max(1, Math.min(SafeTellerRouteValidator.MAX_WAIT_TICKS, value));
    }

    private static int clampStrength(int value) {
        return Math.max(1, Math.min(15, value));
    }

    private static int clampRedstoneDuration(int value) {
        return Math.max(1, Math.min(SafeTellerRouteValidator.MAX_REDSTONE_DURATION_TICKS, value));
    }
}
