package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.entity.ModEntities;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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

public class BankTellerSpawnEggItem extends Item {

    public BankTellerSpawnEggItem() {
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

        BankTellerEntity teller = ModEntities.BANK_TELLER.get().create(serverLevel);
        if (teller == null) {
            return InteractionResult.FAIL;
        }

        CentralBank centralBank = BankManager.getCentralBank(serverLevel.getServer());
        int variant = centralBank != null
                ? centralBank.claimNextBankTellerVariant()
                : BankTellerEntity.VARIANT_MALE;

        UUID boundBankId = BankTellerEntity.readBoundBankIdFromEgg(context.getItemInHand());
        Bank boundBank = null;
        if (boundBankId != null) {
            if (centralBank == null) {
                player.sendSystemMessage(Component.literal("§cBank data is unavailable right now."));
                return InteractionResult.FAIL;
            }
            boundBank = centralBank.getBank(boundBankId);
            if (boundBank == null) {
                player.sendSystemMessage(Component.literal("§cThis teller egg is bound to a bank that no longer exists."));
                return InteractionResult.FAIL;
            }
            int activeCount = BankTellerEntity.countActiveTellersForBank(serverLevel.getServer(), boundBankId);
            if (activeCount >= BankTellerEntity.MAX_TELLERS_PER_BANK) {
                player.sendSystemMessage(Component.literal(
                        "§c" + boundBank.getBankName() + " already has the max "
                                + BankTellerEntity.MAX_TELLERS_PER_BANK + " active bank tellers."
                ));
                return InteractionResult.FAIL;
            }
        }

        teller.moveTo(spawnCenter.x, spawnCenter.y, spawnCenter.z, yaw, 0.0F);
        teller.initializeFromSpawn(player, variant, boundBankId);
        teller.alignBodyTo(yaw);

        if (!serverLevel.noCollision(teller)) {
            teller.discard();
            player.sendSystemMessage(Component.literal("§cNot enough space to place a Bank Teller here."));
            return InteractionResult.FAIL;
        }

        serverLevel.addFreshEntity(teller);
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        if (boundBank != null) {
            int activeCount = BankTellerEntity.countActiveTellersForBank(serverLevel.getServer(), boundBankId);
            player.sendSystemMessage(Component.literal(
                    "§aPlaced teller for §e" + boundBank.getBankName()
                            + "§a. Active tellers: §f" + activeCount
                            + "§7/§f" + BankTellerEntity.MAX_TELLERS_PER_BANK
            ).withStyle(ChatFormatting.GREEN));
        } else {
            player.sendSystemMessage(Component.literal("§aBank Teller placed.").withStyle(ChatFormatting.GREEN));
        }
        return InteractionResult.CONSUME;
    }
}
