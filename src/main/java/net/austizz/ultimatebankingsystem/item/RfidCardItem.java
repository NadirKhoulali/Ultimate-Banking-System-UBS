package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class RfidCardItem extends Item {
    private static final String CARD_ID_KEY = "ubs_rfid_card_id";
    private static final String GRANTS_KEY = "ubs_rfid_grants";

    public RfidCardItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static UUID ensureCardId(ItemStack stack) {
        UUID id = getCardId(stack);
        if (id != null) {
            return id;
        }
        UUID created = UUID.randomUUID();
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        tag.putString(CARD_ID_KEY, created.toString());
        ItemStackDataCompat.setCustomData(stack, tag);
        return created;
    }

    public static UUID getCardId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof RfidCardItem)) {
            return null;
        }
        String raw = ItemStackDataCompat.getCustomData(stack).getString(CARD_ID_KEY);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static int getGrantLevel(ItemStack stack, UUID readerId) {
        if (readerId == null) {
            return -1;
        }
        CompoundTag grant = findGrant(stack, readerId);
        return grant == null ? -1 : grant.getInt("level");
    }

    public static void writeGrant(ItemStack stack, UUID readerId, int level, String label) {
        if (stack == null || stack.isEmpty() || readerId == null || !(stack.getItem() instanceof RfidCardItem)) {
            return;
        }
        ensureCardId(stack);
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        ListTag grants = tag.getList(GRANTS_KEY, Tag.TAG_COMPOUND);
        CompoundTag existing = null;
        for (int i = 0; i < grants.size(); i++) {
            CompoundTag grant = grants.getCompound(i);
            if (readerId.toString().equalsIgnoreCase(grant.getString("reader_id"))) {
                existing = grant;
                break;
            }
        }
        if (existing == null) {
            existing = new CompoundTag();
            grants.add(existing);
        }
        existing.putString("reader_id", readerId.toString());
        existing.putInt("level", Math.max(0, Math.min(100, level)));
        existing.putString("label", label == null ? "" : label.trim());
        tag.put(GRANTS_KEY, grants);
        ItemStackDataCompat.setCustomData(stack, tag);
    }

    public static int grantCount(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof RfidCardItem)) {
            return 0;
        }
        return ItemStackDataCompat.getCustomData(stack).getList(GRANTS_KEY, Tag.TAG_COMPOUND).size();
    }

    private static CompoundTag findGrant(ItemStack stack, UUID readerId) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof RfidCardItem)) {
            return null;
        }
        ListTag grants = ItemStackDataCompat.getCustomData(stack).getList(GRANTS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < grants.size(); i++) {
            CompoundTag grant = grants.getCompound(i);
            if (readerId.toString().equalsIgnoreCase(grant.getString("reader_id"))) {
                return grant;
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        UUID cardId = getCardId(stack);
        tooltip.add(Component.literal("Card ID: " + (cardId == null ? "unwritten" : shortId(cardId)))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Access points: " + grantCount(stack)).withStyle(ChatFormatting.DARK_AQUA));
    }

    private static String shortId(UUID uuid) {
        String raw = uuid.toString().toUpperCase(Locale.ROOT);
        return raw.substring(0, Math.min(8, raw.length()));
    }
}
