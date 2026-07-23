package net.austizz.ultimatebankingsystem.bank.owner;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPcActionPolicyTest {
    private static final Map<String, OwnerPcActionPolicy.Access> EXTERNAL_ACTION_CONTRACT = Map.ofEntries(
            entry("SHOW_INFO", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("SHOW_RESERVE", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("SHOW_DASHBOARD", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("SHOW_ACCOUNTS", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("SHOW_CDS", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("SHOW_LIMITS", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("SHOW_ROLES", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("SHOW_SHARES", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("SHOW_COFOUNDERS", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("SHOW_EMPLOYEES", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("SHOW_LOAN_PRODUCTS", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("SHOW_LOANS", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("SHOW_MARKET", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("BANK_LEVEL_ROADMAP", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("SET_MOTTO", OwnerPcActionPolicy.Access.MUTATION),
            entry("SET_COLOR", OwnerPcActionPolicy.Access.MUTATION),
            entry("SET_LIMIT", OwnerPcActionPolicy.Access.MUTATION),
            entry("SET_CARD_FEES", OwnerPcActionPolicy.Access.MUTATION),
            entry("ROLE_ASSIGN", OwnerPcActionPolicy.Access.MUTATION),
            entry("ROLE_REVOKE", OwnerPcActionPolicy.Access.MUTATION),
            entry("SHARES_SET", OwnerPcActionPolicy.Access.MUTATION),
            entry("COFOUNDER_ADD", OwnerPcActionPolicy.Access.MUTATION),
            entry("HIRE", OwnerPcActionPolicy.Access.MUTATION),
            entry("FIRE", OwnerPcActionPolicy.Access.MUTATION),
            entry("TELLER_ISSUE", OwnerPcActionPolicy.Access.MUTATION),
            entry("TELLER_COUNT", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("BORROW", OwnerPcActionPolicy.Access.MUTATION),
            entry("LEND_OFFER", OwnerPcActionPolicy.Access.MUTATION),
            entry("LEND_ACCEPT", OwnerPcActionPolicy.Access.MUTATION),
            entry("APPEAL", OwnerPcActionPolicy.Access.MUTATION),
            entry("CREATE_LOAN_PRODUCT", OwnerPcActionPolicy.Access.MUTATION),
            entry("ACCOUNT_DETAIL", OwnerPcActionPolicy.Access.READ_ONLY),
            entry("ACCOUNT_FREEZE", OwnerPcActionPolicy.Access.MUTATION),
            entry("ACCOUNT_UNFREEZE", OwnerPcActionPolicy.Access.MUTATION),
            entry("ACCOUNT_TEMP_LIMIT", OwnerPcActionPolicy.Access.MUTATION),
            entry("SAFE_AREA_CLAIM_TOOL", OwnerPcActionPolicy.Access.MUTATION),
            entry("SAFE_BOX_ASSIGN", OwnerPcActionPolicy.Access.MUTATION),
            entry("SAFE_BOX_LOCATE", OwnerPcActionPolicy.Access.MUTATION),
            entry("SAFE_BOX_POLICY", OwnerPcActionPolicy.Access.MUTATION),
            entry("SAFE_BOX_SEIZE", OwnerPcActionPolicy.Access.MUTATION),
            entry("SAFE_ACCESS_GRANT", OwnerPcActionPolicy.Access.MUTATION),
            entry("SAFE_ACCESS_REVOKE", OwnerPcActionPolicy.Access.MUTATION),
            entry("SAFE_ALARM_CONFIG", OwnerPcActionPolicy.Access.MUTATION),
            entry("SAFE_ALARM_TEST", OwnerPcActionPolicy.Access.MUTATION),
            entry("SAFE_ALARM_STOP_TEST", OwnerPcActionPolicy.Access.MUTATION),
            entry("SAFE_ALARM_RESET", OwnerPcActionPolicy.Access.MUTATION),
            entry("VIEWING_ROOM_CLAIM_TOOL", OwnerPcActionPolicy.Access.MUTATION),
            entry("VIEWING_ROOM_ANCHOR", OwnerPcActionPolicy.Access.MUTATION),
            entry("VIEWING_ROOM_RENAME", OwnerPcActionPolicy.Access.MUTATION),
            entry("VIEWING_ROOM_SUSPEND", OwnerPcActionPolicy.Access.MUTATION),
            entry("VIEWING_ROOM_DELETE", OwnerPcActionPolicy.Access.MUTATION)
    );

    @Test
    void externalWireContractMatchesProductionParseAndClassification() {
        assertEquals(EXTERNAL_ACTION_CONTRACT.keySet(), OwnerPcActionPolicy.supportedRawActions());
        EXTERNAL_ACTION_CONTRACT.forEach((rawAction, expectedAccess) -> {
            OwnerPcActionPolicy.Action parsed = OwnerPcActionPolicy.classify("  " + rawAction.toLowerCase() + "  ");
            assertEquals(rawAction, parsed.name());
            assertEquals(expectedAccess, parsed.access(), rawAction);
        });
    }

    @Test
    void readOnlyDirectActionRemainsAvailableWithoutOwnerPcContext() {
        OwnerPcActionPolicy.Decision decision = OwnerPcActionPolicy.authorize(
                "SHOW_INFO", OwnerPcActionPolicy.Channel.DIRECT_OWNER_PC, null);

        assertTrue(decision.allowed());
        assertSame(OwnerPcActionPolicy.Action.SHOW_INFO, decision.action());
    }

    @Test
    void trustedRemoteMutationDoesNotRequireOwnerPcContext() {
        OwnerPcActionPolicy.Decision decision = OwnerPcActionPolicy.authorize(
                "SET_MOTTO", OwnerPcActionPolicy.Channel.TRUSTED_REMOTE, null);

        assertTrue(decision.allowed());
        assertSame(OwnerPcActionPolicy.Action.SET_MOTTO, decision.action());
    }

    @Test
    void unknownActionsFailClosedOnBothExternalChannels() {
        for (OwnerPcActionPolicy.Channel channel : OwnerPcActionPolicy.Channel.values()) {
            OwnerPcActionPolicy.Decision decision = OwnerPcActionPolicy.authorize(
                    "FUTURE_UNCLASSIFIED_ACTION", channel, null);
            assertFalse(decision.allowed(), channel.name());
            assertNull(decision.action());
        }
    }
}
