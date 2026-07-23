package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeAreaSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupNbtCodec;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Resolves physical safe claims and writes consistent door/storage audit events. */
public final class SafeAccessAuditService {
    private SafeAccessAuditService() {
    }

    public static void recordDoorTransition(ServerLevel level,
                                            BlockPos pos,
                                            String doorLabel,
                                            boolean open) {
        if (level == null || pos == null) return;
        CentralBank centralBank = BankManager.getCentralBank(level.getServer());
        if (centralBank == null) return;
        for (UUID bankId : bankIdsAt(centralBank, level, pos, 4)) {
            SafeAccessLogService.recordSystem(centralBank, bankId,
                    SafeAccessLogService.CATEGORY_SECURITY,
                    SafeAccessLogService.OUTCOME_INFO,
                    open ? "DOOR_OPENED" : "DOOR_CLOSED",
                    doorLabel,
                    doorLabel + " reached its fully " + (open ? "open" : "closed") + " position.",
                    level.dimension().location().toString(), pos);
        }
    }

    public static void recordStorageRemoval(ServerLevel level,
                                            BlockPos pos,
                                            ServerPlayer actor,
                                            String storageLabel,
                                            ItemStack removed) {
        if (level == null || pos == null || removed == null || removed.isEmpty()) return;
        recordStorageRemoval(level, pos, actor, storageLabel,
                removed.getHoverName().getString(), removed.getCount());
    }

    public static void recordStorageRemoval(ServerLevel level,
                                            BlockPos pos,
                                            ServerPlayer actor,
                                            String storageLabel,
                                            String itemName,
                                            int count) {
        if (level == null || pos == null || count <= 0) return;
        CentralBank centralBank = BankManager.getCentralBank(level.getServer());
        if (centralBank == null) return;
        String safeStorageLabel = storageLabel == null || storageLabel.isBlank() ? "Vault Storage" : storageLabel;
        for (UUID bankId : bankIdsAt(centralBank, level, pos, 0)) {
            SafeAccessLogService.record(centralBank, bankId, actor,
                    SafeAccessLogService.CATEGORY_STORAGE,
                    SafeAccessLogService.OUTCOME_SUCCESS,
                    "STORAGE_ITEMS_REMOVED",
                    safeStorageLabel,
                    "Removed " + count + "x " + (itemName == null || itemName.isBlank() ? "item" : itemName) + ".",
                    level.dimension().location().toString(), pos);
        }
    }

    public static boolean isInsideSafeClaim(ServerLevel level, BlockPos pos) {
        CentralBank centralBank = level == null ? null : BankManager.getCentralBank(level.getServer());
        return centralBank != null && !bankIdsAt(centralBank, level, pos, 0).isEmpty();
    }

    private static List<UUID> bankIdsAt(CentralBank centralBank,
                                        ServerLevel level,
                                        BlockPos pos,
                                        int expansion) {
        if (centralBank == null || level == null || pos == null) return List.of();
        String dimension = level.dimension().location().toString();
        List<UUID> result = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        List<Bank> banks = new ArrayList<>();
        banks.add(centralBank);
        banks.addAll(centralBank.getBanks().values());
        for (Bank bank : banks) {
            if (bank == null || bank.getBankId() == null || !visited.add(bank.getBankId())) continue;
            var setup = SafeDepositSetupNbtCodec.snapshot(centralBank.getOrCreateBankMetadata(bank.getBankId()));
            boolean matched = false;
            for (SafePremiseSnapshot premise : setup.premises()) {
                for (SafeAreaSnapshot area : premise.safeAreas()) {
                    if (contains(area.bounds(), dimension, pos, expansion)) {
                        matched = true;
                        break;
                    }
                }
                if (matched) break;
            }
            if (matched) result.add(bank.getBankId());
        }
        return List.copyOf(result);
    }

    private static boolean contains(SafeBlockBounds bounds,
                                    String dimension,
                                    BlockPos pos,
                                    int expansion) {
        if (bounds == null || !dimension.equals(bounds.dimension())) return false;
        int margin = Math.max(0, expansion);
        return pos.getX() >= bounds.minX() - margin && pos.getX() <= bounds.maxX() + margin
                && pos.getY() >= bounds.minY() - margin && pos.getY() <= bounds.maxY() + margin
                && pos.getZ() >= bounds.minZ() - margin && pos.getZ() <= bounds.maxZ() + margin;
    }
}
