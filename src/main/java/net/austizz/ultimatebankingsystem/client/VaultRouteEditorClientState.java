package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteFace;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteValidator;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteEditorPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRoutePosition;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteSavePayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteStepPayload;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class VaultRouteEditorClientState {
    public enum PickerMode {
        NONE,
        START,
        FINISH,
        WALK,
        REDSTONE,
        RFID
    }

    public record Identity(UUID bankId,
                           String vaultId,
                           UUID tellerId,
                           SafeTellerRouteDirection direction) {
        public Identity {
            Objects.requireNonNull(bankId, "bankId");
            Objects.requireNonNull(tellerId, "tellerId");
            Objects.requireNonNull(direction, "direction");
            vaultId = vaultId == null ? "" : vaultId.trim();
            if (vaultId.isEmpty()) {
                throw new IllegalArgumentException("vaultId is required");
            }
        }
    }

    private static final VaultRouteEditorSession SESSION = new VaultRouteEditorSession();

    private VaultRouteEditorClientState() {
    }

    public static synchronized void openDetails(UUID bankId, UUID tellerId) {
        SESSION.openDetails(bankId, tellerId);
    }

    public static synchronized void closeDetails() {
        SESSION.clear();
    }

    public static synchronized void requestEditor(String vaultId, SafeTellerRouteDirection direction) {
        SESSION.requestEditor(vaultId, direction);
    }

    public static synchronized void expectServerResponse() {
        SESSION.expectServerResponse();
    }

    public static synchronized boolean acceptServerResponse(OwnerPcVaultRouteEditorPayload payload) {
        return SESSION.acceptServerResponse(payload);
    }

    public static synchronized void beginPicker(PickerMode mode, int stepIndex) {
        SESSION.beginPicker(mode, stepIndex);
    }

    public static synchronized void beginRedstonePicker(int stepIndex, int strength, int durationTicks) {
        SESSION.beginRedstonePicker(stepIndex, strength, durationTicks);
    }

    public static synchronized boolean captureSelection(String selectedDimension,
                                                         OwnerPcVaultRoutePosition position,
                                                         SafeTellerRouteFace face) {
        return SESSION.captureSelection(selectedDimension, position, face).accepted();
    }

    static synchronized VaultRoutePickerSession.CaptureResult captureForHandshake(
            String selectedDimension,
            OwnerPcVaultRoutePosition position,
            SafeTellerRouteFace face) {
        return SESSION.captureSelection(selectedDimension, position, face);
    }

    public static synchronized void insertWalk(int index, OwnerPcVaultRoutePosition target) {
        reportStepLimit(SESSION.draft().insertWalk(index, target));
    }

    public static synchronized void insertWait(int index, int durationTicks) {
        reportStepLimit(SESSION.draft().insertWait(index, durationTicks));
    }

    public static synchronized void insertRedstone(int index,
                                                    OwnerPcVaultRoutePosition target,
                                                    SafeTellerRouteFace face,
                                                    int strength,
                                                    int durationTicks) {
        reportStepLimit(SESSION.draft().insertRedstone(
                index, target, face, strength, durationTicks));
    }

    public static synchronized void insertRfid(int index, OwnerPcVaultRoutePosition scanner) {
        reportStepLimit(SESSION.draft().insertRfid(index, scanner));
    }

    private static void reportStepLimit(boolean applied) {
        if (!applied) {
            SESSION.showMessage(false,
                    "A route can contain at most " + SafeTellerRouteValidator.MAX_STEPS + " steps.");
        }
    }

    public static synchronized void updateWait(int index, int durationTicks) {
        SESSION.draft().updateWait(index, durationTicks);
    }

    public static synchronized void updateRedstone(int index, int strength, int durationTicks) {
        SESSION.draft().updateRedstone(index, strength, durationTicks);
    }

    public static synchronized void deleteStep(int index) {
        SESSION.draft().deleteStep(index);
    }

    public static synchronized void moveStep(int index, int delta) {
        SESSION.draft().moveStep(index, delta);
    }

    public static synchronized OwnerPcVaultRouteSavePayload toSavePayload() {
        return SESSION.draft().toSavePayload(SESSION.identity(), SESSION.editSessionId());
    }

    public static synchronized UUID cancelEditor() {
        return SESSION.cancelEditor();
    }

    public static synchronized void clear() {
        SESSION.clear();
    }

    public static synchronized UUID selectedBankId() {
        return SESSION.selectedBankId();
    }

    public static synchronized UUID selectedTellerId() {
        return SESSION.selectedTellerId();
    }

    public static synchronized Identity identity() {
        return SESSION.identity();
    }

    public static synchronized UUID editSessionId() {
        return SESSION.editSessionId();
    }

    public static synchronized long sessionExpiresAtMillis() {
        return SESSION.sessionExpiresAtMillis();
    }

    public static synchronized boolean isDetailsOpen() {
        return SESSION.isDetailsOpen();
    }

    public static synchronized boolean isEditorOpen() {
        return SESSION.isEditorOpen();
    }

    public static synchronized boolean isWaitingForServer() {
        return SESSION.isWaitingForServer();
    }

    public static synchronized boolean shouldRestoreEditor() {
        return SESSION.shouldRestoreEditor();
    }

    public static synchronized void markEditorRestored() {
        SESSION.markEditorRestored();
    }

    public static synchronized String dimension() {
        return SESSION.draft().dimension();
    }

    public static synchronized OwnerPcVaultRoutePosition start() {
        return SESSION.draft().start();
    }

    public static synchronized OwnerPcVaultRoutePosition finish() {
        return SESSION.draft().finish();
    }

    public static synchronized boolean hasStart() {
        return SESSION.draft().hasStart();
    }

    public static synchronized boolean hasFinish() {
        return SESSION.draft().hasFinish();
    }

    public static synchronized List<OwnerPcVaultRouteStepPayload> steps() {
        return SESSION.draft().steps();
    }

    public static synchronized PickerMode pickerMode() {
        return SESSION.pickerMode();
    }

    public static synchronized String message() {
        return SESSION.message();
    }

    public static synchronized boolean messageSuccess() {
        return SESSION.messageSuccess();
    }

    public static synchronized void showMessage(boolean success, String value) {
        SESSION.showMessage(success, value);
    }
}
