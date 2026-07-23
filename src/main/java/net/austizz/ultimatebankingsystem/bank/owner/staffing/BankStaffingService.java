package net.austizz.ultimatebankingsystem.bank.owner.staffing;

import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BankStaffingService {
    public static final String EMPLOYEES_KEY = "employees";
    public static final String SAFE_ACCESS_KEY = "bankEmployeeSafeAccess";
    private static final UUID CENTRAL_BANK_ID = new UUID(0L, 0L);

    private BankStaffingService() {
    }

    public static BankStaffingRoster readRoster(MinecraftServer server,
                                                CompoundTag metadata,
                                                UUID bankId) {
        CompoundTag safeMetadata = metadata == null ? new CompoundTag() : metadata;
        List<PlayerEmployeeSummary> employees = decodeEmployees(safeMetadata.getString(EMPLOYEES_KEY))
                .entrySet()
                .stream()
                .map(entry -> summary(server, safeMetadata, entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(PlayerEmployeeSummary::resolvedName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(summary -> summary.playerId().toString()))
                .toList();
        return new BankStaffingRoster(employees, collectLoadedBankTellers(server, bankId));
    }

    public static BankStaffingRoster readRoster(CompoundTag metadata) {
        return readRoster(null, metadata, null);
    }

    public static boolean hasEmployee(CompoundTag metadata, UUID employeeId) {
        return metadata != null && employeeId != null
                && decodeEmployees(metadata.getString(EMPLOYEES_KEY)).containsKey(employeeId);
    }

    public static boolean hasExplicitSafeAccess(CompoundTag metadata, UUID employeeId) {
        if (metadata == null || employeeId == null) {
            return false;
        }
        ListTag entries = metadata.getList(SAFE_ACCESS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("playerId")
                    && employeeId.equals(entry.getUUID("playerId"))
                    && entry.getBoolean("safeAccess")) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasEligibleSafeAccessEmployee(CompoundTag metadata) {
        if (metadata == null) {
            return false;
        }
        Map<UUID, EmployeeSpec> employees = decodeEmployees(metadata.getString(EMPLOYEES_KEY));
        if (employees.isEmpty()) {
            return false;
        }
        ListTag entries = metadata.getList(SAFE_ACCESS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            if (entry.hasUUID("playerId")
                    && entry.getBoolean("safeAccess")
                    && employees.containsKey(entry.getUUID("playerId"))) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasEligibleSafeAccessEmployee(MinecraftServer server,
                                                        CompoundTag metadata,
                                                        UUID bankId) {
        if (hasEligibleSafeAccessEmployee(metadata)) {
            return true;
        }
        if (server == null || server.getPlayerList() == null) {
            return false;
        }
        boolean operatorAvailable = server.getPlayerList().getPlayers().stream()
                .anyMatch(player -> player != null && player.hasPermissions(3))
                || server.getPlayerList().getOps().getEntries().stream()
                .anyMatch(entry -> entry != null && entry.getLevel() >= 3);
        return centralBankOperatorEligible(bankId, operatorAvailable);
    }

    static boolean centralBankOperatorEligible(UUID bankId, boolean operatorAvailable) {
        return operatorAvailable && CENTRAL_BANK_ID.equals(bankId);
    }

    public static boolean grantSafeAccess(CompoundTag metadata, UUID employeeId) {
        if (metadata == null || employeeId == null || !hasEmployee(metadata, employeeId)) {
            return false;
        }
        if (hasExplicitSafeAccess(metadata, employeeId)) {
            return false;
        }
        ListTag entries = metadata.getList(SAFE_ACCESS_KEY, Tag.TAG_COMPOUND);
        ListTag next = withoutEmployee(entries, employeeId);
        CompoundTag entry = new CompoundTag();
        entry.putUUID("playerId", employeeId);
        entry.putBoolean("safeAccess", true);
        entry.putLong("updatedAtMillis", System.currentTimeMillis());
        next.add(entry);
        metadata.put(SAFE_ACCESS_KEY, next);
        return true;
    }

    public static boolean revokeSafeAccess(CompoundTag metadata, UUID employeeId) {
        if (metadata == null || employeeId == null) {
            return false;
        }
        ListTag entries = metadata.getList(SAFE_ACCESS_KEY, Tag.TAG_COMPOUND);
        ListTag next = withoutEmployee(entries, employeeId);
        if (next.size() == entries.size()) {
            return false;
        }
        if (next.isEmpty()) {
            metadata.remove(SAFE_ACCESS_KEY);
        } else {
            metadata.put(SAFE_ACCESS_KEY, next);
        }
        return true;
    }

    public static boolean removeEmployee(CompoundTag metadata, UUID employeeId) {
        if (metadata == null || employeeId == null) {
            return false;
        }
        String encoded = metadata.getString(EMPLOYEES_KEY);
        boolean removed = decodeEmployees(encoded).containsKey(employeeId);
        if (removed) {
            metadata.putString(EMPLOYEES_KEY, withoutEmployee(encoded, employeeId));
        }
        revokeSafeAccess(metadata, employeeId);
        return removed;
    }

    public static boolean canAccessProtectedSafeArea(boolean canManageSafeArea,
                                                     CompoundTag metadata,
                                                     UUID playerId) {
        return canManageSafeArea || hasEmployee(metadata, playerId) && hasExplicitSafeAccess(metadata, playerId);
    }

    private static PlayerEmployeeSummary summary(MinecraftServer server,
                                                 CompoundTag metadata,
                                                 UUID employeeId,
                                                 EmployeeSpec spec) {
        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(employeeId);
        return new PlayerEmployeeSummary(
                employeeId,
                resolvePlayerName(server, online, employeeId),
                spec.role(),
                spec.salary(),
                online != null,
                hasExplicitSafeAccess(metadata, employeeId)
        );
    }

    private static List<BankTellerSummary> collectLoadedBankTellers(MinecraftServer server, UUID bankId) {
        if (server == null || bankId == null) {
            return List.of();
        }
        List<BankTellerSummary> tellers = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof BankTellerEntity teller) || teller.isCashier()) {
                    continue;
                }
                if (!bankId.equals(teller.getBoundBankId())) {
                    continue;
                }
                tellers.add(new BankTellerSummary(
                        teller.getUUID(),
                        teller.getCustomName() == null ? "" : teller.getCustomName().getString(),
                        teller.getVariant(),
                        dimension,
                        teller.getX(),
                        teller.getY(),
                        teller.getZ(),
                        !teller.isRemoved(),
                        true
                ));
            }
        }
        tellers.sort(Comparator.comparing(BankTellerSummary::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(summary -> summary.entityId().toString()));
        return tellers;
    }

    private static ListTag withoutEmployee(ListTag entries, UUID employeeId) {
        ListTag next = new ListTag();
        if (entries == null || entries.isEmpty()) {
            return next;
        }
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("playerId") && employeeId.equals(entry.getUUID("playerId"))) {
                continue;
            }
            next.add(entry.copy());
        }
        return next;
    }

    private static String withoutEmployee(String encoded, UUID employeeId) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }
        List<String> retained = new ArrayList<>();
        for (String entry : encoded.split(";", -1)) {
            String[] uuidAndRest = entry.trim().split("=", 2);
            try {
                if (uuidAndRest.length > 1 && employeeId.equals(UUID.fromString(uuidAndRest[0].trim()))) {
                    continue;
                }
            } catch (IllegalArgumentException ignored) {
            }
            retained.add(entry);
        }
        return String.join(";", retained);
    }

    private static Map<UUID, EmployeeSpec> decodeEmployees(String encoded) {
        Map<UUID, EmployeeSpec> result = new LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        for (String entry : encoded.split(";")) {
            String raw = entry.trim();
            if (raw.isBlank() || !raw.contains("=") || !raw.contains(":")) {
                continue;
            }
            String[] uuidAndRest = raw.split("=", 2);
            String[] roleAndSalary = uuidAndRest[1].split(":", 2);
            if (roleAndSalary.length < 2) {
                continue;
            }
            try {
                UUID id = UUID.fromString(uuidAndRest[0].trim());
                String role = roleAndSalary[0].trim().toUpperCase(Locale.ROOT);
                BigDecimal salary = new BigDecimal(roleAndSalary[1].trim());
                if (role.isBlank() || salary.compareTo(BigDecimal.ZERO) < 0) {
                    continue;
                }
                result.put(id, new EmployeeSpec(role, salary));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    private static String resolvePlayerName(MinecraftServer server, ServerPlayer online, UUID playerId) {
        if (online != null) {
            return online.getName().getString();
        }
        if (server != null && server.getProfileCache() != null) {
            var cached = server.getProfileCache().get(playerId);
            if (cached.isPresent() && cached.get().getName() != null && !cached.get().getName().isBlank()) {
                return cached.get().getName();
            }
        }
        String raw = playerId.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }

    private record EmployeeSpec(String role, BigDecimal salary) {
    }
}
