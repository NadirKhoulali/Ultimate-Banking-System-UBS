package net.austizz.ultimatebankingsystem.migration.numismatics;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Converts all Numismatics assets in one bounded inventory atomically. */
public final class NumismaticsInventoryMigrator {
    private static final ResourceLocation CARD_ACCOUNT_COMPONENT = ResourceLocation.fromNamespaceAndPath(
            "numismatics", "card_account_id");

    private NumismaticsInventoryMigrator() {
    }

    public static Result migrate(Slots slots, Context context, String locationKey) {
        if (slots == null || context == null || slots.size() <= 0) return Result.empty();
        MutableResult result = new MutableResult();
        LinkedHashSet<Integer> replaceable = new LinkedHashSet<>();
        List<ItemStack> cardOutputs = new ArrayList<>();
        long coinCents = 0L;

        for (int slot = 0; slot < slots.size(); slot++) {
            ItemStack stack = slots.get(slot);
            if (stack == null || stack.isEmpty()) continue;
            migrateNested(stack, context, locationKey + "/nested-" + slot, result);
            String itemId = itemId(stack);
            NumismaticsCoin coin = NumismaticsCoin.fromItemId(itemId);
            if (coin != null) {
                long value = coin.valueCents(context.centsPerSpur(), stack.getCount());
                coinCents = Math.addExact(coinCents, value);
                result.coinItems += stack.getCount();
                result.convertedCents = Math.addExact(result.convertedCents, value);
                replaceable.add(slot);
                continue;
            }
            if (context.convertCards() && NumismaticsItemIds.isBankCard(itemId)) {
                UUID sourceAccountId = cardAccountId(stack);
                ItemStack replacement;
                if (sourceAccountId == null) {
                    replacement = new ItemStack(ModItems.CREDIT_CARD.get());
                    result.blankCards++;
                } else {
                    UUID targetAccountId = context.accountMappings().get(sourceAccountId);
                    AccountHolder target = targetAccountId == null ? null
                            : context.centralBank().SearchForAccountByAccountId(targetAccountId);
                    if (target == null) {
                        result.unresolved.add("Bound bank card references unmapped account " + sourceAccountId
                                + " at " + locationKey + " slot " + slot + ".");
                        continue;
                    }
                    CreditCardService.CardIssueResult issued = CreditCardService.issueCard(
                            context.centralBank(), target, context.holderName(), false);
                    if (!issued.success()) {
                        result.unresolved.add("Could not issue UBS card for " + sourceAccountId + ": " + issued.message());
                        continue;
                    }
                    replacement = issued.cardStack();
                    result.boundCards++;
                }
                cardOutputs.add(replacement);
                replaceable.add(slot);
            } else if (NumismaticsItemIds.isIdCard(itemId)) {
                result.idCards += stack.getCount();
            }
        }

        if (replaceable.isEmpty()) return result.freeze();
        List<ItemStack> outputs = new ArrayList<>(cardOutputs);
        if (coinCents > 0L) {
            List<ItemStack> cash = cashStacks(coinCents);
            int available = replaceable.size();
            for (int slot = 0; slot < slots.size(); slot++) {
                if (!replaceable.contains(slot) && slots.get(slot).isEmpty()) available++;
            }
            if (outputs.size() + cash.size() <= available) {
                outputs.addAll(cash);
            } else {
                outputs.add(MigrationBankNoteFactory.create(coinCents, context.migrationId(), locationKey + "/cash"));
                result.migrationNotes++;
            }
        }

        List<Integer> destinations = new ArrayList<>(replaceable);
        for (int slot = 0; slot < slots.size(); slot++) {
            if (!replaceable.contains(slot) && slots.get(slot).isEmpty()) destinations.add(slot);
        }
        if (outputs.size() > destinations.size()) {
            result.unresolved.add("Not enough inventory space to convert assets at " + locationKey + ".");
            return result.freeze();
        }

        for (int slot : replaceable) slots.set(slot, ItemStack.EMPTY);
        for (int index = 0; index < outputs.size(); index++) slots.set(destinations.get(index), outputs.get(index));
        slots.changed();
        result.changed = true;
        return result.freeze();
    }

