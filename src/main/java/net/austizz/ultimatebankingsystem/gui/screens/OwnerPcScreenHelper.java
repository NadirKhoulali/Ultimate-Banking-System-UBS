package net.austizz.ultimatebankingsystem.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class OwnerPcScreenHelper {
    public static void openOwnerPcScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        BankOwnerPcScreen freshScreen = new BankOwnerPcScreen(Component.literal("Bank Owner PC"));
        minecraft.setScreen(freshScreen);
        freshScreen.relayoutForCurrentWindow();
    }

    public static void invalidateCachedScreen(BankOwnerPcScreen screen) {
        // No-op: screens are not globally cached anymore.
    }

    public static void invalidateCachedScreen() {
        // No-op: screens are not globally cached anymore.
    }
}
