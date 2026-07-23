package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeAreaSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseSnapshot;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.BankVaultDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(UltimateBankingSystem.MODID)
@PrefixGameTestTemplate(false)
public final class SafeDepositStructureGameTests {
    private static final String EMPTY_TEMPLATE = "empty3x3x3";

    private SafeDepositStructureGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void rowLifecycleFeedsServiceAndUnregisters(GameTestHelper helper) {
        BlockPos relativeRow = new BlockPos(1, 1, 1);
        BlockPos worldRow = helper.absolutePos(relativeRow);
        ServerLevel level = helper.getLevel();
        ListTag areas = area(level, worldRow, worldRow);

        helper.setBlock(relativeRow, ModBlocks.SAFETY_DEPOSIT_BOX_ROW.get());
        helper.runAfterDelay(1, () -> {
            int loadedRows = SafetyDepositBoxService.countLoadedSafeRowBlocks(
                    level.getServer(), areas);
            if (loadedRows != 1) {
                helper.fail("Placed row must register with the loaded-row service; found " + loadedRows);
            }

            helper.destroyBlock(relativeRow);
            helper.runAfterDelay(1, () -> {
                int remainingRows = SafetyDepositBoxService.countLoadedSafeRowBlocks(
                        level.getServer(), areas);
                if (remainingRows != 0) {
                    helper.fail("Removed row must unregister from the loaded-row service; found "
                            + remainingRows);
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void loadedRowQueryDoesNotLoadDistantChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos distantRow = origin.offset(8_192, 0, 8_192);
        int chunkX = distantRow.getX() >> 4;
        int chunkZ = distantRow.getZ() >> 4;
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
            helper.fail("Distant test chunk unexpectedly started loaded");
        }

        LoadedSafeStructureIndex.register(
                level.getServer(),
                level.dimension().location().toString(),
                distantRow,
                LoadedSafeStructureIndex.Kind.ROW);
        int loadedRows = SafetyDepositBoxService.countLoadedSafeRowBlocks(
                level.getServer(), area(level, distantRow, distantRow));

        if (loadedRows != 0) {
            helper.fail("Unloaded indexed row must not be treated as present");
        }
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
            helper.fail("Loaded-row query must not load a distant chunk");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void vaultDoorLifecycleIndexesCompleteMasterAndUnregisters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos master = helper.absolutePos(new BlockPos(8, 1, 8));
        BankVaultDoorBlock door = (BankVaultDoorBlock) ModBlocks.BANK_VAULT_DOOR.get();
        BlockState masterState = door.defaultBlockState();
        level.setBlock(master, masterState, Block.UPDATE_ALL);
        door.setPlacedBy(level, master, masterState, null, ItemStack.EMPTY);

        helper.runAfterDelay(1, () -> {
            if (!BankVaultDoorBlock.isCompleteMultiblock(level, master)) {
                helper.fail("Placed vault door must form a complete multiblock");
            }
            List<LoadedSafeStructureIndex.Entry> indexed = vaultDoorEntries(level, master);
            if (indexed.size() != 1 || !indexed.getFirst().blockPos().equals(master)) {
                helper.fail("Complete vault door must index exactly its master; found " + indexed.size());
            }

            level.destroyBlock(master, false);
            helper.runAfterDelay(1, () -> {
                if (!vaultDoorEntries(level, master).isEmpty()) {
                    helper.fail("Removed vault door master must unregister from the loaded index");
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void boundaryVaultDoorCountsWhenItIntersectsSafeArea(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos master = helper.absolutePos(new BlockPos(8, 1, 8));
        BankVaultDoorBlock door = (BankVaultDoorBlock) ModBlocks.BANK_VAULT_DOOR.get();
        BlockState masterState = door.defaultBlockState();
        level.setBlock(master, masterState, Block.UPDATE_ALL);
        door.setPlacedBy(level, master, masterState, null, ItemStack.EMPTY);

        helper.runAfterDelay(1, () -> {
            List<BlockPos> parts = BankVaultDoorBlock.multiblockPartPositions(level, master);
            SafeBlockBounds premiseBounds = bounds(level, parts);
            BlockPos claimedPart = master.above();
            SafeBlockBounds safeBounds = new SafeBlockBounds(
                    level.dimension().location().toString(),
                    claimedPart.getX(), claimedPart.getY(), claimedPart.getZ(),
                    claimedPart.getX(), claimedPart.getY(), claimedPart.getZ());
            SafeAreaSnapshot safeArea = new SafeAreaSnapshot("safe", "premise", safeBounds, List.of());
            SafePremiseSnapshot premise = new SafePremiseSnapshot(
                    "premise", "bank", premiseBounds, null, null, List.of(safeArea));

            if (safeBounds.contains(master.getX(), master.getY(), master.getZ())) {
                helper.fail("Regression fixture must keep the door master outside the safe claim");
            }
            BlockPos resolved = SafetyDepositBoxService.completeDoorMasterWithin(
                    level, premise, safeArea, master);
            if (!master.equals(resolved)) {
                helper.fail("A complete boundary door inside the premises must satisfy safe-area readiness");
            }
            level.destroyBlock(master, false);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void readinessRebuildsMissingLoadedDoorIndex(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos master = helper.absolutePos(new BlockPos(8, 1, 8));
        BankVaultDoorBlock door = (BankVaultDoorBlock) ModBlocks.BANK_VAULT_DOOR.get();
        BlockState masterState = door.defaultBlockState();
        level.setBlock(master, masterState, Block.UPDATE_ALL);
        door.setPlacedBy(level, master, masterState, null, ItemStack.EMPTY);

        helper.runAfterDelay(1, () -> {
            List<BlockPos> parts = BankVaultDoorBlock.multiblockPartPositions(level, master);
            SafeBlockBounds premiseBounds = bounds(level, parts);
            SafeBlockBounds safeBounds = new SafeBlockBounds(
                    level.dimension().location().toString(),
                    master.getX(), master.getY(), master.getZ(),
                    master.getX(), master.getY(), master.getZ());
            SafeAreaSnapshot safeArea = new SafeAreaSnapshot("safe", "premise", safeBounds, List.of());
            SafePremiseSnapshot premise = new SafePremiseSnapshot(
                    "premise", "bank", premiseBounds, null, null, List.of(safeArea));

            LoadedSafeStructureIndex.clear(level.getServer());
            SafetyDepositBoxService.reconcileLoadedVaultDoorIndex(level, premise, safeArea);
            List<LoadedSafeStructureIndex.Entry> indexed = vaultDoorEntries(level, master);
            if (indexed.size() != 1 || !master.equals(indexed.getFirst().blockPos())) {
                helper.fail("Readiness must rebuild a missing loaded vault-door index entry");
            }
            level.destroyBlock(master, false);
            helper.succeed();
        });
    }

    private static List<LoadedSafeStructureIndex.Entry> vaultDoorEntries(ServerLevel level,
                                                                          BlockPos master) {
        return LoadedSafeStructureIndex.findInBounds(
                level.getServer(),
                LoadedSafeStructureIndex.Kind.VAULT_DOOR_MASTER,
                List.of(new SafeBlockBounds(
                        level.dimension().location().toString(),
                        master.getX(), master.getY(), master.getZ(),
                        master.getX(), master.getY(), master.getZ())),
                entry -> true);
    }

    private static ListTag area(ServerLevel level, BlockPos min, BlockPos max) {
        CompoundTag area = new CompoundTag();
        area.putString("dimension", level.dimension().location().toString());
        area.putInt("minX", Math.min(min.getX(), max.getX()));
        area.putInt("minY", Math.min(min.getY(), max.getY()));
        area.putInt("minZ", Math.min(min.getZ(), max.getZ()));
        area.putInt("maxX", Math.max(min.getX(), max.getX()));
        area.putInt("maxY", Math.max(min.getY(), max.getY()));
        area.putInt("maxZ", Math.max(min.getZ(), max.getZ()));
        ListTag areas = new ListTag();
        areas.add(area);
        return areas;
    }

    private static SafeBlockBounds bounds(ServerLevel level, List<BlockPos> positions) {
        return new SafeBlockBounds(
                level.dimension().location().toString(),
                positions.stream().mapToInt(BlockPos::getX).min().orElseThrow(),
                positions.stream().mapToInt(BlockPos::getY).min().orElseThrow(),
                positions.stream().mapToInt(BlockPos::getZ).min().orElseThrow(),
                positions.stream().mapToInt(BlockPos::getX).max().orElseThrow(),
                positions.stream().mapToInt(BlockPos::getY).max().orElseThrow(),
                positions.stream().mapToInt(BlockPos::getZ).max().orElseThrow());
    }
}
