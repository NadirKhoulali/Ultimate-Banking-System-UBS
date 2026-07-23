package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.SafeVaultReadinessOperation;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePairResolver;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

final class SafeBoxEscortLiveAuthorization {
    private SafeBoxEscortLiveAuthorization() {
    }

    static boolean verify(MinecraftServer server,
                          CentralBank centralBank,
                          SafeBoxEscortRuntimeContext context) {
        if (server == null || centralBank == null || context == null) {
            return false;
        }
        SafeBoxEscortTarget target = context.target();
        AccountHolder account = centralBank.SearchForAccountByAccountId(target.accountId());
        BankTellerEntity teller = findTeller(server, context.tellerId());
        if (account == null || teller == null || !context.playerId().equals(account.getPlayerUUID())
                || !target.bankId().equals(account.getBankId()) || !teller.isAlive()
                || teller.isCashier() || !target.bankId().equals(teller.getBoundBankId())) {
            return false;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(target.bankId());
        SafeBoxEscortContextResolver.ExactAssignment assignment =
                SafeBoxEscortContextResolver.exactAssignment(metadata, target.accountId()).orElse(null);
        if (assignment == null) {
            return false;
        }
        ServerLevel rowLevel = SafeBoxEscortContextResolver.level(server, assignment.dimension());
        SafetyDepositBoxRowBlockEntity row = loadedRow(rowLevel, assignment.rowPos());
        if (row == null || row.getModuleStartForRow(assignment.doorIndex()) != assignment.doorIndex()
                || !row.isAssignableBoxStart(assignment.doorIndex())) {
            return false;
        }
        SafeVaultReadinessOperation readinessOperation =
                SafetyDepositBoxService.safeDepositVaultReadinessOperation(server, metadata);
        SafeVaultReadinessResolver.RowReadiness readiness = readinessOperation.resolve(
                new SafeVaultReadinessResolver.RowLocation(assignment.dimension(), assignment.rowPos()));
        if (!SafeBoxEscortContextResolver.matchesReadyVault(
                readiness, target.bankId(), target.vaultId())) {
            return false;
        }
        SafeTellerRoutePairResolver.Pair routes = SafeBoxEscortMetadataRoutes.exactPair(
                new SafeTellerRoutePairResolver.TellerRequest(
                        new SafeTellerRoutePairResolver.VaultRequest(
                                readinessOperation.routes(), target.bankId(), readiness.vault().id()),
                        context.tellerId())).orElse(null);
        BlockPos door = readinessOperation.resolveDoorMaster(readiness).orElse(null);
        if (routes == null || door == null) {
            return false;
        }
        SafeTellerRoute outbound = routes.outbound();
        SafeTellerRoute returning = routes.returning();
        SafeBoxEscortAuthorizationSnapshot snapshot = new SafeBoxEscortAuthorizationSnapshot(
                account.getPlayerUUID(), account.getBankId(),
                new SafeBoxEscortAuthorizationSnapshot.Teller(
                        teller.getUUID(), teller.getBoundBankId(), teller.isAlive(), teller.isCashier()),
                assignment(target.accountId(), assignment),
                new SafeBoxEscortAuthorizationSnapshot.Vault(
                        readiness.mapped(), readiness.summary().ready(), readiness.vault().id(),
                        new EscortBlockPosition(door.getX(), door.getY(), door.getZ())),
                new SafeBoxEscortAuthorizationSnapshot.Routes(
                        routes.outboundRef(), routes.returnRef(), outbound, returning));
        return SafeBoxEscortContextResolver.matchesFreshSnapshot(context, snapshot);
    }

    static boolean verifyReadiness(CompoundTag metadata,
                                   SafeVaultReadinessResolver.RowReadiness readiness,
                                   SafeBoxEscortRuntimeContext context) {
        if (metadata == null || readiness == null || context == null) {
            return false;
        }
        SafeVaultReadinessResolver.RowReadiness current =
                SafetyDepositBoxService.applyStaffingReadiness(metadata, readiness);
        SafeBoxEscortTarget target = context.target();
        return SafeBoxEscortContextResolver.matchesReadyVault(
                current, target.bankId(), target.vaultId());
    }

    private static SafeBoxEscortAuthorizationSnapshot.Assignment assignment(
            java.util.UUID accountId, SafeBoxEscortContextResolver.ExactAssignment assignment) {
        BlockPos row = assignment.rowPos();
        return new SafeBoxEscortAuthorizationSnapshot.Assignment(
                assignment.bankId(), accountId, assignment.dimension(),
                new EscortBlockPosition(row.getX(), row.getY(), row.getZ()),
                assignment.doorIndex(), assignment.label(), assignment.locked());
    }

    private static SafetyDepositBoxRowBlockEntity loadedRow(ServerLevel level, BlockPos position) {
        return level != null && level.hasChunkAt(position)
                && level.getBlockEntity(position) instanceof SafetyDepositBoxRowBlockEntity row
                ? row : null;
    }

    private static BankTellerEntity findTeller(MinecraftServer server, java.util.UUID tellerId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(tellerId) instanceof BankTellerEntity teller) {
                return teller;
            }
        }
        return null;
    }
}
