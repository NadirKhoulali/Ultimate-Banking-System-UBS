package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.custom.CashStackBlock;
import net.austizz.ultimatebankingsystem.block.custom.MoneyStackBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.List;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UltimateBankingSystem.MODID);

    public static final DeferredItem<Item> ONE_DOLLAR_BILL = ITEMS.register("one_dollar_bill",
            () -> new CashTenderItem(CashStackBlock.CashKind.ONE_DOLLAR_BILL, new Item.Properties()));
    public static final DeferredItem<Item> TWO_DOLLAR_BILL = ITEMS.register("two_dollar_bill",
            () -> new CashTenderItem(CashStackBlock.CashKind.TWO_DOLLAR_BILL, new Item.Properties()));
    public static final DeferredItem<Item> FIVE_DOLLAR_BILL = ITEMS.register("five_dollar_bill",
            () -> new CashTenderItem(CashStackBlock.CashKind.FIVE_DOLLAR_BILL, new Item.Properties()));
    public static final DeferredItem<Item> TEN_DOLLAR_BILL = ITEMS.register("ten_dollar_bill",
            () -> new CashTenderItem(CashStackBlock.CashKind.TEN_DOLLAR_BILL, new Item.Properties()));
    public static final DeferredItem<Item> TWENTY_DOLLAR_BILL = ITEMS.register("twenty_dollar_bill",
            () -> new CashTenderItem(CashStackBlock.CashKind.TWENTY_DOLLAR_BILL, new Item.Properties()));
    public static final DeferredItem<Item> FIFTY_DOLLAR_BILL = ITEMS.register("fifty_dollar_bill",
            () -> new CashTenderItem(CashStackBlock.CashKind.FIFTY_DOLLAR_BILL, new Item.Properties()));
    public static final DeferredItem<Item> HUNDRED_DOLLAR_BILL = ITEMS.register("hundred_dollar_bill",
            () -> new CashTenderItem(CashStackBlock.CashKind.HUNDRED_DOLLAR_BILL, new Item.Properties()));
    public static final DeferredItem<Item> ONE_DOLLAR_STRAP = ITEMS.register("one_dollar_strap",
            () -> new MoneyStrapItem(MoneyStackBlock.BillDenomination.ONE, new Item.Properties()));
    public static final DeferredItem<Item> TWO_DOLLAR_STRAP = ITEMS.register("two_dollar_strap",
            () -> new MoneyStrapItem(MoneyStackBlock.BillDenomination.TWO, new Item.Properties()));
    public static final DeferredItem<Item> FIVE_DOLLAR_STRAP = ITEMS.register("five_dollar_strap",
            () -> new MoneyStrapItem(MoneyStackBlock.BillDenomination.FIVE, new Item.Properties()));
    public static final DeferredItem<Item> TEN_DOLLAR_STRAP = ITEMS.register("ten_dollar_strap",
            () -> new MoneyStrapItem(MoneyStackBlock.BillDenomination.TEN, new Item.Properties()));
    public static final DeferredItem<Item> TWENTY_DOLLAR_STRAP = ITEMS.register("twenty_dollar_strap",
            () -> new MoneyStrapItem(MoneyStackBlock.BillDenomination.TWENTY, new Item.Properties()));
    public static final DeferredItem<Item> FIFTY_DOLLAR_STRAP = ITEMS.register("fifty_dollar_strap",
            () -> new MoneyStrapItem(MoneyStackBlock.BillDenomination.FIFTY, new Item.Properties()));
    public static final DeferredItem<Item> HUNDRED_DOLLAR_STRAP = ITEMS.register("hundred_dollar_strap",
            () -> new MoneyStrapItem(MoneyStackBlock.BillDenomination.HUNDRED, new Item.Properties()));
    public static final DeferredItem<Item> ONE_DOLLAR_MONEY_STACK = ITEMS.register("one_dollar_money_stack",
            () -> new MoneyStackItem(MoneyStackBlock.BillDenomination.ONE, new Item.Properties()));
    public static final DeferredItem<Item> TWO_DOLLAR_MONEY_STACK = ITEMS.register("two_dollar_money_stack",
            () -> new MoneyStackItem(MoneyStackBlock.BillDenomination.TWO, new Item.Properties()));
    public static final DeferredItem<Item> FIVE_DOLLAR_MONEY_STACK = ITEMS.register("five_dollar_money_stack",
            () -> new MoneyStackItem(MoneyStackBlock.BillDenomination.FIVE, new Item.Properties()));
    public static final DeferredItem<Item> TEN_DOLLAR_MONEY_STACK = ITEMS.register("ten_dollar_money_stack",
            () -> new MoneyStackItem(MoneyStackBlock.BillDenomination.TEN, new Item.Properties()));
    public static final DeferredItem<Item> TWENTY_DOLLAR_MONEY_STACK = ITEMS.register("twenty_dollar_money_stack",
            () -> new MoneyStackItem(MoneyStackBlock.BillDenomination.TWENTY, new Item.Properties()));
    public static final DeferredItem<Item> FIFTY_DOLLAR_MONEY_STACK = ITEMS.register("fifty_dollar_money_stack",
            () -> new MoneyStackItem(MoneyStackBlock.BillDenomination.FIFTY, new Item.Properties()));
    public static final DeferredItem<Item> HUNDRED_DOLLAR_MONEY_STACK = ITEMS.register("hundred_dollar_money_stack",
            () -> new MoneyStackItem(MoneyStackBlock.BillDenomination.HUNDRED, new Item.Properties()));
    public static final DeferredItem<Item> PENNY_COIN = ITEMS.register("penny_coin",
            () -> new CashTenderItem(CashStackBlock.CashKind.PENNY_COIN, new Item.Properties()));
    public static final DeferredItem<Item> NICKEL_COIN = ITEMS.register("nickel_coin",
            () -> new CashTenderItem(CashStackBlock.CashKind.NICKEL_COIN, new Item.Properties()));
    public static final DeferredItem<Item> DIME_COIN = ITEMS.register("dime_coin",
            () -> new CashTenderItem(CashStackBlock.CashKind.DIME_COIN, new Item.Properties()));
    public static final DeferredItem<Item> QUARTER_COIN = ITEMS.register("quarter_coin",
            () -> new CashTenderItem(CashStackBlock.CashKind.QUARTER_COIN, new Item.Properties()));
    public static final DeferredItem<Item> HALF_DOLLAR_COIN = ITEMS.register("half_dollar_coin",
            () -> new CashTenderItem(CashStackBlock.CashKind.HALF_DOLLAR_COIN, new Item.Properties()));
    public static final DeferredItem<Item> BANK_NOTE = ITEMS.register("bank_note", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CHEQUE = ITEMS.register("cheque", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CREDIT_CARD = ITEMS.register("credit_card", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> RFID_CARD = ITEMS.register("rfid_card",
            () -> new RfidCardItem(new Item.Properties()));
    public static final DeferredItem<Item> RFID_SPOOFER = ITEMS.register("rfid_spoofer",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> WALLET = ITEMS.register("wallet", WalletItem::new);
    public static final DeferredItem<Item> SMARTPHONE = ITEMS.register("smartphone", SmartphoneItem::new);
    public static final DeferredItem<Item> DALLAS_MASK = ITEMS.register("dallas_mask", DallasMaskItem::new);
    public static final DeferredItem<Item> LOCKPICKING_TOOL = ITEMS.register("lockpicking_tool",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> OVE9000_SAW = ITEMS.register("ove9000_saw", Ove9000SawItem::new);
    public static final DeferredItem<Item> OVE9000_SAW_BLADE = ITEMS.register("ove9000_saw_blade",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> SAFE_CHEST_UPGRADE = ITEMS.register("safe_chest_upgrade",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> SAFETY_DEPOSIT_BOX_SMALL = ITEMS.register("safety_deposit_box_small",
            () -> new SafetyDepositBoxInsertItem(SafetyDepositBoxRowBlockEntity.ModuleType.SMALL));
    public static final DeferredItem<Item> SAFETY_DEPOSIT_BOX_MEDIUM = ITEMS.register("safety_deposit_box_medium",
            () -> new SafetyDepositBoxInsertItem(SafetyDepositBoxRowBlockEntity.ModuleType.MEDIUM));
    public static final DeferredItem<Item> SAFETY_DEPOSIT_BOX_LARGE = ITEMS.register("safety_deposit_box_large",
            () -> new SafetyDepositBoxInsertItem(SafetyDepositBoxRowBlockEntity.ModuleType.LARGE));
    public static final DeferredItem<Item> SAFETY_DEPOSIT_BOX_EXTRA_LARGE = ITEMS.register("safety_deposit_box_extra_large",
            () -> new SafetyDepositBoxInsertItem(SafetyDepositBoxRowBlockEntity.ModuleType.EXTRA_LARGE));
    public static final DeferredItem<Item> SAFETY_DEPOSIT_BOX_COVER = ITEMS.register("safety_deposit_box_cover",
            () -> new SafetyDepositBoxInsertItem(SafetyDepositBoxRowBlockEntity.ModuleType.COVER));
    public static final DeferredItem<Item> HANDHELD_PAYMENT_TERMINAL = ITEMS.register("handheld_payment_terminal", HandheldPaymentTerminalItem::new);
    public static final DeferredItem<Item> BANK_TELLER_SPAWN_EGG = ITEMS.register("bank_teller_spawn_egg", BankTellerSpawnEggItem::new);
    public static final DeferredItem<Item> CASHIER_SPAWN_EGG = ITEMS.register("cashier_spawn_egg", CashierSpawnEggItem::new);

    public static final List<DeferredItem<Item>> USD_BILLS = List.of(
            HUNDRED_DOLLAR_BILL,
            FIFTY_DOLLAR_BILL,
            TWENTY_DOLLAR_BILL,
            TEN_DOLLAR_BILL,
            FIVE_DOLLAR_BILL,
            TWO_DOLLAR_BILL,
            ONE_DOLLAR_BILL
    );

    public static final List<DeferredItem<Item>> USD_STRAPS = List.of(
            HUNDRED_DOLLAR_STRAP,
            FIFTY_DOLLAR_STRAP,
            TWENTY_DOLLAR_STRAP,
            TEN_DOLLAR_STRAP,
            FIVE_DOLLAR_STRAP,
            TWO_DOLLAR_STRAP,
            ONE_DOLLAR_STRAP
    );

    public static final List<DeferredItem<Item>> USD_MONEY_STACKS = List.of(
            HUNDRED_DOLLAR_MONEY_STACK,
            FIFTY_DOLLAR_MONEY_STACK,
            TWENTY_DOLLAR_MONEY_STACK,
            TEN_DOLLAR_MONEY_STACK,
            FIVE_DOLLAR_MONEY_STACK,
            TWO_DOLLAR_MONEY_STACK,
            ONE_DOLLAR_MONEY_STACK
    );

    public static final List<DeferredItem<Item>> USD_COINS = List.of(
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
