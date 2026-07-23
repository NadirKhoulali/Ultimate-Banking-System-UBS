package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.EscortBlockPosition;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortAccessRequest;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortCoordinator;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRegistry;
import net.austizz.ultimatebankingsystem.menu.SafetyDepositBoxMenu;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxOpenAuthorityGameTestAssertions.fail;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxOpenAuthorityGameTestAssertions.finish;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxOpenAuthorityGameTestAssertions.fixture;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxOpenAuthorityGameTestAssertions.pass;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxOpenAuthorityGameTestAssertions.require;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxOpenAuthorityGameTestAssertions.requireLocations;

@GameTestHolder(UltimateBankingSystem.MODID + "_task14")
@PrefixGameTestTemplate(false)
public final class SafetyDepositBoxOpenAuthorityGameTests {
    private SafetyDepositBoxOpenAuthorityGameTests() {
    }

    @GameTest(batch = "task14-1", templateNamespace = UltimateBankingSystem.MODID + "_task14",
            template = "empty3x3x3", timeoutTicks = 120)
    public static void staleMetadataClearsRowCacheAndCannotOpen(GameTestHelper helper) {
        SafetyDepositBoxOpenAuthorityGameTestFixture fixture = fixture(helper);
        helper.runAfterDelay(2, () -> finish(helper, fixture, () -> {
            fixture.row().assignDoor(0, fixture.accountId(), "STALE-CACHE");
            fixture.removeAllAssignments();

            SafetyDepositBoxService.openDoorForPlayer(fixture.player(), fixture.row(), 0);

            require(helper, fixture.row().getAssignedAccountId(0) == null,
                    "metadata removal must clear the stale cached account");
            require(helper, fixture.row().getBoxNumber(0).isBlank(),
                    "metadata removal must clear the stale cached box label");
            require(helper, fixture.player().containerMenu == fixture.player().inventoryMenu,
                    "stale row cache must not open a menu");
            pass(helper, fixture, "staleMetadataClearsRowCacheAndCannotOpen");
        }));
    }

    @GameTest(batch = "task14-2", templateNamespace = UltimateBankingSystem.MODID + "_task14",
            template = "empty3x3x3", timeoutTicks = 140)
    public static void realEscortAndServiceRejectEverySubstitution(GameTestHelper helper) {
        SafetyDepositBoxOpenAuthorityGameTestFixture fixture = fixture(helper);
        helper.runAfterDelay(2, () -> finish(helper, fixture, () -> {
            require(helper, fixture.player().hasPermissions(3),
                    "fixture player must exercise actual level-4 operator authority");
            require(helper, !fixture.hasPrimaryAuthority(),
                    "PUBLIC premise, bank/account ownership, management, and operator authority must not bypass escort");

            SafetyDepositBoxExactRouteFullPathMatrix.verify(helper, fixture);

            fixture.startExactEscort();
            require(helper, fixture.hasPrimaryAuthority(), "exact active escort must authorize the real service");
            require(helper, fixture.inspectExact() == SafeBoxEscortCoordinator.AccessDecision.ALLOWED,
                    "exact coordinator target must be allowed");
            SafeBoxEscortAccessRequest.ExactBox exact = fixture.primaryBox();
            require(helper, fixture.inspect(new SafeBoxEscortAccessRequest(UUID.randomUUID(), exact))
                    == SafeBoxEscortCoordinator.AccessDecision.NO_ACTIVE_ESCORT,
                    "wrong player must not borrow an escort");
            requireDenied(helper, fixture, new SafeBoxEscortAccessRequest(fixture.player().getUUID(),
                    new SafeBoxEscortAccessRequest.ExactBox(UUID.randomUUID(), exact.accountId(),
                            exact.dimension(), exact.rowPosition(), exact.doorIndex())), "wrong bank");
            requireDenied(helper, fixture, new SafeBoxEscortAccessRequest(fixture.player().getUUID(),
                    new SafeBoxEscortAccessRequest.ExactBox(exact.bankId(), UUID.randomUUID(),
                            exact.dimension(), exact.rowPosition(), exact.doorIndex())), "wrong account");
            requireDenied(helper, fixture, new SafeBoxEscortAccessRequest(fixture.player().getUUID(),
                    new SafeBoxEscortAccessRequest.ExactBox(exact.bankId(), exact.accountId(),
                            "minecraft:the_nether", exact.rowPosition(), exact.doorIndex())), "wrong dimension");
            requireDenied(helper, fixture, new SafeBoxEscortAccessRequest(fixture.player().getUUID(),
                    new SafeBoxEscortAccessRequest.ExactBox(exact.bankId(), exact.accountId(), exact.dimension(),
                            new EscortBlockPosition(exact.rowPosition().x() + 1,
                                    exact.rowPosition().y(), exact.rowPosition().z()),
                            exact.doorIndex())), "wrong row");
            requireDenied(helper, fixture, new SafeBoxEscortAccessRequest(fixture.player().getUUID(),
                    new SafeBoxEscortAccessRequest.ExactBox(exact.bankId(), exact.accountId(),
                            exact.dimension(), exact.rowPosition(), 1)), "wrong box door");
            require(helper, !fixture.hasSiblingAuthority(),
                    "a second assigned box owned by the same player must still require its own escort");

            fixture.setPrimaryLocked(true);
            require(helper, !fixture.hasPrimaryAuthority(), "locked assignment must deny owner/operator access");
            fixture.restoreReadyMetadata();
            fixture.removeEligibleStaff();
            require(helper, !fixture.hasPrimaryAuthority(), "NOT_READY staffing must deny owner/operator access");
            fixture.restoreReadyMetadata();
            fixture.renameVault("wrong-vault");
            require(helper, !fixture.hasPrimaryAuthority(), "wrong live vault must invalidate the escort");
            fixture.restoreReadyMetadata();
            fixture.destroyVaultDoor();
            require(helper, !fixture.hasPrimaryAuthority(), "vault-door/readiness loss must deny immediately");
            pass(helper, fixture, "realEscortAndServiceRejectEverySubstitution");
        }));
    }

