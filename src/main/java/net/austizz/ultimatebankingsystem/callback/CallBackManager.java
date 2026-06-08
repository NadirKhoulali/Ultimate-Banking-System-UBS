package net.austizz.ultimatebankingsystem.callback;

import net.austizz.ultimatebankingsystem.network.ServerActionAlert;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class CallBackManager {

    private static final ConcurrentHashMap<UUID, Consumer<ServerPlayer>> CALLBACKS = new ConcurrentHashMap<>();

    public static String createCallback(Consumer<ServerPlayer> action) {
        UUID id = UUID.randomUUID();
        CALLBACKS.put(id, action);

        // Optioneel: Verwijder de callback na 5 minuten om geheugenlekken te voorkomen
        new Thread(() -> {
            try { Thread.sleep(300000); } catch (InterruptedException ignored) {}
            CALLBACKS.remove(id);
        }).start();

        return id.toString();
    }

    public static void execute(UUID id, ServerPlayer player) {
        Consumer<ServerPlayer> action = CALLBACKS.remove(id); // remove() voert het uit én verwijdert het direct
        if (action == null) {
            String message = "Action either expired or cancelled, please try again!";
            player.sendSystemMessage(Component.literal(message));
            // Mirror callback expiry/cancel feedback into the unified top alert system.
            ServerActionAlert.send(player, "Action", message, net.austizz.ultimatebankingsystem.network.DeliveryAlertPayload.AlertTone.WARNING, 3600);
            return;
        }
        action.accept(player);
    }
    public static void removeCallback(UUID id) {
        CALLBACKS.remove(id);
    }
}
