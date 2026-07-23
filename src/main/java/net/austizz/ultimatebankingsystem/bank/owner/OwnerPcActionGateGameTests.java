package net.austizz.ultimatebankingsystem.bank.owner;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder("ultimatebankingsystem_task14_ownerpc")
@PrefixGameTestTemplate(false)
public final class OwnerPcActionGateGameTests {
    private static final List<String> READ_ONLY_ACTIONS = List.of(
            "SHOW_INFO",
            "SHOW_RESERVE",
            "SHOW_DASHBOARD",
            "SHOW_ACCOUNTS",
            "SHOW_CDS",
            "SHOW_LIMITS",
            "SHOW_ROLES",
            "SHOW_SHARES",
            "SHOW_COFOUNDERS",
            "SHOW_EMPLOYEES",
            "SHOW_LOAN_PRODUCTS",
            "SHOW_LOANS",
            "SHOW_MARKET",
            "BANK_LEVEL_ROADMAP",
            "TELLER_COUNT",
            "ACCOUNT_DETAIL"
    );

    private OwnerPcActionGateGameTests() {
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 160)
    public static void directMutationRequiresEveryLivePcFact(GameTestHelper helper) {
        OwnerPcActionGameTestFixture fixture = OwnerPcActionGameTestFixture.create(helper);
        try {
            fixture.assertAbsentDenied();
            fixture.assertTrustedRemoteMutationAllowed();
            fixture.assertUnloadedDenied();
            fixture.assertWrongBlockDenied();
            fixture.assertWrongMachineDenied();
            fixture.assertCrossDimensionDenied();
            fixture.assertOutOfRangeDenied();
            fixture.assertPoweredOffDenied();
            fixture.assertLockedDenied();
            fixture.assertLiveMutationExactlyOnce();
            UltimateBankingSystem.LOGGER.info(
                    "TASK14_OWNER_PC_MUTATION_MATRIX_COMPLETE denials=8 liveWrites=1 trustedRemoteWrites=1");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 160)
    public static void everyAwayReadIsStatePure(GameTestHelper helper) {
        OwnerPcActionGameTestFixture fixture = OwnerPcActionGameTestFixture.create(helper);
        try {
            fixture.prepareAwayReadState();
            for (String action : READ_ONLY_ACTIONS) {
                fixture.assertPureAwayRead(action);
            }
            UltimateBankingSystem.LOGGER.info(
                    "TASK14_OWNER_PC_READ_MATRIX_COMPLETE reads={}", READ_ONLY_ACTIONS.size());
            helper.succeed();
        } finally {
            fixture.close();
        }
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 160)
    public static void staleOperationalMetadataIsProjectedWithoutWrites(GameTestHelper helper) {
        OwnerPcOperationalProjectionGameTestFixture fixture =
                OwnerPcOperationalProjectionGameTestFixture.create(helper);
        try {
            fixture.assertExpiredLockdownProjection();
            fixture.assertReserveGraceAndRecoveryProjection();
            fixture.assertDailyRolloverProjection();
            UltimateBankingSystem.LOGGER.info(
                    "TASK14_OWNER_PC_OPERATIONAL_PROJECTION_COMPLETE scenarios=5 writes=0");
            helper.succeed();
        } finally {
            fixture.close();
        }
    }
}
