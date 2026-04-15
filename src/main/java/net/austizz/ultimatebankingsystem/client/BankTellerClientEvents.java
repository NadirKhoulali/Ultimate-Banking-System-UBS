package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.client.renderer.BankTellerRenderer;
import net.austizz.ultimatebankingsystem.entity.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BankTellerClientEvents {

    private BankTellerClientEvents() {}

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BANK_TELLER.get(), BankTellerRenderer::new);
    }
}
