package net.austizz.ultimatebankingsystem.bank.owner.premise;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimSelection;
import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimToolPurpose;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortCoordinator;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.SafeBoxViewingCoordinator;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomNbtStore;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeAreaSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupMigration;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupNbtCodec;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.zone.SafeBoxZoneCache;
import net.austizz.ultimatebankingsystem.network.OwnerPcPremiseActionPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class OwnerPcPremiseAdminService {
    private static final int ADMIN_PERMISSION_LEVEL = 3;

    private OwnerPcPremiseAdminService() {
    }

    public static OwnerPcPremiseService.Result execute(MinecraftServer server,
                                                       CentralBank centralBank,
                                                       ServerPlayer player,
                                                       OwnerPcPremiseActionPayload payload) {
        if (!authorized(player) || server == null || centralBank == null || payload == null) {
            return failure(payload, "Administrator premise management is unavailable.");
        }
        return OwnerPcPremiseService.execute(
                OwnerPcPremiseServerPorts.forAdmin(server, centralBank, payload.bankId()), payload);
    }

    public static OwnerPcPremiseService.Result forceDelete(MinecraftServer server,
                                                           CentralBank centralBank,
                                                           ServerPlayer player,
                                                           UUID bankId,
                                                           String premiseId) {
        if (!authorized(player) || server == null || centralBank == null
                || bankId == null || premiseId == null || premiseId.isBlank()) {
            return new OwnerPcPremiseService.Result(
                    bankId, OwnerPcPremiseActionPayload.Action.DELETE, premiseId,
                    false, "Administrator premise deletion is unavailable.");
        }
        synchronized (SafeBoxEscortCoordinator.class) {
            CompoundTag current = centralBank.getBankMetadata().get(bankId);
            SafePremiseSnapshot premise = current == null
                    ? null
                    : SafeDepositSetupNbtCodec.snapshot(current).premises().stream()
                            .filter(candidate -> premiseId.equals(candidate.id()))
                            .filter(candidate -> bankId.toString().equalsIgnoreCase(candidate.bankId()))
                            .findFirst()
                            .orElse(null);
            if (premise == null || premise.bounds() == null) {
                return new OwnerPcPremiseService.Result(
                        bankId, OwnerPcPremiseActionPayload.Action.DELETE, premiseId,
                        false, "Premise not found: " + premiseId);
            }

            Set<String> vaultIds = vaultIds(premise);
            CompoundTag staged = current.copy();
            int removedPremises = removePremise(staged, bankId, premiseId);
            if (removedPremises != 1) {
                return new OwnerPcPremiseService.Result(
                        bankId, OwnerPcPremiseActionPayload.Action.DELETE, premiseId,
                        false, "Premise metadata changed before the administrator deletion.");
            }
            int removedRoutes = removeRoutes(staged, bankId, vaultIds);
            int removedLegacyAreas = removeLegacyAreas(staged, premise.bounds());
            int cancelledEscorts = SafeBoxEscortCoordinator.cancelVaults(server, vaultIds);
            int cancelledViewingSessions = SafeBoxViewingCoordinator.activeRoomIds(server).stream()
                    .filter(roomId -> ViewingRoomNbtStore.read(staged).stream()
                            .anyMatch(room -> room.id().equals(roomId) && premiseId.equals(room.premiseId())))
                    .mapToInt(roomId -> SafeBoxViewingCoordinator.cancelRoom(server, roomId))
                    .sum();
            int removedViewingRooms = net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomService
                    .removeRoomsForPremise(staged, premiseId);
            int removedAssignments = SafetyDepositBoxService.forceRemoveAssignmentsInPremise(
                    server, staged, bankId, premise.bounds());

            centralBank.putBankMetadata(bankId, staged);
            SafeBoxZoneCache.clear(server);
            String message = "Premise force-deleted. Removed " + removedAssignments
                    + " assignment(s), " + removedRoutes + " route(s), "
                    + removedLegacyAreas + " legacy area record(s), and cancelled "
                    + cancelledEscorts + " legacy escort(s), cancelled " + cancelledViewingSessions
                    + " viewing session(s), and removed " + removedViewingRooms + " viewing room(s).";
            return new OwnerPcPremiseService.Result(
                    bankId, OwnerPcPremiseActionPayload.Action.DELETE, premiseId, true, message);
        }
    }

    public static SafetyDepositBoxService.ActionResult startClaimSession(
            MinecraftServer server,
            CentralBank centralBank,
            ServerPlayer player,
            UUID bankId,
            SafeClaimToolPurpose purpose,
            String premiseId) {
        if (player == null) {
            return SafetyDepositBoxService.ActionResult.fail(
                    "Run this command as a player to use the premise claim tool.");
        }
        if (!authorized(player)) {
            return SafetyDepositBoxService.ActionResult.fail(
                    "You do not have permission to manage bank premises.");
        }
        boolean bankExists = centralBank != null && bankId != null
                && (bankId.equals(centralBank.getBankId()) || centralBank.getBank(bankId) != null);
        if (!bankExists) {
            return SafetyDepositBoxService.ActionResult.fail("The selected bank no longer exists.");
        }
        centralBank.getOrCreateBankMetadata(bankId);
        return SafetyDepositBoxService.startAdminPremiseClaimToolSession(
                server, centralBank, player, bankId, purpose, premiseId);
    }

    public static OwnerPcPremiseService.Result applyObservedSelection(
            MinecraftServer server,
            CentralBank centralBank,
            ServerPlayer player,
            UUID bankId,
            SafeClaimToolPurpose purpose,
            String premiseId,
            SafeClaimSelection selection) {
        OwnerPcPremiseActionPayload.Action action = purpose == SafeClaimToolPurpose.PREMISE_EXIT_EDIT
                ? OwnerPcPremiseActionPayload.Action.START_EXIT_EDIT
                : OwnerPcPremiseActionPayload.Action.START_CLAIM;
        if (player == null || !authorized(player) || server == null || centralBank == null) {
            return new OwnerPcPremiseService.Result(
                    bankId, action, premiseId, false,
                    "Administrator permission is required to apply this premise selection.");
        }
        return OwnerPcPremiseService.applyObservedSelection(
                OwnerPcPremiseServerPorts.forAdmin(server, centralBank, bankId),
                bankId, purpose, premiseId, selection);
    }

    private static boolean authorized(ServerPlayer player) {
        return player == null || player.hasPermissions(ADMIN_PERMISSION_LEVEL);
    }

    private static Set<String> vaultIds(SafePremiseSnapshot premise) {
        Set<String> ids = new LinkedHashSet<>();
        for (SafeAreaSnapshot area : premise.safeAreas()) {
            for (SafeVaultSnapshot vault : area.vaults()) {
                if (vault != null && vault.id() != null && !vault.id().isBlank()) {
                    ids.add(vault.id());
                }
            }
        }
        return Set.copyOf(ids);
    }

    private static int removePremise(CompoundTag metadata, UUID bankId, String premiseId) {
        ListTag existing = metadata.getList(
                SafeDepositSetupMigration.PREMISES_KEY, Tag.TAG_COMPOUND);
        ListTag retained = new ListTag();
        int removed = 0;
        for (int index = 0; index < existing.size(); index++) {
            CompoundTag premise = existing.getCompound(index);
            if (premiseId.equals(premise.getString("id"))
                    && bankId.toString().equalsIgnoreCase(premise.getString("bankId"))) {
                removed++;
            } else {
                retained.add(premise.copy());
            }
        }
        metadata.put(SafeDepositSetupMigration.PREMISES_KEY, retained);
        return removed;
    }

    private static int removeRoutes(CompoundTag metadata, UUID bankId, Set<String> vaultIds) {
        ListTag existing = metadata.getList("safeTellerRoutes", Tag.TAG_COMPOUND);
        ListTag retained = new ListTag();
        int removed = 0;
        for (int index = 0; index < existing.size(); index++) {
            CompoundTag route = existing.getCompound(index);
            boolean belongs = bankId.toString().equalsIgnoreCase(route.getString("bankId"))
                    && vaultIds.contains(route.getString("vaultId"));
            if (belongs) {
                removed++;
            } else {
                retained.add(route.copy());
            }
        }
        metadata.put("safeTellerRoutes", retained);
        return removed;
    }

    private static int removeLegacyAreas(CompoundTag metadata, SafeBlockBounds premiseBounds) {
        ListTag existing = metadata.getList(
                SafeDepositSetupMigration.LEGACY_AREAS_KEY, Tag.TAG_COMPOUND);
        ListTag retained = new ListTag();
        int removed = 0;
        for (int index = 0; index < existing.size(); index++) {
            CompoundTag area = existing.getCompound(index);
            SafeBlockBounds areaBounds = storedBounds(area);
            if (areaBounds != null && premiseBounds.contains(areaBounds)) {
                removed++;
            } else {
                retained.add(area.copy());
            }
        }
        metadata.put(SafeDepositSetupMigration.LEGACY_AREAS_KEY, retained);
        return removed;
    }

    private static SafeBlockBounds storedBounds(CompoundTag tag) {
        if (tag == null || tag.getString("dimension").isBlank()) {
            return null;
        }
        String[] coordinates = {"minX", "minY", "minZ", "maxX", "maxY", "maxZ"};
        for (String coordinate : coordinates) {
            if (!tag.contains(coordinate, Tag.TAG_INT)) {
                return null;
            }
        }
        return new SafeBlockBounds(
                tag.getString("dimension"),
                tag.getInt("minX"), tag.getInt("minY"), tag.getInt("minZ"),
                tag.getInt("maxX"), tag.getInt("maxY"), tag.getInt("maxZ"));
    }

    private static OwnerPcPremiseService.Result failure(
            OwnerPcPremiseActionPayload payload,
            String message) {
        UUID bankId = payload == null ? null : payload.bankId();
        OwnerPcPremiseActionPayload.Action action = payload == null
                ? OwnerPcPremiseActionPayload.Action.DELETE
                : payload.action();
        String premiseId = payload == null ? "" : payload.premiseId();
        return new OwnerPcPremiseService.Result(bankId, action, premiseId, false, message);
    }
}
