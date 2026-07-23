package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.BANK;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.TELLER;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.VAULT;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.copy;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.invoke;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.metadata;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.putBoolean;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.putInt;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.putString;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.remove;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.replaceOutbound;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.replaceReturn;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.resolve;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.resolveRequest;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.setupAuthority;
import static net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeTellerRoutePairTestFixture.tellerRequest;

class SafeBoxEscortMetadataRoutesTest {
    @Test
    void resolvesOneFullyDecodedExactPair() throws Exception {
        SafeTellerRoutePairTestFixture.Fixture fixture = metadata();
        Object refs = resolve(fixture.metadata()).orElseThrow();

        assertEquals(fixture.outboundId(), invoke(invoke(refs, "outbound"), "id"));
        assertEquals(fixture.returningId(), invoke(invoke(refs, "returning"), "id"));
        assertTrue(setupAuthority(fixture.metadata()));
    }

    @Test
    void requestUsesAnImmutableMetadataSnapshot() throws Exception {
        SafeTellerRoutePairTestFixture.Fixture fixture = metadata();
        Object request = tellerRequest(fixture.metadata());

        fixture.routes().clear();

        assertTrue(resolveRequest(request).isPresent());
        assertTrue(resolve(fixture.metadata()).isEmpty());
    }

    @Test
    void rejectsNoHookAndMultipleMatchingHooks() throws Exception {
        SafeTellerRoutePairTestFixture.Fixture noHook = metadata();
        noHook.hooks().clear();
        assertInvalid(noHook);

        SafeTellerRoutePairTestFixture.Fixture multiple = metadata();
        multiple.hooks().add(copy(multiple.hook()));
        assertInvalid(multiple);

        SafeTellerRoutePairTestFixture.Fixture unbound = metadata();
        putBoolean(unbound.hook(), "bankBound", false);
        assertInvalid(unbound);
    }

    @Test
    void rejectsMissingAndNonStringRequiredHookTags() throws Exception {
        for (String key : new String[]{"bankBound", "outboundRouteRef", "returnRouteRef"}) {
            SafeTellerRoutePairTestFixture.Fixture missing = metadata();
            remove(missing.hook(), key);
            assertInvalid(missing);

            SafeTellerRoutePairTestFixture.Fixture wrongType = metadata();
            putInt(wrongType.hook(), key, 1);
            assertInvalid(wrongType);
        }
    }

    @Test
    void rejectsBlankAndSameReferences() throws Exception {
        SafeTellerRoutePairTestFixture.Fixture blankOutbound = metadata();
        putString(blankOutbound.hook(), "outboundRouteRef", " ");
        assertInvalid(blankOutbound);

        SafeTellerRoutePairTestFixture.Fixture blankReturn = metadata();
        putString(blankReturn.hook(), "returnRouteRef", "");
        assertInvalid(blankReturn);

        SafeTellerRoutePairTestFixture.Fixture same = metadata();
        putString(same.hook(), "returnRouteRef", same.outboundId());
        assertInvalid(same);
    }

    @Test
    void rejectsMissingAndStaleRouteIds() throws Exception {
        SafeTellerRoutePairTestFixture.Fixture missing = metadata();
        missing.routes().remove(0);
        assertInvalid(missing);

        SafeTellerRoutePairTestFixture.Fixture stale = metadata();
        putString(stale.hook(), "outboundRouteRef", "stale-route-id");
        assertInvalid(stale);
    }

    @Test
    void rejectsDuplicateRawRouteIdsBeforeDedupe() throws Exception {
        SafeTellerRoutePairTestFixture.Fixture duplicate = metadata();
        duplicate.routes().add(copy(duplicate.outbound()));
        assertInvalid(duplicate);

        SafeTellerRoutePairTestFixture.Fixture malformedDuplicate = metadata();
        Object malformed = copy(malformedDuplicate.outbound());
        putString(malformed, "direction", "not-a-direction");
        malformedDuplicate.routes().add(malformed);
        assertInvalid(malformedDuplicate);

        SafeTellerRoutePairTestFixture.Fixture duplicateReturn = metadata();
        duplicateReturn.routes().add(copy(duplicateReturn.returning()));
        assertInvalid(duplicateReturn);
    }

