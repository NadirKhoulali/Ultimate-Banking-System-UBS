package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.client.renderer.BankTellerRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.GlassCounterDisplayRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.ModularWallDisplayRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.PalletRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.ShopSellingTableRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.ShoppingBasketRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.TallWallShelfRenderer;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.entity.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BankTellerClientEvents {

    private BankTellerClientEvents() {}

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BANK_TELLER.get(), BankTellerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TALL_WALL_SHELF.get(), TallWallShelfRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SHOP_SELLING_TABLE.get(), ShopSellingTableRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SHOPPING_BASKET.get(), ShoppingBasketRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PALLET.get(), PalletRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MODULAR_WALL_DISPLAY.get(), ModularWallDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GLASS_COUNTER_DISPLAY.get(), GlassCounterDisplayRenderer::new);
    }
}
