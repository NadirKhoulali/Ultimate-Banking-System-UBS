package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.network.OwnerPcDesktopActionResponsePayload;

public interface OwnerPcClientScreen {
    void refreshFromNetwork();

    default void handleDesktopActionResponse(OwnerPcDesktopActionResponsePayload payload) {
    }
}
