package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.gui.screens.NumismaticsMigrationScreen;
import net.austizz.ultimatebankingsystem.migration.numismatics.NumismaticsMigrationSnapshot;
import net.minecraft.client.Minecraft;

public final class NumismaticsMigrationClient {
    private NumismaticsMigrationClient() {
    }

    public static void accept(String snapshotJson) {
        NumismaticsMigrationSnapshot snapshot = NumismaticsMigrationSnapshot.fromJson(snapshotJson);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof NumismaticsMigrationScreen screen) {
                screen.refresh(snapshot);
            } else {
                minecraft.setScreen(new NumismaticsMigrationScreen(snapshot));
            }
        });
    }
}
