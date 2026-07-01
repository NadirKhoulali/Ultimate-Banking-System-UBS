package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.UUID;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class BankSafeAreaEvents {
    private BankSafeAreaEvents() {
    }

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!mayModify(serverPlayer, serverPlayer.level(), event.getPos())) {
            event.setCanceled(true);
            serverPlayer.sendSystemMessage(Component.literal("This bank safe area is protected."));
        }
    }

    @SubscribeEvent
    public static void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!mayModify(serverPlayer, serverPlayer.level(), event.getPos())) {
            event.setCanceled(true);
            serverPlayer.sendSystemMessage(Component.literal("This bank safe area is protected."));
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (level.getBlockState(pos).is(ModBlocks.SAFETY_DEPOSIT_BOX_ROW.get())) {
            return;
        }
        if (!mayModify(serverPlayer, level, pos)) {
            event.setCanceled(true);
            serverPlayer.sendSystemMessage(Component.literal("This bank safe area is protected."));
        }
    }

    private static boolean mayModify(ServerPlayer player, Level level, BlockPos pos) {
        MinecraftServer server = player.getServer();
        CentralBank centralBank = BankManager.getCentralBank(server);
        UUID bankId = SafetyDepositBoxService.findBankIdForSafeArea(centralBank, level, pos);
        return bankId == null || SafetyDepositBoxService.canManageSafeArea(centralBank, player, bankId);
    }
}
