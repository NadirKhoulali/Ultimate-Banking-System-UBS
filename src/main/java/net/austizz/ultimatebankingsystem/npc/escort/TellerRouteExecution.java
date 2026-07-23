package net.austizz.ultimatebankingsystem.npc.escort;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePosition;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteStep;

import java.util.Optional;
import java.util.UUID;

final class TellerRouteExecution {
    static final Limits DEFAULT_LIMITS = new Limits(0.75D, 0.35D, 1_200, 100);
    private static final double MIN_PROGRESS_SQUARED = 0.0025D;

    private final UUID sessionId;
    private final UUID tellerId;
    private final SafeTellerRoute route;
    private final Actor actor;
    private final Limits limits;
    private TellerEscortNavigationState.Status status = TellerEscortNavigationState.Status.RUNNING;
    private TellerEscortNavigationState.FailureReason failureReason =
            TellerEscortNavigationState.FailureReason.NONE;
    private int stepIndex;
    private int stepTicks;
    private int stalledTicks;
    private double lastDistanceSquared = Double.POSITIVE_INFINITY;
    private boolean stepStarted;
    private TellerCleanupSequence cleanupSequence;

    TellerRouteExecution(UUID sessionId, UUID tellerId, SafeTellerRoute route, Actor actor, Limits limits) {
        this.sessionId = sessionId;
        this.tellerId = tellerId;
        this.route = route;
        this.actor = actor;
        this.limits = limits;
    }

    boolean start() {
        return actor.acquireMovementLease(sessionId);
    }

    void tick() {
        if (cleanupSequence != null) {
            continueCleanup();
            return;
        }
        if (status != TellerEscortNavigationState.Status.RUNNING) {
            return;
        }
        try {
            if (!actor.hasMovementLease(sessionId)) {
                finish(TellerEscortNavigationState.Status.FAILED,
                        TellerEscortNavigationState.FailureReason.LEASE_LOST);
                return;
            }
            if (!actor.withinPremise()) {
                finish(TellerEscortNavigationState.Status.FAILED,
                        TellerEscortNavigationState.FailureReason.OUTSIDE_BANK_PREMISE);
                return;
            }
            if (stepIndex >= route.steps().size()) {
                finish(TellerEscortNavigationState.Status.ARRIVED,
                        TellerEscortNavigationState.FailureReason.NONE);
                return;
            }
            SafeTellerRouteStep step = route.steps().get(stepIndex);
            boolean justStarted = false;
            if (!stepStarted) {
                if (!startStep(step)) {
                    return;
                }
                justStarted = true;
            }
            tickStep(step, justStarted);
        } catch (RuntimeException exception) {
            finish(TellerEscortNavigationState.Status.FAILED,
                    TellerEscortNavigationState.FailureReason.INTERNAL_ERROR);
        }
    }

    private boolean startStep(SafeTellerRouteStep step) {
        if (step == null) {
            throw new IllegalArgumentException("Route step is missing");
        }
        stepStarted = true;
        stepTicks = 0;
        stalledTicks = 0;
        return switch (step) {
            case SafeTellerRouteStep.Walk walk -> startWalk(walk);
            case SafeTellerRouteStep.Wait ignored -> true;
            case SafeTellerRouteStep.Redstone redstone -> startRedstone(redstone);
            case SafeTellerRouteStep.Rfid rfid -> startRfid(rfid);
        };
    }

    private boolean startWalk(SafeTellerRouteStep.Walk walk) {
        lastDistanceSquared = actor.distanceToSqr(walk.target());
        stepTicks = 1;
        if (arrived(lastDistanceSquared)) {
            advanceStep(true);
            return false;
        }
        if (!actor.moveTo(walk.target(), limits.movementSpeed())) {
            finish(TellerEscortNavigationState.Status.FAILED,
                    TellerEscortNavigationState.FailureReason.PATH_NOT_FOUND);
        }
        return false;
    }

    private boolean startRedstone(SafeTellerRouteStep.Redstone redstone) {
        Optional<TellerEscortNavigationState.FailureReason> failure =
                actor.startTemporaryRedstone(redstone);
        if (failure.isPresent()) {
            finish(TellerEscortNavigationState.Status.FAILED, failure.get());
            return false;
        }
        return true;
    }

    private boolean startRfid(SafeTellerRouteStep.Rfid rfid) {
        Optional<TellerEscortNavigationState.FailureReason> failure = actor.activateRfid(rfid);
        if (failure.isPresent()) {
            finish(TellerEscortNavigationState.Status.FAILED, failure.get());
            return false;
        }
        advanceStep(false);
        return false;
    }

