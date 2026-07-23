package net.austizz.ultimatebankingsystem.bank.owner;

public final class OwnerPcMutationContextCollector {
    private static final double MAX_DISTANCE_SQUARED = 100.0D;

    public enum BlockKind {
        OWNER_PC,
        OTHER,
        UNAVAILABLE
    }

    public record RememberedPc(String dimensionId,
                               int x,
                               int y,
                               int z,
                               String machineId) {
    }

    public record LoadedPc(boolean levelPresent,
                           boolean chunkLoaded,
                           BlockKind blockKind,
                           String indexedMachineId,
                           boolean poweredOn,
                           boolean sessionUnlocked) {
        public LoadedPc {
            blockKind = blockKind == null ? BlockKind.UNAVAILABLE : blockKind;
            indexedMachineId = indexedMachineId == null ? "" : indexedMachineId.trim();
        }
    }

    public record PlayerLocation(String dimensionId,
                                 double x,
                                 double y,
                                 double z) {
    }

    private OwnerPcMutationContextCollector() {
    }

    public static OwnerPcActionPolicy.MutationContext collect(RememberedPc remembered,
                                                               LoadedPc loadedPc,
                                                               PlayerLocation player) {
        if (remembered == null) {
            return unavailable();
        }
        LoadedPc pc = loadedPc == null
                ? new LoadedPc(false, false, BlockKind.UNAVAILABLE, "", false, false)
                : loadedPc;
        boolean levelLoaded = pc.levelPresent() && pc.chunkLoaded();
        boolean ownerPcBlock = levelLoaded && pc.blockKind() == BlockKind.OWNER_PC;
        boolean machineMatches = ownerPcBlock
                && !blank(remembered.machineId())
                && remembered.machineId().trim().equalsIgnoreCase(pc.indexedMachineId());
        boolean sameDimension = pc.levelPresent()
                && player != null
                && sameDimension(remembered.dimensionId(), player.dimensionId());
        boolean withinRange = sameDimension
                && distanceSquared(remembered, player) <= MAX_DISTANCE_SQUARED;
        boolean poweredOn = machineMatches && withinRange && pc.poweredOn();
        boolean sessionUnlocked = poweredOn && pc.sessionUnlocked();
        return new OwnerPcActionPolicy.MutationContext(true, levelLoaded, ownerPcBlock,
                machineMatches, sameDimension, withinRange, poweredOn, sessionUnlocked);
    }

    private static OwnerPcActionPolicy.MutationContext unavailable() {
        return new OwnerPcActionPolicy.MutationContext(false, false, false, false,
                false, false, false, false);
    }

    private static double distanceSquared(RememberedPc remembered, PlayerLocation player) {
        double dx = player.x() - (remembered.x() + 0.5D);
        double dy = player.y() - (remembered.y() + 0.5D);
        double dz = player.z() - (remembered.z() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean sameDimension(String left, String right) {
        return !blank(left) && !blank(right) && left.trim().equalsIgnoreCase(right.trim());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
