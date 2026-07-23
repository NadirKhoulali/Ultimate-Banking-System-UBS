package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.BankVaultDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        if (!mayModify(serverPlayer, serverPlayer.level(), affectedPositionsFor(event.getState(), event.getPos()))) {
            event.setCanceled(true);
            serverPlayer.sendSystemMessage(Component.literal("This bank safe area is protected."));
        }
    }

    @SubscribeEvent
    public static void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (event.getPlacedBlock().is(ModBlocks.SAFETY_DEPOSIT_BOX_ROW.get())) {
            MinecraftServer server = serverPlayer.getServer();
            CentralBank centralBank = BankManager.getCentralBank(server);
            SafetyDepositBoxService.ActionResult result = SafetyDepositBoxService.validateSafeRowPlacement(
                    server,
                    centralBank,
                    serverPlayer,
                    serverPlayer.level(),
                    event.getPos()
            );
            if (!result.success()) {
                event.setCanceled(true);
                serverPlayer.sendSystemMessage(Component.literal(result.message()));
            }
            return;
        }
        if (!mayModify(serverPlayer, serverPlayer.level(), affectedPositionsFor(event.getPlacedBlock(), event.getPos()))) {
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
        return mayModify(player, level, List.of(pos));
    }

    private static boolean mayModify(ServerPlayer player, Level level, Collection<BlockPos> positions) {
        MinecraftServer server = player.getServer();
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (level == null) {
            return true;
        }
        String dimension = level.dimension().location().toString();
        return BankSafeAreaMutationAuthorization.mayModifyAll(
                positions,
                pos -> intersectingBankClaims(centralBank, dimension, pos),
                bankId -> SafetyDepositBoxService.canManageSafeArea(centralBank, player, bankId)
        );
    }

    static Collection<BlockPos> affectedPositionsFor(BlockState state, BlockPos pos) {
        if (state != null && state.getBlock() instanceof BankVaultDoorBlock) {
            List<BlockPos> positions = BankVaultDoorBlock.plannedPartPositions(state, pos);
            if (!positions.isEmpty()) {
                return positions;
            }
        }
        return List.of(pos);
    }

    private static Collection<UUID> intersectingBankClaims(CentralBank centralBank, String dimension, BlockPos pos) {
        if (centralBank == null || pos == null) {
            return List.of();
        }
        Set<UUID> bankIds = new LinkedHashSet<>();
        for (UUID bankId : centralBank.getBankMetadata().keySet()) {
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
            ListTag areas = metadata.getList(SafetyDepositBoxService.AREAS_KEY, Tag.TAG_COMPOUND);
            for (Tag raw : areas) {
                if (raw instanceof CompoundTag area && contains(area, dimension, pos)) {
                    bankIds.add(bankId);
                    break;
                }
            }
        }
        return bankIds;
    }

    private static boolean contains(CompoundTag area, String dimension, BlockPos pos) {
        SafeBlockBounds bounds = new SafeBlockBounds(
                area.getString("dimension"),
                area.getInt("minX"),
                area.getInt("minY"),
                area.getInt("minZ"),
                area.getInt("maxX"),
                area.getInt("maxY"),
                area.getInt("maxZ")
        );
        return bounds.contains(dimension, pos.getX(), pos.getY(), pos.getZ());
    }
}
