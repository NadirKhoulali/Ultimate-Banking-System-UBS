package net.austizz.ultimatebankingsystem.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class OwnerPcScreenHelper {
    private static BankOwnerPcScreen cachedScreen;

    public static void openOwnerPcScreen() {
        if (cachedScreen == null) {
            cachedScreen = new BankOwnerPcScreen(Component.literal("Bank Owner PC"));
        }
        Minecraft.getInstance().setScreen(cachedScreen);
    }

    public static void invalidateCachedScreen(BankOwnerPcScreen screen) {
        if (cachedScreen == screen) {
            cachedScreen = null;
        }
    }

    public static void invalidateCachedScreen() {
        cachedScreen = null;
    }
}
