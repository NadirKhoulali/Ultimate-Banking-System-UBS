package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, UltimateBankingSystem.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> UBS_TAB = CREATIVE_TABS.register("ubs", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ultimatebankingsystem.ubs"))
                    .icon(() -> new ItemStack(ModBlocks.ATM_MACHINE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.ATM_MACHINE.get());
                        output.accept(ModBlocks.BANK_OWNER_PC.get());
                        output.accept(ModBlocks.COLOR_BUTTON_BLOCK.get());
                        output.accept(ModBlocks.PAYMENT_TERMINAL.get());
                        output.accept(ModBlocks.TALL_WALL_SHELF.get());
                        output.accept(ModBlocks.SHOP_SHELF.get());
                        output.accept(ModBlocks.CREATIVE_SHOP_SELLING_TABLE.get());
                        output.accept(ModBlocks.CREATIVE_SHOP_SELLING_TABLE_LARGE.get());
                        output.accept(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_SMALL.get());
                        output.accept(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_MEDIUM.get());
                        output.accept(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_LARGE.get());
                        output.accept(ModBlocks.CREATIVE_MODULAR_WALL_DISPLAY.get());
                        output.accept(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY.get());
                        output.accept(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY_OPEN.get());
                        output.accept(ModBlocks.SHOP_SELLING_TABLE.get());
                        output.accept(ModBlocks.SHOP_SELLING_TABLE_LARGE.get());
                        output.accept(ModBlocks.INVISIBLE_DISPLAY_SMALL.get());
                        output.accept(ModBlocks.INVISIBLE_DISPLAY_MEDIUM.get());
                        output.accept(ModBlocks.INVISIBLE_DISPLAY_LARGE.get());
                        output.accept(ModBlocks.MODULAR_WALL_DISPLAY.get());
                        output.accept(ModBlocks.GLASS_COUNTER_DISPLAY.get());
                        output.accept(ModBlocks.GLASS_COUNTER_DISPLAY_OPEN.get());
                        output.accept(ModBlocks.SHOPPING_BASKET.get());
                        output.accept(ModBlocks.SHOPPING_BAG.get());
                        output.accept(ModBlocks.SHOPPING_BASKET_HOLDER.get());
                        output.accept(ModBlocks.CARDBOARD_BOX.get());
                        output.accept(ModBlocks.PALLET.get());
                        output.accept(ModItems.HANDHELD_PAYMENT_TERMINAL.get());
                        output.accept(ModItems.ONE_DOLLAR_BILL.get());
                        output.accept(ModItems.TWO_DOLLAR_BILL.get());
                        output.accept(ModItems.FIVE_DOLLAR_BILL.get());
                        output.accept(ModItems.TEN_DOLLAR_BILL.get());
                        output.accept(ModItems.TWENTY_DOLLAR_BILL.get());
                        output.accept(ModItems.FIFTY_DOLLAR_BILL.get());
                        output.accept(ModItems.HUNDRED_DOLLAR_BILL.get());
                        output.accept(ModItems.HALF_DOLLAR_COIN.get());
                        output.accept(ModItems.QUARTER_COIN.get());
                        output.accept(ModItems.DIME_COIN.get());
                        output.accept(ModItems.NICKEL_COIN.get());
                        output.accept(ModItems.PENNY_COIN.get());
                        output.accept(ModItems.BANK_NOTE.get());
                        output.accept(ModItems.CHEQUE.get());
                        output.accept(ModItems.CREDIT_CARD.get());
                        output.accept(ModItems.WALLET.get());
                        output.accept(ModItems.SMARTPHONE.get());
                        output.accept(ModItems.BANK_TELLER_SPAWN_EGG.get());
                        output.accept(ModItems.CASHIER_SPAWN_EGG.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {}

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}
