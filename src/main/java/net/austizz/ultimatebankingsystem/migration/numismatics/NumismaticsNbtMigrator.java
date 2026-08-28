package net.austizz.ultimatebankingsystem.migration.numismatics;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** In-place NBT transformer used for offline player and nested inventory data. */
public final class NumismaticsNbtMigrator {
    private static final ResourceLocation CARD_ACCOUNT_COMPONENT = ResourceLocation.fromNamespaceAndPath(
            "numismatics", "card_account_id");

    private NumismaticsNbtMigrator() {
    }

    public static Result migrate(CompoundTag root, Context context, String sourceKey) {
        MutableResult result = new MutableResult();
        transform(root, context, sourceKey == null ? "nbt" : sourceKey, result);
        return result.freeze();
    }

    private static Tag transform(Tag tag, Context context, String path, MutableResult result) {
        if (tag instanceof CompoundTag compound) {
            String itemId = compound.contains("id", Tag.TAG_STRING) ? compound.getString("id") : "";
            NumismaticsCoin coin = NumismaticsCoin.fromItemId(itemId);
            if (coin != null) {
                int count = readCount(compound);
                long cents = coin.valueCents(context.centsPerSpur(), count);
                ItemStack note = MigrationBankNoteFactory.create(cents, context.migrationId(), path);
                result.changed = true;
                result.coinItems += count;
                result.convertedCents = Math.addExact(result.convertedCents, cents);
                result.migrationNotes++;
                return preserveContainerKeys(compound, asCompound(note.saveOptional(context.registries())));
            }
            if (context.convertCards() && NumismaticsItemIds.isBankCard(itemId)) {
                ItemStack source = ItemStack.parseOptional(context.registries(), compound);
                UUID sourceAccountId = cardAccountId(source);
                ItemStack replacement;
                if (sourceAccountId == null) {
                    replacement = new ItemStack(ModItems.CREDIT_CARD.get());
                    result.blankCards++;
                } else {
                    UUID targetId = context.accountMappings().get(sourceAccountId);
                    AccountHolder target = targetId == null ? null : context.centralBank().SearchForAccountByAccountId(targetId);
                    if (target == null) {
                        result.unresolved.add("Bound bank card references unmapped account " + sourceAccountId + " at " + path + ".");
                        return compound;
                    }
                    CreditCardService.CardIssueResult issued = CreditCardService.issueCard(
                            context.centralBank(), target, context.holderName(), false);
                    if (!issued.success()) {
                        result.unresolved.add("Could not issue UBS card at " + path + ": " + issued.message());
                        return compound;
                    }
                    replacement = issued.cardStack();
                    result.boundCards++;
                }
                result.changed = true;
                return preserveContainerKeys(compound, asCompound(replacement.saveOptional(context.registries())));
            }
            if (NumismaticsItemIds.isIdCard(itemId)) result.idCards += readCount(compound);

            List<String> keys = new ArrayList<>(compound.getAllKeys());
            for (String key : keys) {
                Tag child = compound.get(key);
                if (child != null) compound.put(key, transform(child, context, path + "/" + key, result));
            }
            return compound;
        }
        if (tag instanceof ListTag list) {
            for (int index = 0; index < list.size(); index++) {
                list.set(index, transform(list.get(index), context, path + "[" + index + "]", result));
            }
            return list;
        }
        return tag;
    }

    private static CompoundTag preserveContainerKeys(CompoundTag source, CompoundTag replacement) {
        for (String key : source.getAllKeys()) {
            if ("id".equals(key) || "count".equals(key) || "Count".equals(key) || "components".equals(key)) continue;
            Tag value = source.get(key);
            if (value != null) replacement.put(key, value.copy());
        }
        return replacement;
    }

    private static CompoundTag asCompound(Tag tag) {
        if (tag instanceof CompoundTag compound) return compound;
        throw new IllegalStateException("Serialized ItemStack was not a compound tag.");
    }

    private static int readCount(CompoundTag tag) {
        Tag value = tag.get("count");
        if (!(value instanceof NumericTag)) value = tag.get("Count");
        return value instanceof NumericTag numeric ? Math.max(1, numeric.getAsInt()) : 1;
    }

    @SuppressWarnings("unchecked")
    private static UUID cardAccountId(ItemStack stack) {
        DataComponentType<?> raw = BuiltInRegistries.DATA_COMPONENT_TYPE.get(CARD_ACCOUNT_COMPONENT);
        if (raw == null) return null;
        Object value = stack.get((DataComponentType<Object>) raw);
        return value instanceof UUID id ? id : null;
    }

    public record Context(UUID migrationId, int centsPerSpur, boolean convertCards,
                          CentralBank centralBank, Map<UUID, UUID> accountMappings,
                          HolderLookup.Provider registries, String holderName) {
        public Context {
            accountMappings = accountMappings == null ? Map.of() : Map.copyOf(accountMappings);
            holderName = holderName == null || holderName.isBlank() ? "Migrated Account Holder" : holderName;
        }
    }

    public record Result(boolean changed, long coinItems, long convertedCents, long boundCards,
                         long blankCards, long idCards, long migrationNotes, List<String> unresolved) {
        public Result { unresolved = unresolved == null ? List.of() : List.copyOf(unresolved); }
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
        Result freeze() { return new Result(changed, coinItems, convertedCents, boundCards, blankCards, idCards, migrationNotes, List.copyOf(unresolved)); }
    }
}
