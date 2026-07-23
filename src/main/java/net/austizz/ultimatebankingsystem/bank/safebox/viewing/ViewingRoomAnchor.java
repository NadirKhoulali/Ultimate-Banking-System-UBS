package net.austizz.ultimatebankingsystem.bank.safebox.viewing;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.minecraft.core.BlockPos;

public record ViewingRoomAnchor(String dimension,
                                double x,
                                double y,
                                double z,
                                float yaw,
                                float pitch) {
    public ViewingRoomAnchor {
        dimension = SafeBlockBounds.normalizeDimension(dimension);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Viewing-room anchor coordinates must be finite.");
        }
    }

    public BlockPos blockPosition() {
        return BlockPos.containing(x, y, z);
    }

    public boolean inside(SafeBlockBounds bounds) {
        BlockPos pos = blockPosition();
        return bounds != null && bounds.contains(dimension, pos.getX(), pos.getY(), pos.getZ());
    }
}
