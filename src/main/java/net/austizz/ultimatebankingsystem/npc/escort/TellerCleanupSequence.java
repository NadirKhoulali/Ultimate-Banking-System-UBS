package net.austizz.ultimatebankingsystem.npc.escort;

import java.util.UUID;

final class TellerCleanupSequence {
    private static final int MAX_EAGER_PASSES = 5;

    private final TellerRouteExecution.Actor actor;
    private final UUID sessionId;
    private final TellerEscortNavigationState.Status requestedStatus;
    private final TellerEscortNavigationState.FailureReason requestedReason;
    private int passes;
    private boolean navigationStopped;
    private boolean redstoneCleared;
    private boolean rfidCleared;
    private boolean movementLeaseReleased;
    private boolean sawFailure;

    TellerCleanupSequence(TellerRouteExecution.Actor actor,
                          UUID sessionId,
                          TellerEscortNavigationState.Status requestedStatus,
                          TellerEscortNavigationState.FailureReason requestedReason) {
        this.actor = actor;
        this.sessionId = sessionId;
        this.requestedStatus = requestedStatus;
        this.requestedReason = requestedReason;
    }

    Result advance() {
        passes++;
        navigationStopped = attempt(navigationStopped, actor::stopNavigation);
        redstoneCleared = attempt(redstoneCleared, actor::clearTemporaryRedstone);
        rfidCleared = attempt(rfidCleared, () -> actor.clearRfidAccess(sessionId));
        movementLeaseReleased = attempt(movementLeaseReleased,
                () -> actor.releaseMovementLease(sessionId));
        boolean complete = navigationStopped && redstoneCleared && rfidCleared && movementLeaseReleased;
        if (!complete && passes < MAX_EAGER_PASSES) {
            return Result.pending();
        }
        if (!complete) {
            return Result.recovering();
        }
        if (sawFailure) {
            return Result.finished(TellerEscortNavigationState.Status.FAILED,
                    TellerEscortNavigationState.FailureReason.INTERNAL_ERROR);
        }
        return Result.finished(requestedStatus, requestedReason);
    }

    private boolean attempt(boolean alreadyComplete, Runnable action) {
        if (alreadyComplete) {
            return true;
        }
        try {
            action.run();
            return true;
        } catch (RuntimeException exception) {
            sawFailure = true;
            return false;
        }
    }

    record Result(boolean finished,
                  TellerEscortNavigationState.Status status,
                  TellerEscortNavigationState.FailureReason failureReason) {
        static Result pending() {
            return new Result(false, TellerEscortNavigationState.Status.RUNNING,
                    TellerEscortNavigationState.FailureReason.NONE);
        }

        static Result recovering() {
            return new Result(false, TellerEscortNavigationState.Status.FAILED,
                    TellerEscortNavigationState.FailureReason.CLEANUP_INCOMPLETE);
        }

        static Result finished(TellerEscortNavigationState.Status status,
                               TellerEscortNavigationState.FailureReason reason) {
            return new Result(true, status, reason);
        }
    }
}
