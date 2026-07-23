package net.austizz.ultimatebankingsystem.npc.escort;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteFace;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePosition;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.RfidSignalRelayBlock;
import net.austizz.ultimatebankingsystem.entity.ModEntities;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(UltimateBankingSystem.MODID)
@PrefixGameTestTemplate(false)
public final class TellerEscortNavigationGameTests {
    private static final String EMPTY_TEMPLATE = "empty3x3x3";

    private TellerEscortNavigationGameTests() {
    }
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void temporaryRelayPowersExactlySelectedTargetFaceForAllSixDirections(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos targetPos = helper.absolutePos(new BlockPos(1, 4, 1));
        clearCube(level, targetPos, 2);
        for (Direction targetFace : Direction.values()) {
            int power = 10 + targetFace.ordinal() % 5;
            TemporaryDirectionalRedstoneLease lease = acquire(
                    helper, targetPos, targetFace, power, 60);
            BlockPos relayPos = targetPos.relative(targetFace);
            BlockState relay = level.getBlockState(relayPos);
            if (relay.getValue(RfidSignalRelayBlock.SIGNAL_SIDE) != targetFace) {
                helper.fail("Relay for " + targetFace + " exposed "
                        + relay.getValue(RfidSignalRelayBlock.SIGNAL_SIDE)
                        + " instead of the selected target face");
            }
            for (Direction queriedFace : Direction.values()) {
                int expected = queriedFace == targetFace ? power : 0;
                int actual = level.getSignal(relayPos, queriedFace);
                if (actual != expected) {
                    helper.fail("Relay for " + targetFace + " returned " + actual
                            + " on " + queriedFace + "; expected " + expected);
                }
            }
            if (level.getBestNeighborSignal(targetPos) != power) {
                helper.fail("Target face " + targetFace + " did not receive power " + power);
            }
            assertUnrelatedNeighborsUnpowered(helper, level, targetPos, relayPos, power);
            lease.close();
            if (!level.getBlockState(relayPos).isAir()) {
                helper.fail("Closing " + targetFace + " relay did not restore air");
            }
        }
        helper.succeed();
    }
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void staleCleanupFromLeaseADoesNotRemoveReplacementLeaseB(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos targetPos = helper.absolutePos(new BlockPos(1, 4, 1));
        clearCube(level, targetPos, 2);
        TemporaryDirectionalRedstoneLease leaseA = acquire(
                helper, targetPos, Direction.EAST, 4, 2);
        leaseA.close();
        TemporaryDirectionalRedstoneLease leaseB = acquire(
                helper, targetPos, Direction.EAST, 13, 40);
        BlockPos relayPos = targetPos.east();
        helper.runAfterDelay(3, () -> {
            BlockState current = level.getBlockState(relayPos);
            if (!current.is(ModBlocks.RFID_SIGNAL_RELAY.get())
                    || !current.getValue(RfidSignalRelayBlock.TEMPORARY)
                    || current.getValue(RfidSignalRelayBlock.POWER) != 13) {
                helper.fail("Lease A cleanup removed or changed replacement lease B");
            }
            leaseB.close();
            if (!level.getBlockState(relayPos).isAir()) {
                helper.fail("Lease B close did not restore air");
            }
            helper.succeed();
        });
    }
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void restartAndAbandonedFallbackClearOnlyTemporaryRelays(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos restartTarget = helper.absolutePos(new BlockPos(1, 4, 1));
        BlockPos temporaryPos = restartTarget.north();
        BlockPos persistentPos = restartTarget.offset(3, 0, 0);
        BlockPos abandonedTarget = restartTarget.offset(6, 0, 0);
        BlockPos abandonedRelay = abandonedTarget.west();
        clearCube(level, restartTarget, 1);
        clearCube(level, persistentPos, 1);
        clearCube(level, abandonedTarget, 1);
        BlockState persistent = relayState(Direction.SOUTH, 7, false);
        level.setBlock(persistentPos, persistent, Block.UPDATE_ALL);
        level.scheduleTick(persistentPos, persistent.getBlock(), 2);
        TemporaryDirectionalRedstoneLease restartLease = acquire(
                helper, restartTarget, Direction.NORTH, 8, 40);
        TemporaryRelayLeaseState.forgetRuntimeOwner(level, temporaryPos);
        level.getBlockTicks().clearArea(new BoundingBox(temporaryPos));
        level.scheduleTick(temporaryPos, ModBlocks.RFID_SIGNAL_RELAY.get(), 2);
        TemporaryDirectionalRedstoneLease abandoned = acquire(
                helper, abandonedTarget, Direction.WEST, 5, 2);
        helper.runAfterDelay(3, () -> {
            if (!level.getBlockState(temporaryPos).isAir()
                    || TemporaryRelayLeaseState.hasLease(level, temporaryPos)) {
                helper.fail("Restart fallback did not clear temporary relay state");
            }
            if (!level.getBlockState(abandonedRelay).isAir()
                    || TemporaryRelayLeaseState.hasLease(level, abandonedRelay)) {
                helper.fail("Expired abandoned relay did not clear its block and owner metadata");
            }
            BlockState currentPersistent = level.getBlockState(persistentPos);
            if (!currentPersistent.is(ModBlocks.RFID_SIGNAL_RELAY.get())
                    || currentPersistent.getValue(RfidSignalRelayBlock.TEMPORARY)) {
                helper.fail("Fallback cleanup changed a persistent RFID relay");
            }
            restartLease.close();
            abandoned.close();
            TemporaryDirectionalRedstoneLease replacement = acquire(
                    helper, abandonedTarget, Direction.WEST, 9, 20);
            replacement.close();
            level.removeBlock(persistentPos, false);
            helper.succeed();
        });
    }
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void realTellerMovementLeaseRestoresStationaryStateAndIsNotSerialized(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BankTellerEntity teller = ModEntities.BANK_TELLER.get().create(level);
        if (teller == null) {
            helper.fail("Bank teller entity type did not create an entity");
            return;
        }
        teller.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        level.addFreshEntity(teller);
        UUID sessionId = UUID.randomUUID();
        float fixedYaw = 32.0F;
        teller.alignBodyTo(fixedYaw);
        assertStationary(helper, teller, sessionId, fixedYaw, "before lease");
        if (!teller.beginEscortMovementLease(sessionId)) {
            helper.fail("Teller rejected its first movement lease");
        }
        if (teller.isNoAi() || !teller.hasEscortMovementLease(sessionId)
                || teller.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) <= 0.0D) {
            helper.fail("Lease did not enable teller AI and movement speed");
        }
        CompoundTag saved = new CompoundTag();
        teller.addAdditionalSaveData(saved);
        teller.setDeltaMovement(0.4D, 0.0D, 0.2D);
        teller.setYRot(91.0F);
        teller.setYBodyRot(91.0F);
        teller.setYHeadRot(91.0F);
        if (!teller.endEscortMovementLease(sessionId)) {
            helper.fail("Teller rejected movement lease cleanup");
        }
        assertStationary(helper, teller, sessionId, fixedYaw, "after lease");
        BankTellerEntity reloaded = ModEntities.BANK_TELLER.get().create(level);
        if (reloaded == null) {
            helper.fail("Bank teller entity type did not create reload probe");
            return;
        }
        reloaded.readAdditionalSaveData(saved);
        assertStationary(helper, reloaded, sessionId, fixedYaw, "after reload");
        reloaded.discard();
        teller.setDeltaMovement(0.3D, 0.0D, 0.0D);
        helper.runAfterDelay(2, () -> {
            assertStationary(helper, teller, sessionId, fixedYaw, "outside lease tick");
            teller.discard();
            helper.succeed();
        });
    }
    private static TemporaryDirectionalRedstoneLease acquire(GameTestHelper helper,
                                                               BlockPos targetPos,
                                                               Direction targetFace,
                                                               int power,
                                                               int durationTicks) {
        TemporaryDirectionalRedstoneLease.Attempt attempt = TemporaryDirectionalRedstoneLease.acquire(
                helper.getLevel(), position(targetPos), SafeTellerRouteFace.valueOf(targetFace.name()),
                power, durationTicks);
        if (!attempt.success()) {
            helper.fail("Could not place " + targetFace + " relay: " + attempt.failureReason());
            throw new IllegalStateException("GameTest failure did not abort");
        }
        return attempt.lease();
    }
    private static void assertUnrelatedNeighborsUnpowered(GameTestHelper helper,
                                                           ServerLevel level,
                                                           BlockPos targetPos,
                                                           BlockPos relayPos,
                                                           int power) {
        for (Direction offset : Direction.values()) {
            BlockPos adjacent = relayPos.relative(offset);
            int expected = adjacent.equals(targetPos) ? power : 0;
            int actual = level.getBestNeighborSignal(adjacent);
            if (actual != expected) {
                helper.fail("Relay activated unrelated adjacent position " + adjacent
                        + " with " + actual + "; expected " + expected);
            }
        }
    }
    private static void assertStationary(GameTestHelper helper,
                                         BankTellerEntity teller,
                                         UUID sessionId,
                                         float fixedYaw,
                                         String phase) {
        if (!teller.isNoAi()
                || teller.hasEscortMovementLease(sessionId)
                || teller.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) != 0.0D
                || !teller.getNavigation().isDone()
                || teller.getDeltaMovement().lengthSqr() != 0.0D
                || teller.isPushable()
                || teller.isPushedByFluid()
                || !teller.isInvulnerable()
                || Math.abs(Mth.wrapDegrees(teller.getYRot() - fixedYaw)) > 0.001F
                || Math.abs(Mth.wrapDegrees(teller.getVisualRotationYInDegrees() - fixedYaw)) > 0.001F
                || Math.abs(Mth.wrapDegrees(teller.getYHeadRot() - fixedYaw)) > 0.001F) {
            helper.fail("Teller was not fully stationary and protected " + phase);
        }
    }
    private static BlockState relayState(Direction side, int power, boolean temporary) {
        return ModBlocks.RFID_SIGNAL_RELAY.get().defaultBlockState()
                .setValue(RfidSignalRelayBlock.SIGNAL_SIDE, side)
                .setValue(RfidSignalRelayBlock.POWER, power)
                .setValue(RfidSignalRelayBlock.TEMPORARY, temporary);
    }
    private static SafeTellerRoutePosition position(BlockPos pos) {
        return new SafeTellerRoutePosition(pos.getX(), pos.getY(), pos.getZ());
    }
    private static void clearCube(ServerLevel level, BlockPos center, int radius) {
        BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                        center.offset(radius, radius, radius))
                .forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
    }
}
