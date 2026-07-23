package net.austizz.ultimatebankingsystem.bank.owner;

import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class OwnerPcSafePolicySnapshot {
    private OwnerPcSafePolicySnapshot() {
    }

    public static List<String> rows(MinecraftServer server, CompoundTag metadata) {
        CompoundTag safeMetadata = metadata == null ? new CompoundTag() : metadata;
        Map<SafetyDepositBoxRowBlockEntity.ModuleType, int[]> counts = new EnumMap<>(
                SafetyDepositBoxRowBlockEntity.ModuleType.class);
        for (SafetyDepositBoxRowBlockEntity.ModuleType type : SafetyDepositBoxService.assignableModuleTypes()) {
            counts.put(type, new int[]{0, 0, 0});
        }
        Set<String> assigned = assignmentKeys(
                safeMetadata.getList(SafetyDepositBoxService.ASSIGNMENTS_KEY, Tag.TAG_COMPOUND));
        ListTag areas = safeMetadata.getList(SafetyDepositBoxService.AREAS_KEY, Tag.TAG_COMPOUND);
        for (SafetyDepositBoxService.LoadedSafeRow loaded :
                SafetyDepositBoxService.collectLoadedSafeRows(server, areas)) {
            countRow(loaded, assigned, counts);
        }

        List<String> rows = new ArrayList<>();
        for (SafetyDepositBoxRowBlockEntity.ModuleType type : SafetyDepositBoxService.assignableModuleTypes()) {
            SafetyDepositBoxService.PricingPolicy policy = SafetyDepositBoxService.pricingPolicy(safeMetadata, type);
            int[] values = counts.get(type);
            rows.add("@safe_policy="
                    + field(type.name())
                    + "|" + field(SafetyDepositBoxService.shortModuleLabel(type))
                    + "|" + field(policy.mode())
                    + "|" + policy.amount().max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN).toPlainString()
                    + "|" + Math.max(1L, policy.rentPeriodTicks())
                    + "|" + Math.max(1L, policy.overdueTicks())
                    + "|" + Math.max(0, values[0])
                    + "|" + Math.max(0, values[1])
                    + "|" + Math.max(0, values[2]));
        }
        return List.copyOf(rows);
    }

    private static void countRow(SafetyDepositBoxService.LoadedSafeRow loaded,
                                 Set<String> assigned,
                                 Map<SafetyDepositBoxRowBlockEntity.ModuleType, int[]> counts) {
        if (loaded == null || loaded.row() == null || loaded.pos() == null) {
            return;
        }
        for (int door = 0; door < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; door++) {
            if (!loaded.row().isAssignableBoxStart(door)) {
                continue;
            }
            SafetyDepositBoxRowBlockEntity.ModuleType type = loaded.row().getModuleType(door);
            int[] values = counts.get(type);
            if (values == null) {
                continue;
            }
            values[0]++;
            boolean occupied = loaded.row().getAssignedAccountId(door) != null
                    || assigned.contains(key(loaded.dimension(), loaded.pos(), door));
            values[occupied ? 1 : 2]++;
        }
    }

    private static Set<String> assignmentKeys(ListTag assignments) {
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < assignments.size(); index++) {
            CompoundTag assignment = assignments.getCompound(index);
            keys.add(key(assignment.getString("dimension"),
                    new BlockPos(assignment.getInt("x"), assignment.getInt("y"), assignment.getInt("z")),
                    assignment.getInt("doorIndex")));
        }
        return keys;
    }

    private static String key(String dimension, BlockPos pos, int door) {
        return dimension(dimension) + "|" + pos.getX() + "|" + pos.getY() + "|"
                + pos.getZ() + "|" + Math.max(0, door);
    }

    private static String dimension(String value) {
        return value == null || value.isBlank()
                ? "minecraft:overworld"
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String field(String value) {
        return value == null || value.isBlank() ? "" : value.replace('|', '/').trim();
    }
}
