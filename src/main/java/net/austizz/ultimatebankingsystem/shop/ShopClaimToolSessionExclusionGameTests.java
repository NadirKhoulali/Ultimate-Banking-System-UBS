package net.austizz.ultimatebankingsystem.shop;

import com.mojang.authlib.GameProfile;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(UltimateBankingSystem.MODID + "_source_behavior_replacements")
@PrefixGameTestTemplate(false)
public final class ShopClaimToolSessionExclusionGameTests {
    private ShopClaimToolSessionExclusionGameTests() {
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 100)
    public static void bankClaimSessionRejectsEveryShopClaimFlowWithoutMutation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(
                level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "ubs-task14-shop"),
                ClientInformation.createDefault());
        CentralBank centralBank = new CentralBank();
        UUID bankId = UUID.fromString("14000000-0000-0000-0000-000000000014");
        UUID shopId = UUID.fromString("24000000-0000-0000-0000-000000000014");
        Bank bank = new Bank(bankId, "Claim Exclusion Bank", BigDecimal.ZERO, 1.0D, player.getUUID());
        centralBank.addBank(bank);
        ShopFixture shop = new ShopFixture(centralBank, shopId);
        installShopFixture(shop, player.getUUID());
        installCharacterizedHotbar(player);
        ClaimFixture fixture = new ClaimFixture(helper, player, shop);
        HotbarState originalHotbar = fixture.snapshotHotbar();

        try {
            List<ShopService.ShopSummary> shops = ShopService.listOwnerShopSummaries(centralBank, player.getUUID());
            require(helper, shops.size() == 1, "Shop fixture did not resolve exactly one owner shop");
            require(helper, shopId.equals(shops.getFirst().shopId()), "Shop fixture resolved the wrong shop");

            SafetyDepositBoxService.ActionResult bankStart = SafetyDepositBoxService.startSafeAreaClaimToolSession(
                    helper.getLevel().getServer(), centralBank, player, bankId);
            require(helper, bankStart.success(), "Bank safe-area claim session failed to start: " + bankStart.message());
            HotbarState bankHotbar = fixture.snapshotHotbar();
            fixture.assertHotbar(originalHotbar,
                    "Starting bank claim mode mutated the player's hotbar");

            fixture.assertRejectedWithoutMutation("plot", bankHotbar,
                    ShopService.startClaimToolSession(centralBank, player, shopId, false));
            fixture.assertRejectedWithoutMutation("stockroom", bankHotbar,
                    ShopService.startClaimToolSession(centralBank, player, shopId, true));
            fixture.assertRejectedWithoutMutation("pallet", bankHotbar,
                    ShopService.startPalletClaimToolSession(centralBank, player, shopId));

            SafetyDepositBoxService.ActionResult bankFinish = SafetyDepositBoxService.finishSafeAreaClaimToolSession(
                    player, "Test bank claim session closed.");
            require(helper, bankFinish.success(), "Bank safe-area claim session failed to close");
            fixture.assertHotbar(originalHotbar,
                    "Closing bank claim session did not restore characterized hotbar");

            fixture.assertClaimFlowStartsAndRestores("plot", false, originalHotbar);
            helper.succeed();
        } finally {
            ShopService.closeAllClaimToolSessions(player, "Test cleanup.");
            SafetyDepositBoxService.closeSafeAreaClaimToolSession(player, "Test cleanup.");
            centralBank.getBankMetadata().remove(bankId);
            centralBank.getBanks().remove(bankId);
        }
    }

    private static void installShopFixture(ShopFixture fixture, UUID ownerId) {
        CompoundTag shop = new CompoundTag();
        shop.putUUID("id", fixture.shopId());
        shop.putUUID("owner", ownerId);
        shop.putString("name", "Claim Exclusion Shop");
        ListTag shops = new ListTag();
        shops.add(shop);
        CompoundTag root = new CompoundTag();
        root.put("shops", shops);
        CentralBank centralBank = fixture.centralBank();
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        metadata.put("ubs_shop_root", root);
        centralBank.putBankMetadata(centralBank.getBankId(), metadata);
    }

    private static void installCharacterizedHotbar(ServerPlayer player) {
        Item[] items = {Items.DIAMOND, Items.EMERALD, Items.IRON_INGOT, Items.GOLD_INGOT,
                Items.REDSTONE, Items.LAPIS_LAZULI, Items.COAL, Items.COPPER_INGOT, Items.QUARTZ};
        for (int slot = 0; slot < items.length; slot++) {
            player.getInventory().setItem(slot, new ItemStack(items[slot], slot + 1));
        }
        player.getInventory().selected = 6;
    }

    private record ShopFixture(CentralBank centralBank, UUID shopId) {
    }

    private record HotbarState(List<ItemStack> items, int selected) {
    }

    private record ClaimFixture(GameTestHelper helper, ServerPlayer player, ShopFixture shop) {
        private HotbarState snapshotHotbar() {
            List<ItemStack> snapshot = new ArrayList<>(9);
            for (int slot = 0; slot < 9; slot++) {
                snapshot.add(player.getInventory().getItem(slot).copy());
            }
            return new HotbarState(snapshot, player.getInventory().selected);
        }

        private void assertRejectedWithoutMutation(String flow,
                                                   HotbarState expected,
                                                   ShopService.ShopActionResult result) {
            require(helper, !result.success(), flow + " claim flow started during a bank claim session");
            require(helper, SafetyDepositBoxService.hasSafeAreaClaimToolSession(player.getUUID()),
                    flow + " rejection closed the active bank claim session");
            require(helper, !ShopService.hasAnyClaimToolSession(player.getUUID()),
                    flow + " rejection created a shop claim session");
            assertHotbar(expected, flow + " rejection changed hotbar or selected slot");
        }

        private void assertClaimFlowStartsAndRestores(String flow,
                                                      boolean stockroom,
                                                      HotbarState originalHotbar) {
            ShopService.ShopActionResult start = ShopService.startClaimToolSession(
                    shop.centralBank(), player, shop.shopId(), stockroom);
            require(helper, start.success(),
                    flow + " claim flow did not start without a bank session: " + start.message());
            require(helper, ShopService.hasClaimToolSession(player.getUUID()),
                    flow + " start did not register its compiled service session");
            assertHotbar(originalHotbar, flow + " claim start mutated hotbar or selected slot");
            ShopService.ShopActionResult finish = ShopService.finishClaimToolSession(player, "Test flow closed.");
            require(helper, finish.success(), flow + " claim flow did not close");
            assertHotbar(originalHotbar, flow + " claim flow did not restore characterized hotbar");
        }

        private void assertHotbar(HotbarState expected, String message) {
            require(helper, player.getInventory().selected == expected.selected(),
                    message + ": selected slot changed");
            for (int slot = 0; slot < 9; slot++) {
                ItemStack actual = player.getInventory().getItem(slot);
                ItemStack wanted = expected.items().get(slot);
                require(helper, actual.getCount() == wanted.getCount()
                                && ItemStack.isSameItemSameComponents(actual, wanted),
                        message + ": slot " + slot + " changed");
            }
        }
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
