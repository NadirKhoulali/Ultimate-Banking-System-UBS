package net.austizz.ultimatebankingsystem.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ATMScreenHelper {
    public static void openATMScreen() {
        Minecraft.getInstance().setScreen(new BankScreen(Component.literal("ATM Machine")));
    }
}
