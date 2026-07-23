package net.austizz.ultimatebankingsystem.bank.owner.premise;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimToolPurpose;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.SafeBoxViewingCoordinator;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomService;
import net.austizz.ultimatebankingsystem.bank.safebox.zone.SafeBoxZoneCache;
import net.austizz.ultimatebankingsystem.network.OwnerPcDesktopDataPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcPremiseActionPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class OwnerPcPremiseServerPorts implements OwnerPcPremiseService.Ports {
    private final MinecraftServer server;
    private final CentralBank centralBank;
    private final ServerPlayer player;
    private final boolean requireNearbyComputer;
    private final OwnerPcPremiseService.Authority authorityOverride;

    private OwnerPcPremiseServerPorts(MinecraftServer server,
                                      CentralBank centralBank,
                                      ServerPlayer player,
                                      boolean requireNearbyComputer) {
        this(server, centralBank, player, requireNearbyComputer, null);
    }

    private OwnerPcPremiseServerPorts(MinecraftServer server,
                                      CentralBank centralBank,
                                      ServerPlayer player,
                                      boolean requireNearbyComputer,
                                      OwnerPcPremiseService.Authority authorityOverride) {
        this.server = server;
        this.centralBank = centralBank;
        this.player = player;
        this.requireNearbyComputer = requireNearbyComputer;
        this.authorityOverride = authorityOverride;
    }

    static OwnerPcPremiseServerPorts forDirectAction(MinecraftServer server,
                                                      CentralBank centralBank,
                                                      ServerPlayer player) {
        return new OwnerPcPremiseServerPorts(server, centralBank, player, true);
    }

    static OwnerPcPremiseServerPorts forDeferredClaimApply(MinecraftServer server,
                                                            CentralBank centralBank,
                                                            ServerPlayer player) {
        return new OwnerPcPremiseServerPorts(server, centralBank, player, false);
    }

    static OwnerPcPremiseServerPorts forAdmin(MinecraftServer server,
                                               CentralBank centralBank,
                                               UUID bankId) {
        CompoundTag metadata = centralBank == null || bankId == null
                ? null
                : centralBank.getBankMetadata().get(bankId);
        boolean bankExists = centralBank != null && bankId != null
                && (bankId.equals(centralBank.getBankId()) || centralBank.getBank(bankId) != null);
        OwnerPcPremiseService.Authority authority = new OwnerPcPremiseService.Authority(
                metadata == null ? null : OwnerPcPremisePayloadBuilder.metadataModel(metadata),
                bankExists, true, true, true, false, true);
        return new OwnerPcPremiseServerPorts(server, centralBank, null, false, authority);
    }

    static OwnerPcPremiseServerPorts forIntegrationTest(
            MinecraftServer server,
            CentralBank centralBank,
            OwnerPcPremiseService.Authority authority) {
        return new OwnerPcPremiseServerPorts(server, centralBank, null, false, authority);
    }

    @Override
    public OwnerPcPremiseService.Authority authority(UUID bankId) {
        if (authorityOverride != null) {
            return authorityOverride;
        }
        if (server == null || centralBank == null || player == null || bankId == null) {
            return null;
        }
        boolean validComputer = BankOwnerPcService.hasValidDesktopContext(
                server, player, requireNearbyComputer);
        OwnerPcDesktopDataPayload desktop = validComputer
                ? BankOwnerPcService.buildDesktopData(centralBank, player.getUUID())
                : null;
        CompoundTag metadata = centralBank.getBankMetadata().get(bankId);
        return new OwnerPcPremiseService.Authority(
                metadata == null ? null : OwnerPcPremisePayloadBuilder.metadataModel(metadata),
                centralBank.getBank(bankId) != null,
                validComputer && desktop != null && desktop.computerId() != null
                        && !desktop.computerId().isBlank(),
                desktop != null && desktop.poweredOn(),
                desktop != null && desktop.sessionUnlocked(),
                BankOwnerPcService.isOwner(centralBank, player.getUUID(), bankId),
                player.hasPermissions(3));
    }

    @Override
    public OwnerPcPremiseService.SessionResult startSession(
            UUID bankId,
            SafeClaimToolPurpose purpose,
            String premiseId) {
        SafetyDepositBoxService.ActionResult result =
                SafetyDepositBoxService.startPremiseClaimToolSession(
                        server, centralBank, player, bankId, purpose, premiseId);
        return new OwnerPcPremiseService.SessionResult(result.success(), result.message());
    }

    @Override
    public OwnerPcPremiseService.Result withMutation(
            UUID bankId,
            OwnerPcPremiseActionPayload.Action action,
            String premiseId,
            OwnerPcPremiseService.Mutation mutation) {
        synchronized (SafeBoxViewingCoordinator.class) {
            OwnerPcPremiseService.Authority authority = authority(bankId);
            Set<String> activeVaultIds = SafeBoxViewingCoordinator.activePremiseIds(server);
            AtomicBoolean committed = new AtomicBoolean();
            Consumer<Map<String, Object>> commit = updated -> commit(
                    bankId, action, premiseId, authority, updated, committed);
            try {
                OwnerPcPremiseService.Result result = mutation.apply(
                        authority, activeVaultIds, commit);
                if (result == null || result.success() != committed.get()) {
                    return failure(bankId, action, premiseId,
                            "Premise action did not complete atomically.");
                }
                return result;
            } catch (RuntimeException exception) {
                UltimateBankingSystem.LOGGER.error(
                        "[UBS] Premise mutation failed for bank {} action {}",
                        bankId, action, exception);
                return failure(bankId, action, premiseId,
                        "Premise action failed before it could be committed.");
            }
        }
    }

    private void commit(UUID bankId,
                        OwnerPcPremiseActionPayload.Action action,
                        String premiseId,
                        OwnerPcPremiseService.Authority authority,
                        Map<String, Object> updated,
                        AtomicBoolean committed) {
        if (authority == null || authority.metadata() == null || updated == null
                || !committed.compareAndSet(false, true)) {
            throw new IllegalStateException("Premise mutation commit is unavailable or duplicated.");
        }
        CompoundTag current = centralBank.getBankMetadata().get(bankId);
        if (current == null || !authority.metadata().equals(
                OwnerPcPremisePayloadBuilder.metadataModel(current))) {
            throw new IllegalStateException("Premise metadata changed during the server action.");
        }
        CompoundTag staged = OwnerPcPremisePayloadBuilder.applySetupMutation(
                current, authority.metadata(), updated);
        if (action == OwnerPcPremiseActionPayload.Action.DELETE) {
            ViewingRoomService.removeRoomsForPremise(staged, premiseId);
        }
        centralBank.putBankMetadata(bankId, staged);
        SafeBoxZoneCache.clear(server);
    }

    private static OwnerPcPremiseService.Result failure(
            UUID bankId,
            OwnerPcPremiseActionPayload.Action action,
            String premiseId,
            String message) {
        return new OwnerPcPremiseService.Result(
                bankId, action, premiseId, false, message);
    }
}
