package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteFace;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteValidator;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRoutePosition;

final class VaultRoutePickerSession {
    record CaptureResult(boolean accepted,
                         boolean reopenOwnerPc,
                         boolean restoreEditor,
                         boolean messageSuccess,
                         String message) {
    }

    private VaultRouteEditorClientState.PickerMode mode = VaultRouteEditorClientState.PickerMode.NONE;
    private int stepIndex = -1;
    private int strength = 15;
    private int durationTicks = 20;
    private boolean awaitingWorldSelection;

    void begin(VaultRouteEditorClientState.PickerMode requestedMode, int requestedStepIndex) {
        if (requestedMode == null
                || requestedMode == VaultRouteEditorClientState.PickerMode.NONE
                || requestedMode == VaultRouteEditorClientState.PickerMode.REDSTONE) {
            throw new IllegalArgumentException("a start, finish, walk, or RFID picker is required");
        }
        mode = requestedMode;
        stepIndex = requestedStepIndex;
        awaitingWorldSelection = true;
    }

    void beginRedstone(int requestedStepIndex, int requestedStrength, int requestedDurationTicks) {
        mode = VaultRouteEditorClientState.PickerMode.REDSTONE;
        stepIndex = requestedStepIndex;
        strength = Math.max(1, Math.min(15, requestedStrength));
        durationTicks = Math.max(1, Math.min(
                SafeTellerRouteValidator.MAX_REDSTONE_DURATION_TICKS, requestedDurationTicks));
        awaitingWorldSelection = true;
    }

    CaptureResult capture(VaultRouteDraft draft,
                          String selectedDimension,
                          OwnerPcVaultRoutePosition position,
                          SafeTellerRouteFace face) {
        if (!awaitingWorldSelection || mode == VaultRouteEditorClientState.PickerMode.NONE
                || position == null || selectedDimension == null || selectedDimension.isBlank()) {
            return new CaptureResult(false, false, false, false, "No route selection is active.");
        }
        String dimension = selectedDimension.trim();
        if (!draft.acceptsDimension(dimension)) {
            clear();
            return new CaptureResult(
                    false, true, true, false,
                    "Route points must stay in " + draft.dimension() + ".");
        }

        boolean accepted = switch (mode) {
            case START -> {
                draft.establishDimension(dimension);
                draft.setStart(position);
                yield true;
            }
            case FINISH -> {
                draft.establishDimension(dimension);
                draft.setFinish(position);
                yield true;
            }
            case WALK -> {
                boolean applied = draft.replaceOrInsertWalk(stepIndex, position);
                if (applied) {
                    draft.establishDimension(dimension);
                }
                yield applied;
            }
            case REDSTONE -> {
                boolean applied = draft.replaceOrInsertRedstone(
                        stepIndex, position, face, strength, durationTicks);
                if (applied) {
                    draft.establishDimension(dimension);
                }
                yield applied;
            }
            case RFID -> {
                boolean applied = draft.replaceOrInsertRfid(stepIndex, position);
                if (applied) {
                    draft.establishDimension(dimension);
                }
                yield applied;
            }
            case NONE -> false;
        };
        clear();
        if (!accepted) {
            return new CaptureResult(
                    false, true, true, false,
                    "A route can contain at most " + SafeTellerRouteValidator.MAX_STEPS + " steps.");
        }
        return new CaptureResult(
                true, true, true, true,
                "Coordinate captured. Reopening the owner PC...");
    }

    void clear() {
        mode = VaultRouteEditorClientState.PickerMode.NONE;
        stepIndex = -1;
        strength = 15;
        durationTicks = 20;
        awaitingWorldSelection = false;
    }

    VaultRouteEditorClientState.PickerMode mode() {
        return mode;
    }

    boolean awaitingWorldSelection() {
        return awaitingWorldSelection;
    }
}