    @GameTest(batch = "task14-3", templateNamespace = UltimateBankingSystem.MODID + "_task14",
            template = "empty3x3x3", timeoutTicks = 160)
    public static void pendingOpenRechecksAfterAuthorityRevocation(GameTestHelper helper) {
        SafetyDepositBoxOpenAuthorityGameTestFixture fixture = fixture(helper);
        helper.runAfterDelay(2, () -> {
            try {
                fixture.startExactEscort();
                SafetyDepositBoxService.openDoorForPlayer(fixture.player(), fixture.row(), 0);
                fixture.removePrimaryAssignment();
            } catch (Throwable failure) {
                fail(helper, fixture, failure);
                return;
            }
            helper.runAfterDelay(40, () -> finish(helper, fixture, () -> {
                SafetyDepositBoxService.tickSessions(fixture.server());
                require(helper, fixture.row().getCurrentDoorProgress(0) > 0.0F,
                        "allowed open must have entered the real door/pending transition");
                require(helper, fixture.player().containerMenu == fixture.player().inventoryMenu,
                        "revocation before delayed creation must leave no menu");
                pass(helper, fixture, "pendingOpenRechecksAfterAuthorityRevocation");
            }));
        });
    }

    @GameTest(batch = "task14-5", templateNamespace = UltimateBankingSystem.MODID + "_task14",
            template = "empty3x3x3", timeoutTicks = 160)
    public static void pendingOpenExpiresBeforeRegistryCleanup(GameTestHelper helper) {
        SafetyDepositBoxOpenAuthorityGameTestFixture fixture = fixture(helper);
        helper.runAfterDelay(2, () -> finish(helper, fixture, () -> {
            long grantedAtTick = fixture.startExactEscort();
            long deadline = grantedAtTick + SafeBoxEscortRegistry.INSPECTION_TIMEOUT_TICKS;
            SafetyDepositBoxOpenAuthorityGameTestClock.advanceTo(fixture.server(), deadline - 1L);
            require(helper, fixture.hasPrimaryAuthority(),
                    "exact escort must allow the open at deadline minus one");

            SafetyDepositBoxService.openDoorForPlayer(fixture.player(), fixture.row(), 0);
            ((ServerLevelData) fixture.level().getLevelData())
                    .setGameTime(fixture.level().getGameTime() + 40L);
            SafetyDepositBoxOpenAuthorityGameTestClock.advanceTo(fixture.server(), deadline);
            require(helper, SafeBoxEscortCoordinator.activeForPlayer(
                    fixture.server(), fixture.player().getUUID()).isPresent(),
                    "registry cleanup must not have run before the pending tick");

            SafetyDepositBoxService.tickSessions(fixture.server());

            require(helper, SafeBoxEscortCoordinator.activeForPlayer(
                    fixture.server(), fixture.player().getUUID()).isPresent(),
                    "pending authorization must not depend on registry cleanup");
            require(helper, fixture.player().containerMenu == fixture.player().inventoryMenu,
                    "an escort expiring before pending creation must leave no menu");
            pass(helper, fixture, "pendingOpenExpiresBeforeRegistryCleanup");
        }));
    }

