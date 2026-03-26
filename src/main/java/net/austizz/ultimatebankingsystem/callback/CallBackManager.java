package net.austizz.ultimatebankingsystem.callback;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.checkerframework.checker.units.qual.C;

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
            player.sendSystemMessage(Component.literal("Action either expired or cancelled, please try again!"));
            return;
        }
        action.accept(player);
    }
    public static void removeCallback(UUID id) {
        CALLBACKS.remove(id);
    }
}
