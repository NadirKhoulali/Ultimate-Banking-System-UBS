package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.migration.numismatics.NumismaticsMigrationService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Locale;

public final class NumismaticsMigrationPayloads {
    private NumismaticsMigrationPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(NumismaticsMigrationOpenPayload.TYPE,
                NumismaticsMigrationOpenPayload.STREAM_CODEC, NumismaticsMigrationPayloads::handleOpen);
        registrar.playToServer(NumismaticsMigrationActionPayload.TYPE,
                NumismaticsMigrationActionPayload.STREAM_CODEC, NumismaticsMigrationPayloads::handleAction);
        registrar.playToServer(NumismaticsMigrationUploadBeginPayload.TYPE,
                NumismaticsMigrationUploadBeginPayload.STREAM_CODEC, NumismaticsMigrationPayloads::handleUploadBegin);
        registrar.playToServer(NumismaticsMigrationUploadChunkPayload.TYPE,
                NumismaticsMigrationUploadChunkPayload.STREAM_CODEC, NumismaticsMigrationPayloads::handleUploadChunk);
        registrar.playToServer(NumismaticsMigrationUploadFinishPayload.TYPE,
                NumismaticsMigrationUploadFinishPayload.STREAM_CODEC, NumismaticsMigrationPayloads::handleUploadFinish);
    }

    private static void handleOpen(NumismaticsMigrationOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> client = Class.forName(
                        "net.austizz.ultimatebankingsystem.client.NumismaticsMigrationClient");
                Method accept = client.getMethod("accept", String.class);
                accept.invoke(null, payload.snapshotJson());
            } catch (ReflectiveOperationException exception) {
                UltimateBankingSystem.LOGGER.error("Could not open the Numismatics migration client", exception);
            }
        });
    }

    private static void handleAction(NumismaticsMigrationActionPayload payload, IPayloadContext context) {
        withPlayer(context, player -> {
            String action = payload.action() == null ? "" : payload.action().trim().toUpperCase(Locale.ROOT);
            switch (action) {
                case "REFRESH" -> NumismaticsMigrationService.refresh(player, payload.token());
                case "WORLD_SOURCE" -> NumismaticsMigrationService.selectWorldSource(player, payload.token());
                case "SERVER_PATH" -> NumismaticsMigrationService.selectServerSource(
                        player, payload.token(), Path.of(payload.text()));
                case "SET_OPTIONS" -> NumismaticsMigrationService.setOptions(player, payload.token(),
                        payload.number(), payload.text(), payload.flag(), payload.secondFlag());
                case "PREFLIGHT" -> NumismaticsMigrationService.runPreflight(player, payload.token());
                case "EXECUTE" -> NumismaticsMigrationService.execute(player, payload.token());
                case "RESUME" -> NumismaticsMigrationService.resume(player, payload.token());
                case "RESET" -> NumismaticsMigrationService.reset(player, payload.token());
                case "ROLLBACK" -> NumismaticsMigrationService.requestRollback(player, payload.token());
                case "CLAIM_RECOVERY" -> NumismaticsMigrationService.claimRecovery(player, payload.token());
                case "STOP_SERVER" -> NumismaticsMigrationService.stopServer(player, payload.token());
                default -> throw new IllegalArgumentException("Unknown Numismatics migration action: " + action);
            }
        });
    }

    private static void handleUploadBegin(NumismaticsMigrationUploadBeginPayload payload, IPayloadContext context) {
        withPlayer(context, player -> NumismaticsMigrationService.beginUpload(player, payload.token(),
                payload.uploadId(), payload.fileName(), payload.size(), payload.chunks(), payload.sha256()));
    }

    private static void handleUploadChunk(NumismaticsMigrationUploadChunkPayload payload, IPayloadContext context) {
        withPlayer(context, player -> NumismaticsMigrationService.acceptUploadChunk(player, payload.token(),
                payload.uploadId(), payload.index(), payload.bytes()));
    }

    private static void handleUploadFinish(NumismaticsMigrationUploadFinishPayload payload, IPayloadContext context) {
        withPlayer(context, player -> NumismaticsMigrationService.finishUpload(
                player, payload.token(), payload.uploadId()));
    }

    private static void withPlayer(IPayloadContext context, PlayerAction action) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            try {
                action.run(player);
            } catch (RuntimeException exception) {
                UltimateBankingSystem.LOGGER.warn("Rejected Numismatics migration request from {}: {}",
                        player.getGameProfile().getName(), exception.getMessage());
                player.sendSystemMessage(Component.literal(exception.getMessage() == null
                        ? "The migration request failed." : exception.getMessage()).withStyle(ChatFormatting.RED));
            }
        });
    }

    @FunctionalInterface
    private interface PlayerAction {
        void run(ServerPlayer player);
    }
}
