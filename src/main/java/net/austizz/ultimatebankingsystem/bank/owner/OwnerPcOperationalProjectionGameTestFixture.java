package net.austizz.ultimatebankingsystem.bank.owner;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.owner.OwnerPcActionGameTestPorts.CountingCentralBank;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankDataPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.util.UUID;

final class OwnerPcOperationalProjectionGameTestFixture implements AutoCloseable {
    private static final UUID BANK_ID = UUID.fromString("30000000-0000-0000-0000-000000000014");
    private static final UUID ACCOUNT_ID = UUID.fromString("40000000-0000-0000-0000-000000000014");

    private final GameTestHelper helper;
    private final MinecraftServer server;
    private final ServerPlayer player;
    private final CountingCentralBank centralBank;
    private final Bank bank;

    private OwnerPcOperationalProjectionGameTestFixture(GameTestHelper helper,
                                                        ServerPlayer player,
                                                        CountingCentralBank centralBank,
                                                        Bank bank) {
        this.helper = helper;
        this.server = helper.getLevel().getServer();
        this.player = player;
        this.centralBank = centralBank;
        this.bank = bank;
    }

    static OwnerPcOperationalProjectionGameTestFixture create(GameTestHelper helper) {
        ServerPlayer player = makeRegisteredMockPlayer(helper);
        CountingCentralBank centralBank = new CountingCentralBank();
        Bank bank = new Bank(BANK_ID, "Projection Bank", new BigDecimal("1000.00"), 1.0D, player.getUUID());
        bank.AddAccount(new AccountHolder(
                player.getUUID(), new BigDecimal("900.00"), AccountTypes.CheckingAccount,
                "1234", BANK_ID, ACCOUNT_ID));
        centralBank.addBank(bank);
        centralBank.putBankMetadata(BANK_ID, baselineMetadata(helper.getLevel().getGameTime()));
        centralBank.resetMetadataWrites();
        return new OwnerPcOperationalProjectionGameTestFixture(helper, player, centralBank, bank);
    }

    static void prepareStaleMetadata(CountingCentralBank centralBank, UUID bankId, long gameTime) {
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            throw new IllegalStateException("Projection bank fixture is missing");
        }
        bank.setReserve(new BigDecimal("1000.00"));
        CompoundTag stale = baselineMetadata(gameTime);
        stale.putString("status", "LOCKDOWN");
        stale.putLong("lockdownUntilTick", gameTime - 1L);
        stale.putLong("dailyWindowDay", gameTime / 24_000L - 1L);
        stale.putString("dailyWithdrawn", "75.00");
        stale.putInt("queuedWithdrawalCount", 3);
        centralBank.putBankMetadata(bankId, stale);
        centralBank.resetMetadataWrites();
    }

    static boolean isExpectedProjection(OwnerPcBankDataPayload payload) {
        return payload != null
                && "ACTIVE".equals(payload.status())
                && "0.00".equals(payload.dailyUsed());
    }

    void assertExpiredLockdownProjection() {
        long gameTime = helper.getLevel().getGameTime();
        bank.setReserve(new BigDecimal("1000.00"));
        CompoundTag metadata = baselineMetadata(gameTime);
        metadata.putString("status", "LOCKDOWN");
        metadata.putLong("lockdownUntilTick", gameTime - 1L);
        assertProjection(metadata, "ACTIVE", "0.00");
    }

    void assertReserveGraceAndRecoveryProjection() {
        long gameTime = helper.getLevel().getGameTime();
        bank.setReserve(BigDecimal.ZERO);
        CompoundTag grace = baselineMetadata(gameTime);
        assertProjection(grace, "WARNING", "0.00");

        CompoundTag restricted = baselineMetadata(gameTime);
        restricted.putString("status", "WARNING");
        restricted.putLong("reserveBreachStartTick",
                gameTime - Math.max(20L, Config.BANK_RESERVE_GRACE_TICKS.get()) - 1L);
        assertProjection(restricted, "RESTRICTED", "0.00");

        bank.setReserve(new BigDecimal("1000.00"));
        CompoundTag recovered = baselineMetadata(gameTime);
        recovered.putString("status", "RESTRICTED");
        recovered.putLong("reserveBreachStartTick", gameTime - 1L);
        assertProjection(recovered, "ACTIVE", "0.00");
    }

    void assertDailyRolloverProjection() {
        long gameTime = helper.getLevel().getGameTime();
        bank.setReserve(new BigDecimal("1000.00"));
        CompoundTag stale = baselineMetadata(gameTime);
        stale.putLong("dailyWindowDay", gameTime / 24_000L - 1L);
        stale.putString("dailyWithdrawn", "75.00");
        stale.putInt("queuedWithdrawalCount", 3);
        assertProjection(stale, "ACTIVE", "0.00");
    }

    private void assertProjection(CompoundTag metadata, String status, String dailyUsed) {
        centralBank.putBankMetadata(BANK_ID, metadata);
        byte[] before = fullState();
        centralBank.resetMetadataWrites();

        OwnerPcBankDataPayload payload = BankOwnerPcService.buildBankDataPayload(
                server, centralBank, player.getUUID(), BANK_ID);

        require(payload != null, "Operational projection did not produce a payload");
        require(status.equals(payload.status()),
                "Expected projected status " + status + " but got " + payload.status());
        require(dailyUsed.equals(payload.dailyUsed()),
                "Expected projected daily usage " + dailyUsed + " but got " + payload.dailyUsed());
        require(centralBank.metadataWrites() == 0, "Operational projection persisted metadata");
        require(java.util.Arrays.equals(before, fullState()),
                "Operational projection changed complete serialized state");
    }

    private byte[] fullState() {
        return OwnerPcGameTestSerialization.save(centralBank, server);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    private static CompoundTag baselineMetadata(long gameTime) {
        CompoundTag metadata = new CompoundTag();
        metadata.putString("status", "ACTIVE");
        metadata.putLong("dailyWindowDay", gameTime / 24_000L);
        metadata.putString("dailyWithdrawn", "0");
        metadata.putInt("queuedWithdrawalCount", 0);
        return metadata;
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

    @Override
    public void close() {
        server.getPlayerList().remove(player);
        centralBank.getBankMetadata().remove(BANK_ID);
        centralBank.getBanks().remove(BANK_ID);
    }
}
