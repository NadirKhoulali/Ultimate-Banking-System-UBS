package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.SafeVaultReadinessOperation;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePairResolver;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeAreaSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultSnapshot;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.network.BankTellerSafeBoxState;
import net.austizz.ultimatebankingsystem.util.RegistryKeysCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

final class SafeBoxEscortContextResolver {
    private SafeBoxEscortContextResolver() {
    }

    static Resolution resolve(CheckoutRequest request) {
        MinecraftServer server = request == null ? null : request.server();
        CentralBank centralBank = request == null ? null : request.centralBank();
        ServerPlayer player = request == null ? null : request.player();
        BankTellerEntity teller = request == null ? null : request.teller();
        BankTellerSafeBoxState.AccountAssignment selected = request == null ? null : request.selected();
        if (server == null || centralBank == null || player == null || teller == null || selected == null) {
            return Resolution.failure("Safe-deposit escort data is unavailable.");
        }
        if (!teller.isAlive() || teller.isCashier()) {
            return Resolution.failure("The selected teller cannot escort customers to the vault.");
        }
        UUID bankId = teller.getBoundBankId();
        AccountHolder account = centralBank.SearchForAccountByAccountId(selected.accountId());
        if (bankId == null || account == null || !player.getUUID().equals(account.getPlayerUUID())
                || !bankId.equals(account.getBankId())) {
            return Resolution.failure("The selected account is no longer owned at this teller's bank.");
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        ExactAssignment assignment = exactAssignment(metadata, selected.accountId()).orElse(null);
        if (assignment == null || !bankId.equals(assignment.bankId())
                || assignment.locked() || !selected.ready() || selected.locked()
                || !assignment.matches(selected)) {
            return Resolution.failure("The selected safety deposit box assignment changed or is locked.");
        }
        ServerLevel rowLevel = level(server, assignment.dimension());
        BlockPos rowPos = assignment.rowPos();
        if (rowLevel == null || !rowLevel.hasChunkAt(rowPos)
                || !(rowLevel.getBlockEntity(rowPos) instanceof SafetyDepositBoxRowBlockEntity row)) {
            return Resolution.failure("The assigned safety deposit row is not loaded.");
        }
        int normalizedDoor = row.getModuleStartForRow(assignment.doorIndex());
        if (normalizedDoor < 0 || normalizedDoor != assignment.doorIndex()
                || !row.isAssignableBoxStart(normalizedDoor)) {
            return Resolution.failure("The assigned safety deposit door is malformed.");
        }

        SafeVaultReadinessOperation readinessOperation =
                SafetyDepositBoxService.safeDepositVaultReadinessOperation(server, metadata);
        SafeVaultReadinessResolver.RowReadiness readiness = readinessOperation.resolve(
                new SafeVaultReadinessResolver.RowLocation(assignment.dimension(), rowPos));
        if (!matchesReadyVault(readiness, bankId, selected.vaultId())) {
            return Resolution.failure("The assigned safety deposit vault is no longer ready.");
        }
        SafeTellerRoutePairResolver.Pair routes = SafeBoxEscortMetadataRoutes.exactPair(
                new SafeTellerRoutePairResolver.TellerRequest(
                        new SafeTellerRoutePairResolver.VaultRequest(
                                readinessOperation.routes(), bankId, readiness.vault().id()), teller.getUUID()))
                .orElse(null);
        if (routes == null) {
            return Resolution.failure("This teller has no exact outbound and return route for the assigned vault.");
        }
        SafeTellerRoute outbound = routes.outbound();
        SafeTellerRoute returning = routes.returning();
        BlockPos doorMaster = readinessOperation.resolveDoorMaster(readiness).orElse(null);
        if (doorMaster == null) {
            return Resolution.failure("The assigned vault routes or vault door reference are invalid.");
        }

        try {
            SafeBoxEscortTarget target = new SafeBoxEscortTarget(
                    bankId, readiness.vault().id(), account.getAccountUUID(), assignment.dimension(),
                    new EscortBlockPosition(rowPos.getX(), rowPos.getY(), rowPos.getZ()),
                    normalizedDoor, teller.getUUID());
            return Resolution.success(new SafeBoxEscortRuntimeContext(
                    UUID.randomUUID(), player.getUUID(), target,
                    area(readiness.premise().bounds()), area(readiness.safeArea().bounds()),
                    new SafeBoxEscortRuntimeContext.Exit(
                            readiness.premise().exit().dimension(), readiness.premise().exit().x(),
                            readiness.premise().exit().y(), readiness.premise().exit().z(),
                            readiness.premise().exit().yaw()),
                    new EscortBlockPosition(doorMaster.getX(), doorMaster.getY(), doorMaster.getZ()),
                    outbound, returning, assignment.label()));
        } catch (IllegalArgumentException exception) {
            return Resolution.failure("The assigned vault route does not match the current setup.");
        }
    }

    static boolean freshlyAuthorized(MinecraftServer server,
                                     CentralBank centralBank,
                                     SafeBoxEscortRuntimeContext context) {
        return SafeBoxEscortLiveAuthorization.verify(server, centralBank, context);
    }

    static boolean matchesFreshSnapshot(SafeBoxEscortRuntimeContext context,
                                        SafeBoxEscortAuthorizationSnapshot snapshot) {
        return context != null && snapshot != null && snapshot.matches(context);
    }

    static boolean matchesReadyVault(SafeVaultReadinessResolver.RowReadiness readiness,
                                     UUID bankId,
                                     String vaultId) {
        return readiness != null
                && bankId != null
                && vaultId != null
                && readiness.mapped()
                && readiness.premise() != null
                && readiness.safeArea() != null
                && readiness.vault() != null
                && readiness.summary() != null
                && readiness.summary().ready()
                && bankId.toString().equalsIgnoreCase(readiness.premise().bankId())
                && vaultId.equals(readiness.vault().id());
    }

    static Optional<ExactAssignment> exactAssignment(CompoundTag metadata, UUID accountId) {
        if (metadata == null || accountId == null) {
            return Optional.empty();
        }
        ExactAssignment found = null;
        ListTag assignments = metadata.getList(SafetyDepositBoxService.ASSIGNMENTS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < assignments.size(); index++) {
            CompoundTag tag = assignments.getCompound(index);
            if (!tag.hasUUID("accountId") || !accountId.equals(tag.getUUID("accountId"))) {
                continue;
            }
            ExactAssignment candidate = readAssignment(tag);
            if (candidate == null || found != null) {
                return Optional.empty();
            }
            found = candidate;
        }
        return Optional.ofNullable(found);
    }

    private static ExactAssignment readAssignment(CompoundTag tag) {
        if (!tag.hasUUID("bankId") || !tag.contains("dimension", Tag.TAG_STRING)
                || !tag.contains("x", Tag.TAG_INT) || !tag.contains("y", Tag.TAG_INT)
                || !tag.contains("z", Tag.TAG_INT) || !tag.contains("doorIndex", Tag.TAG_INT)
                || !tag.contains("boxNumber", Tag.TAG_STRING)) {
            return null;
        }
        String dimension = SafeBlockBounds.normalizeDimension(tag.getString("dimension"));
        int door = tag.getInt("doorIndex");
        if (dimension.isBlank() || door < 0 || door >= SafetyDepositBoxRowBlockEntity.DOOR_COUNT) {
            return null;
        }
        return new ExactAssignment(tag.getUUID("bankId"), dimension,
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                door, tag.getString("boxNumber"), tag.getBoolean("locked"));
    }

    private static SafeBoxArea area(SafeBlockBounds bounds) {
        return new SafeBoxArea(bounds.dimension(), bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(SafeBlockBounds.normalizeDimension(dimension));
        if (id == null) {
            return null;
        }
        ResourceKey<Level> key = RegistryKeysCompat.createValueKey(
                RegistryKeysCompat.DIMENSION_REGISTRY_KEY, id);
        return server.getLevel(key);
    }

    record CheckoutRequest(MinecraftServer server,
                           CentralBank centralBank,
                           ServerPlayer player,
                           BankTellerEntity teller,
                           BankTellerSafeBoxState.AccountAssignment selected) {
    }

    record Resolution(SafeBoxEscortRuntimeContext context, String message) {
        static Resolution success(SafeBoxEscortRuntimeContext context) {
            return new Resolution(context, "");
        }

        static Resolution failure(String message) {
            return new Resolution(null, message);
        }

        boolean success() {
            return context != null;
        }
    }

    record ExactAssignment(UUID bankId, String dimension, BlockPos rowPos,
                           int doorIndex, String label, boolean locked) {
        boolean matches(BankTellerSafeBoxState.AccountAssignment selected) {
            return selected != null && dimension.equals(SafeBlockBounds.normalizeDimension(selected.dimension()))
                    && rowPos.equals(new BlockPos(selected.x(), selected.y(), selected.z()))
                    && doorIndex == selected.doorIndex() && label.equals(selected.assignmentLabel());
        }

    }
}
