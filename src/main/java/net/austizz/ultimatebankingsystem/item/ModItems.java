package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UltimateBankingSystem.MODID);

    public static final DeferredItem<Item> ONE_DOLLAR_BILL = ITEMS.register("one_dollar_bill", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> TWO_DOLLAR_BILL = ITEMS.register("two_dollar_bill", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> FIVE_DOLLAR_BILL = ITEMS.register("five_dollar_bill", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> TEN_DOLLAR_BILL = ITEMS.register("ten_dollar_bill", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> TWENTY_DOLLAR_BILL = ITEMS.register("twenty_dollar_bill", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> FIFTY_DOLLAR_BILL = ITEMS.register("fifty_dollar_bill", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> HUNDRED_DOLLAR_BILL = ITEMS.register("hundred_dollar_bill", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> BANK_NOTE = ITEMS.register("bank_note", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CHEQUE = ITEMS.register("cheque", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> BANK_TELLER_SPAWN_EGG = ITEMS.register("bank_teller_spawn_egg", BankTellerSpawnEggItem::new);

    public static final List<DeferredItem<Item>> USD_BILLS = List.of(
            HUNDRED_DOLLAR_BILL,
            FIFTY_DOLLAR_BILL,
            TWENTY_DOLLAR_BILL,
            TEN_DOLLAR_BILL,
            FIVE_DOLLAR_BILL,
            TWO_DOLLAR_BILL,
            ONE_DOLLAR_BILL
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
