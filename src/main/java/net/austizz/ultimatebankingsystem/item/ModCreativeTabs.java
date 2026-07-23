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

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BANKING_TAB = CREATIVE_TABS.register("banking", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ultimatebankingsystem.banking"))
                    .icon(() -> new ItemStack(ModBlocks.ATM_MACHINE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.ATM_MACHINE.get());
                        output.accept(ModBlocks.BANK_OWNER_PC.get());
                        output.accept(ModItems.BANK_TELLER_SPAWN_EGG.get());
                        output.accept(ModItems.BANK_NOTE.get());
                        output.accept(ModItems.CHEQUE.get());
                        output.accept(ModItems.CREDIT_CARD.get());
                        output.accept(ModItems.WALLET.get());
                        output.accept(ModItems.SMARTPHONE.get());
                    })
                    .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CURRENCY_TAB = CREATIVE_TABS.register("currency", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ultimatebankingsystem.currency"))
                    .icon(() -> new ItemStack(ModItems.HUNDRED_DOLLAR_MONEY_STACK.get()))
                    .displayItems((parameters, output) -> {
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
                        output.accept(ModItems.ONE_DOLLAR_STRAP.get());
                        output.accept(ModItems.TWO_DOLLAR_STRAP.get());
                        output.accept(ModItems.FIVE_DOLLAR_STRAP.get());
                        output.accept(ModItems.TEN_DOLLAR_STRAP.get());
                        output.accept(ModItems.TWENTY_DOLLAR_STRAP.get());
                        output.accept(ModItems.FIFTY_DOLLAR_STRAP.get());
                        output.accept(ModItems.HUNDRED_DOLLAR_STRAP.get());
                        output.accept(ModItems.ONE_DOLLAR_MONEY_STACK.get());
                        output.accept(ModItems.TWO_DOLLAR_MONEY_STACK.get());
                        output.accept(ModItems.FIVE_DOLLAR_MONEY_STACK.get());
                        output.accept(ModItems.TEN_DOLLAR_MONEY_STACK.get());
                        output.accept(ModItems.TWENTY_DOLLAR_MONEY_STACK.get());
                        output.accept(ModItems.FIFTY_DOLLAR_MONEY_STACK.get());
                        output.accept(ModItems.HUNDRED_DOLLAR_MONEY_STACK.get());
                        output.accept(ModBlocks.GOLD_BAR.get());
                        output.accept(ModBlocks.SILVER_BAR.get());
                        output.accept(ModBlocks.METAL_PALLET.get());
                        output.accept(ModBlocks.MONEY_BRIEFCASE.get());
                    })
                    .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SHOP_MANAGEMENT_TAB = CREATIVE_TABS.register("shop_management", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ultimatebankingsystem.shop_management"))
                    .icon(() -> new ItemStack(ModBlocks.PAYMENT_TERMINAL.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.PAYMENT_TERMINAL.get());
                        output.accept(ModItems.HANDHELD_PAYMENT_TERMINAL.get());
                        output.accept(ModItems.CASHIER_SPAWN_EGG.get());
                        output.accept(ModBlocks.COLOR_BUTTON_BLOCK.get());
                        output.accept(ModBlocks.TALL_WALL_SHELF.get());
                        output.accept(ModBlocks.SHOP_SHELF.get());
                        output.accept(ModBlocks.SHOP_SELLING_TABLE.get());
                        output.accept(ModBlocks.SHOP_SELLING_TABLE_LARGE.get());
                        output.accept(ModBlocks.INVISIBLE_DISPLAY_SMALL.get());
                        output.accept(ModBlocks.INVISIBLE_DISPLAY_MEDIUM.get());
                        output.accept(ModBlocks.INVISIBLE_DISPLAY_LARGE.get());
                        output.accept(ModBlocks.MODULAR_WALL_DISPLAY.get());
                        output.accept(ModBlocks.GLASS_COUNTER_DISPLAY.get());
                        output.accept(ModBlocks.GLASS_COUNTER_DISPLAY_OPEN.get());
                        output.accept(ModBlocks.CREATIVE_SHOP_SELLING_TABLE.get());
                        output.accept(ModBlocks.CREATIVE_SHOP_SELLING_TABLE_LARGE.get());
                        output.accept(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_SMALL.get());
                        output.accept(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_MEDIUM.get());
                        output.accept(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_LARGE.get());
                        output.accept(ModBlocks.CREATIVE_MODULAR_WALL_DISPLAY.get());
                        output.accept(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY.get());
                        output.accept(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY_OPEN.get());
                        output.accept(ModBlocks.SHOPPING_BASKET.get());
                        output.accept(ModBlocks.SHOPPING_BAG.get());
                        output.accept(ModBlocks.SHOPPING_BASKET_HOLDER.get());
                        output.accept(ModBlocks.CARDBOARD_BOX.get());
                        output.accept(ModBlocks.PALLET.get());
                    })
                    .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SECURITY_TAB = CREATIVE_TABS.register("security", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ultimatebankingsystem.security"))
                    .icon(() -> new ItemStack(ModBlocks.STANDING_SAFE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.BANK_VAULT_DOOR.get());
                        output.accept(ModBlocks.BANK_SAFE_IRON_BAR_GATE.get());
                        output.accept(ModBlocks.SAFETY_DEPOSIT_BOX_ROW.get());
                        output.accept(ModItems.SAFETY_DEPOSIT_BOX_SMALL.get());
                        output.accept(ModItems.SAFETY_DEPOSIT_BOX_MEDIUM.get());
                        output.accept(ModItems.SAFETY_DEPOSIT_BOX_LARGE.get());
                        output.accept(ModItems.SAFETY_DEPOSIT_BOX_EXTRA_LARGE.get());
                        output.accept(ModItems.SAFETY_DEPOSIT_BOX_COVER.get());
                        output.accept(ModBlocks.STANDING_SAFE.get());
                        output.accept(ModBlocks.COMPACT_SAFE.get());
                        output.accept(ModItems.SAFE_CHEST_UPGRADE.get());
                        output.accept(ModBlocks.ACCESS_VERIFIER.get());
                        output.accept(ModBlocks.RFID_SCANNER.get());
                        output.accept(ModItems.RFID_CARD.get());
                    })
                    .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> HEIST_TAB = CREATIVE_TABS.register("heist", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ultimatebankingsystem.heist"))
                    .icon(() -> new ItemStack(ModBlocks.HEIST_DRILL.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.HEIST_DRILL.get());
                        output.accept(ModBlocks.THERMAL_DRILL.get());
                        output.accept(ModItems.DALLAS_MASK.get());
                        output.accept(ModItems.LOCKPICKING_TOOL.get());
                        output.accept(ModItems.RFID_SPOOFER.get());
                        output.accept(ModItems.OVE9000_SAW.get());
                        output.accept(ModItems.OVE9000_SAW_BLADE.get());
                        output.accept(ModBlocks.HEIST_DUFFEL.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {}

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}
