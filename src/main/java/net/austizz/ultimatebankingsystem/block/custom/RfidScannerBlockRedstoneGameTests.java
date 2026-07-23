package net.austizz.ultimatebankingsystem.block.custom;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.RfidScannerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(UltimateBankingSystem.MODID + "_source_behavior_replacements")
@PrefixGameTestTemplate(false)
public final class RfidScannerBlockRedstoneGameTests {
    private RfidScannerBlockRedstoneGameTests() {
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 100)
    public static void configuredScannerPowersOnlyItsTargetRelay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos scannerPos = helper.absolutePos(new BlockPos(0, 4, 1));
        BlockPos relayPos = helper.absolutePos(new BlockPos(1, 4, 1));
        BlockPos targetPos = helper.absolutePos(new BlockPos(2, 4, 1));
        try {
            level.setBlock(relayPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(scannerPos, ModBlocks.RFID_SCANNER.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(targetPos, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

            if (!(level.getBlockEntity(scannerPos) instanceof RfidScannerBlockEntity scanner)) {
                helper.fail("Placed RFID scanner did not create its compiled block entity");
                return;
            }
            scanner.loadWithComponents(configuredTarget(level, targetPos, relayPos), level.registryAccess());
            CompoundTag loaded = scanner.getUpdateTag(level.registryAccess());
            require(helper, "OPEN".equals(loaded.getString("force_mode")),
                    "RFID scanner did not load configured OPEN target mode");
            require(helper, loaded.getList("success_targets", Tag.TAG_COMPOUND).size() == 1,
                    "RFID scanner did not load its configured success target");
            require(helper, scanner.getLevel() == level, "RFID scanner block entity was detached from the test world");
            RfidScannerBlockEntity.serverTick(level, scannerPos, level.getBlockState(scannerPos), scanner);

            BlockState scannerState = level.getBlockState(scannerPos);
            require(helper, !scannerState.isSignalSource(),
                    "Configured RFID scanner advertised itself as a redstone source");
            for (Direction direction : Direction.values()) {
                require(helper, scannerState.getSignal(level, scannerPos, direction) == 0,
                        "Configured RFID scanner emitted weak power toward " + direction);
                require(helper, scannerState.getDirectSignal(level, scannerPos, direction) == 0,
                        "Configured RFID scanner emitted direct power toward " + direction);
            }

            BlockState relayState = level.getBlockState(relayPos);
            require(helper, relayState.is(ModBlocks.RFID_SIGNAL_RELAY.get()),
                    "Configured success target did not create its relay");
            require(helper, level.getSignal(relayPos, Direction.WEST) == 13,
                    "Configured target relay did not preserve success power 13");
            require(helper, level.getBestNeighborSignal(targetPos) == 13,
                    "Configured target block did not receive relay power 13");
            helper.succeed();
        } finally {
            level.removeBlock(scannerPos, false);
            level.removeBlock(relayPos, false);
            level.removeBlock(targetPos, false);
        }
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 100)
    public static void heistSpoofPulsesSuccessRelayAndCanBeRevoked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos scannerPos = helper.absolutePos(new BlockPos(0, 4, 1));
        BlockPos relayPos = helper.absolutePos(new BlockPos(1, 4, 1));
        BlockPos targetPos = helper.absolutePos(new BlockPos(2, 4, 1));
        try {
            level.setBlock(relayPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(scannerPos, ModBlocks.RFID_SCANNER.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(targetPos, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            if (!(level.getBlockEntity(scannerPos) instanceof RfidScannerBlockEntity scanner)) {
                helper.fail("Placed RFID scanner did not create its compiled block entity");
                return;
            }
            scanner.loadWithComponents(spoofableTarget(level, targetPos, relayPos), level.registryAccess());
            UUID sessionId = UUID.randomUUID();
            require(helper, scanner.canSpoofSuccess(), "Configured reader rejected a valid heist spoof");
            require(helper, scanner.activateHeistSpoof(sessionId), "Heist spoof did not activate");
            require(helper, level.getBestNeighborSignal(targetPos) == 13,
                    "Spoofed success relay did not power its configured target");
            require(helper, !level.getBlockState(scannerPos).isSignalSource(),
                    "Spoofed scanner advertised itself as a redstone source");

            scanner.revokeEscortAccess(sessionId);
            require(helper, level.getBestNeighborSignal(targetPos) == 0,
                    "Heist cleanup did not revoke the spoofed success relay");
            helper.succeed();
        } finally {
            level.removeBlock(scannerPos, false);
            level.removeBlock(relayPos, false);
            level.removeBlock(targetPos, false);
        }
    }

    private static CompoundTag configuredTarget(ServerLevel level, BlockPos targetPos, BlockPos relayPos) {
        CompoundTag target = new CompoundTag();
        target.putString("dimension", dimension(level));
        target.putInt("target_x", targetPos.getX());
        target.putInt("target_y", targetPos.getY());
        target.putInt("target_z", targetPos.getZ());
        target.putInt("relay_x", relayPos.getX());
        target.putInt("relay_y", relayPos.getY());
        target.putInt("relay_z", relayPos.getZ());
        target.putString("face", Direction.WEST.name());
        target.putString("label", "Task 14 target");
        ListTag targets = new ListTag();
        targets.add(target);

        CompoundTag scanner = new CompoundTag();
        scanner.putBoolean("enabled", true);
        scanner.putString("force_mode", "OPEN");
        scanner.putInt("idle_signal", 7);
        scanner.putInt("success_signal", 13);
        scanner.putInt("fail_signal", 11);
        scanner.put("success_targets", targets);
        scanner.put("fail_targets", new ListTag());
        return scanner;
    }

    private static CompoundTag spoofableTarget(ServerLevel level, BlockPos targetPos, BlockPos relayPos) {
        CompoundTag scanner = configuredTarget(level, targetPos, relayPos);
        scanner.putBoolean("configured", true);
        scanner.putString("force_mode", "NORMAL");
        scanner.putInt("idle_signal", 0);
        scanner.putInt("success_duration_ticks", 60);
        return scanner;
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
