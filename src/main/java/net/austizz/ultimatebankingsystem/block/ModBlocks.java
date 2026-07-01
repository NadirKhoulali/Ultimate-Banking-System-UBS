package net.austizz.ultimatebankingsystem.block;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.custom.ATMBlock;
import net.austizz.ultimatebankingsystem.block.custom.BankOwnerPcBlock;
import net.austizz.ultimatebankingsystem.block.custom.BankSafeIronBarGateBlock;
import net.austizz.ultimatebankingsystem.block.custom.BankVaultDoorBlock;
import net.austizz.ultimatebankingsystem.block.custom.CardboardBoxBlock;
import net.austizz.ultimatebankingsystem.block.custom.ColorButtonBlock;
import net.austizz.ultimatebankingsystem.block.custom.GlassCounterDisplayBlock;
import net.austizz.ultimatebankingsystem.block.custom.InvisibleDisplayBlock;
import net.austizz.ultimatebankingsystem.block.custom.ModularWallDisplayBlock;
import net.austizz.ultimatebankingsystem.block.custom.PalletBlock;
import net.austizz.ultimatebankingsystem.block.custom.SafetyDepositBoxRowBlock;
import net.austizz.ultimatebankingsystem.block.custom.ShopSellingTableBlock;
import net.austizz.ultimatebankingsystem.block.custom.ShopSellingTableLargeBlock;
import net.austizz.ultimatebankingsystem.block.custom.ShoppingBasketBlock;
import net.austizz.ultimatebankingsystem.block.custom.ShoppingBagBlock;
import net.austizz.ultimatebankingsystem.block.custom.ShoppingBasketHolderBlock;
import net.austizz.ultimatebankingsystem.block.custom.ShopTerminalBlock;
import net.austizz.ultimatebankingsystem.block.custom.TallWallShelfBlock;
import net.austizz.ultimatebankingsystem.item.ColoredNameBlockItem;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.item.ShoppingBagItem;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(UltimateBankingSystem.MODID);

    public static final DeferredBlock<Block> ATM_MACHINE = registerBlock("atm_machine",
            () -> new ATMBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(4f)
                    .sound(SoundType.METAL)


            ));
    public static final DeferredBlock<Block> BANK_OWNER_PC = registerBlock("bank_owner_pc",
            () -> new BankOwnerPcBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(3.5f)
                    .sound(SoundType.METAL)
            ));
    public static final DeferredBlock<Block> BANK_VAULT_DOOR = registerBlock("bank_vault_door",
            () -> new BankVaultDoorBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(8.0f, 1200.0f)
                    .sound(SoundType.METAL)
            ));
    public static final DeferredBlock<Block> BANK_SAFE_IRON_BAR_GATE = registerBlock("bank_safe_iron_bar_gate",
            () -> new BankSafeIronBarGateBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(8.0f, 1200.0f)
                    .sound(SoundType.METAL)
            ));
    public static final DeferredBlock<Block> SAFETY_DEPOSIT_BOX_ROW = registerBlock("safety_deposit_box_row",
            () -> new SafetyDepositBoxRowBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(5.0f, 1200.0f)
                    .sound(SoundType.METAL)
            ));
    public static final DeferredBlock<Block> COLOR_BUTTON_BLOCK = registerBlock("color_button_block",
            () -> new ColorButtonBlock(BlockBehaviour.Properties.of()
                    .strength(1.5f)
                    .sound(SoundType.STONE)


            ));
    public static final DeferredBlock<Block> PAYMENT_TERMINAL = registerBlock("payment_terminal",
            () -> new ShopTerminalBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(3.0f)
                    .sound(SoundType.METAL)
            ));
    public static final DeferredBlock<Block> TALL_WALL_SHELF = registerBlock("tall_wall_shelf",
            () -> new TallWallShelfBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Shelf is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.WOOD),
                    true
            ), true);
    public static final DeferredBlock<Block> SHOP_SHELF = registerBlock("shop_shelf",
            () -> new TallWallShelfBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Shelf is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.WOOD),
                    false
            ), false);
    public static final DeferredBlock<Block> SHOPPING_BASKET = registerBlock("shopping_basket",
            () -> new ShoppingBasketBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(1.0f)
                    .sound(SoundType.WOOD)
            ));
    public static final DeferredBlock<Block> SHOPPING_BAG = registerBlock("shopping_bag",
            () -> new ShoppingBagBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(1.0f)
                    .sound(SoundType.WOOL)
            ));
    public static final DeferredBlock<Block> SHOPPING_BASKET_HOLDER = registerBlock("shopping_basket_holder",
            () -> new ShoppingBasketHolderBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(2.0f)
                    .sound(SoundType.METAL)
            ));
    public static final DeferredBlock<Block> SHOP_SELLING_TABLE = registerBlock("shop_selling_table",
            () -> new ShopSellingTableBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Table is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.WOOD)
            ));
    public static final DeferredBlock<Block> CREATIVE_SHOP_SELLING_TABLE = registerBlock("creative_shop_selling_table",
            () -> new ShopSellingTableBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Table is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.WOOD)
            ), true);
    public static final DeferredBlock<Block> SHOP_SELLING_TABLE_LARGE = registerBlock("shop_selling_table_large",
            () -> new ShopSellingTableLargeBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Table is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.WOOD)
            ));
    public static final DeferredBlock<Block> CREATIVE_SHOP_SELLING_TABLE_LARGE = registerBlock("creative_shop_selling_table_large",
            () -> new ShopSellingTableLargeBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Table is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.WOOD)
            ), true);
    public static final DeferredBlock<Block> INVISIBLE_DISPLAY_SMALL = registerBlock("invisible_display_small",
            () -> new InvisibleDisplayBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Display is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.GLASS),
                    InvisibleDisplayBlock.SizePreset.SMALL
            ));
    public static final DeferredBlock<Block> INVISIBLE_DISPLAY_MEDIUM = registerBlock("invisible_display_medium",
            () -> new InvisibleDisplayBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Display is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.GLASS),
                    InvisibleDisplayBlock.SizePreset.MEDIUM
            ));
    public static final DeferredBlock<Block> INVISIBLE_DISPLAY_LARGE = registerBlock("invisible_display_large",
            () -> new InvisibleDisplayBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Display is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.GLASS),
                    InvisibleDisplayBlock.SizePreset.LARGE
            ));
    public static final DeferredBlock<Block> CREATIVE_INVISIBLE_DISPLAY_SMALL = registerBlock("creative_invisible_display_small",
            () -> new InvisibleDisplayBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Display is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.GLASS),
                    InvisibleDisplayBlock.SizePreset.SMALL
            ), true);
    public static final DeferredBlock<Block> CREATIVE_INVISIBLE_DISPLAY_MEDIUM = registerBlock("creative_invisible_display_medium",
            () -> new InvisibleDisplayBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Display is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.GLASS),
                    InvisibleDisplayBlock.SizePreset.MEDIUM
            ), true);
    public static final DeferredBlock<Block> CREATIVE_INVISIBLE_DISPLAY_LARGE = registerBlock("creative_invisible_display_large",
            () -> new InvisibleDisplayBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Display is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.GLASS),
                    InvisibleDisplayBlock.SizePreset.LARGE
            ), true);
    public static final DeferredBlock<Block> MODULAR_WALL_DISPLAY = registerBlock("modular_wall_display",
            () -> new ModularWallDisplayBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Display is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.GLASS)
            ));
    public static final DeferredBlock<Block> CREATIVE_MODULAR_WALL_DISPLAY = registerBlock("creative_modular_wall_display",
            () -> new ModularWallDisplayBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Display is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.GLASS)
            ), true);
    public static final DeferredBlock<Block> GLASS_COUNTER_DISPLAY = registerBlock("glass_counter_display",
            () -> new GlassCounterDisplayBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Display is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.GLASS)
            ));
    public static final DeferredBlock<Block> CREATIVE_GLASS_COUNTER_DISPLAY = registerBlock("creative_glass_counter_display",
            () -> new GlassCounterDisplayBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Display is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.GLASS)
            ), true);
    public static final DeferredBlock<Block> GLASS_COUNTER_DISPLAY_OPEN = registerBlock("glass_counter_display_open",
            () -> new GlassCounterDisplayBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Display is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.GLASS)
            ));
    public static final DeferredBlock<Block> CREATIVE_GLASS_COUNTER_DISPLAY_OPEN = registerBlock("creative_glass_counter_display_open",
            () -> new GlassCounterDisplayBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            // Display is intentionally non-minable; removal is via Shelf UI action only.
                            .strength(-1.0f, 3600000.0f)
                            .sound(SoundType.GLASS)
            ), true);
    public static final DeferredBlock<Block> CARDBOARD_BOX = registerBlock("cardboard_box",
            () -> new CardboardBoxBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            .strength(1.6f)
                            .sound(SoundType.WOOD)
            ));
    public static final DeferredBlock<Block> PALLET = registerBlock("pallet",
            () -> new PalletBlock(
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            .strength(2.0f)
                            .sound(SoundType.WOOD)
            ));


    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        return registerBlock(name, block, false);
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name,
                                                                     Supplier<T> block,
                                                                     boolean purpleName) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn, purpleName);
        return toReturn;
    }

    private static  <T extends Block> void registerBlockItem(String name,
                                                             DeferredBlock<T> block,
                                                             boolean purpleName) {
        if ("shopping_bag".equals(name)) {
            // Generic/shop-ordered shopping bags should stack; cashier-packed bags remain non-stackable due unique NBT contents.
            ModItems.ITEMS.register(name, () -> new ShoppingBagItem(block.get(), new Item.Properties().stacksTo(64)));
            return;
        }
        ModItems.ITEMS.register(name, () -> purpleName
                ? new ColoredNameBlockItem(block.get(), new Item.Properties(), ChatFormatting.LIGHT_PURPLE)
                : new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register (IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
