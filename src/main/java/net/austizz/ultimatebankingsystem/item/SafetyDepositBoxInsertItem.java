package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public class SafetyDepositBoxInsertItem extends Item {
    private final SafetyDepositBoxRowBlockEntity.ModuleType moduleType;

    public SafetyDepositBoxInsertItem(SafetyDepositBoxRowBlockEntity.ModuleType moduleType) {
        super(new Item.Properties());
        this.moduleType = moduleType == null ? SafetyDepositBoxRowBlockEntity.ModuleType.SMALL : moduleType;
    }

    public SafetyDepositBoxRowBlockEntity.ModuleType moduleType() {
        return moduleType;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof SafetyDepositBoxRowBlockEntity row)) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }
        if (!canInstallInRow(serverPlayer, level, pos)) {
            serverPlayer.sendSystemMessage(Component.literal("Only bank owners, directors, and operators can install safety deposit box inserts here."));
            return InteractionResult.FAIL;
        }

        int startRow = row.firstAvailableStart(moduleType);
        if (startRow < 0) {
            serverPlayer.sendSystemMessage(Component.literal("This safety deposit shell does not have enough free space for a " + moduleType.displayName() + "."));
            return InteractionResult.FAIL;
        }
        if (!row.installModule(startRow, moduleType)) {
            serverPlayer.sendSystemMessage(Component.literal("Could not install this safety deposit box insert."));
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        serverPlayer.sendSystemMessage(Component.literal("Installed " + moduleType.displayName() + " in shell row " + (startRow + 1) + "."));
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(moduleType.rowSpan() + " shell row" + (moduleType.rowSpan() == 1 ? "" : "s"))
                .withStyle(ChatFormatting.GRAY));
        if (moduleType.assignable()) {
            tooltip.add(Component.literal(moduleType.inventorySlots() + " safety box slots")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.literal("Cover plate only")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.literal("Right-click a safety deposit shell to install from top to bottom.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static boolean canInstallInRow(ServerPlayer player, Level level, BlockPos pos) {
        if (player.hasPermissions(3) || player.getAbilities().instabuild) {
            return true;
        }
        CentralBank centralBank = BankManager.getCentralBank(player.server);
        UUID bankId = SafetyDepositBoxService.findBankIdForSafeArea(centralBank, level, pos);
        return bankId != null && SafetyDepositBoxService.canManageSafeArea(centralBank, player, bankId);
    }
}
