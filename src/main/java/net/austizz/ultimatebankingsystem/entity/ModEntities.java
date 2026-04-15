package net.austizz.ultimatebankingsystem.entity;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, UltimateBankingSystem.MODID);

    public static final RegistryObject<EntityType<BankTellerEntity>> BANK_TELLER =
            ENTITY_TYPES.register("bank_teller", () ->
                    EntityType.Builder.of(BankTellerEntity::new, MobCategory.MISC)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build(UltimateBankingSystem.MODID + ":bank_teller"));

    private ModEntities() {}

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
