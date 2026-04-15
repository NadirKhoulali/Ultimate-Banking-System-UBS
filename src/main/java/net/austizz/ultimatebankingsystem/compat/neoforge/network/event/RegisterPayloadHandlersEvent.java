package net.austizz.ultimatebankingsystem.compat.neoforge.network.event;

import net.austizz.ultimatebankingsystem.compat.neoforge.network.registration.PayloadRegistrar;

public final class RegisterPayloadHandlersEvent {
    public PayloadRegistrar registrar(String version) {
        return new PayloadRegistrar(version);
    }
}
