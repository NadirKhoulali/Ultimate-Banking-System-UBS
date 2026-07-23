package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmartphoneBankAccessGateTest {
    @Test
    void routesMissingAccountsToWelcome() {
        assertEquals(SmartphoneBankAccessGate.Result.WELCOME,
                SmartphoneBankAccessGate.decide(false, false, false, false));
        assertEquals(SmartphoneBankAccessGate.Result.WELCOME,
                SmartphoneBankAccessGate.decide(true, false, false, false));
    }

    @Test
    void routesEachAccountThroughItsOwnPinState() {
        assertEquals(SmartphoneBankAccessGate.Result.SET_PIN,
                SmartphoneBankAccessGate.decide(true, true, false, false));
        assertEquals(SmartphoneBankAccessGate.Result.SIGN_IN,
                SmartphoneBankAccessGate.decide(true, true, true, false));
        assertEquals(SmartphoneBankAccessGate.Result.READY,
                SmartphoneBankAccessGate.decide(true, true, true, true));
    }
}
