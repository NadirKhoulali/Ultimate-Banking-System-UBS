package net.austizz.ultimatebankingsystem.bank.handler;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.data.BankSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BankManager {
    private static MinecraftServer serverInstance;
    private static CentralBank centralBank;
    private static BankSavedData dataRef;

    // Prevent dirty marking during NBT (de)serialization to avoid recursion on load
    private static int suspendDirtyDepth = 0;

    // Wordt aangeroepen bij ServerAboutToStart of ServerStarting
    public static void init(MinecraftServer server) {
        // We slaan de bankdata op in de Overworld (centrale plek)
        serverInstance = server;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);

        if (overworld != null) {
            // Dit roept BankSavedData.load() aan als het bestand bestaat,
            // of de constructor BankSavedData() als het nieuw is.
            dataRef = overworld.getDataStorage().computeIfAbsent(
                    BankSavedData.factory(),
                    "ultimate_banking_system"
            );

            centralBank = dataRef.getCentralBank();

            // Log even of het gelukt is
            UltimateBankingSystem.LOGGER.info("[UBS] Central Bank loaded with {} bank(s).", centralBank.getBanks().size());
        } else {
            UltimateBankingSystem.LOGGER.warn("[UBS] Overworld level is null during BankManager.init; data won't load.");
        }
    }

    // Gebruik dit overal in je mod om bij de banken te komen
    public static CentralBank getCentralBank(MinecraftServer server) {
        if (server != null && server != serverInstance) {
            // Server changed (e.g., switched worlds in SP). Re-init for this server.
            init(server);
        }
        if (centralBank == null && server != null) {
            init(server); // Forceer laden als het nog niet gebeurd is
        }
        return centralBank;
    }

    public static void markDirty() {
        // Avoid recursive loads: during NBT (de)serialization we never want to mark dirty
        if (suspendDirtyDepth > 0) {
            return;
        }
        if (dataRef != null) {
            dataRef.setDirty();
        } else if (serverInstance != null) {
            // Fallback: try to fetch and mark
            ServerLevel overworld = serverInstance.getLevel(Level.OVERWORLD);
            if (overworld != null) {
                BankSavedData data = overworld.getDataStorage().get(BankSavedData.factory(), "ultimate_banking_system");
                if (data != null) {
                    data.setDirty();
                }
            }
        }
    }

    // Call before starting to reconstruct objects from NBT; call endSuspendDirty() in a finally block
    public static void beginSuspendDirty() {
        suspendDirtyDepth++;
    }

    public static void endSuspendDirty() {
        if (suspendDirtyDepth > 0) {
            suspendDirtyDepth--;
        }
    }

    public static boolean isDirtySuspended() {
        return suspendDirtyDepth > 0;
    }

    public static void shutdown() {
        // Mark current data dirty so it gets flushed on save, then clear references
        if (dataRef != null) {
            dataRef.setDirty();
        }
        serverInstance = null;
        centralBank = null;
        dataRef = null;

        UltimateBankingSystem.LOGGER.info("[UBS] BankManager shutdown: cleared cached references.");
    }
//    public  static void CountDownDirty() {
//        ScheduledExecutorService scheduler = null;
//        try {
//             scheduler = Executors.newSingleThreadScheduledExecutor();
//
//        }catch (Exception e) {
//            UltimateBankingSystem.LOGGER.error(e.getMessage());
//        }finally {
//            Runnable task = ()-> {
//                markDirty();
//                String timestamp = java.time.LocalDateTime.now().toString();
//                UltimateBankingSystem.LOGGER.info("saved files at: {}", timestamp);
//            };
//
//            // Start na 1 seconde en herhaal elke 3 seconden
//            assert scheduler != null;
//            scheduler.scheduleAtFixedRate(task, 1, 10, TimeUnit.SECONDS);
//
//        }
//
//    }
}

