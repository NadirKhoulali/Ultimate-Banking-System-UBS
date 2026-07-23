package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.owner.staffing.BankStaffingService;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.EscortBlockPosition;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortAccessRequest;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortCoordinator;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortPhase;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntime;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.network.BankTellerSafeBoxState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.UUID;

final class SafetyDepositBoxOpenAuthorityGameTestFixture {
    private static final BlockPos RELATIVE_ROW = new BlockPos(1, 1, 1);
    private static final BlockPos RELATIVE_DOOR = new BlockPos(8, 1, 8);

    private final GameTestHelper helper;
    private final ServerLevel level;
    private final MinecraftServer server;
    private final CentralBank centralBank;
    private final ServerPlayer player;
    private final BankTellerEntity teller;
    private final SafetyDepositBoxRowBlockEntity row;
    private final SafetyDepositBoxOpenAuthorityGameTestSpec spec;
    private final CompoundTag readyMetadata;
    private final AccountHolder account;

    private SafetyDepositBoxOpenAuthorityGameTestFixture(GameTestHelper helper) {
        this.helper = helper;
        this.level = helper.getLevel();
        this.server = level.getServer();
        this.centralBank = BankManager.getCentralBank(server);
        BlockPos rowPos = helper.absolutePos(RELATIVE_ROW);
        BlockPos doorMaster = helper.absolutePos(RELATIVE_DOOR);
        SafetyDepositBoxOpenAuthorityGameTestWorld.placeStructures(
                helper, level, RELATIVE_ROW, doorMaster);
        this.row = SafetyDepositBoxOpenAuthorityGameTestWorld.requireRow(level, rowPos);
        SafetyDepositBoxOpenAuthorityGameTestWorld.installModules(row, helper::fail);
        this.player = SafetyDepositBoxOpenAuthorityGameTestWorld.makeRegisteredMockPlayer(helper);
        NetworkRegistry.configureMockConnection(player.connection.getConnection());
        SafetyDepositBoxOpenAuthorityGameTestWorld.grantOperator(server, player);
        this.spec = new SafetyDepositBoxOpenAuthorityGameTestSpec(
                new SafetyDepositBoxOpenAuthorityGameTestSpec.Identities(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), player.getUUID()),
                new SafetyDepositBoxOpenAuthorityGameTestSpec.Layout(dimension(), rowPos, doorMaster));
        this.account = SafetyDepositBoxOpenAuthorityGameTestWorld.installAccounts(
                centralBank, spec.identities());
        this.teller = SafetyDepositBoxOpenAuthorityGameTestWorld.installTeller(
                level, rowPos, player, bankId(), helper::fail);
        CompoundTag metadata = SafetyDepositBoxOpenAuthorityGameTestMetadata.ready(spec);
        SafetyDepositBoxOpenAuthorityGameTestMetadata.bindRoutes(
                metadata, spec.routeBinding(teller.getUUID(), teller.blockPosition()), helper::fail);
        this.readyMetadata = metadata.copy();
        putMetadata(metadata);
        row.assignDoor(0, accountId(), SafetyDepositBoxOpenAuthorityGameTestMetadata.PRIMARY_LABEL);
        row.assignDoor(1, spec.identities().siblingAccountId(),
                SafetyDepositBoxOpenAuthorityGameTestMetadata.SIBLING_LABEL);
        player.setPos(rowPos.getX() + 0.5D, rowPos.getY() + 0.5D, rowPos.getZ() + 0.5D);
    }

    static SafetyDepositBoxOpenAuthorityGameTestFixture install(GameTestHelper helper) {
        return new SafetyDepositBoxOpenAuthorityGameTestFixture(helper);
    }

    long startExactEscort() {
        var readiness = SafetyDepositBoxService.safeDepositVaultReadiness(
                server, centralBank.getOrCreateBankMetadata(bankId()),
                new SafeVaultReadinessResolver.RowLocation(dimension(), rowPos()));
        require(readiness.mapped() && readiness.summary() != null && readiness.summary().ready(),
                "fixture vault must be READY: " + readiness.humanMissingReasons());
        SafeBoxEscortCoordinator.StartResult start = tryStartExactEscort();
        require(start.success(), "real coordinator start failed: " + start.message());
        SafeBoxEscortCoordinator.tick(server);
        require(SafeBoxEscortCoordinator.activeForPlayer(server, player.getUUID())
                        .map(session -> session.phase() == SafeBoxEscortPhase.AT_VAULT).orElse(false),
                "real route must reach AT_VAULT");
        long grantedAtTick = server.getTickCount();
        SafeBoxEscortRuntime.InteractionStatus status = SafeBoxEscortCoordinator.handleTellerInteraction(
                new SafeBoxEscortCoordinator.InteractionRequest(
                        server, player.getUUID(), teller.getUUID(), grantedAtTick));
        require(status == SafeBoxEscortRuntime.InteractionStatus.ACCESS_GRANTED,
                "real coordinator grant failed: " + status);
        return grantedAtTick;
    }

    boolean vaultReady() {
        var readiness = SafetyDepositBoxService.safeDepositVaultReadiness(
                server, centralBank.getOrCreateBankMetadata(bankId()),
                new SafeVaultReadinessResolver.RowLocation(dimension(), rowPos()));
        return readiness.mapped() && readiness.summary() != null && readiness.summary().ready();
    }

    SafeBoxEscortCoordinator.StartResult tryStartExactEscort() {
        return SafeBoxEscortCoordinator.start(new SafeBoxEscortCoordinator.StartRequest(
                server, centralBank, player, teller, selectedAssignment()));
    }

    boolean hasPrimaryAuthority() {
        return SafetyDepositBoxService.hasOpenBoxAuthority(player, accountId(), rowPos(), 0);
    }

    boolean hasSiblingAuthority() {
        return SafetyDepositBoxService.hasOpenBoxAuthority(
                player, spec.identities().siblingAccountId(), rowPos(), 1);
    }

    SafeBoxEscortCoordinator.AccessDecision inspectExact() {
        return inspect(new SafeBoxEscortAccessRequest(player.getUUID(), primaryBox()));
    }

    SafeBoxEscortCoordinator.AccessDecision inspect(SafeBoxEscortAccessRequest request) {
        return SafeBoxEscortCoordinator.inspectAccess(server, request);
    }

    SafeBoxEscortAccessRequest.ExactBox primaryBox() {
        BlockPos row = rowPos();
        return new SafeBoxEscortAccessRequest.ExactBox(
                bankId(), accountId(), dimension(),
                new EscortBlockPosition(row.getX(), row.getY(), row.getZ()), 0);
    }

    void removeAllAssignments() {
        CompoundTag metadata = currentMetadata();
        metadata.put(SafetyDepositBoxService.ASSIGNMENTS_KEY, new ListTag());
        putMetadata(metadata);
    }

    void removePrimaryAssignment() {
        CompoundTag metadata = currentMetadata();
        ListTag retained = new ListTag();
        ListTag assignments = metadata.getList(SafetyDepositBoxService.ASSIGNMENTS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < assignments.size(); index++) {
            CompoundTag assignment = assignments.getCompound(index);
            if (!assignment.hasUUID("accountId") || !accountId().equals(assignment.getUUID("accountId"))) {
                retained.add(assignment.copy());
            }
        }
        metadata.put(SafetyDepositBoxService.ASSIGNMENTS_KEY, retained);
        putMetadata(metadata);
    }

    void setPrimaryLocked(boolean locked) {
        CompoundTag metadata = currentMetadata();
        ListTag assignments = metadata.getList(SafetyDepositBoxService.ASSIGNMENTS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < assignments.size(); index++) {
            CompoundTag assignment = assignments.getCompound(index);
            if (assignment.hasUUID("accountId") && accountId().equals(assignment.getUUID("accountId"))) {
                assignment.putBoolean("locked", locked);
            }
        }
        putMetadata(metadata);
    }

    void removeEligibleStaff() {
        CompoundTag metadata = currentMetadata();
        metadata.remove(BankStaffingService.SAFE_ACCESS_KEY);
        putMetadata(metadata);
    }

    void apply(SafetyDepositBoxExactRouteMalformedCases.Case malformed) {
        CompoundTag metadata = currentMetadata();
        malformed.apply(metadata);
        putMetadata(metadata);
    }

    void endExactEscort() {
        SafeBoxEscortCoordinator.onLogout(server, player.getUUID());
        for (int attempt = 0; attempt < 4; attempt++) {
            SafeBoxEscortCoordinator.tick(server);
        }
        require(SafeBoxEscortCoordinator.activeForPlayer(server, player.getUUID()).isEmpty(),
                "escort cleanup did not release the fixture player");
    }

    void renameVault(String vaultId) {
        CompoundTag metadata = currentMetadata();
        CompoundTag premise = metadata.getList("safeDepositPremises", Tag.TAG_COMPOUND).getCompound(0);
        CompoundTag area = premise.getList("safeAreas", Tag.TAG_COMPOUND).getCompound(0);
        area.getList("vaults", Tag.TAG_COMPOUND).getCompound(0).putString("id", vaultId);
        putMetadata(metadata);
    }

    void restoreReadyMetadata() {
        putMetadata(readyMetadata.copy());
        SafetyDepositBoxService.syncAssignmentsToRow(row);
    }

    void destroyVaultDoor() {
        level.destroyBlock(spec.layout().doorMaster(), false);
    }

    void cleanup() {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        endExactEscort();
        SafetyDepositBoxService.revokeEscortAccess(player.getUUID());
        server.getPlayerList().deop(player.getGameProfile());
        server.getPlayerList().remove(player);
        teller.discard();
        level.destroyBlock(rowPos(), false);
        level.destroyBlock(spec.layout().doorMaster(), false);
        centralBank.getBankMetadata().remove(bankId());
        centralBank.getBanks().remove(bankId());
        BankManager.markDirty();
        require(SafeBoxEscortCoordinator.activeForPlayer(server, player.getUUID()).isEmpty(),
                "cleanup left an active escort");
        require(server.getPlayerList().getPlayer(player.getUUID()) == null,
                "cleanup left the mock player registered");
    }

    private BankTellerSafeBoxState.AccountAssignment selectedAssignment() {
        return SafetyDepositBoxOpenAuthorityGameTestMetadata.selectedAssignment(spec);
    }

    private CompoundTag currentMetadata() {
        return centralBank.getOrCreateBankMetadata(bankId()).copy();
    }

    private void putMetadata(CompoundTag metadata) {
        centralBank.putBankMetadata(bankId(), metadata);
        BankManager.markDirty();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    ServerLevel level() { return level; }
    MinecraftServer server() { return server; }
    ServerPlayer player() { return player; }
    SafetyDepositBoxRowBlockEntity row() { return row; }
    AccountHolder account() { return account; }
    UUID bankId() { return spec.identities().bankId(); }
    UUID tellerId() { return teller.getUUID(); }
    UUID accountId() { return spec.identities().accountId(); }
    BlockPos rowPos() { return spec.layout().rowPos(); }
    String dimension() { return level.dimension().location().toString(); }
}
