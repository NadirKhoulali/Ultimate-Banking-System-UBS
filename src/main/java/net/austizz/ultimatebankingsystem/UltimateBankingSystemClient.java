package net.austizz.ultimatebankingsystem;

import net.austizz.ultimatebankingsystem.client.HudClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = UltimateBankingSystem.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = UltimateBankingSystem.MODID, value = Dist.CLIENT)
public class UltimateBankingSystemClient {
    public UltimateBankingSystemClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        UltimateBankingSystem.LOGGER.info("HELLO FROM CLIENT SETUP");
        UltimateBankingSystem.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        if (!HudClientState.isEnabled()) {
            return;
        }

        String balance = HudClientState.getBalanceText();
        if (balance.isBlank()) {
            return;
        }

        String text = "Balance: " + Config.CURRENCY_SYMBOL.get() + balance;
        GuiGraphics graphics = event.getGuiGraphics();
        int color = Config.HUD_TEXT_COLOR.get();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int textWidth = mc.font.width(text);
        int textHeight = mc.font.lineHeight;

        int x = 6;
        int y = 6;
        String corner = Config.HUD_CORNER.get().trim().toUpperCase();
        switch (corner) {
            case "TOP_RIGHT" -> {
                x = width - textWidth - 6;
                y = 6;
            }
            case "BOTTOM_LEFT" -> {
                x = 6;
                y = height - textHeight - 6;
            }
            case "BOTTOM_RIGHT" -> {
                x = width - textWidth - 6;
                y = height - textHeight - 6;
            }
            default -> {
                x = 6;
                y = 6;
            }
        }

        graphics.drawString(mc.font, text, x, y, color, true);
    }
}
