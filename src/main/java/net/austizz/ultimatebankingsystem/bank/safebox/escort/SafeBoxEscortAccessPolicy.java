package net.austizz.ultimatebankingsystem.bank.safebox.escort;

final class SafeBoxEscortAccessPolicy {
    private SafeBoxEscortAccessPolicy() {
    }

    static SafeBoxEscortRuntime.AccessDecision inspect(SafeBoxEscortRegistry registry,
                                                        SafeBoxEscortRuntimeState.Active active,
                                                        SafeBoxEscortAccessRequest request,
                                                        long currentServerTick) {
        if (active == null) {
            return SafeBoxEscortRuntime.AccessDecision.NO_ACTIVE_ESCORT;
        }
        SafeBoxEscortSession session = registry.activeInspectionForPlayerAt(
                request.playerId(), currentServerTick).orElse(null);
        SafeBoxEscortTarget activeTarget = active.context.target();
        if (session != null && active.accessOwned && request.matches(activeTarget)
                && session.target().equals(activeTarget)) {
            return SafeBoxEscortRuntime.AccessDecision.ALLOWED;
        }
        return SafeBoxEscortRuntime.AccessDecision.DENIED_ACTIVE_ESCORT;
    }
}