    @GameTest(batch = "task14-4", templateNamespace = UltimateBankingSystem.MODID + "_task14",
            template = "empty3x3x3", timeoutTicks = 220)
    public static void liveMenuGuardsConserveItemsAfterRevocation(GameTestHelper helper) {
        SafetyDepositBoxOpenAuthorityGameTestFixture fixture = fixture(helper);
        helper.runAfterDelay(2, () -> {
            try {
                fixture.account().getSafeBoxSlots().put(0, ItemStackDataCompat.saveStack(
                        new ItemStack(Items.EMERALD), fixture.level().registryAccess()));
                fixture.startExactEscort();
                SafetyDepositBoxService.openDoorForPlayer(fixture.player(), fixture.row(), 0);
            } catch (Throwable failure) {
                fail(helper, fixture, failure);
                return;
            }
            helper.runAfterDelay(40, () -> verifyClickGuard(helper, fixture));
        });
    }

    private static void verifyClickGuard(GameTestHelper helper,
                                         SafetyDepositBoxOpenAuthorityGameTestFixture fixture) {
        try {
            SafetyDepositBoxService.tickSessions(fixture.server());
            require(helper, fixture.player().containerMenu instanceof SafetyDepositBoxMenu,
                    "exact escort must create the real delayed menu");
            SafetyDepositBoxMenu menu = (SafetyDepositBoxMenu) fixture.player().containerMenu;
            require(helper, menu.stillValid(fixture.player()), "current exact authority must keep menu valid");
            menu.setCarried(new ItemStack(Items.DIAMOND));
            fixture.removePrimaryAssignment();
            require(helper, !menu.stillValid(fixture.player()), "metadata revocation must invalidate live menu");

            menu.clicked(0, 0, ClickType.PICKUP, fixture.player());

            require(helper, fixture.player().containerMenu == fixture.player().inventoryMenu,
                    "revoked click must close before mutation");
            requireLocations(helper, SafetyDepositBoxOpenAuthorityItemProbe.counts(
                    menu, fixture, Items.DIAMOND),
                    new SafetyDepositBoxOpenAuthorityItemProbe.LocationCounts(0, 0, 1, 0),
                    "revoked cursor diamond");
            requireLocations(helper, SafetyDepositBoxOpenAuthorityItemProbe.counts(
                    menu, fixture, Items.EMERALD),
                    new SafetyDepositBoxOpenAuthorityItemProbe.LocationCounts(0, 0, 0, 1),
                    "pre-revocation box emerald");

            fixture.restoreReadyMetadata();
            SafetyDepositBoxService.openDoorForPlayer(fixture.player(), fixture.row(), 0);
        } catch (Throwable failure) {
            fail(helper, fixture, failure);
            return;
        }
        helper.runAfterDelay(4, () -> verifyQuickMoveGuard(helper, fixture));
    }

    private static void verifyQuickMoveGuard(GameTestHelper helper,
                                             SafetyDepositBoxOpenAuthorityGameTestFixture fixture) {
        finish(helper, fixture, () -> {
            SafetyDepositBoxService.tickSessions(fixture.server());
            require(helper, fixture.player().containerMenu instanceof SafetyDepositBoxMenu,
                    "restored current authority must create a second real menu");
            SafetyDepositBoxMenu menu = (SafetyDepositBoxMenu) fixture.player().containerMenu;
            fixture.player().getInventory().setItem(9, new ItemStack(Items.GOLD_INGOT));
            fixture.setPrimaryLocked(true);

            require(helper, menu.quickMoveStack(fixture.player(), 9).isEmpty(),
                    "revoked quick move must report no transfer");
            require(helper, fixture.player().containerMenu == fixture.player().inventoryMenu,
                    "revoked quick move must close before mutation");
            requireLocations(helper, SafetyDepositBoxOpenAuthorityItemProbe.counts(
                    menu, fixture, Items.GOLD_INGOT),
                    new SafetyDepositBoxOpenAuthorityItemProbe.LocationCounts(0, 0, 1, 0),
                    "revoked quick-move gold");
            requireLocations(helper, SafetyDepositBoxOpenAuthorityItemProbe.counts(
                    menu, fixture, Items.EMERALD),
                    new SafetyDepositBoxOpenAuthorityItemProbe.LocationCounts(0, 0, 0, 1),
                    "persisted pre-revocation emerald");
            pass(helper, fixture, "liveMenuGuardsConserveItemsAfterRevocation");
        });
    }

    private static void requireDenied(GameTestHelper helper,
                                      SafetyDepositBoxOpenAuthorityGameTestFixture fixture,
                                      SafeBoxEscortAccessRequest request,
                                      String label) {
        require(helper, fixture.inspect(request) == SafeBoxEscortCoordinator.AccessDecision.DENIED_ACTIVE_ESCORT,
                label + " must be denied by the real coordinator");
    }
}
