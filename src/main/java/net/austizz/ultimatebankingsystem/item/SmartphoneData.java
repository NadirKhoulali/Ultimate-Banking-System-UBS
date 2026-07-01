package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SmartphoneData {
    public static final int MAX_NOTES = 12;
    public static final int MAX_NOTE_CHARS = 4000;
    public static final int MAX_PAINTINGS = 6;
    public static final int MAX_PAINTING_CHARS = 4000;

    private static final String TAG_OWNER_ID = "ubs_phone_owner";
    private static final String TAG_OWNER_NAME = "ubs_phone_owner_name";
    private static final String TAG_ACCENT = "ubs_phone_accent";
    private static final String TAG_WALLPAPER = "ubs_phone_wallpaper";
    private static final String TAG_LAYOUT = "ubs_phone_layout";
    private static final String TAG_TAP_ACCOUNT = "ubs_phone_tap_account";
    private static final String TAG_VIRTUAL_CARDS = "ubs_phone_virtual_cards";
    private static final String TAG_NOTES = "ubs_phone_notes";
    private static final String TAG_PAINTINGS = "ubs_phone_paintings";

    private SmartphoneData() {
    }

    public static void ensureOwner(ItemStack stack, ServerPlayer player) {
        if (stack == null || stack.isEmpty() || player == null) {
            return;
        }
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        if (!tag.hasUUID(TAG_OWNER_ID)) {
            tag.putUUID(TAG_OWNER_ID, player.getUUID());
            tag.putString(TAG_OWNER_NAME, player.getGameProfile().getName());
            tag.putString(TAG_ACCENT, "cyan");
            tag.putString(TAG_WALLPAPER, "aurora");
            tag.putString(TAG_LAYOUT, "classic");
            ItemStackDataCompat.setCustomData(stack, tag);
        }
    }

    public static boolean hasOwner(ItemStack stack) {
        return ItemStackDataCompat.getCustomData(stack).hasUUID(TAG_OWNER_ID);
    }

    public static UUID getOwnerId(ItemStack stack) {
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        return tag.hasUUID(TAG_OWNER_ID) ? tag.getUUID(TAG_OWNER_ID) : null;
    }

    public static String getOwnerName(ItemStack stack) {
        String name = ItemStackDataCompat.getCustomData(stack).getString(TAG_OWNER_NAME);
        return name == null || name.isBlank() ? "Unbound" : name;
    }

    public static void setOwnerName(ItemStack stack, String name) {
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        tag.putString(TAG_OWNER_NAME, clampText(name, 32));
        ItemStackDataCompat.setCustomData(stack, tag);
    }

    public static boolean isOwner(ItemStack stack, ServerPlayer player) {
        UUID owner = getOwnerId(stack);
        return owner != null && player != null && owner.equals(player.getUUID());
    }

    public static String getAccent(ItemStack stack) {
        return readString(stack, TAG_ACCENT, "cyan");
    }

    public static String getWallpaper(ItemStack stack) {
        return readString(stack, TAG_WALLPAPER, "aurora");
    }

    public static String getLayout(ItemStack stack) {
        return readString(stack, TAG_LAYOUT, "classic");
    }

    public static void setTheme(ItemStack stack, String accent, String wallpaper) {
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        tag.putString(TAG_ACCENT, sanitizeChoice(accent, "cyan"));
        tag.putString(TAG_WALLPAPER, sanitizeChoice(wallpaper, "aurora"));
        ItemStackDataCompat.setCustomData(stack, tag);
    }

    public static UUID getTapAccount(ItemStack stack) {
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        return tag.hasUUID(TAG_TAP_ACCOUNT) ? tag.getUUID(TAG_TAP_ACCOUNT) : null;
    }

    public static void setTapAccount(ItemStack stack, UUID accountId) {
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        if (accountId == null) {
            tag.remove(TAG_TAP_ACCOUNT);
        } else {
            tag.putUUID(TAG_TAP_ACCOUNT, accountId);
        }
        ItemStackDataCompat.setCustomData(stack, tag);
    }

    public static String getOrCreateVirtualCardNumber(ItemStack stack, UUID accountId) {
        if (stack == null || stack.isEmpty() || accountId == null) {
            return "";
        }
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        CompoundTag cards = tag.getCompound(TAG_VIRTUAL_CARDS);
        String key = accountId.toString();
        String existing = cards.getString(key);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String generated = generateCardNumber(stack, accountId);
        cards.putString(key, generated);
        tag.put(TAG_VIRTUAL_CARDS, cards);
        ItemStackDataCompat.setCustomData(stack, tag);
        return generated;
    }

    public static List<String> getNotes(ItemStack stack) {
        return readStringList(stack, TAG_NOTES, MAX_NOTES);
    }

    public static void saveNote(ItemStack stack, int index, String text) {
        List<String> notes = getNotes(stack);
        int safeIndex = Math.max(0, Math.min(MAX_NOTES - 1, index));
        while (notes.size() <= safeIndex) {
            notes.add("");
        }
        notes.set(safeIndex, clampText(text, MAX_NOTE_CHARS));
        writeStringList(stack, TAG_NOTES, notes, MAX_NOTES);
    }

    public static void deleteNote(ItemStack stack, int index) {
        List<String> notes = getNotes(stack);
        if (index >= 0 && index < notes.size()) {
            notes.remove(index);
        }
        writeStringList(stack, TAG_NOTES, notes, MAX_NOTES);
    }

    public static List<String> getPaintings(ItemStack stack) {
        return readStringList(stack, TAG_PAINTINGS, MAX_PAINTINGS);
    }

    public static void savePainting(ItemStack stack, int index, String encodedPixels) {
        List<String> paintings = getPaintings(stack);
        int safeIndex = Math.max(0, Math.min(MAX_PAINTINGS - 1, index));
        while (paintings.size() <= safeIndex) {
            paintings.add("");
        }
        paintings.set(safeIndex, clampText(encodedPixels, MAX_PAINTING_CHARS));
        writeStringList(stack, TAG_PAINTINGS, paintings, MAX_PAINTINGS);
    }

    public static ItemStack findHeldPhone(ServerPlayer player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.SMARTPHONE.get())) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.is(ModItems.SMARTPHONE.get())) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack findInventoryPhone(ServerPlayer player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack held = findHeldPhone(player);
        if (!held.isEmpty()) {
            return held;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.SMARTPHONE.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static String readString(ItemStack stack, String key, String fallback) {
        String value = ItemStackDataCompat.getCustomData(stack).getString(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String sanitizeChoice(String value, String fallback) {
        String cleaned = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!cleaned.matches("[a-z0-9_-]{1,24}")) {
            return fallback;
        }
        return cleaned;
    }

    private static List<String> readStringList(ItemStack stack, String key, int max) {
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        ListTag raw = tag.getList(key, Tag.TAG_STRING);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < raw.size() && values.size() < max; i++) {
            values.add(raw.getString(i));
        }
        return values;
    }

    private static void writeStringList(ItemStack stack, String key, List<String> values, int max) {
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        ListTag list = new ListTag();
        if (values != null) {
            for (String value : values) {
                if (list.size() >= max) {
                    break;
                }
                list.add(StringTag.valueOf(value == null ? "" : value));
            }
        }
        tag.put(key, list);
        ItemStackDataCompat.setCustomData(stack, tag);
    }

    private static String clampText(String text, int maxChars) {
        String value = text == null ? "" : text.trim();
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private static String generateCardNumber(ItemStack stack, UUID accountId) {
        UUID owner = getOwnerId(stack);
        String seed = (owner == null ? "" : owner.toString()) + ":" + accountId;
        UUID stable = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
        long raw = Math.abs(stable.getMostSignificantBits() ^ stable.getLeastSignificantBits());
        String digits = String.format(Locale.ROOT, "%015d", raw % 1_000_000_000_000_000L);
        return "UBS " + digits.substring(0, 3) + " " + digits.substring(3, 7) + " "
                + digits.substring(7, 11) + " " + digits.substring(11);
    }
}
