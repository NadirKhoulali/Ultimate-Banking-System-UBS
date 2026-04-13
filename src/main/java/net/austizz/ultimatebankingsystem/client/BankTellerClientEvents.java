package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.client.renderer.BankTellerRenderer;
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
    }
}

