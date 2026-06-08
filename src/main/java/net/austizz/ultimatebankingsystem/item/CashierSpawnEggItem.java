package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.entity.ModEntities;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class CashierSpawnEggItem extends Item {
    public static final String TAG_OWNER = "ubs_cashier_owner";
    public static final String TAG_SHOP_ID = "ubs_cashier_shop_id";
    public static final String TAG_EMPLOYEE_ID = "ubs_cashier_employee_id";
    public static final String TAG_SHOP_NAME = "ubs_cashier_shop_name";

    public CashierSpawnEggItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.FAIL;
        }

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 spawnCenter = Vec3.atBottomCenterOf(spawnPos);
        float yaw = Mth.wrapDegrees(player.getYRot() + 180.0F);
        CompoundTag custom = ItemStackDataCompat.getCustomData(context.getItemInHand());

        CentralBank centralBank = BankManager.getCentralBank(serverLevel.getServer());
        if (centralBank == null) {
            player.sendSystemMessage(UbsTranslations.literal("§cBank data is unavailable. Try again in a moment."));
            return InteractionResult.FAIL;
        }

        if (custom == null || !custom.hasUUID(TAG_SHOP_ID)) {
            player.sendSystemMessage(UbsTranslations.literal("§cThis cashier spawn egg is not assigned to any shop."));
            player.sendSystemMessage(UbsTranslations.literal("§7Use the Shop Manager app -> Team & POS -> Hire Cashier NPC."));
            return InteractionResult.FAIL;
        }

        UUID shopId = custom.getUUID(TAG_SHOP_ID);
        UUID ownerId = custom.hasUUID(TAG_OWNER) ? custom.getUUID(TAG_OWNER) : null;
        if (ownerId == null || !ShopService.hasShop(centralBank, ownerId, shopId)) {
            ownerId = ShopService.resolveShopOwnerId(centralBank, shopId);
        }
        if (ownerId == null || !ShopService.hasShop(centralBank, ownerId, shopId)) {
            player.sendSystemMessage(UbsTranslations.literal("§cThis cashier spawn egg points to a shop that no longer exists."));
            player.sendSystemMessage(UbsTranslations.literal("§7Issue a new cashier from the Shop Manager app."));
            return InteractionResult.FAIL;
        }

        boolean playerIsAffiliated = ShopService.hasShop(centralBank, player.getUUID(), shopId)
                || player.getUUID().equals(ownerId);
        if (!playerIsAffiliated && !player.hasPermissions(2)) {
            player.sendSystemMessage(UbsTranslations.literal("§cYou are not affiliated with this shop."));
            player.sendSystemMessage(UbsTranslations.literal("§7Only the shop owner (or OP) can place this cashier."));
            return InteractionResult.FAIL;
        }

        UUID employeeId = custom != null && custom.hasUUID(TAG_EMPLOYEE_ID) ? custom.getUUID(TAG_EMPLOYEE_ID) : UUID.randomUUID();
        String shopName = custom == null ? "" : custom.getString(TAG_SHOP_NAME);

        BankTellerEntity cashier = ModEntities.BANK_TELLER.get().create(serverLevel);
        if (cashier == null) {
            return InteractionResult.FAIL;
        }

        if (!ShopService.canPlaceCashierAt(
                centralBank,
                ownerId,
                shopId,
                serverLevel.dimension().location().toString(),
                spawnPos
        )) {
            player.sendSystemMessage(UbsTranslations.literal("§cCashier must be placed inside this shop's claimed plot."));
            return InteractionResult.FAIL;
        }
        int variant = centralBank != null
                ? centralBank.claimNextBankTellerVariant()
                : BankTellerEntity.VARIANT_MALE;

        cashier.moveTo(spawnCenter.x, spawnCenter.y, spawnCenter.z, yaw, 0.0F);
        cashier.initializeFromSpawn(player, variant, null);
        cashier.setOwnerUUID(ownerId);
        cashier.setCashier(true);
        cashier.setShopId(shopId);
        cashier.setEmployeeId(employeeId);
        if (shopName != null && !shopName.isBlank()) {
            cashier.setCustomName(UbsTranslations.literal("[Shop] Cashier - ")
                    .append(Component.literal(shopName))
                    .withStyle(ChatFormatting.AQUA));
            cashier.setCustomNameVisible(true);
        }
        cashier.alignBodyTo(yaw);

        if (!serverLevel.noCollision(cashier)) {
            cashier.discard();
            player.sendSystemMessage(UbsTranslations.literal("§cNot enough space to place a Cashier here."));
            return InteractionResult.FAIL;
        }

        serverLevel.addFreshEntity(cashier);
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        player.sendSystemMessage(UbsTranslations.literal("§aCashier placed (ID: ")
                .append(Component.literal(shortId(employeeId)))
                .append(Component.literal(")."))
                .withStyle(ChatFormatting.GREEN));
        return InteractionResult.CONSUME;
    }

    private static String shortId(UUID id) {
        if (id == null) {
            return "-";
        }
        String raw = id.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }
}
