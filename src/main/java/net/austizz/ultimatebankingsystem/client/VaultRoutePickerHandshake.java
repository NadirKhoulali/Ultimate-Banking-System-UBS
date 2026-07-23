package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteFace;
import net.austizz.ultimatebankingsystem.network.OpenBankOwnerPcPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRoutePosition;

public final class VaultRoutePickerHandshake {
    public record LaunchIntent(boolean removeOwnerPcScreen) {
    }

    public record CaptureIntent(boolean selectionAccepted,
                                OpenBankOwnerPcPayload reopenPayload) {
    }

    private VaultRoutePickerHandshake() {
    }

    public static LaunchIntent begin(VaultRouteEditorClientState.PickerMode mode, int stepIndex) {
        VaultRouteEditorClientState.beginPicker(mode, stepIndex);
        return new LaunchIntent(true);
    }

    public static LaunchIntent beginRedstone(int stepIndex, int strength, int durationTicks) {
        VaultRouteEditorClientState.beginRedstonePicker(stepIndex, strength, durationTicks);
        return new LaunchIntent(true);
    }

    public static CaptureIntent capture(String dimension,
                                        OwnerPcVaultRoutePosition position,
                                        SafeTellerRouteFace face) {
        VaultRoutePickerSession.CaptureResult result = VaultRouteEditorClientState.captureForHandshake(
                dimension, position, face);
        OpenBankOwnerPcPayload reopen = result.reopenOwnerPc() ? new OpenBankOwnerPcPayload() : null;
        return new CaptureIntent(result.accepted(), reopen);
    }
}
