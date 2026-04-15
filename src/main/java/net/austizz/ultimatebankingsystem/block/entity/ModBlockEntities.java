package net.austizz.ultimatebankingsystem.block.entity;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShopTerminalBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, UltimateBankingSystem.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShopTerminalBlockEntity>> PAYMENT_TERMINAL =
            BLOCK_ENTITY_TYPES.register("payment_terminal", () ->
                    BlockEntityType.Builder.of(
                            ShopTerminalBlockEntity::new,
                            ModBlocks.PAYMENT_TERMINAL.get()
                    ).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
