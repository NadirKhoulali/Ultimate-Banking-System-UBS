package net.austizz.ultimatebankingsystem.npc.escort;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePosition;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteStep;

import java.util.EnumMap;
import java.util.Optional;
import java.util.UUID;

final class TellerEscortTestActor implements TellerRouteExecution.Actor {
    enum ThrowPoint {
        MOVE,
        REDSTONE,
        STOP_NAVIGATION,
        CLEAR_REDSTONE,
        RELEASE_LEASE
    }

    private static final int ALWAYS_FAIL = -1;
    private final EnumMap<ThrowPoint, Integer> failures = new EnumMap<>(ThrowPoint.class);
    private TellerEscortNavigationCoordinator coordinator;
    private UUID leaseSession;
    private double x;
    private double y = 64.0D;
    private double z;
    private boolean navigationActive;
    private boolean redstoneActive;
    private int moveCount;
    private int redstoneStartCount;
    private int releaseCount;
    private int redstoneClearCount;
    private SafeTellerRoutePosition lastMoveTarget;
    private double lastMoveSpeed;
    private SafeTellerRouteStep.Redstone lastRedstoneStep;
    private TellerEscortNavigationState.FailureReason redstoneFailure;
    private TellerEscortNavigationState.FailureReason rfidFailure;
    private boolean insidePremise = true;
    private int rfidActivationCount;
    private int rfidClearCount;

    @Override
    public boolean acquireMovementLease(UUID sessionId) {
        if (leaseSession != null && !leaseSession.equals(sessionId)) {
            return false;
        }
        leaseSession = sessionId;
        return true;
    }

    @Override
    public boolean hasMovementLease(UUID sessionId) {
        return sessionId.equals(leaseSession);
    }

    @Override
    public boolean withinPremise() {
        return insidePremise;
    }

    @Override
    public void releaseMovementLease(UUID sessionId) {
        throwIfConfigured(ThrowPoint.RELEASE_LEASE);
        if (sessionId.equals(leaseSession)) {
            leaseSession = null;
            releaseCount++;
        }
    }

    @Override
    public double distanceToSqr(SafeTellerRoutePosition target) {
        double dx = target.x() - x;
        double dy = target.y() - y;
        double dz = target.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public boolean moveTo(SafeTellerRoutePosition target, double speed) {
        moveCount++;
        lastMoveTarget = target;
        lastMoveSpeed = speed;
        navigationActive = true;
        throwIfConfigured(ThrowPoint.MOVE);
        return true;
    }

    @Override
    public boolean navigationDone() {
        return !navigationActive;
    }

    @Override
    public void stopNavigation() {
        throwIfConfigured(ThrowPoint.STOP_NAVIGATION);
        navigationActive = false;
    }

    @Override
    public Optional<TellerEscortNavigationState.FailureReason> startTemporaryRedstone(
            SafeTellerRouteStep.Redstone step) {
        redstoneStartCount++;
        lastRedstoneStep = step;
        if (redstoneFailure != null) {
            return Optional.of(redstoneFailure);
        }
        redstoneActive = true;
        throwIfConfigured(ThrowPoint.REDSTONE);
        return Optional.empty();
    }

    @Override
    public void clearTemporaryRedstone() {
        throwIfConfigured(ThrowPoint.CLEAR_REDSTONE);
        if (redstoneActive) {
            redstoneActive = false;
            redstoneClearCount++;
        }
    }

    @Override
    public Optional<TellerEscortNavigationState.FailureReason> activateRfid(
            SafeTellerRouteStep.Rfid step) {
        rfidActivationCount++;
        return Optional.ofNullable(rfidFailure);
    }

    @Override
    public void clearRfidAccess(UUID sessionId) {
        rfidClearCount++;
    }

    void attach(TellerEscortNavigationCoordinator value) {
        coordinator = value;
    }

    TellerEscortNavigationCoordinator coordinator() {
        return coordinator;
    }

    void arriveAt(SafeTellerRoutePosition target) {
        x = target.x();
        y = target.y();
        z = target.z();
        navigationActive = false;
    }

    void moveCloser(double amount) {
        x += amount;
    }

    void activateNavigationForTest() {
        navigationActive = true;
    }

    void failRedstoneWith(TellerEscortNavigationState.FailureReason reason) {
        redstoneFailure = reason;
    }

    void failRfidWith(TellerEscortNavigationState.FailureReason reason) {
        rfidFailure = reason;
    }

    void setInsidePremise(boolean value) {
        insidePremise = value;
    }

    void failAlways(ThrowPoint... points) {
        for (ThrowPoint point : points) {
            failures.put(point, ALWAYS_FAIL);
        }
    }

    void failNext(ThrowPoint point, int times) {
        if (times < 1) {
            throw new IllegalArgumentException("times must be positive");
        }
        failures.put(point, times);
    }

    void allow(ThrowPoint... points) {
        for (ThrowPoint point : points) {
            failures.remove(point);
        }
    }

    boolean leased() {
        return leaseSession != null;
    }

    boolean navigationActive() {
        return navigationActive;
    }

    boolean redstoneActive() {
        return redstoneActive;
    }

    int releaseCount() {
        return releaseCount;
    }

    int redstoneClearCount() {
        return redstoneClearCount;
    }

    int moveCount() {
        return moveCount;
    }

    SafeTellerRoutePosition lastMoveTarget() {
        return lastMoveTarget;
    }

    double lastMoveSpeed() {
        return lastMoveSpeed;
    }

    int redstoneStartCount() {
        return redstoneStartCount;
    }

    SafeTellerRouteStep.Redstone lastRedstoneStep() {
        return lastRedstoneStep;
    }

    int rfidActivationCount() {
        return rfidActivationCount;
    }

    int rfidClearCount() {
        return rfidClearCount;
    }

    private void throwIfConfigured(ThrowPoint point) {
        Integer remaining = failures.get(point);
        if (remaining == null) {
            return;
        }
        if (remaining > 0) {
            if (remaining == 1) {
                failures.remove(point);
            } else {
                failures.put(point, remaining - 1);
            }
        }
        throw new IllegalStateException("actor failure at " + point);
    }

}
