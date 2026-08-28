package net.austizz.ultimatebankingsystem.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmScreenLayoutTest {
    @Test
    void usesExpandedTerminalAtNormalDesktopViewport() {
        AtmScreenLayout layout = AtmScreenLayout.forViewport(960, 540);

        assertEquals(420, layout.width());
        assertEquals(300, layout.height());
        assertFalse(layout.compact());
    }

    @Test
    void staysInsideSmallVanillaScaledViewport() {
        AtmScreenLayout layout = AtmScreenLayout.forViewport(320, 240);

        assertEquals(304, layout.width());
        assertEquals(224, layout.height());
        assertTrue(layout.compact());
    }
}
