package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public record OwnerPcPremisePayload(
        String premiseId,
        String dimension,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        String exitDimension,
        int exitX,
        int exitY,
        int exitZ,
        float exitYaw,
        SafePremiseMode mode,
        Status status,
        int safeAreaCount,
        int vaultCount,
        int readyVaultCount,
        int viewingRoomCount,
        int readyViewingRoomCount,
        boolean migrationBacked,
        List<DeleteBlocker> deleteBlockers
) {
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_DIMENSION_LENGTH = 128;
    private static final int MAX_DELETE_BLOCKERS = DeleteBlocker.values().length;

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcPremisePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.premiseId(), MAX_ID_LENGTH);
                        buf.writeUtf(payload.dimension(), MAX_DIMENSION_LENGTH);
                        buf.writeInt(payload.minX());
                        buf.writeInt(payload.minY());
                        buf.writeInt(payload.minZ());
                        buf.writeInt(payload.maxX());
                        buf.writeInt(payload.maxY());
                        buf.writeInt(payload.maxZ());
                        buf.writeUtf(payload.exitDimension(), MAX_DIMENSION_LENGTH);
                        buf.writeInt(payload.exitX());
                        buf.writeInt(payload.exitY());
                        buf.writeInt(payload.exitZ());
                        buf.writeFloat(payload.exitYaw());
                        buf.writeVarInt(payload.mode().ordinal());
                        buf.writeVarInt(payload.status().ordinal());
                        buf.writeVarInt(payload.safeAreaCount());
                        buf.writeVarInt(payload.vaultCount());
                        buf.writeVarInt(payload.readyVaultCount());
                        buf.writeVarInt(payload.viewingRoomCount());
                        buf.writeVarInt(payload.readyViewingRoomCount());
                        buf.writeBoolean(payload.migrationBacked());
                        buf.writeVarInt(payload.deleteBlockers().size());
                        for (DeleteBlocker blocker : payload.deleteBlockers()) {
                            buf.writeVarInt(blocker.ordinal());
                        }
                    },
                    buf -> {
                        String premiseId = buf.readUtf(MAX_ID_LENGTH);
                        String dimension = buf.readUtf(MAX_DIMENSION_LENGTH);
                        int minX = buf.readInt();
                        int minY = buf.readInt();
                        int minZ = buf.readInt();
                        int maxX = buf.readInt();
                        int maxY = buf.readInt();
                        int maxZ = buf.readInt();
                        String exitDimension = buf.readUtf(MAX_DIMENSION_LENGTH);
                        int exitX = buf.readInt();
                        int exitY = buf.readInt();
                        int exitZ = buf.readInt();
                        float exitYaw = buf.readFloat();
                        SafePremiseMode mode = enumValue(
                                SafePremiseMode.values(), buf.readVarInt(), "premise mode");
                        Status status = enumValue(Status.values(), buf.readVarInt(), "premise status");
                        int safeAreaCount = buf.readVarInt();
                        int vaultCount = buf.readVarInt();
                        int readyVaultCount = buf.readVarInt();
                        int viewingRoomCount = buf.readVarInt();
                        int readyViewingRoomCount = buf.readVarInt();
                        boolean migrationBacked = buf.readBoolean();
                        int blockerCount = buf.readVarInt();
                        if (blockerCount < 0 || blockerCount > MAX_DELETE_BLOCKERS) {
                            throw new IllegalArgumentException("Invalid premise delete blocker count: " + blockerCount);
                        }
                        List<DeleteBlocker> blockers = new ArrayList<>(blockerCount);
                        for (int index = 0; index < blockerCount; index++) {
                            blockers.add(enumValue(
                                    DeleteBlocker.values(), buf.readVarInt(), "premise delete blocker"));
                        }
                        return new OwnerPcPremisePayload(
                                premiseId, dimension,
                                minX, minY, minZ, maxX, maxY, maxZ,
                                exitDimension, exitX, exitY, exitZ, exitYaw,
                                mode, status, safeAreaCount, vaultCount, readyVaultCount,
                                viewingRoomCount, readyViewingRoomCount,
                                migrationBacked, blockers);
                    }
            );

    public OwnerPcPremisePayload {
        premiseId = requireText(premiseId, "premiseId", MAX_ID_LENGTH);
        dimension = requireText(dimension, "dimension", MAX_DIMENSION_LENGTH);
        exitDimension = requireText(exitDimension, "exitDimension", MAX_DIMENSION_LENGTH);
        mode = Objects.requireNonNull(mode, "mode");
        status = Objects.requireNonNull(status, "status");
        if (safeAreaCount < 0 || vaultCount < 0 || readyVaultCount < 0
                || readyVaultCount > vaultCount || viewingRoomCount < 0
                || readyViewingRoomCount < 0 || readyViewingRoomCount > viewingRoomCount) {
            throw new IllegalArgumentException("Premise counts must be non-negative and internally consistent.");
        }
        EnumSet<DeleteBlocker> supplied = EnumSet.noneOf(DeleteBlocker.class);
        if (deleteBlockers != null) {
            for (DeleteBlocker blocker : deleteBlockers) {
                if (blocker != null) {
                    supplied.add(blocker);
                }
            }
        }
        deleteBlockers = List.of(DeleteBlocker.values()).stream()
                .filter(supplied::contains)
                .toList();
    }

    public boolean canDelete() {
        return deleteBlockers.isEmpty();
    }

    private static String requireText(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must contain 1-" + maxLength + " characters.");
        }
        return normalized;
    }

    private static <E extends Enum<E>> E enumValue(E[] values, int ordinal, String field) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid " + field + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    public enum Status {
        NOT_READY,
        READY
    }

    public enum DeleteBlocker {
        NON_EMPTY,
        MIGRATION_BACKED,
        ASSIGNED,
        ROUTED,
        ACTIVE
    }
}
