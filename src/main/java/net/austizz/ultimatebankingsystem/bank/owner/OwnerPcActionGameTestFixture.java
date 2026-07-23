package net.austizz.ultimatebankingsystem.bank.owner;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.owner.OwnerPcActionGameTestPorts.CountingCentralBank;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.network.OwnerPcActionRequestDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;

final class OwnerPcActionGameTestFixture implements AutoCloseable {
    private static final UUID BANK_ID = UUID.fromString("10000000-0000-0000-0000-000000000014");
    private static final UUID ACCOUNT_ID = UUID.fromString("20000000-0000-0000-0000-000000000014");

    private final GameTestHelper helper;
    private final MinecraftServer server;
    private final ServerLevel level;
    private final ServerPlayer player;
    private final CountingCentralBank centralBank;
    private final AccountHolder account;
    private final BlockPos mainPc;
    private final BlockPos secondaryPc;
    private BlockPos crossDimensionPc;

    private OwnerPcActionGameTestFixture(GameTestHelper helper,
                                         MinecraftServer server,
                                         ServerLevel level,
                                         ServerPlayer player,
                                         CountingCentralBank centralBank,
                                         AccountHolder account) {
        this.helper = helper;
        this.server = server;
        this.level = level;
        this.player = player;
        this.centralBank = centralBank;
        this.account = account;
        this.mainPc = helper.absolutePos(new BlockPos(1, 1, 1));
        this.secondaryPc = helper.absolutePos(new BlockPos(2, 1, 1));
    }

    static OwnerPcActionGameTestFixture create(GameTestHelper helper) {
        ServerPlayer player = makeRegisteredMockPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        BankOwnerPcService.clearRememberedDesktopContext(player.getUUID());
        CountingCentralBank centralBank = new CountingCentralBank();
        Bank bank = new Bank(BANK_ID, "Mutation Gate Bank", BigDecimal.ZERO, 1.0D, player.getUUID());
        AccountHolder account = new AccountHolder(
                player.getUUID(), new BigDecimal("900.00"), AccountTypes.CheckingAccount,
                "1234", BANK_ID, ACCOUNT_ID);
        bank.AddAccount(account);
        centralBank.addBank(bank);
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(BANK_ID);
        metadata.putString("motto", "before");
        centralBank.putBankMetadata(BANK_ID, metadata);
        centralBank.resetMetadataWrites();
        return new OwnerPcActionGameTestFixture(
                helper, helper.getLevel().getServer(), helper.getLevel(), player, centralBank, account);
    }

    void assertAbsentDenied() {
        BankOwnerPcService.clearRememberedDesktopContext(player.getUUID());
        assertDenied("absent");
    }

    void assertTrustedRemoteMutationAllowed() {
        centralBank.resetMetadataWrites();
        BankOwnerPcService.ActionResult result = BankOwnerPcService.executeAction(
                server, centralBank, player, BANK_ID, "SET_MOTTO", "trusted-remote", "", "", "");
        require(result.success(), "Trusted remote SET_MOTTO was gated");
        require(centralBank.metadataWrites() == 1, "Trusted remote mutation did not persist exactly once");
    }

    void assertUnloadedDenied() {
        BlockPos unloaded = new BlockPos(29_000_000, 64, 29_000_000);
        require(!level.hasChunkAt(unloaded), "Unloaded-context fixture unexpectedly has a loaded chunk");
        remember(level, unloaded);
        assertDenied("unloaded");
    }

    void assertWrongBlockDenied() {
        level.removeBlock(mainPc, false);
        teleportNear(mainPc);
        remember(level, mainPc);
        assertDenied("wrong-block");
    }

    void assertWrongMachineDenied() {
        placeOwnerPc(level, mainPc);
        teleportNear(mainPc);
        remember(level, mainPc);
        CompoundTag centralMetadata = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag index = centralMetadata.getCompound("ownerPcDesktopMachineIndex");
        index.putString(level.dimension().location() + "|" + mainPc.getX() + "|"
                + mainPc.getY() + "|" + mainPc.getZ(), UUID.randomUUID().toString());
        centralMetadata.put("ownerPcDesktopMachineIndex", index);
        centralBank.putBankMetadata(centralBank.getBankId(), centralMetadata);
        assertDenied("wrong-machine");
    }

    void assertCrossDimensionDenied() {
        ServerLevel nether = server.getLevel(Level.NETHER);
        require(nether != null, "Nether level is unavailable for cross-dimension fixture");
        crossDimensionPc = nether.getSharedSpawnPos().above(8);
        nether.getChunkAt(crossDimensionPc);
        placeOwnerPc(nether, crossDimensionPc);
        teleportNear(mainPc);
        remember(nether, crossDimensionPc);
        assertDenied("cross-dimension");
    }

    void assertOutOfRangeDenied() {
        placeOwnerPc(level, mainPc);
        remember(level, mainPc);
        player.teleportTo(mainPc.getX() + 20.5D, mainPc.getY() + 1.0D, mainPc.getZ() + 0.5D);
        assertDenied("out-of-range");
    }

    void assertPoweredOffDenied() {
        placeOwnerPc(level, mainPc);
        teleportNear(mainPc);
        remember(level, mainPc);
        BankOwnerPcService.ActionResult result = BankOwnerPcService.executeDesktopAction(
                server, centralBank, player, "POWER_OFF", "", "", "");
        require(result.success(), "Unable to power off Owner PC fixture: " + result.message());
        assertDenied("powered-off");
    }

    void assertLockedDenied() {
        placeOwnerPc(level, secondaryPc);
        teleportNear(secondaryPc);
        remember(level, secondaryPc);
        assertDenied("locked");
    }

