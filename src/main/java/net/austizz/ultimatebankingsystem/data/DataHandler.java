package net.austizz.ultimatebankingsystem.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public class DataHandler extends SavedData {

    public static DataHandler create() {
        return new DataHandler();
    }
    public static DataHandler load(CompoundTag tag) {
        DataHandler data = DataHandler.create();

        return data;
    }
    @Override
    public CompoundTag save(CompoundTag tag) {

        return tag;
    }

    public void foo() {
        // Change data in saved data
        // Call set dirty if data changes
        this.setDirty();
    }

}
