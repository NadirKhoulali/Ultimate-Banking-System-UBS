package net.austizz.ultimatebankingsystem.bank.data;

import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BankSavedData extends SavedData {
    private CentralBank centralBank;

    // Constructor voor een nieuwe save (zonder data)
    public BankSavedData() {
        this.centralBank = new CentralBank();
    }

    // Constructor voor het laden van bestaande data
    public BankSavedData(CentralBank centralBank) {
        this.centralBank = centralBank;
    }

    public CentralBank getCentralBank() {
        return this.centralBank;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        // De CentralBank slaat zichzelf EN zijn sub-banken op via zijn eigen save()
        return this.centralBank.save(tag, registries);
    }

    public static BankSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        // Suspend dirty marking during NBT load to avoid recursive re-entry into data storage
        BankManager.beginSuspendDirty();
        try {
            CentralBank loadedCentralBank = CentralBank.load(tag, registries);
            return new BankSavedData(loadedCentralBank);
        } finally {
            BankManager.endSuspendDirty();
        }
    }

    public static SavedData.Factory<BankSavedData> factory() {
        return new SavedData.Factory<>(
                BankSavedData::new,    // Voor nieuwe werelden
                BankSavedData::load,   // Voor bestaande werelden
                null
        );
    }
    // In BankSavedData.java
    public static void markDirty(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            BankSavedData data = overworld.getDataStorage().get(BankSavedData.factory(), "ultimate_banking_system");
            if (data != null) {
                data.setDirty(); // <--- DIT zorgt ervoor dat Minecraft gaat opslaan
            }
        }
    }

}