    @Test
    void rejectsMalformedRequiredRouteFieldsAndSteps() throws Exception {
        SafeTellerRoutePairTestFixture.Fixture missingField = metadata();
        remove(missingField.outbound(), "finishX");
        assertInvalid(missingField);

        SafeTellerRoutePairTestFixture.Fixture malformedSteps = metadata();
        putInt(malformedSteps.returning(), "steps", 7);
        assertInvalid(malformedSteps);

        SafeTellerRoutePairTestFixture.Fixture malformedStorage = metadata();
        putInt(malformedStorage.metadata(), "safeTellerRoutes", 7);
        assertInvalid(malformedStorage);
    }

    @Test
    void rejectsDecodedRoutesBoundToWrongBankVaultOrTeller() throws Exception {
        SafeTellerRoutePairTestFixture.Fixture wrongBank = metadata();
        replaceOutbound(wrongBank, new SafeTellerRoutePairTestFixture.RouteIdentity(
                new UUID(0L, 3L).toString(), VAULT, TELLER.toString(), "OUTBOUND"));
        assertInvalid(wrongBank);

        SafeTellerRoutePairTestFixture.Fixture wrongVault = metadata();
        replaceOutbound(wrongVault, new SafeTellerRoutePairTestFixture.RouteIdentity(
                BANK.toString(), "vault-b", TELLER.toString(), "OUTBOUND"));
        assertInvalid(wrongVault);

        SafeTellerRoutePairTestFixture.Fixture wrongTeller = metadata();
        replaceOutbound(wrongTeller, new SafeTellerRoutePairTestFixture.RouteIdentity(
                BANK.toString(), VAULT, new UUID(0L, 4L).toString(), "OUTBOUND"));
        assertInvalid(wrongTeller);
    }

    @Test
    void rejectsReturnRouteBoundToWrongBankVaultOrTeller() throws Exception {
        SafeTellerRoutePairTestFixture.Fixture wrongBank = metadata();
        replaceReturn(wrongBank, new SafeTellerRoutePairTestFixture.RouteIdentity(
                new UUID(0L, 3L).toString(), VAULT, TELLER.toString(), "RETURN"));
        assertInvalid(wrongBank);

        SafeTellerRoutePairTestFixture.Fixture wrongVault = metadata();
        replaceReturn(wrongVault, new SafeTellerRoutePairTestFixture.RouteIdentity(
                BANK.toString(), "vault-b", TELLER.toString(), "RETURN"));
        assertInvalid(wrongVault);

        SafeTellerRoutePairTestFixture.Fixture wrongTeller = metadata();
        replaceReturn(wrongTeller, new SafeTellerRoutePairTestFixture.RouteIdentity(
                BANK.toString(), VAULT, new UUID(0L, 4L).toString(), "RETURN"));
        assertInvalid(wrongTeller);
    }

    @Test
    void rejectsMissingOrInvalidDirectionOnEitherRoute() throws Exception {
        for (boolean outbound : new boolean[]{true, false}) {
            SafeTellerRoutePairTestFixture.Fixture missing = metadata();
            remove(outbound ? missing.outbound() : missing.returning(), "direction");
            assertInvalid(missing);

            SafeTellerRoutePairTestFixture.Fixture invalid = metadata();
            putString(outbound ? invalid.outbound() : invalid.returning(), "direction", "NOT_A_DIRECTION");
            assertInvalid(invalid);
        }
    }

    @Test
    void rejectsSwappedOutboundAndReturnDirections() throws Exception {
        SafeTellerRoutePairTestFixture.Fixture fixture = metadata();
        putString(fixture.hook(), "outboundRouteRef", fixture.returningId());
        putString(fixture.hook(), "returnRouteRef", fixture.outboundId());
        assertInvalid(fixture);
    }

    private static void assertInvalid(SafeTellerRoutePairTestFixture.Fixture fixture) throws Exception {
        assertTrue(resolve(fixture.metadata()).isEmpty());
        assertFalse(setupAuthority(fixture.metadata()));
    }
}
