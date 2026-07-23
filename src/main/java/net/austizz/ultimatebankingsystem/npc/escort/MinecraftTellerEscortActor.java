package net.austizz.ultimatebankingsystem.npc.escort;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePosition;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteStep;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.EscortBlockPosition;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxArea;
import net.austizz.ultimatebankingsystem.block.entity.custom.RfidScannerBlockEntity;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class MinecraftTellerEscortActor implements TellerRouteExecution.Actor {
    private final MinecraftServer server;
    private final ServerLevel routeLevel;
    private final UUID tellerId;
    private final UUID authorizedPlayerId;
    private final SafeBoxArea premiseBounds;
    private final Set<BlockPos> activatedRfidScanners = new LinkedHashSet<>();
    private TemporaryDirectionalRedstoneLease redstoneLease;
    private UUID movementSessionId;

    MinecraftTellerEscortActor(MinecraftServer server,
                               ServerLevel routeLevel,
                               UUID tellerId,
                               UUID authorizedPlayerId,
                               SafeBoxArea premiseBounds) {
        this.server = server;
        this.routeLevel = routeLevel;
        this.tellerId = tellerId;
        this.authorizedPlayerId = authorizedPlayerId;
        this.premiseBounds = premiseBounds;
    }

    boolean available() {
        BankTellerEntity teller = routeTeller();
        return teller != null && !teller.isCashier();
    }

    @Override
    public boolean acquireMovementLease(UUID sessionId) {
        BankTellerEntity teller = routeTeller();
        boolean acquired = teller != null && !teller.isCashier() && teller.beginEscortMovementLease(sessionId);
        if (acquired) {
            movementSessionId = sessionId;
        }
        return acquired;
    }

    @Override
    public boolean hasMovementLease(UUID sessionId) {
        BankTellerEntity teller = routeTeller();
        return teller != null && teller.hasEscortMovementLease(sessionId);
    }

    @Override
    public void releaseMovementLease(UUID sessionId) {
        BankTellerEntity teller = findTeller();
        if (teller != null) {
            teller.endEscortMovementLease(sessionId);
        }
        if (sessionId != null && sessionId.equals(movementSessionId)) {
            movementSessionId = null;
        }
    }

    @Override
    public boolean withinPremise() {
        BankTellerEntity teller = routeTeller();
        if (teller == null || premiseBounds == null) {
            return false;
        }
        BlockPos pos = teller.blockPosition();
        return premiseBounds.contains(routeLevel.dimension().location().toString(),
                new EscortBlockPosition(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    public double distanceToSqr(SafeTellerRoutePosition target) {
        BankTellerEntity teller = routeTeller();
        TellerRouteWalkDestination.Destination destination = walkDestination(target);
        return teller == null || destination == null ? Double.POSITIVE_INFINITY : teller.distanceToSqr(
                destination.x(), destination.y(), destination.z());
    }

    @Override
    public boolean moveTo(SafeTellerRoutePosition target, double speed) {
        BankTellerEntity teller = routeTeller();
        TellerRouteWalkDestination.Destination destination = walkDestination(target);
        return teller != null && destination != null && contains(target) && teller.getNavigation().moveTo(
                destination.x(), destination.y(), destination.z(), speed);
    }

    @Override
    public boolean navigationDone() {
        BankTellerEntity teller = routeTeller();
        return teller == null || teller.getNavigation().isDone();
    }

    @Override
    public void stopNavigation() {
        BankTellerEntity teller = findTeller();
        if (teller != null) {
            teller.getNavigation().stop();
        }
    }

    @Override
    public Optional<TellerEscortNavigationState.FailureReason> startTemporaryRedstone(
            SafeTellerRouteStep.Redstone step) {
        clearTemporaryRedstone();
        TemporaryDirectionalRedstoneLease.Attempt attempt = TemporaryDirectionalRedstoneLease.acquire(
                routeLevel, step.target(), step.face(), step.strength(), step.durationTicks());
        redstoneLease = attempt.lease();
        if (!attempt.success()) {
            return Optional.of(attempt.failureReason());
        }
        return Optional.empty();
    }

    @Override
    public void clearTemporaryRedstone() {
        if (redstoneLease != null) {
            redstoneLease.close();
            redstoneLease = null;
        }
    }

    @Override
    public Optional<TellerEscortNavigationState.FailureReason> activateRfid(
            SafeTellerRouteStep.Rfid step) {
        if (step == null || step.scanner() == null || !contains(step.scanner())) {
            return Optional.of(TellerEscortNavigationState.FailureReason.OUTSIDE_BANK_PREMISE);
        }
        BlockPos pos = new BlockPos(step.scanner().x(), step.scanner().y(), step.scanner().z());
        LevelChunk chunk = routeLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !(chunk.getBlockEntities().get(pos) instanceof RfidScannerBlockEntity scanner)) {
            return Optional.of(TellerEscortNavigationState.FailureReason.RFID_SCANNER_UNAVAILABLE);
        }
        if (!scanner.activateForEscort(movementSessionId, authorizedPlayerId)) {
            return Optional.of(TellerEscortNavigationState.FailureReason.RFID_ACCESS_DENIED);
        }
        activatedRfidScanners.add(pos.immutable());
        return Optional.empty();
    }

    @Override
    public void clearRfidAccess(UUID sessionId) {
        for (BlockPos pos : activatedRfidScanners) {
            LevelChunk chunk = routeLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null && chunk.getBlockEntities().get(pos) instanceof RfidScannerBlockEntity scanner) {
                scanner.revokeEscortAccess(sessionId);
            }
        }
        activatedRfidScanners.clear();
    }

    private boolean contains(SafeTellerRoutePosition position) {
        return premiseBounds != null && position != null
                && premiseBounds.contains(routeLevel.dimension().location().toString(),
                new EscortBlockPosition(position.x(), position.y(), position.z()));
    }

    private TellerRouteWalkDestination.Destination walkDestination(SafeTellerRoutePosition target) {
        if (target == null) {
            return null;
        }
        BlockPos anchor = new BlockPos(target.x(), target.y(), target.z());
        VoxelShape collision = routeLevel.getBlockState(anchor).getCollisionShape(routeLevel, anchor);
        double surfaceHeight = collision.isEmpty() ? 0.0D : collision.max(net.minecraft.core.Direction.Axis.Y);
        return TellerRouteWalkDestination.onSurface(target, surfaceHeight);
    }

    private BankTellerEntity routeTeller() {
        return routeLevel.getEntity(tellerId) instanceof BankTellerEntity teller ? teller : null;
    }

    private BankTellerEntity findTeller() {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(tellerId) instanceof BankTellerEntity teller) {
                return teller;
            }
        }
        return null;
    }

}
