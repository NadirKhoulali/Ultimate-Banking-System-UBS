package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

final class OwnerPcActionServerPorts implements OwnerPcActionRequestDispatcher.Ports {
    private final ServerPlayer player;

    OwnerPcActionServerPorts(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public BankOwnerPcService.ActionResult executeDirect(OwnerPcActionRequestDispatcher.Request request) {
        MinecraftServer server = player == null ? null : player.getServer();
        CentralBank centralBank = server == null ? null : BankManager.getCentralBank(server);
        if (server == null || centralBank == null) {
            return new BankOwnerPcService.ActionResult(false, "Bank data is unavailable.");
        }
        return BankOwnerPcService.executeDirectAction(
                server, centralBank, player, request.bankId(), request.action(),
                request.arg1(), request.arg2(), request.arg3(), request.arg4());
    }

    @Override
    public void sendResponse(OwnerPcActionRequestDispatcher.Response response) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new OwnerPcActionResponsePayload(
                    response.bankId(), response.success(), response.message()));
        }
    }

    @Override
    public void refreshBankData(UUID bankId) {
        MinecraftServer server = player == null ? null : player.getServer();
        CentralBank centralBank = server == null ? null : BankManager.getCentralBank(server);
        if (centralBank == null || bankId == null) {
            return;
        }
        OwnerPcBankDataPayload payload = BankOwnerPcService.buildBankDataPayload(
                server, centralBank, player.getUUID(), bankId,
                bankId.equals(centralBank.getBankId()) && player.hasPermissions(3));
        if (payload != null) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
