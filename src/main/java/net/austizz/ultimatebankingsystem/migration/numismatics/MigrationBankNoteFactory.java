package net.austizz.ultimatebankingsystem.migration.numismatics;

import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.util.UUID;

public final class MigrationBankNoteFactory {
    private MigrationBankNoteFactory() {
    }

    public static ItemStack create(long cents, UUID migrationId, String sourceKey) {
        if (cents <= 0L) return ItemStack.EMPTY;
        String stableSource = sourceKey == null ? "unknown" : sourceKey;
        UUID serialId = UUID.nameUUIDFromBytes(("ubs:numismatics:" + migrationId + ":" + stableSource).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String serial = "MIG-" + serialId.toString().toUpperCase(java.util.Locale.ROOT);
        BigDecimal amount = BigDecimal.valueOf(cents, 2);

        ItemStack note = new ItemStack(ModItems.BANK_NOTE.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("ubs_note_serial", serial);
        tag.putString("ubs_note_amount", amount.toPlainString());
        tag.putString("ubs_note_issuer", "Numismatics Migration");
        tag.putString("ubs_migration_source", stableSource);
        tag.putBoolean("ubs_migration_note", true);
        if (migrationId != null) tag.putUUID("ubs_migration_id", migrationId);
        ItemStackDataCompat.setCustomData(note, tag);
        ItemStackDataCompat.setCustomName(note, Component.literal("Numismatics Migration Note - $" + amount.toPlainString()));
        return note;
    }
}