    private static void migrateNested(ItemStack parent, Context context, String locationKey, MutableResult result) {
        ItemContainerContents container = parent.get(DataComponents.CONTAINER);
        if (container != null && container != ItemContainerContents.EMPTY) {
            List<ItemStack> contents = new ArrayList<>(container.stream().toList());
            Result nested = migrate(new ListSlots(contents), context, locationKey + "/container");
            result.add(nested);
            if (nested.changed()) parent.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        }

        BundleContents bundle = parent.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle == null || bundle.isEmpty()) return;
        List<ItemStack> kept = new ArrayList<>();
        int index = 0;
        boolean changed = false;
        for (ItemStack child : bundle.itemsCopy()) {
            String childLocation = locationKey + "/bundle-" + index++;
            String id = itemId(child);
            NumismaticsCoin coin = NumismaticsCoin.fromItemId(id);
            if (coin != null) {
                long cents = coin.valueCents(context.centsPerSpur(), child.getCount());
                context.recoverySink().accept(MigrationBankNoteFactory.create(cents, context.migrationId(), childLocation));
                result.coinItems += child.getCount();
                result.convertedCents = Math.addExact(result.convertedCents, cents);
                result.migrationNotes++;
                changed = true;
                continue;
            }
            if (context.convertCards() && NumismaticsItemIds.isBankCard(id)) {
                UUID source = cardAccountId(child);
                ItemStack converted = null;
                if (source == null) {
                    converted = new ItemStack(ModItems.CREDIT_CARD.get());
                    result.blankCards++;
                } else {
                    UUID targetId = context.accountMappings().get(source);
                    AccountHolder target = targetId == null ? null : context.centralBank().SearchForAccountByAccountId(targetId);
                    if (target != null) {
                        CreditCardService.CardIssueResult issued = CreditCardService.issueCard(
                                context.centralBank(), target, context.holderName(), false);
                        if (issued.success()) {
                            converted = issued.cardStack();
                            result.boundCards++;
                        }
                    }
                }
                if (converted != null) {
                    context.recoverySink().accept(converted);
                    changed = true;
                    continue;
                }
                result.unresolved.add("Could not convert bank card inside bundle at " + childLocation + ".");
            }
            migrateNested(child, context, childLocation, result);
            kept.add(child);
        }
        if (changed) parent.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(kept));
    }

    private static List<ItemStack> cashStacks(long cents) {
        List<ItemStack> stacks = new ArrayList<>();
        for (NumismaticsCashPlan.Output output : NumismaticsCashPlan.plan(cents)) {
            Item item = cashItem(output.itemPath());
            if (item == null) throw new IllegalStateException("Unknown UBS cash item " + output.itemPath());
            stacks.add(new ItemStack(item, output.count()));
        }
        return stacks;
    }

    private static Item cashItem(String path) {
        return switch (path) {
            case "hundred_dollar_money_stack" -> ModItems.HUNDRED_DOLLAR_MONEY_STACK.get();
            case "fifty_dollar_money_stack" -> ModItems.FIFTY_DOLLAR_MONEY_STACK.get();
            case "twenty_dollar_money_stack" -> ModItems.TWENTY_DOLLAR_MONEY_STACK.get();
            case "ten_dollar_money_stack" -> ModItems.TEN_DOLLAR_MONEY_STACK.get();
            case "five_dollar_money_stack" -> ModItems.FIVE_DOLLAR_MONEY_STACK.get();
            case "two_dollar_money_stack" -> ModItems.TWO_DOLLAR_MONEY_STACK.get();
            case "one_dollar_money_stack" -> ModItems.ONE_DOLLAR_MONEY_STACK.get();
            case "hundred_dollar_bill" -> ModItems.HUNDRED_DOLLAR_BILL.get();
            case "fifty_dollar_bill" -> ModItems.FIFTY_DOLLAR_BILL.get();
            case "twenty_dollar_bill" -> ModItems.TWENTY_DOLLAR_BILL.get();
            case "ten_dollar_bill" -> ModItems.TEN_DOLLAR_BILL.get();
            case "five_dollar_bill" -> ModItems.FIVE_DOLLAR_BILL.get();
            case "two_dollar_bill" -> ModItems.TWO_DOLLAR_BILL.get();
            case "one_dollar_bill" -> ModItems.ONE_DOLLAR_BILL.get();
            case "half_dollar_coin" -> ModItems.HALF_DOLLAR_COIN.get();
            case "quarter_coin" -> ModItems.QUARTER_COIN.get();
            case "dime_coin" -> ModItems.DIME_COIN.get();
            case "nickel_coin" -> ModItems.NICKEL_COIN.get();
            case "penny_coin" -> ModItems.PENNY_COIN.get();
            default -> null;
        };
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    @SuppressWarnings("unchecked")
    private static UUID cardAccountId(ItemStack stack) {
        DataComponentType<?> raw = BuiltInRegistries.DATA_COMPONENT_TYPE.get(CARD_ACCOUNT_COMPONENT);
        if (raw == null) return null;
        Object value = stack.get((DataComponentType<Object>) raw);
        return value instanceof UUID id ? id : null;
    }

    public interface Slots {
        int size();
        ItemStack get(int slot);
        void set(int slot, ItemStack stack);
        default void changed() {
        }
    }

    public record Context(UUID migrationId,
                          int centsPerSpur,
                          boolean convertCards,
                          CentralBank centralBank,
                          Map<UUID, UUID> accountMappings,
                          String holderName,
                          Consumer<ItemStack> recoverySink) {
        public Context {
            accountMappings = accountMappings == null ? Map.of() : Map.copyOf(accountMappings);
            holderName = holderName == null || holderName.isBlank() ? "Migrated Account Holder" : holderName;
            recoverySink = recoverySink == null ? ignored -> { } : recoverySink;
        }
    }

    public record Result(boolean changed, long coinItems, long convertedCents, long boundCards,
                         long blankCards, long idCards, long migrationNotes, List<String> unresolved) {
        public Result {
            unresolved = unresolved == null ? List.of() : List.copyOf(unresolved);
        }
        public static Result empty() { return new Result(false, 0, 0, 0, 0, 0, 0, List.of()); }
    }

    private static final class MutableResult {
        boolean changed;
        long coinItems;
        long convertedCents;
        long boundCards;
        long blankCards;
        long idCards;
        long migrationNotes;
        final Set<String> unresolved = new LinkedHashSet<>();
        void add(Result value) {
            changed |= value.changed(); coinItems += value.coinItems(); convertedCents += value.convertedCents();
            boundCards += value.boundCards(); blankCards += value.blankCards(); idCards += value.idCards();
            migrationNotes += value.migrationNotes(); unresolved.addAll(value.unresolved());
        }
        Result freeze() { return new Result(changed, coinItems, convertedCents, boundCards, blankCards, idCards, migrationNotes, List.copyOf(unresolved)); }
    }

    private static final class ListSlots implements Slots {
        private final List<ItemStack> values;
        private ListSlots(List<ItemStack> values) { this.values = values; }
        public int size() { return values.size(); }
        public ItemStack get(int slot) { return values.get(slot); }
        public void set(int slot, ItemStack stack) { values.set(slot, stack == null ? ItemStack.EMPTY : stack); }
    }
}
