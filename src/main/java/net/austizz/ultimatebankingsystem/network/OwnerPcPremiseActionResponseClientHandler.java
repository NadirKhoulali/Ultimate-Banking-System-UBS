package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.gui.screens.ClientOwnerPcData;
import net.austizz.ultimatebankingsystem.gui.screens.OwnerPcClientScreen;

final class OwnerPcPremiseActionResponseClientHandler {
    private OwnerPcPremiseActionResponseClientHandler() {
    }

    static void handle(OwnerPcPremiseActionResponsePayload payload,
                       OwnerPcClientScreen ownerScreen) {
        ClientOwnerPcData.setPremiseActionResponse(payload);
        ClientOwnerPcData.setToast(payload.success(), payload.message());
        if (ownerScreen != null) {
            ownerScreen.refreshFromNetwork();
        }
    }
}
