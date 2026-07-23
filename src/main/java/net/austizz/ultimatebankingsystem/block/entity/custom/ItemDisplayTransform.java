package net.austizz.ultimatebankingsystem.block.entity.custom;

public record ItemDisplayTransform(
        float offsetX,
        float offsetY,
        float offsetZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float scaleX,
        float scaleY,
        float scaleZ
) {
    public static final ItemDisplayTransform DEFAULT = new ItemDisplayTransform(
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            1.0F, 1.0F, 1.0F
    );
}
