package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortCoordinator;
import net.minecraft.gametest.framework.GameTestHelper;

final class SafetyDepositBoxExactRouteFullPathMatrix {
    private SafetyDepositBoxExactRouteFullPathMatrix() {
    }

    static void verify(GameTestHelper helper, SafetyDepositBoxOpenAuthorityGameTestFixture fixture) {
        var malformedCases = SafetyDepositBoxExactRouteMalformedCases.all(fixture.bankId(), fixture.tellerId());
        require(helper, malformedCases.size() == 31, "full-path malformed route matrix must contain 31 cases");
        for (SafetyDepositBoxExactRouteMalformedCases.Case malformed : malformedCases) {
            fixture.restoreReadyMetadata();
            fixture.apply(malformed);
            require(helper, !fixture.vaultReady(), malformed.name() + " setup readiness must fail");
            require(helper, !fixture.tryStartExactEscort().success(),
                    malformed.name() + " checkout/context resolution must fail");

            fixture.restoreReadyMetadata();
            fixture.startExactEscort();
            fixture.apply(malformed);
            require(helper, fixture.inspectExact() == SafeBoxEscortCoordinator.AccessDecision.DENIED_ACTIVE_ESCORT,
                    malformed.name() + " live authorization must fail");
            fixture.endExactEscort();
        }
        UltimateBankingSystem.LOGGER.info(
                "TASK14_EXACT_ROUTE_FULL_PATH_MATRIX_COMPLETE cases={}", malformedCases.size());
        fixture.restoreReadyMetadata();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