    private void tickStep(SafeTellerRouteStep step, boolean justStarted) {
        switch (step) {
            case null -> throw new IllegalArgumentException("Route step is missing");
            case SafeTellerRouteStep.Walk walk -> tickWalk(walk);
            case SafeTellerRouteStep.Wait wait -> {
                if (++stepTicks >= wait.durationTicks()) {
                    advanceStep(false);
                }
            }
            case SafeTellerRouteStep.Redstone redstone -> {
                if (!justStarted && ++stepTicks >= redstone.durationTicks()) {
                    advanceStep(true);
                }
            }
            case SafeTellerRouteStep.Rfid ignored -> {
                // RFID activation advances immediately from startRfid().
            }
        }
    }

    private void tickWalk(SafeTellerRouteStep.Walk walk) {
        double distanceSquared = actor.distanceToSqr(walk.target());
        if (arrived(distanceSquared)) {
            advanceStep(true);
            return;
        }
        if (actor.navigationDone()) {
            finish(TellerEscortNavigationState.Status.FAILED,
                    TellerEscortNavigationState.FailureReason.PATH_NOT_FOUND);
            return;
        }
        if (++stepTicks >= limits.maxWalkTicks()) {
            finish(TellerEscortNavigationState.Status.FAILED,
                    TellerEscortNavigationState.FailureReason.WALK_TIMEOUT);
            return;
        }
        if (distanceSquared < lastDistanceSquared - MIN_PROGRESS_SQUARED) {
            stalledTicks = 0;
        } else if (++stalledTicks >= limits.maxStallTicks()) {
            finish(TellerEscortNavigationState.Status.FAILED,
                    TellerEscortNavigationState.FailureReason.WALK_STALLED);
            return;
        }
        lastDistanceSquared = distanceSquared;
    }

    private boolean arrived(double distanceSquared) {
        return distanceSquared <= limits.arrivalTolerance() * limits.arrivalTolerance();
    }

    private void advanceStep(boolean stopCurrentAction) {
        if (stopCurrentAction && cleanupCurrentAction()) {
            finish(TellerEscortNavigationState.Status.FAILED,
                    TellerEscortNavigationState.FailureReason.INTERNAL_ERROR);
            return;
        }
        stepIndex++;
        stepStarted = false;
        if (stepIndex >= route.steps().size()) {
            finish(TellerEscortNavigationState.Status.ARRIVED,
                    TellerEscortNavigationState.FailureReason.NONE);
        }
    }

    void cancel() {
        if (status == TellerEscortNavigationState.Status.RUNNING) {
            finish(TellerEscortNavigationState.Status.CANCELLED,
                    TellerEscortNavigationState.FailureReason.NONE);
        }
    }

    private void finish(TellerEscortNavigationState.Status terminalStatus,
                        TellerEscortNavigationState.FailureReason reason) {
        if (status != TellerEscortNavigationState.Status.RUNNING || cleanupSequence != null) {
            return;
        }
        cleanupSequence = new TellerCleanupSequence(actor, sessionId, terminalStatus, reason);
        continueCleanup();
    }

    void failInternal() {
        if (status == TellerEscortNavigationState.Status.RUNNING && cleanupSequence == null) {
            finish(TellerEscortNavigationState.Status.FAILED,
                    TellerEscortNavigationState.FailureReason.INTERNAL_ERROR);
        }
    }

    private boolean cleanupCurrentAction() {
        boolean failed = false;
        try {
            actor.stopNavigation();
        } catch (RuntimeException exception) {
            failed = true;
        }
        try {
            actor.clearTemporaryRedstone();
        } catch (RuntimeException exception) {
            failed = true;
        }
        return failed;
    }

    private void continueCleanup() {
        TellerCleanupSequence.Result result = cleanupSequence.advance();
        status = result.status();
        failureReason = result.failureReason();
        if (result.finished()) {
            cleanupSequence = null;
        }
    }

    boolean terminal() {
        return cleanupSequence == null && status != TellerEscortNavigationState.Status.RUNNING;
    }

    TellerEscortNavigationState snapshot() {
        return new TellerEscortNavigationState(sessionId, tellerId, status, failureReason, stepIndex);
    }

    record Limits(double arrivalTolerance, double movementSpeed, int maxWalkTicks, int maxStallTicks) {
        Limits {
            if (arrivalTolerance <= 0.0D || movementSpeed <= 0.0D
                    || maxWalkTicks < 1 || maxStallTicks < 1) {
                throw new IllegalArgumentException("Navigation limits must be positive");
            }
        }
    }

    interface Actor {
        boolean acquireMovementLease(UUID sessionId);

        boolean hasMovementLease(UUID sessionId);

        boolean withinPremise();

        void releaseMovementLease(UUID sessionId);

        double distanceToSqr(SafeTellerRoutePosition target);

        boolean moveTo(SafeTellerRoutePosition target, double speed);

        boolean navigationDone();

        void stopNavigation();

        Optional<TellerEscortNavigationState.FailureReason> startTemporaryRedstone(
                SafeTellerRouteStep.Redstone step);

        void clearTemporaryRedstone();

        Optional<TellerEscortNavigationState.FailureReason> activateRfid(
                SafeTellerRouteStep.Rfid step);

        void clearRfidAccess(UUID sessionId);
    }
}
