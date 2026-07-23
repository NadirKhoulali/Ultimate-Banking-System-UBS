package net.austizz.ultimatebankingsystem.npc.escort;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteFace;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePosition;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.RfidSignalRelayBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

final class TemporaryDirectionalRedstoneLease implements AutoCloseable {
    private final TemporaryRelayTransaction transaction;

    private TemporaryDirectionalRedstoneLease(TemporaryRelayTransaction transaction) {
        this.transaction = transaction;
    }

    static Attempt acquire(ServerLevel level,
                           SafeTellerRoutePosition target,
                           SafeTellerRouteFace face,
                           int requestedPower,
                           int durationTicks) {
        BlockPos targetPos = new BlockPos(target.x(), target.y(), target.z());
        Direction targetFace = Direction.valueOf(face.name());
        BlockPos relayPos = targetPos.relative(targetFace);
        if (!level.hasChunkAt(targetPos) || !level.hasChunkAt(relayPos)) {
            return Attempt.failure(TellerEscortNavigationState.FailureReason.RELAY_POSITION_UNLOADED);
        }
        BlockState previousState = level.getBlockState(relayPos);
        if (!previousState.isAir() || level.getBlockEntity(relayPos) != null) {
            return Attempt.failure(TellerEscortNavigationState.FailureReason.RELAY_POSITION_OCCUPIED);
        }

        Direction outputSide = targetFace;
        int clampedPower = Math.max(1, Math.min(15, requestedPower));
        BlockState placed = ModBlocks.RFID_SIGNAL_RELAY.get().defaultBlockState()
                .setValue(RfidSignalRelayBlock.SIGNAL_SIDE, outputSide)
                .setValue(RfidSignalRelayBlock.POWER, clampedPower)
                .setValue(RfidSignalRelayBlock.TEMPORARY, true);
        TemporaryRelayTransaction.Attempt transaction = TemporaryRelayTransaction.acquire(
                new MinecraftOperations(level, targetPos, relayPos, placed, previousState, durationTicks));
        TemporaryDirectionalRedstoneLease lease = transaction.transaction() == null
                ? null : new TemporaryDirectionalRedstoneLease(transaction.transaction());
        return transaction.success()
                ? Attempt.success(lease)
                : Attempt.failure(TellerEscortNavigationState.FailureReason.RELAY_PLACEMENT_FAILED, lease);
    }

    @Override
    public void close() {
        transaction.close();
    }

    private static final class MinecraftOperations implements TemporaryRelayTransaction.Operations {
        private final ServerLevel level;
        private final BlockPos targetPos;
        private final BlockPos relayPos;
        private final BlockState placedState;
        private final BlockState previousState;
        private final int durationTicks;

        private MinecraftOperations(ServerLevel level,
                                    BlockPos targetPos,
                                    BlockPos relayPos,
                                    BlockState placedState,
                                    BlockState previousState,
                                    int durationTicks) {
            this.level = level;
            this.targetPos = targetPos;
            this.relayPos = relayPos;
            this.placedState = placedState;
            this.previousState = previousState;
            this.durationTicks = durationTicks;
        }

        @Override
        public void scheduleFallback() {
            level.scheduleTick(relayPos, placedState.getBlock(),
                    TemporaryRelayLeaseState.initialFallbackDelay(durationTicks));
        }

        @Override
        public boolean place() {
            return level.setBlock(relayPos, placedState, Block.UPDATE_ALL);
        }

        @Override
        public boolean claim(UUID token) {
            return TemporaryRelayLeaseState.claim(level, relayPos, token, durationTicks);
        }

        @Override
        public TemporaryRelayTransaction.Ownership ownership(UUID token) {
            return TemporaryRelayLeaseState.ownership(level, relayPos, token);
        }

        @Override
        public boolean release(UUID token) {
            return TemporaryRelayLeaseState.release(level, relayPos, token);
        }

        @Override
        public boolean restore() {
            BlockState current = level.getBlockState(relayPos);
            if (current.equals(previousState)) {
                level.updateNeighborsAt(targetPos, placedState.getBlock());
                return true;
            }
            if (!current.is(ModBlocks.RFID_SIGNAL_RELAY.get())
                    || !current.getValue(RfidSignalRelayBlock.TEMPORARY)) {
                return true;
            }
            boolean changed = level.setBlock(relayPos, previousState, Block.UPDATE_ALL);
            if (changed || level.getBlockState(relayPos).equals(previousState)) {
                level.updateNeighborsAt(targetPos, placedState.getBlock());
                return true;
            }
            return false;
        }

        @Override
        public void notifyTarget() {
            level.neighborChanged(targetPos, placedState.getBlock(), relayPos);
        }
    }

    record Attempt(TemporaryDirectionalRedstoneLease lease,
                   TellerEscortNavigationState.FailureReason failureReason) {
        static Attempt success(TemporaryDirectionalRedstoneLease lease) {
            return new Attempt(lease, TellerEscortNavigationState.FailureReason.NONE);
        }

        static Attempt failure(TellerEscortNavigationState.FailureReason reason) {
            return new Attempt(null, reason);
        }

        static Attempt failure(TellerEscortNavigationState.FailureReason reason,
                               TemporaryDirectionalRedstoneLease cleanupLease) {
            return new Attempt(cleanupLease, reason);
        }

        boolean success() {
            return failureReason == TellerEscortNavigationState.FailureReason.NONE;
        }
    }
}
