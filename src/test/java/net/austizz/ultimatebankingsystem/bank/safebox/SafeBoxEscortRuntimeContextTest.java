package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.EscortBlockPosition;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxArea;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntimeContext;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import org.junit.jupiter.api.Test;

import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.PLAYER;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.SESSION;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.TELLER;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.id;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.route;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.target;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafeBoxEscortRuntimeContextTest {
    @Test
    void vaultDoorMasterMaySitOutsideSafeBoundsWhenStillInsidePremise() {
        assertDoesNotThrow(() -> new SafeBoxEscortRuntimeContext(
                SESSION, PLAYER, target(),
                new SafeBoxArea("minecraft:overworld", 0, 50, 0, 40, 80, 40),
                new SafeBoxArea("minecraft:overworld", 10, 60, 10, 20, 70, 20),
                new SafeBoxEscortRuntimeContext.Exit("minecraft:overworld", -2, 64, -2, 90.0F),
                new EscortBlockPosition(9, 64, 15),
                route(TELLER, SafeTellerRouteDirection.OUTBOUND),
                route(TELLER, SafeTellerRouteDirection.RETURN), "Box A-1"));
    }

    @Test
    void mismatchedRouteIdentityIsRejectedBeforeRuntimeStart() {
        assertThrows(IllegalArgumentException.class, () -> new SafeBoxEscortRuntimeContext(
                SESSION, PLAYER, target(),
                new SafeBoxArea("minecraft:overworld", 0, 50, 0, 40, 80, 40),
                new SafeBoxArea("minecraft:overworld", 10, 60, 10, 20, 70, 20),
                new SafeBoxEscortRuntimeContext.Exit("minecraft:overworld", -2, 64, -2, 90.0F),
                new EscortBlockPosition(18, 64, 18),
                route(id(99), SafeTellerRouteDirection.OUTBOUND),
                route(TELLER, SafeTellerRouteDirection.RETURN), "Box A-1"));
    }
}
