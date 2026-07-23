package net.austizz.ultimatebankingsystem.heist;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class HeistDrillGeometry {
    public static final double VAULT_DOOR_FACE_OFFSET = 0.20D;

    private static final double VAULT_RENDER_Y = 1.12D;
    private static final double VAULT_TIP_MODEL_Y = 0.49D;
    // Slightly behind the mesh extremity so particles remain visible outside the door surface.
    private static final double VAULT_VISIBLE_TIP_MODEL_Z = 1.43D;

    private HeistDrillGeometry() {}

    public static Vec3 vaultVisibleTip(BlockPos masterPos, Direction facing) {
        if (masterPos == null) return Vec3.ZERO;
        Direction horizontal = facing == null || !facing.getAxis().isHorizontal()
                ? Direction.NORTH : facing;
        double localZ = VAULT_DOOR_FACE_OFFSET + VAULT_VISIBLE_TIP_MODEL_Z;
        double angle = Math.toRadians(rotationFor(horizontal));
        double rotatedX = Math.sin(angle) * localZ;
        double rotatedZ = Math.cos(angle) * localZ;
        return new Vec3(
                masterPos.getX() + 0.5D + rotatedX,
                masterPos.getY() + VAULT_RENDER_Y + VAULT_TIP_MODEL_Y,
                masterPos.getZ() + 0.5D + rotatedZ
        );
    }

    public static float rotationFor(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0F;
            case EAST -> -90.0F;
            case WEST -> 90.0F;
            case NORTH, UP, DOWN -> 0.0F;
        };
    }
}
