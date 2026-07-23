package net.austizz.ultimatebankingsystem.bank.owner;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.network.OwnerPcActionRequestDispatcher;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankDataPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class OwnerPcActionGameTestPorts implements OwnerPcActionRequestDispatcher.Ports {
    private final MinecraftServer server;
    private final CountingCentralBank centralBank;
    private final ServerPlayer player;
    private final List<OwnerPcActionRequestDispatcher.Response> responses = new ArrayList<>();
    private int executeCalls;
    private int refreshCalls;
    private OwnerPcBankDataPayload refreshedPayload;

    OwnerPcActionGameTestPorts(MinecraftServer server,
                               CountingCentralBank centralBank,
                               ServerPlayer player) {
        this.server = server;
        this.centralBank = centralBank;
        this.player = player;
    }

    @Override
    public BankOwnerPcService.ActionResult executeDirect(OwnerPcActionRequestDispatcher.Request request) {
        executeCalls++;
        return BankOwnerPcService.executeDirectAction(
                server, centralBank, player, request.bankId(), request.action(),
                request.arg1(), request.arg2(), request.arg3(), request.arg4());
    }

    @Override
    public void sendResponse(OwnerPcActionRequestDispatcher.Response response) {
        responses.add(response);
    }

    @Override
    public void refreshBankData(UUID bankId) {
        refreshCalls++;
        refreshedPayload = BankOwnerPcService.buildBankDataPayload(
                server, centralBank, player.getUUID(), bankId,
                bankId.equals(centralBank.getBankId()) && player.hasPermissions(3));
    }

    int executeCalls() {
        return executeCalls;
    }

    int refreshCalls() {
        return refreshCalls;
    }

    OwnerPcBankDataPayload refreshedPayload() {
        return refreshedPayload;
    }

    OwnerPcActionRequestDispatcher.Response onlyResponse() {
        return responses.size() == 1 ? responses.getFirst() : null;
    }

    static final class CountingCentralBank extends CentralBank {
        private int metadataWrites;

        @Override
        public void putBankMetadata(UUID bankId, net.minecraft.nbt.CompoundTag metadata) {
            metadataWrites++;
            super.putBankMetadata(bankId, metadata);
        }

        int metadataWrites() {
            return metadataWrites;
        }

        void resetMetadataWrites() {
            metadataWrites = 0;
        }
    }
}
