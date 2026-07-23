package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import java.util.Objects;
import java.util.UUID;

final class SafeBoxEscortRuntimeAuthorization {
    private final SafeBoxEscortRuntimeState state;
    private final SafeBoxEscortAuthorizationPolicy liveAuthorization;

    SafeBoxEscortRuntimeAuthorization(SafeBoxEscortRuntimeState state,
                                      SafeBoxEscortRuntimePorts.Effects effects) {
        this.state = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(effects, "effects");
        this.liveAuthorization = (playerId, target) -> {
            SafeBoxEscortRuntimeState.Active active = state.forPlayer(playerId);
            if (active == null || !active.context.target().equals(target)) {
                return SafeBoxEscortAuthorizationPolicy.Decision.DENIED;
            }
            try {
                return effects.freshlyAuthorized(active.context)
                        ? SafeBoxEscortAuthorizationPolicy.Decision.GRANTED
                        : SafeBoxEscortAuthorizationPolicy.Decision.DENIED;
            } catch (RuntimeException ignored) {
                return SafeBoxEscortAuthorizationPolicy.Decision.DENIED;
            }
        };
    }

    SafeBoxEscortAuthorizationPolicy policy() {
        return liveAuthorization;
    }

    SafeBoxEscortRuntime.AccessDecision inspect(SafeBoxEscortRegistry registry,
                                                SafeBoxEscortAccessRequest request,
                                                long currentServerTick) {
        Objects.requireNonNull(request, "request");
        return currentDecision(request.playerId(), SafeBoxEscortAccessPolicy.inspect(
                registry, state.forPlayer(request.playerId()), request, currentServerTick));
    }

    private SafeBoxEscortRuntime.AccessDecision currentDecision(
            UUID playerId,
            SafeBoxEscortRuntime.AccessDecision decision) {
        if (decision != SafeBoxEscortRuntime.AccessDecision.ALLOWED) {
            return decision;
        }
        SafeBoxEscortRuntimeState.Active active = state.forPlayer(playerId);
        return active != null && liveAuthorization.authorize(playerId, active.context.target())
                == SafeBoxEscortAuthorizationPolicy.Decision.GRANTED
                ? SafeBoxEscortRuntime.AccessDecision.ALLOWED
                : SafeBoxEscortRuntime.AccessDecision.DENIED_ACTIVE_ESCORT;
    }
}