    void assertLiveMutationExactlyOnce() {
        BankOwnerPcService.ActionResult unlock = BankOwnerPcService.executeDesktopAction(
                server, centralBank, player, "AUTH_SET_PIN", "1234", "task fourteen recovery", "");
        require(unlock.success(), "Unable to unlock live Owner PC fixture: " + unlock.message());
        CompoundTag accountBefore = accountState();
        centralBank.resetMetadataWrites();
        OwnerPcActionGameTestPorts ports = dispatch("SET_MOTTO", "live-once");
        require(ports.onlyResponse() != null && ports.onlyResponse().success(), "Live mutation was denied");
        require(ports.executeCalls() == 1, "Live request executed more than once");
        require(ports.refreshCalls() == 1 && ports.refreshedPayload() != null,
                "Live request did not perform one pure refresh");
        require(centralBank.metadataWrites() == 1, "Live mutation persistence count was not exactly one");
        require("live-once".equals(centralBank.readBankMetadata(BANK_ID).getString("motto")),
                "Live mutation did not store the requested motto");
        require(accountBefore.equals(accountState()), "Live motto mutation changed account state");
    }

    void prepareAwayReadState() {
        BankOwnerPcService.clearRememberedDesktopContext(player.getUUID());
        level.removeBlock(mainPc, false);
        level.removeBlock(secondaryPc, false);
        makeAccountStateStale();
        OwnerPcOperationalProjectionGameTestFixture.prepareStaleMetadata(
                centralBank, BANK_ID, level.getGameTime());
    }

    void assertPureAwayRead(String action) {
        byte[] before = fullState();
        centralBank.resetMetadataWrites();
        String arg1 = "ACCOUNT_DETAIL".equals(action) ? ACCOUNT_ID.toString() : "";
        OwnerPcActionGameTestPorts ports = dispatch(action, arg1);
        require(ports.onlyResponse() != null && ports.onlyResponse().success(),
                action + " failed away from the Owner PC");
        require(ports.executeCalls() == 1, action + " executed an unexpected number of times");
        require(ports.refreshCalls() == 1
                        && OwnerPcOperationalProjectionGameTestFixture.isExpectedProjection(ports.refreshedPayload()),
                action + " did not return a bank-data snapshot");
        require(centralBank.metadataWrites() == 0, action + " crossed a metadata persistence boundary");
        require(java.util.Arrays.equals(before, fullState()), action + " changed serialized bank/account state");
    }

    private void assertDenied(String context) {
        byte[] before = fullState();
        centralBank.resetMetadataWrites();
        OwnerPcActionGameTestPorts ports = dispatch("SET_MOTTO", "forbidden-" + context);
        require(ports.onlyResponse() != null && !ports.onlyResponse().success(),
                "Direct mutation was not denied for " + context);
        require(ports.executeCalls() == 1, "Denied " + context + " request executed more than once");
        require(ports.refreshCalls() == 0, "Denied " + context + " request reached refresh");
        require(centralBank.metadataWrites() == 0, "Denied " + context + " request persisted metadata");
        require(java.util.Arrays.equals(before, fullState()), "Denied " + context + " request changed serialized state");
    }

    private OwnerPcActionGameTestPorts dispatch(String action, String arg1) {
        OwnerPcActionGameTestPorts ports = new OwnerPcActionGameTestPorts(server, centralBank, player);
        OwnerPcActionRequestDispatcher.dispatch(new OwnerPcActionRequestDispatcher.Request(
                BANK_ID, action, arg1, "", "", ""), ports);
        return ports;
    }

    private void remember(ServerLevel pcLevel, BlockPos pos) {
        BankOwnerPcService.rememberDesktopContext(
                centralBank, player.getUUID(), pcLevel.dimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ());
    }

    private void placeOwnerPc(ServerLevel pcLevel, BlockPos pos) {
        pcLevel.setBlockAndUpdate(pos, ModBlocks.BANK_OWNER_PC.get().defaultBlockState());
    }

    private void teleportNear(BlockPos pos) {
        player.teleportTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
    }

    private byte[] fullState() {
        return OwnerPcGameTestSerialization.save(centralBank, server);
    }

    private CompoundTag accountState() {
        return account.save(new CompoundTag(), server.registryAccess());
    }

    private void makeAccountStateStale() {
        setField("dailyWithdrawalWindowDay", Long.MIN_VALUE);
        setField("dailyWithdrawnAmount", new BigDecimal("75.00"));
        setField("dailyWithdrawalResetEpochMillis", 1L);
        setField("temporaryWithdrawalLimit", new BigDecimal("250"));
        setField("temporaryWithdrawalLimitExpiresAtGameTime", level.getGameTime() - 1L);
        setField("temporaryWithdrawalLimitExpiresAtEpochMillis", 1L);
    }

    private void setField(String name, Object value) {
        try {
            Field field = AccountHolder.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(account, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to prepare account fixture field " + name, exception);
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    @Override
    public void close() {
        BankOwnerPcService.clearRememberedDesktopContext(player.getUUID());
        level.removeBlock(mainPc, false);
        level.removeBlock(secondaryPc, false);
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether != null && crossDimensionPc != null) {
            nether.removeBlock(crossDimensionPc, false);
        }
        server.getPlayerList().remove(player);
        centralBank.getBankMetadata().remove(BANK_ID);
        centralBank.getBanks().remove(BANK_ID);
    }

    @SuppressWarnings("removal")
    private static ServerPlayer makeRegisteredMockPlayer(GameTestHelper helper) {
        try {
            return helper.makeMockServerPlayerInLevel();
        } catch (UnsupportedOperationException exception) {
            ServerPlayer registered = helper.getLevel().getServer().getPlayerList()
                    .getPlayerByName("test-mock-player");
            if (registered == null) {
                throw exception;
            }
            return registered;
        }
    }
}
