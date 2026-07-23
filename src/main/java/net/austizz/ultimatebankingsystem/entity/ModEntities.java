package net.austizz.ultimatebankingsystem.entity;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.entity.custom.SafetyDepositBoxDisplayProxyEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, UltimateBankingSystem.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<BankTellerEntity>> BANK_TELLER =
            ENTITY_TYPES.register("bank_teller", () ->
                    EntityType.Builder.of(BankTellerEntity::new, MobCategory.MISC)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build(UltimateBankingSystem.MODID + ":bank_teller"));

    public static final DeferredHolder<EntityType<?>, EntityType<SafetyDepositBoxDisplayProxyEntity>>
            SAFETY_DEPOSIT_BOX_DISPLAY_PROXY = ENTITY_TYPES.register("safety_deposit_box_display_proxy", () ->
                    EntityType.Builder.<SafetyDepositBoxDisplayProxyEntity>of(
                                    SafetyDepositBoxDisplayProxyEntity::new, MobCategory.MISC)
                            .sized(0.95F, 1.25F)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build(UltimateBankingSystem.MODID + ":safety_deposit_box_display_proxy"));

    private ModEntities() {}

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
