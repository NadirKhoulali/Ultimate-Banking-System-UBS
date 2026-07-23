package net.austizz.ultimatebankingsystem.api.bank;

import net.austizz.ultimatebankingsystem.api.ApiBlockPosition;
import net.austizz.ultimatebankingsystem.api.ApiManagementResult;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;
import net.austizz.ultimatebankingsystem.api.internal.ApiInternals;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;
import net.austizz.ultimatebankingsystem.bank.owner.staffing.BankStaffingRoster;
import net.austizz.ultimatebankingsystem.bank.owner.staffing.BankStaffingService;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver;
import net.austizz.ultimatebankingsystem.heist.HeistService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApiStatus.Internal
public final class UltimateBankManagementApiImpl implements UltimateBankManagementApi {
    @Override
    public boolean isAvailable() {
        return ApiInternals.centralBank() != null;
    }

    @Override
    public List<ApiBankManagementSnapshot> getBanks() {
        CentralBank centralBank = ApiInternals.centralBank();
        if (centralBank == null) return List.of();
        List<ApiBankManagementSnapshot> banks = centralBank.getBanks().values().stream()
                .filter(java.util.Objects::nonNull)
                .map(bank -> snapshot(centralBank, bank))
                .sorted(Comparator.comparing(ApiBankManagementSnapshot::centralBank).reversed()
                        .thenComparing(ApiBankManagementSnapshot::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return List.copyOf(banks);
    }

    @Override
    public Optional<ApiBankManagementSnapshot> getBank(UUID bankId) {
        if (bankId == null) return Optional.empty();
        return getBanks().stream().filter(bank -> bankId.equals(bank.bankId())).findFirst();
    }

    @Override
    public Optional<ApiBankManagementSnapshot> findBank(String nameOrId) {
        String query = nameOrId == null ? "" : nameOrId.trim();
        if (query.isEmpty()) return Optional.empty();
        try {
            Optional<ApiBankManagementSnapshot> byId = getBank(UUID.fromString(query));
            if (byId.isPresent()) return byId;
        } catch (IllegalArgumentException ignored) {
        }
        return getBanks().stream().filter(bank -> bank.name().equalsIgnoreCase(query)).findFirst();
    }

    @Override
    public List<ApiBankManagementSnapshot> getOwnedBanks(UUID playerId) {
        if (playerId == null) return List.of();
        return getBanks().stream().filter(bank -> playerId.equals(bank.ownerId())).toList();
    }

    @Override
    public List<ApiBankManagementSnapshot> getAccessibleBanks(UUID playerId) {
        if (playerId == null) return List.of();
        return getBanks().stream().filter(bank -> playerCanAccessBank(playerId, bank.bankId())).toList();
    }

    @Override
    public boolean playerOwnsBank(UUID playerId, UUID bankId) {
        return playerId != null && getBank(bankId).map(bank -> playerId.equals(bank.ownerId())).orElse(false);
    }

    @Override
    public boolean playerOwnsAnyBank(UUID playerId) {
        return !getOwnedBanks(playerId).isEmpty();
    }

    @Override
    public boolean playerCanAccessBank(UUID playerId, UUID bankId) {
        CentralBank centralBank = ApiInternals.centralBank();
        if (centralBank == null || playerId == null || bankId == null) return false;
        ServerPlayer player = ApiInternals.onlinePlayer(playerId);
        boolean operator = player != null && player.hasPermissions(3);
        return BankOwnerPcService.canAccessBank(centralBank, playerId, bankId, operator);
    }

    @Override
    public boolean playerCanManageSafeArea(UUID playerId, UUID bankId) {
        CentralBank centralBank = ApiInternals.centralBank();
        if (centralBank == null || playerId == null || bankId == null) return false;
        ServerPlayer player = ApiInternals.onlinePlayer(playerId);
        return player != null ? SafetyDepositBoxService.canManageSafeArea(centralBank, player, bankId)
                : SafetyDepositBoxService.canManageSafeArea(centralBank, playerId, bankId);
    }

    @Override
    public boolean playerCanAccessProtectedSafeArea(UUID playerId, UUID bankId) {
        CentralBank centralBank = ApiInternals.centralBank();
        if (centralBank == null || playerId == null || bankId == null) return false;
        ServerPlayer player = ApiInternals.onlinePlayer(playerId);
        return player != null ? SafetyDepositBoxService.canAccessProtectedSafeArea(centralBank, player, bankId)
                : SafetyDepositBoxService.canAccessProtectedSafeArea(centralBank, playerId, bankId);
    }

    @Override
    public boolean isBankUnderAttack(UUID bankId) {
        MinecraftServer server = ApiInternals.server();
        return server != null && bankId != null && HeistService.isBankFrozen(server, bankId);
    }

    @Override
    public ApiBankStaffingSnapshot getStaffing(UUID bankId) {
        MinecraftServer server = ApiInternals.server();
        CentralBank centralBank = ApiInternals.centralBank();
        if (centralBank == null || bankId == null || centralBank.getBank(bankId) == null) {
            return new ApiBankStaffingSnapshot(bankId, List.of(), List.of());
        }
        BankStaffingRoster roster = BankStaffingService.readRoster(server, centralBank.readBankMetadata(bankId), bankId);
        List<ApiBankEmployeeSnapshot> employees = roster.playerEmployees().stream()
                .map(employee -> new ApiBankEmployeeSnapshot(employee.playerId(), employee.resolvedName(),
                        employee.role(), employee.salary(), employee.online(), employee.safeAccessGranted()))
                .toList();
        List<ApiBankTellerSnapshot> tellers = roster.bankTellers().stream()
                .map(teller -> new ApiBankTellerSnapshot(teller.entityId(), teller.displayName(), teller.variant(),
                        new ApiBlockPosition(teller.dimension(), floor(teller.x()), floor(teller.y()), floor(teller.z())),
                        teller.active(), teller.bound()))
                .toList();
        return new ApiBankStaffingSnapshot(bankId, employees, tellers);
    }

    @Override
    public ApiSafeDepositSetupSnapshot getSafeDepositSetup(UUID bankId) {
        MinecraftServer server = ApiInternals.server();
        CentralBank centralBank = ApiInternals.centralBank();
        if (centralBank == null || bankId == null || centralBank.getBank(bankId) == null) {
            return new ApiSafeDepositSetupSnapshot(bankId, 0, false, 0, 0, 0, 0, List.of(), List.of());
        }
        CompoundTag metadata = centralBank.readBankMetadata(bankId);
        SafeDepositSetupSnapshot setup = SafetyDepositBoxService.safeDepositSetupSnapshot(metadata);
        List<SafeVaultReadinessResolver.RowReadiness> readiness = server == null
                ? List.of() : SafetyDepositBoxService.safeDepositVaultReadiness(server, metadata);
        Map<String, SafeVaultReadinessResolver.RowReadiness> byVault = new LinkedHashMap<>();
        for (SafeVaultReadinessResolver.RowReadiness row : readiness) {
            if (row != null && row.vault() != null) byVault.put(row.vault().id(), row);
        }
        Set<String> missing = new LinkedHashSet<>();
        readiness.forEach(row -> {
            if (row != null) missing.addAll(row.humanMissingReasons());
        });
        List<ApiBankPremiseSnapshot> premises = new ArrayList<>();
        int safeAreas = 0;
        int vaults = 0;
        int ready = 0;
        for (SafePremiseSnapshot premise : setup.premises()) {
            int premiseVaults = premise.safeAreas().stream().mapToInt(area -> area.vaults().size()).sum();
            int premiseReady = premise.safeAreas().stream().flatMap(area -> area.vaults().stream())
                    .map(vault -> byVault.get(vault.id()))
                    .mapToInt(row -> row != null && row.summary() != null && row.summary().ready() ? 1 : 0).sum();
            safeAreas += premise.safeAreas().size();
            vaults += premiseVaults;
            ready += premiseReady;
            premises.add(new ApiBankPremiseSnapshot(bankId, premise.id(), premise.mode().name(),
                    ApiInternals.bounds(premise.bounds()), ApiInternals.position(premise.exit()),
                    premise.exit() == null ? 0.0F : premise.exit().yaw(), premise.safeAreas().size(),
                    premiseVaults, premiseReady));
        }
        boolean enabled = vaults > 0 && ready == vaults;
        return new ApiSafeDepositSetupSnapshot(bankId, setup.version(), enabled, premises.size(), safeAreas,
                vaults, ready, List.copyOf(missing), premises);
    }

    @Override
    public ApiManagementResult setEmployeeSafeAccess(UUID actorId, UUID bankId, UUID employeeId, boolean allowed) {
        MinecraftServer server = ApiInternals.server();
        CentralBank centralBank = ApiInternals.centralBank();
        if (!ApiInternals.canMutate(server) || centralBank == null) {
            return ApiManagementResult.fail("Bank mutations must run on the server thread.");
        }
        if (!playerCanManageSafeArea(actorId, bankId)) {
            return ApiManagementResult.fail("Actor is not allowed to manage this bank's safe area.");
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        if (!BankStaffingService.hasEmployee(metadata, employeeId)) {
            return ApiManagementResult.fail("Target player is not a current bank employee.");
        }
        boolean changed = allowed ? BankStaffingService.grantSafeAccess(metadata, employeeId)
                : BankStaffingService.revokeSafeAccess(metadata, employeeId);
        if (!changed) return ApiManagementResult.ok("Safe access is already " + (allowed ? "enabled" : "disabled") + ".");
        centralBank.putBankMetadata(bankId, metadata);
        BankManager.markDirty();
        return ApiManagementResult.ok("Employee safe access " + (allowed ? "enabled" : "disabled") + ".");
    }

    @Override
    public ApiManagementResult setInterestRate(UUID actorId, UUID bankId, double annualPercent) {
        MinecraftServer server = ApiInternals.server();
        CentralBank centralBank = ApiInternals.centralBank();
        if (!ApiInternals.canMutate(server) || centralBank == null) {
            return ApiManagementResult.fail("Bank mutations must run on the server thread.");
        }
        ServerPlayer actor = ApiInternals.onlinePlayer(actorId);
        boolean operator = actor != null && actor.hasPermissions(3);
        if (!operator && !BankOwnerPcService.isOwner(centralBank, actorId, bankId)) {
            return ApiManagementResult.fail("Only the bank owner or an operator can change the interest rate.");
        }
        if (!Double.isFinite(annualPercent) || annualPercent < 0.0D) {
            return ApiManagementResult.fail("Interest rate must be a finite non-negative percentage.");
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) return ApiManagementResult.fail("Bank not found.");
        bank.setInterestRate(annualPercent);
        return ApiManagementResult.ok("Interest rate set to " + bank.getInterestRate() + "%.");
    }

    private ApiBankManagementSnapshot snapshot(CentralBank centralBank, Bank bank) {
        ApiSafeDepositSetupSnapshot setup = getSafeDepositSetup(bank.getBankId());
        ApiBankStaffingSnapshot staffing = getStaffing(bank.getBankId());
        return new ApiBankManagementSnapshot(bank.getBankId(), bank.getBankName(), bank.getBankOwnerId(),
                bank.getBankId().equals(centralBank.getBankId()),
                UltimateBankingApiProvider.get().getBankStatus(bank.getBankId()), bank.getBankAccounts().size(),
                bank.getTotalDeposits(), bank.getDeclaredReserve(), bank.getMinimumRequiredReserve(),
                bank.getReserveRatio(), bank.getOutstandingLoanBalance(), bank.getMaxLendableAmount(),
                bank.getInterestRate(), setup.premiseCount(), setup.safeAreaCount(), setup.vaultCount(),
                setup.readyVaultCount(), staffing.employees().size(), staffing.tellers().size(),
                isBankUnderAttack(bank.getBankId()));
    }

    private int floor(double value) {
        return (int) Math.floor(value);
    }
}
