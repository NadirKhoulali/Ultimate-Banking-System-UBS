package net.austizz.ultimatebankingsystem.entity;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModEntityEvents {

    private ModEntityEvents() {}

    @SubscribeEvent
    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.BANK_TELLER.get(), BankTellerEntity.createAttributes().build());
    }
}

