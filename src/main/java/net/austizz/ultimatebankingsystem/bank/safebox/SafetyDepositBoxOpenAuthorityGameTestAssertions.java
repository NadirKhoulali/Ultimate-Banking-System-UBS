package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.gametest.framework.GameTestHelper;

final class SafetyDepositBoxOpenAuthorityGameTestAssertions {
    private SafetyDepositBoxOpenAuthorityGameTestAssertions() {
    }

    static SafetyDepositBoxOpenAuthorityGameTestFixture fixture(GameTestHelper helper) {
        try {
            return SafetyDepositBoxOpenAuthorityGameTestFixture.install(helper);
        } catch (RuntimeException failure) {
            helper.fail("Fixture installation failed: " + failure);
            throw failure;
        }
    }

    static void finish(GameTestHelper helper,
                       SafetyDepositBoxOpenAuthorityGameTestFixture fixture,
                       Runnable body) {
        try {
            body.run();
        } catch (Throwable failure) {
            fail(helper, fixture, failure);
        }
    }

    static void fail(GameTestHelper helper,
                     SafetyDepositBoxOpenAuthorityGameTestFixture fixture,
                     Throwable failure) {
        fixture.cleanup();
        helper.fail("Open-authority GameTest failed: " + failure);
    }

    static void pass(GameTestHelper helper,
                     SafetyDepositBoxOpenAuthorityGameTestFixture fixture,
                     String name) {
        fixture.cleanup();
        UltimateBankingSystem.LOGGER.info("TASK14_GAMETEST_PASS {} cleanup=PASS", name);
        helper.succeed();
    }

    static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    static void requireLocations(
            GameTestHelper helper,
            SafetyDepositBoxOpenAuthorityItemProbe.LocationCounts actual,
            SafetyDepositBoxOpenAuthorityItemProbe.LocationCounts expected,
            String label) {
        require(helper, actual.equals(expected) && actual.total() == 1,
                label + " locations expected " + expected + " but were " + actual);
    }
}
