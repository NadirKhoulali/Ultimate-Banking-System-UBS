package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.assertPendingOperationRetained;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.desktopButton;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.desktopEditBox;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.enumFieldName;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.focusProbe;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.formInputs;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.invokeKey;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.load;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.loadedPendingModalScreen;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.modalWidgets;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.registerModalWidget;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.setEnumField;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.setField;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPcPremisesPanelKeyboardInteractionTest {
    @Test
    void pendingPremiseDeleteModalConsumesEnterBeforeFocusedTaskbarStartCanHideBankWindow() throws Exception {
        OwnerPcPremisesPanelScreenFixture.LoadedModalScreen fixture = loadedPendingModalScreen();
        fixture.screenType().getMethod("setFocused", fixture.listenerType())
                .invoke(fixture.screen(), fixture.startButton());

        assertTrue(invokeKey(fixture, "keyPressed", 257, 0));

        assertEquals("BANK_APP", enumFieldName(fixture.screen(), "activeWindow"),
                "Enter must not activate the focused Start widget behind a pending delete modal");
        assertPendingOperationRetained(fixture.screen());
    }

    @Test
    void focusedModalOwnedListenerReceivesEnterAndSpace() throws Exception {
        for (int keyCode : List.of(257, 32)) {
            OwnerPcPremisesPanelScreenFixture.LoadedModalScreen fixture = loadedPendingModalScreen();
            OwnerPcPremisesPanelScreenFixture.FocusProbe modalOwned = focusProbe(fixture.listenerType());
            registerModalWidget(fixture.screen(), modalOwned.listener());
            fixture.screenType().getMethod("setFocused", fixture.listenerType())
                    .invoke(fixture.screen(), modalOwned.listener());

            assertTrue(invokeKey(fixture, "keyPressed", keyCode, 0));

            assertEquals(1, modalOwned.keyPresses().get(),
                    "The modal-owned listener must receive key code " + keyCode);
            assertPendingOperationRetained(fixture.screen());
        }
    }

    @Test
    void focusedModalOwnedListenerReceivesTypedCharacterAndKeyRelease() throws Exception {
        OwnerPcPremisesPanelScreenFixture.LoadedModalScreen fixture = loadedPendingModalScreen();
        OwnerPcPremisesPanelScreenFixture.FocusProbe modalOwned = focusProbe(fixture.listenerType());
        registerModalWidget(fixture.screen(), modalOwned.listener());
        fixture.screenType().getMethod("setFocused", fixture.listenerType())
                .invoke(fixture.screen(), modalOwned.listener());

        assertTrue((boolean) fixture.screenType().getMethod("charTyped", char.class, int.class)
                .invoke(fixture.screen(), 'x', 0));
        assertTrue(invokeKey(fixture, "keyReleased", 65, 0));

        assertEquals(1, modalOwned.typedCharacters().get());
        assertEquals(1, modalOwned.keyReleases().get());
        assertPendingOperationRetained(fixture.screen());
    }

    @Test
    void staffModalRecoversInputFocusAndAcceptsTypingWhenUnderlyingFocusIsStale() throws Exception {
        OwnerPcPremisesPanelScreenFixture.LoadedModalScreen fixture = loadedPendingModalScreen();
        setField(fixture.screen(), "premiseDeleteState",
                load("gui.screens.OwnerPcPremiseDeleteLifecycle").getMethod("closed").invoke(null));
        setEnumField(fixture.screen(), "bankActionModal", "STAFF_EMPLOYEE");
        Object input = desktopEditBox("Player");
        registerModalWidget(fixture.screen(), input);
        formInputs(fixture.screen()).put("staff.player", input);
        fixture.screenType().getMethod("setFocused", fixture.listenerType())
                .invoke(fixture.screen(), fixture.startButton());

        assertTrue((boolean) fixture.screenType().getMethod("charTyped", char.class, int.class)
                .invoke(fixture.screen(), 'D', 0));

        assertSame(input, fixture.screenType().getMethod("getFocused").invoke(fixture.screen()));
        assertEquals("D", input.getClass().getMethod("getValue").invoke(input));
    }

    @Test
    void pendingPremiseDeleteModalConsumesSpaceBeforeUnderlyingFocusReceivesIt() throws Exception {
        OwnerPcPremisesPanelScreenFixture.LoadedModalScreen fixture = loadedPendingModalScreen();
        OwnerPcPremisesPanelScreenFixture.FocusProbe underlying = focusProbe(fixture.listenerType());
        fixture.screenType().getMethod("setFocused", fixture.listenerType())
                .invoke(fixture.screen(), underlying.listener());

        assertTrue(invokeKey(fixture, "keyPressed", 32, 0));

        assertEquals(0, underlying.keyPresses().get());
        assertPendingOperationRetained(fixture.screen());
    }

    @Test
    void pendingPremiseDeleteModalTabMovesFocusToFirstModalOwnedWidgetOnly() throws Exception {
        OwnerPcPremisesPanelScreenFixture.LoadedModalScreen fixture = loadedPendingModalScreen();
        Object firstModalWidget = desktopButton("Cancel");
        Object secondModalWidget = desktopButton("Delete");
        modalWidgets(fixture.screen()).addAll(List.of(firstModalWidget, secondModalWidget));
        OwnerPcPremisesPanelScreenFixture.FocusProbe underlying = focusProbe(fixture.listenerType());
        fixture.screenType().getMethod("setFocused", fixture.listenerType())
                .invoke(fixture.screen(), underlying.listener());

        assertTrue(invokeKey(fixture, "keyPressed", 258, 0));

        assertSame(firstModalWidget, fixture.screenType().getMethod("getFocused").invoke(fixture.screen()));
        assertEquals(0, underlying.keyPresses().get());
        assertPendingOperationRetained(fixture.screen());
    }

    @Test
    void pendingPremiseDeleteModalShiftTabWrapsWithinModalOwnedWidgets() throws Exception {
        OwnerPcPremisesPanelScreenFixture.LoadedModalScreen fixture = loadedPendingModalScreen();
        Object firstModalWidget = desktopButton("Cancel");
        Object secondModalWidget = desktopButton("Delete");
        modalWidgets(fixture.screen()).addAll(List.of(firstModalWidget, secondModalWidget));
        fixture.screenType().getMethod("setFocused", fixture.listenerType())
                .invoke(fixture.screen(), firstModalWidget);

        assertTrue(invokeKey(fixture, "keyPressed", 258, 0x1));

        assertSame(secondModalWidget, fixture.screenType().getMethod("getFocused").invoke(fixture.screen()));
        assertPendingOperationRetained(fixture.screen());
    }

    @Test
    void pendingPremiseDeleteModalConsumesTypedCharactersBeforeUnderlyingFocusReceivesThem() throws Exception {
        OwnerPcPremisesPanelScreenFixture.LoadedModalScreen fixture = loadedPendingModalScreen();
        OwnerPcPremisesPanelScreenFixture.FocusProbe underlying = focusProbe(fixture.listenerType());
        fixture.screenType().getMethod("setFocused", fixture.listenerType())
                .invoke(fixture.screen(), underlying.listener());

        assertTrue((boolean) fixture.screenType().getMethod("charTyped", char.class, int.class)
                .invoke(fixture.screen(), 'x', 0));

        assertEquals(0, underlying.typedCharacters().get());
        assertPendingOperationRetained(fixture.screen());
    }

    @Test
    void pendingPremiseDeleteModalConsumesKeyReleaseBeforeUnderlyingFocusReceivesIt() throws Exception {
        OwnerPcPremisesPanelScreenFixture.LoadedModalScreen fixture = loadedPendingModalScreen();
        OwnerPcPremisesPanelScreenFixture.FocusProbe underlying = focusProbe(fixture.listenerType());
        fixture.screenType().getMethod("setFocused", fixture.listenerType())
                .invoke(fixture.screen(), underlying.listener());

        assertTrue(invokeKey(fixture, "keyReleased", 65, 0));

        assertEquals(0, underlying.keyReleases().get());
        assertPendingOperationRetained(fixture.screen());
    }

    @Test
    void pendingPremiseDeleteModalEscapeCannotDismissOrLoseOperationIdentity() throws Exception {
        OwnerPcPremisesPanelScreenFixture.LoadedModalScreen fixture = loadedPendingModalScreen();

        assertTrue(invokeKey(fixture, "keyPressed", 256, 0));

        assertPendingOperationRetained(fixture.screen());
    }

    @Test
    void nonPendingModernBankModalStillClosesWithEscape() throws Exception {
        OwnerPcPremisesPanelScreenFixture.LoadedModalScreen fixture = loadedPendingModalScreen();
        setField(fixture.screen(), "premiseDeleteState",
                load("gui.screens.OwnerPcPremiseDeleteLifecycle").getMethod("closed").invoke(null));
        setEnumField(fixture.screen(), "bankActionModal", "BRANDING_MOTTO");
        setField(fixture.screen(), "desktopAuthenticated", false);

        assertTrue(invokeKey(fixture, "keyPressed", 256, 0));

        assertEquals("NONE", enumFieldName(fixture.screen(), "bankActionModal"));
        assertEquals("BANK_APP", enumFieldName(fixture.screen(), "activeWindow"));
    }
}
