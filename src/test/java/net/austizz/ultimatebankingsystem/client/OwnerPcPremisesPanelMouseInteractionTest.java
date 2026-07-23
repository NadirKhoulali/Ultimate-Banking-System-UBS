package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.Test;

import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.assertPendingOperationRetained;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.focusProbe;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.loadedPendingModalScreen;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.registerModalWidget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPcPremisesPanelMouseInteractionTest {
    @Test
    void modernBankModalMouseBoundsRejectOverlappingRealTaskbarStartWidget() throws Exception {
        OwnerPcPremisesPanelScreenFixture.LoadedModalScreen fixture = loadedPendingModalScreen();
        fixture.startButton().getClass().getMethod("setX", int.class).invoke(fixture.startButton(), 100);
        fixture.startButton().getClass().getMethod("setY", int.class).invoke(fixture.startButton(), 100);

        assertTrue((boolean) fixture.screenType()
                .getMethod("mouseClicked", double.class, double.class, int.class)
                .invoke(fixture.screen(), 101.0D, 101.0D, 0));

        assertPendingOperationRetained(fixture.screen());
    }

    @Test
    void modalOwnedListenerClickInvokesCallbackFocusesAndStartsDragging() throws Exception {
        OwnerPcPremisesPanelScreenFixture.LoadedModalScreen fixture = loadedPendingModalScreen();
        OwnerPcPremisesPanelScreenFixture.FocusProbe modalOwned = focusProbe(fixture.listenerType());
        registerModalWidget(fixture.screen(), modalOwned.listener());

        assertTrue((boolean) fixture.screenType()
                .getMethod("mouseClicked", double.class, double.class, int.class)
                .invoke(fixture.screen(), 101.0D, 101.0D, 0));

        assertEquals(1, modalOwned.mouseClicks().get(), "The modal-owned listener callback must run");
        assertSame(modalOwned.listener(), fixture.screenType().getMethod("getFocused").invoke(fixture.screen()));
        assertTrue((boolean) fixture.screenType().getMethod("isDragging").invoke(fixture.screen()));
        assertPendingOperationRetained(fixture.screen());
    }
}
