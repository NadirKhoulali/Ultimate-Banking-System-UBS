package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class DepositLayer extends AbstractScreenLayer {
    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");

    public DepositLayer(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    protected void onInit() {
        addWidget(new NineSliceTexturedButton(
            bankScreen.getPanelLeft() + 10,
            bankScreen.getPanelTop() + bankScreen.getPanelHeight() - 30,
            50, 20,
            ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
            4, 4, 4, 4,
            Component.literal("Back").withStyle(ChatFormatting.WHITE),
            btn -> bankScreen.popLayer()
        ));
    }
}
