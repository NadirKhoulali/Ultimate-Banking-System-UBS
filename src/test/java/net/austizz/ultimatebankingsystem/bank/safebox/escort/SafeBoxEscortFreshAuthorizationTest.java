package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxEscortFreshAuthorizationTest {
    @Test
    void productionResolverMatcherDeniesTellerAndVaultRebinding() throws Exception {
        SafeBoxEscortFreshAuthorizationFixture fixture = new SafeBoxEscortFreshAuthorizationFixture();
        assertTrue(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(), fixture.assignment(),
                fixture.vault(), fixture.routes())));

        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(UUID.randomUUID(), fixture.bank, true, false),
                fixture.assignment(), fixture.vault(), fixture.routes())));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(fixture.teller, UUID.randomUUID(), true, false),
                fixture.assignment(), fixture.vault(), fixture.routes())));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(fixture.teller, fixture.bank, false, false),
                fixture.assignment(), fixture.vault(), fixture.routes())));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(fixture.teller, fixture.bank, true, true),
                fixture.assignment(), fixture.vault(), fixture.routes())));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(), fixture.assignment(),
                fixture.vault(false, true, "vault-a", fixture.door), fixture.routes())));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(), fixture.assignment(),
                fixture.vault(true, false, "vault-a", fixture.door), fixture.routes())));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(), fixture.assignment(),
                fixture.vault(true, true, "rebound-vault", fixture.door), fixture.routes())));
    }

    @Test
    void productionResolverMatcherDeniesAccountAssignmentAndDoorRebinding() throws Exception {
        SafeBoxEscortFreshAuthorizationFixture fixture = new SafeBoxEscortFreshAuthorizationFixture();

        assertFalse(fixture.matches(fixture.snapshot(
                UUID.randomUUID(), fixture.bank, fixture.teller(), fixture.assignment(),
                fixture.vault(), fixture.routes())));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, UUID.randomUUID(), fixture.teller(), fixture.assignment(),
                fixture.vault(), fixture.routes())));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(),
                fixture.assignment(UUID.randomUUID(), fixture.account, "minecraft:overworld",
                        fixture.row, 3, "Box A-1", false), fixture.vault(), fixture.routes())));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(),
                fixture.assignment(fixture.bank, UUID.randomUUID(), "minecraft:overworld",
                        fixture.row, 3, "Box A-1", false), fixture.vault(), fixture.routes())));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(),
                fixture.assignment(fixture.bank, fixture.account, "minecraft:the_nether",
                        fixture.row, 3, "Box A-1", false), fixture.vault(), fixture.routes())));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(),
                fixture.assignment(fixture.bank, fixture.account, "minecraft:overworld",
                        fixture.position(13, 64, 14), 3, "Box A-1", false),
                fixture.vault(), fixture.routes())));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(),
                fixture.assignment(fixture.bank, fixture.account, "minecraft:overworld",
                        fixture.row, 4, "Box A-1", false), fixture.vault(), fixture.routes())));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(),
                fixture.assignment(fixture.bank, fixture.account, "minecraft:overworld",
                        fixture.row, 3, "reassigned", false), fixture.vault(), fixture.routes())));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(),
                fixture.assignment(fixture.bank, fixture.account, "minecraft:overworld",
                        fixture.row, 3, "Box A-1", true), fixture.vault(), fixture.routes())));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(), fixture.assignment(),
                fixture.vault(true, true, "vault-a", fixture.position(17, 64, 18)), fixture.routes())));
    }

    @Test
    void productionResolverMatcherDeniesEitherRouteBodyOrHookReferenceChange() throws Exception {
        SafeBoxEscortFreshAuthorizationFixture fixture = new SafeBoxEscortFreshAuthorizationFixture();
        Object changedOutbound = fixture.route("OUTBOUND", List.of());
        Object changedReturn = fixture.route("RETURN", List.of());

        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(), fixture.assignment(), fixture.vault(),
                fixture.routes("outbound-rebound", fixture.routeId(fixture.returning),
                        fixture.outbound, fixture.returning))));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(), fixture.assignment(), fixture.vault(),
                fixture.routes(fixture.routeId(fixture.outbound), "return-rebound",
                        fixture.outbound, fixture.returning))));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(), fixture.assignment(), fixture.vault(),
                fixture.routes(fixture.routeId(fixture.outbound), fixture.routeId(fixture.returning),
                        changedOutbound, fixture.returning))));
        assertFalse(fixture.matches(fixture.snapshot(
                fixture.owner, fixture.bank, fixture.teller(), fixture.assignment(), fixture.vault(),
                fixture.routes(fixture.routeId(fixture.outbound), fixture.routeId(fixture.returning),
                        fixture.outbound, changedReturn))));
    }
}
