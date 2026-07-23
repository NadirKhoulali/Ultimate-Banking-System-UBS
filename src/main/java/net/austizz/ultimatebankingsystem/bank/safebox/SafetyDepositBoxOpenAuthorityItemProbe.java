package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.menu.SafetyDepositBoxMenu;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class SafetyDepositBoxOpenAuthorityItemProbe {
    private SafetyDepositBoxOpenAuthorityItemProbe() {
    }

    static LocationCounts counts(SafetyDepositBoxMenu menu,
                                 SafetyDepositBoxOpenAuthorityGameTestFixture fixture,
                                 Item item) {
        int carried = menu.getCarried().is(item) ? menu.getCarried().getCount() : 0;
        return new LocationCounts(
                carried,
                menu(menu, item),
                player(fixture, item),
                persisted(fixture, item));
    }

    static int persisted(SafetyDepositBoxOpenAuthorityGameTestFixture fixture, Item item) {
        int count = 0;
        for (CompoundTag tag : fixture.account().getSafeBoxSlots().values()) {
            ItemStack stack = ItemStackDataCompat.parseStack(tag, fixture.level().registryAccess());
            count += stack.is(item) ? stack.getCount() : 0;
        }
        return count;
    }

    private static int menu(SafetyDepositBoxMenu menu, Item item) {
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            count += stack.is(item) ? stack.getCount() : 0;
        }
        return count;
    }

    private static int player(SafetyDepositBoxOpenAuthorityGameTestFixture fixture, Item item) {
        int count = 0;
        for (int slot = 0; slot < fixture.player().getInventory().getContainerSize(); slot++) {
            ItemStack stack = fixture.player().getInventory().getItem(slot);
            count += stack.is(item) ? stack.getCount() : 0;
        }
        return count;
    }

    record LocationCounts(int cursor, int box, int player, int persisted) {
        int total() {
            return cursor + box + player + persisted;
        }
    }
}
