package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class MainMenuLayer extends AbstractScreenLayer {

    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem",
            "textures/gui/atm_buttons.png"
    );

    public MainMenuLayer(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    protected void onInit() {
        Component title = Component.literal("ATM Machine").withStyle(ChatFormatting.BLACK);
        int titlePx = minecraft.font.width(title);

        addWidget(new MultiLineTextWidget(
                (screenWidth / 2) - (titlePx / 2),
                (screenHeight / 2) - 80,
                title,
                minecraft.font
        ));

        // 9-slice button example.
        // Texture expected:
        // - assets/ultimatebankingsystem/textures/gui/atm_buttons.png
        // - PNG size: 120x40
        // - Normal frame: 120x20 at (u=0,v=0)
        // - Hover frame:  120x20 at (u=0,v=20)
        // Border sizes (4,4,4,4) mean the corners are 4x4 and the center/edges tile.
        addWidget(new NineSliceTexturedButton(
                (screenWidth / 2) - 60, // x on screen
                (screenHeight / 2) - 40, // y on screen
                120, 20, // button size ON SCREEN
                ATM_BUTTONS,
                0, 0, // where the normal frame starts inside the PNG
                120, 20, // frame size INSIDE THE PNG
                120, 40, // FULL PNG size
                4, 4, 4, 4, // left, right, top, bottom borders
                Component.literal("Deposit").withStyle(ChatFormatting.BLACK),
                btn -> {
                    // TODO: click action
                }
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Optional custom drawing for the layer.
    }
}
