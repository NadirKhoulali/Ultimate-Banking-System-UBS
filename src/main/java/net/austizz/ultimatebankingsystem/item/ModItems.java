package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UltimateBankingSystem.MODID);

    public static final RegistryObject<Item> ONE_DOLLAR_BILL = ITEMS.register("one_dollar_bill", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TWO_DOLLAR_BILL = ITEMS.register("two_dollar_bill", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> FIVE_DOLLAR_BILL = ITEMS.register("five_dollar_bill", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TEN_DOLLAR_BILL = ITEMS.register("ten_dollar_bill", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TWENTY_DOLLAR_BILL = ITEMS.register("twenty_dollar_bill", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> FIFTY_DOLLAR_BILL = ITEMS.register("fifty_dollar_bill", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> HUNDRED_DOLLAR_BILL = ITEMS.register("hundred_dollar_bill", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PENNY_COIN = ITEMS.register("penny_coin", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> NICKEL_COIN = ITEMS.register("nickel_coin", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> DIME_COIN = ITEMS.register("dime_coin", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> QUARTER_COIN = ITEMS.register("quarter_coin", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> HALF_DOLLAR_COIN = ITEMS.register("half_dollar_coin", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BANK_NOTE = ITEMS.register("bank_note", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CHEQUE = ITEMS.register("cheque", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CREDIT_CARD = ITEMS.register("credit_card", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> HANDHELD_PAYMENT_TERMINAL = ITEMS.register("handheld_payment_terminal", HandheldPaymentTerminalItem::new);
    public static final RegistryObject<Item> BANK_TELLER_SPAWN_EGG = ITEMS.register("bank_teller_spawn_egg", BankTellerSpawnEggItem::new);

    public static final List<RegistryObject<Item>> USD_BILLS = List.of(
            HUNDRED_DOLLAR_BILL,
            FIFTY_DOLLAR_BILL,
            TWENTY_DOLLAR_BILL,
            TEN_DOLLAR_BILL,
            FIVE_DOLLAR_BILL,
            TWO_DOLLAR_BILL,
            ONE_DOLLAR_BILL
    );

    public static final List<RegistryObject<Item>> USD_COINS = List.of(
            HALF_DOLLAR_COIN,
            QUARTER_COIN,
            DIME_COIN,
            NICKEL_COIN,
            PENNY_COIN
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
