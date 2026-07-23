package net.austizz.ultimatebankingsystem.heist;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupNbtCodec;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.SafeBoxViewingCoordinator;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.BankVaultDoorBlock;
import net.austizz.ultimatebankingsystem.block.custom.CashStackBlock;
import net.austizz.ultimatebankingsystem.block.custom.MoneyStackBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.BankVaultDoorBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.MetalPalletBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.SecureSafeBlockEntity;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.util.RegistryKeysCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HeistEligibilityService {
    private HeistEligibilityService() {}

    public static List<HeistTarget> targets(MinecraftServer server, CentralBank centralBank, HeistSavedData data) {
        if (server == null || centralBank == null) return List.of();
        Map<UUID, Bank> banks = new LinkedHashMap<>();
        banks.put(centralBank.getBankId(), centralBank);
        banks.putAll(centralBank.getBanks());
        List<HeistTarget> targets = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Bank bank : banks.values()) {
            if (bank == null || bank.getBankId() == null) continue;
            var setup = SafeDepositSetupNbtCodec.snapshot(centralBank.getOrCreateBankMetadata(bank.getBankId()));
            for (SafePremiseSnapshot premise : setup.premises()) {
                if (premise == null || premise.bounds() == null) continue;
                targets.add(evaluate(server, centralBank, bank, premise, data, now));
            }
        }
        targets.sort(Comparator.comparing(HeistTarget::eligible).reversed()
                .thenComparing(HeistTarget::bankName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(HeistTarget::premiseId));
        return List.copyOf(targets);
    }

    public static HeistTarget find(MinecraftServer server, CentralBank centralBank, HeistSavedData data,
                                   UUID bankId, String premiseId) {
        if (bankId == null || premiseId == null) return null;
        return targets(server, centralBank, data).stream()
                .filter(target -> target.bankId().equals(bankId) && target.premiseId().equals(premiseId))
                .findFirst().orElse(null);
    }

    private static HeistTarget evaluate(MinecraftServer server,
                                        CentralBank centralBank,
                                        Bank bank,
                                        SafePremiseSnapshot premise,
                                        HeistSavedData data,
                                        long now) {
        List<String> blockers = new ArrayList<>();
        ServerLevel level = level(server, premise.bounds().dimension());
        if (level == null) blockers.add("Premise dimension is unavailable.");
        SafeExitSnapshot exit = premise.exit();
        if (exit == null || !exit.dimension().equals(premise.bounds().dimension())
                || premise.bounds().contains(exit.x(), exit.y(), exit.z())) {
            blockers.add("Outside extraction point is missing or inside the premise.");
        }
        Scan scan = level == null ? Scan.empty() : scan(server, centralBank, bank.getBankId(), premise, level);
        if (scan.ownerPcs.isEmpty()) blockers.add("At least one bank owner PC must be bound inside this premise.");
        if (scan.vaultDoors.isEmpty()) blockers.add("A complete bank vault door is required.");
        if (scan.lootSources <= 0) blockers.add("No physical cash, bullion, pallet stock, or funded deposit box is available.");
        if (SafeBoxViewingCoordinator.activePremiseIds(server).contains(premise.id()) || scan.busyTeller) {
            blockers.add("A teller or private deposit-box viewing session is active.");
        }
        if (data != null && data.bankCooldown(bank.getBankId()) > now) {
            blockers.add("This bank is on heist cooldown.");
        }
        if (data != null && data.sessions().stream().anyMatch(session -> session.phase().isRunning()
                && bank.getBankId().equals(session.bankId()) && premise.id().equals(session.premiseId()))) {
            blockers.add("Another heist is already active at this premise.");
        }
        return new HeistTarget(bank.getBankId(), bank.getBankName(), premise.id(), premise.bounds(), exit,
                premise.bounds().dimension(), scan.ownerPcs.isEmpty() ? null : scan.ownerPcs.getFirst(),
                scan.vaultDoors.isEmpty() ? null : scan.vaultDoors.getFirst(),
                blockers.isEmpty(), blockers, scan.lootSources);
    }

    private static Scan scan(MinecraftServer server, CentralBank centralBank, UUID bankId,
                             SafePremiseSnapshot premise, ServerLevel level) {
        List<BlockPos> pcs = new ArrayList<>();
        List<BlockPos> vaults = new ArrayList<>();
        int loot = 0;
        boolean vaultClosed = true;
        boolean busyTeller = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = premise.bounds().minX(); x <= premise.bounds().maxX(); x++) {
            for (int z = premise.bounds().minZ(); z <= premise.bounds().maxZ(); z++) {
                cursor.set(x, premise.bounds().minY(), z);
                if (!level.hasChunkAt(cursor)) continue;
                for (int y = premise.bounds().minY(); y <= premise.bounds().maxY(); y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(ModBlocks.BANK_OWNER_PC.get())) pcs.add(cursor.immutable());
                    if (state.is(ModBlocks.BANK_VAULT_DOOR.get())) {
                        BlockPos master = BankVaultDoorBlock.getMasterPos(state, cursor);
                        if (master.equals(cursor) && BankVaultDoorBlock.isCompleteMultiblock(level, master)) {
                            vaults.add(master.immutable());
                            if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)) vaultClosed = false;
                            if (level.getBlockEntity(master) instanceof BankVaultDoorBlockEntity door
                                    && door.getCurrentAnimationProgress() > 0.01F) vaultClosed = false;
                        }
                    }
                    if (state.getBlock() instanceof CashStackBlock || state.getBlock() instanceof MoneyStackBlock
                            || state.is(ModBlocks.GOLD_BAR.get()) || state.is(ModBlocks.SILVER_BAR.get())) loot++;
                    if (level.getBlockEntity(cursor) instanceof MetalPalletBlockEntity pallet && hasItems(pallet)) loot++;
                    if (level.getBlockEntity(cursor) instanceof SecureSafeBlockEntity safe && hasItems(safe)) loot++;
                    if (level.getBlockEntity(cursor) instanceof SafetyDepositBoxRowBlockEntity row) {
                        for (int door = 0; door < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; door++) {
                            if (!row.isAssignableBoxStart(door) || !row.isAssigned(door)) continue;
                            AccountHolder account = centralBank.SearchForAccountByAccountId(row.getAssignedAccountId(door));
                            if (account != null && !account.getSafeBoxSlots().isEmpty()) loot++;
                        }
                    }
                }
            }
        }
        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof BankTellerEntity teller) || teller.isCashier()
                    || !bankId.equals(teller.getBoundBankId())
                    || !premise.bounds().contains(teller.blockPosition().getX(), teller.blockPosition().getY(), teller.blockPosition().getZ())) continue;
            for (var player : server.getPlayerList().getPlayers()) {
                if (teller.isCustomerUseHeldBy(player.getUUID())) { busyTeller = true; break; }
            }
            if (busyTeller) break;
        }
        return new Scan(List.copyOf(pcs), List.copyOf(vaults), loot, vaultClosed, busyTeller);
    }

    private static boolean hasItems(MetalPalletBlockEntity pallet) {
        for (int slot = 0; slot < pallet.getItemHandler().getSlots(); slot++) {
            if (!pallet.getItemHandler().getStackInSlot(slot).isEmpty()) return true;
        }
        return false;
    }

    private static boolean hasItems(SecureSafeBlockEntity safe) {
        for (int slot = 0; slot < safe.getItemHandler().getSlots(); slot++) {
            if (safe.isActiveSlot(slot) && !safe.getItemHandler().getStackInSlot(slot).isEmpty()) return true;
        }
        return false;
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) return null;
        ResourceKey<Level> key = RegistryKeysCompat.createValueKey(RegistryKeysCompat.DIMENSION_REGISTRY_KEY, id);
        return server.getLevel(key);
    }

    private record Scan(List<BlockPos> ownerPcs, List<BlockPos> vaultDoors, int lootSources,
                        boolean vaultClosed, boolean busyTeller) {
        static Scan empty() { return new Scan(List.of(), List.of(), 0, false, false); }
    }
}
