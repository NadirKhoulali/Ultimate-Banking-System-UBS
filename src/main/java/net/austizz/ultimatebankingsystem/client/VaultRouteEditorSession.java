package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteFace;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteEditorPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRoutePosition;

import java.util.Objects;
import java.util.UUID;

final class VaultRouteEditorSession {
    private final VaultRouteDraft draft = new VaultRouteDraft();
    private final VaultRoutePickerSession picker = new VaultRoutePickerSession();

    private UUID selectedBankId;
    private UUID selectedTellerId;
    private UUID editSessionId;
    private long sessionExpiresAtMillis;
    private VaultRouteEditorClientState.Identity identity;
    private boolean detailsOpen;
    private boolean editorOpen;
    private boolean waitingForServer;
    private boolean restoreEditor;
    private String message = "";
    private boolean messageSuccess = true;

    void openDetails(UUID bankId, UUID tellerId) {
        Objects.requireNonNull(bankId, "bankId");
        Objects.requireNonNull(tellerId, "tellerId");
        if (!bankId.equals(selectedBankId) || !tellerId.equals(selectedTellerId)) {
            clearRouteDraft();
        }
        selectedBankId = bankId;
        selectedTellerId = tellerId;
        detailsOpen = true;
        editorOpen = false;
        restoreEditor = false;
        showMessage(true, "");
    }

    void requestEditor(String vaultId, SafeTellerRouteDirection direction) {
        if (selectedBankId == null || selectedTellerId == null) {
            throw new IllegalStateException("teller details must be open before configuring a route");
        }
        VaultRouteEditorClientState.Identity requested = new VaultRouteEditorClientState.Identity(
                selectedBankId, vaultId, selectedTellerId, direction);
        if (!requested.equals(identity)) {
            clearRouteDraft();
            identity = requested;
            detailsOpen = true;
        }
        waitingForServer = true;
        editorOpen = false;
        restoreEditor = false;
        editSessionId = null;
        sessionExpiresAtMillis = 0L;
        showMessage(true, "Loading route from server...");
    }

    void expectServerResponse() {
        if (identity == null || !editorOpen) {
            throw new IllegalStateException("no route draft is open");
        }
        waitingForServer = true;
        showMessage(true, "Saving route...");
    }

    boolean acceptServerResponse(OwnerPcVaultRouteEditorPayload payload) {
        if (payload == null || !waitingForServer || !matches(payload)) {
            return false;
        }
        waitingForServer = false;
        showMessage(payload.success(), payload.message());
        if (!payload.success()) {
            editSessionId = payload.editSessionId();
            sessionExpiresAtMillis = payload.sessionExpiresAtMillis();
            return true;
        }
        if (payload.editSessionId() == null) {
            finishSavedRoute(payload.message());
            return true;
        }
        editSessionId = payload.editSessionId();
        sessionExpiresAtMillis = payload.sessionExpiresAtMillis();
        draft.load(payload);
        picker.clear();
        editorOpen = true;
        detailsOpen = true;
        restoreEditor = false;
        return true;
    }

    private boolean matches(OwnerPcVaultRouteEditorPayload payload) {
        return identity != null
                && identity.bankId().equals(payload.bankId())
                && identity.vaultId().equals(payload.vaultId())
                && identity.tellerId().equals(payload.tellerId())
                && identity.direction() == payload.direction();
    }

    void beginPicker(VaultRouteEditorClientState.PickerMode mode, int stepIndex) {
        requireOpenEditor();
        picker.begin(mode, stepIndex);
        restoreEditor = false;
        showMessage(true, "Right-click a block with your main hand.");
    }

    void beginRedstonePicker(int stepIndex, int strength, int durationTicks) {
        requireOpenEditor();
        picker.beginRedstone(stepIndex, strength, durationTicks);
        restoreEditor = false;
        showMessage(true, "Right-click the redstone target face with your main hand.");
    }

    VaultRoutePickerSession.CaptureResult captureSelection(String dimension,
                                                            OwnerPcVaultRoutePosition position,
                                                            SafeTellerRouteFace face) {
        VaultRoutePickerSession.CaptureResult result = picker.capture(draft, dimension, position, face);
        if (result.reopenOwnerPc()) {
            restoreEditor = result.restoreEditor();
            showMessage(result.messageSuccess(), result.message());
        }
        return result;
    }

    private void requireOpenEditor() {
        if (!editorOpen) {
            throw new IllegalStateException("route editor is not open");
        }
    }

    UUID cancelEditor() {
        UUID cancelled = editSessionId;
        clearRouteDraft();
        detailsOpen = selectedBankId != null && selectedTellerId != null;
        return cancelled;
    }

    void clear() {
        clearRouteDraft();
        selectedBankId = null;
        selectedTellerId = null;
        detailsOpen = false;
    }

    private void clearRouteDraft() {
        identity = null;
        editSessionId = null;
        sessionExpiresAtMillis = 0L;
        editorOpen = false;
        waitingForServer = false;
        restoreEditor = false;
        draft.clear();
        picker.clear();
        showMessage(true, "");
    }

    private void finishSavedRoute(String savedMessage) {
        clearRouteDraft();
        detailsOpen = selectedBankId != null && selectedTellerId != null;
        showMessage(true, savedMessage);
    }

    void showMessage(boolean success, String value) {
        messageSuccess = success;
        message = value == null ? "" : value.trim();
    }

    VaultRouteDraft draft() {
        return draft;
    }

    UUID selectedBankId() {
        return selectedBankId;
    }

    UUID selectedTellerId() {
        return selectedTellerId;
    }

    VaultRouteEditorClientState.Identity identity() {
        return identity;
    }

    UUID editSessionId() {
        return editSessionId;
    }

    long sessionExpiresAtMillis() {
        return sessionExpiresAtMillis;
    }

    boolean isDetailsOpen() {
        return detailsOpen;
    }

    boolean isEditorOpen() {
        return editorOpen;
    }

    boolean isWaitingForServer() {
        return waitingForServer;
    }

    boolean shouldRestoreEditor() {
        return restoreEditor && editorOpen && !picker.awaitingWorldSelection();
    }

    void markEditorRestored() {
        restoreEditor = false;
    }

    VaultRouteEditorClientState.PickerMode pickerMode() {
        return picker.mode();
    }

    String message() {
        return message;
    }

    boolean messageSuccess() {
        return messageSuccess;
    }
}
