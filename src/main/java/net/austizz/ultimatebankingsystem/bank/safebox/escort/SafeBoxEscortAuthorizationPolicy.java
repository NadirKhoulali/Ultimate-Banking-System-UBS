package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import java.util.UUID;

@FunctionalInterface
public interface SafeBoxEscortAuthorizationPolicy {
    Decision authorize(UUID playerId, SafeBoxEscortTarget exactTarget);

    enum Decision {
        GRANTED,
        DENIED
    }

    static SafeBoxEscortAuthorizationPolicy denyAll() {
        return (playerId, exactTarget) -> Decision.DENIED;
    }
}
