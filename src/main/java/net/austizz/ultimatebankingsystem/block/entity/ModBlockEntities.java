package net.austizz.ultimatebankingsystem.block.entity;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.BankSafeIronBarGateBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.BankVaultDoorBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.GlassCounterDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ModularWallDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.PalletBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.CardboardBoxBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShopTerminalBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShopSellingTableBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShoppingBasketBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShoppingBagBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShoppingBasketHolderBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.TallWallShelfBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, UltimateBankingSystem.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShopTerminalBlockEntity>> PAYMENT_TERMINAL =
            BLOCK_ENTITY_TYPES.register("payment_terminal", () ->
                    BlockEntityType.Builder.of(
                            ShopTerminalBlockEntity::new,
                            ModBlocks.PAYMENT_TERMINAL.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BankVaultDoorBlockEntity>> BANK_VAULT_DOOR =
            BLOCK_ENTITY_TYPES.register("bank_vault_door", () ->
                    BlockEntityType.Builder.of(
                            BankVaultDoorBlockEntity::new,
                            ModBlocks.BANK_VAULT_DOOR.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BankSafeIronBarGateBlockEntity>> BANK_SAFE_IRON_BAR_GATE =
            BLOCK_ENTITY_TYPES.register("bank_safe_iron_bar_gate", () ->
                    BlockEntityType.Builder.of(
                            BankSafeIronBarGateBlockEntity::new,
                            ModBlocks.BANK_SAFE_IRON_BAR_GATE.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SafetyDepositBoxRowBlockEntity>> SAFETY_DEPOSIT_BOX_ROW =
            BLOCK_ENTITY_TYPES.register("safety_deposit_box_row", () ->
                    BlockEntityType.Builder.of(
                            SafetyDepositBoxRowBlockEntity::new,
                            ModBlocks.SAFETY_DEPOSIT_BOX_ROW.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TallWallShelfBlockEntity>> TALL_WALL_SHELF =
            BLOCK_ENTITY_TYPES.register("tall_wall_shelf", () ->
                    BlockEntityType.Builder.of(
                            TallWallShelfBlockEntity::new,
                            ModBlocks.TALL_WALL_SHELF.get(),
                            ModBlocks.SHOP_SHELF.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShoppingBasketBlockEntity>> SHOPPING_BASKET =
            BLOCK_ENTITY_TYPES.register("shopping_basket", () ->
                    BlockEntityType.Builder.of(
                            ShoppingBasketBlockEntity::new,
                            ModBlocks.SHOPPING_BASKET.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShoppingBagBlockEntity>> SHOPPING_BAG =
            BLOCK_ENTITY_TYPES.register("shopping_bag", () ->
                    BlockEntityType.Builder.of(
                            ShoppingBagBlockEntity::new,
                            ModBlocks.SHOPPING_BAG.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShoppingBasketHolderBlockEntity>> SHOPPING_BASKET_HOLDER =
            BLOCK_ENTITY_TYPES.register("shopping_basket_holder", () ->
                    BlockEntityType.Builder.of(
                            ShoppingBasketHolderBlockEntity::new,
                            ModBlocks.SHOPPING_BASKET_HOLDER.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShopSellingTableBlockEntity>> SHOP_SELLING_TABLE =
            BLOCK_ENTITY_TYPES.register("shop_selling_table", () ->
                    BlockEntityType.Builder.of(
                            ShopSellingTableBlockEntity::new,
                            ModBlocks.SHOP_SELLING_TABLE.get(),
                            ModBlocks.CREATIVE_SHOP_SELLING_TABLE.get(),
                            ModBlocks.SHOP_SELLING_TABLE_LARGE.get(),
                            ModBlocks.CREATIVE_SHOP_SELLING_TABLE_LARGE.get(),
                            ModBlocks.INVISIBLE_DISPLAY_SMALL.get(),
                            ModBlocks.INVISIBLE_DISPLAY_MEDIUM.get(),
                            ModBlocks.INVISIBLE_DISPLAY_LARGE.get(),
                            ModBlocks.CREATIVE_INVISIBLE_DISPLAY_SMALL.get(),
                            ModBlocks.CREATIVE_INVISIBLE_DISPLAY_MEDIUM.get(),
                            ModBlocks.CREATIVE_INVISIBLE_DISPLAY_LARGE.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ModularWallDisplayBlockEntity>> MODULAR_WALL_DISPLAY =
            BLOCK_ENTITY_TYPES.register("modular_wall_display", () ->
                    BlockEntityType.Builder.of(
                            ModularWallDisplayBlockEntity::new,
                            ModBlocks.MODULAR_WALL_DISPLAY.get(),
                            ModBlocks.CREATIVE_MODULAR_WALL_DISPLAY.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GlassCounterDisplayBlockEntity>> GLASS_COUNTER_DISPLAY =
            BLOCK_ENTITY_TYPES.register("glass_counter_display", () ->
                    BlockEntityType.Builder.of(
                            GlassCounterDisplayBlockEntity::new,
                            ModBlocks.GLASS_COUNTER_DISPLAY.get(),
                            ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY.get(),
                            ModBlocks.GLASS_COUNTER_DISPLAY_OPEN.get(),
                            ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY_OPEN.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CardboardBoxBlockEntity>> CARDBOARD_BOX =
            BLOCK_ENTITY_TYPES.register("cardboard_box", () ->
                    BlockEntityType.Builder.of(
                            CardboardBoxBlockEntity::new,
                            ModBlocks.CARDBOARD_BOX.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PalletBlockEntity>> PALLET =
            BLOCK_ENTITY_TYPES.register("pallet", () ->
                    BlockEntityType.Builder.of(
                            PalletBlockEntity::new,
                            ModBlocks.PALLET.get()
                    ).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
