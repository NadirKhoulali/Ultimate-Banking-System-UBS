package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.gui.screens.CardboardBoxScreen;
import net.austizz.ultimatebankingsystem.gui.screens.ShoppingBagScreen;
import net.austizz.ultimatebankingsystem.menu.ModMenus;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientModBusEvents {
    private ClientModBusEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.GLASS_COUNTER_DISPLAY.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.GLASS_COUNTER_DISPLAY_OPEN.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY_OPEN.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.MODULAR_WALL_DISPLAY.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CREATIVE_MODULAR_WALL_DISPLAY.get(), RenderType.translucent());
        });
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CARDBOARD_BOX.get(), CardboardBoxScreen::new);
        event.register(ModMenus.SHOPPING_BAG.get(), ShoppingBagScreen::new);
        event.register(ModMenus.SHOPPING_BAG_ITEM.get(), ShoppingBagScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        PickpocketKeyMappings.register(event);
    }
}
