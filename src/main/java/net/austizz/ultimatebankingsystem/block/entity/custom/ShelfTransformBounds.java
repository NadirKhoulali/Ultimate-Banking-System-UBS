package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public record ShelfTransformBounds(
        float minOffsetX,
        float maxOffsetX,
        float minOffsetY,
        float maxOffsetY,
        float minOffsetZ,
        float maxOffsetZ,
        float minRotation,
        float maxRotation,
        float minScale,
        float maxScale
) {
    private static final ShelfTransformBounds TALL_WALL = new ShelfTransformBounds(
            -0.26F, 0.26F,
            -0.12F, 0.24F,
            -0.10F, 0.14F,
            -180.0F, 180.0F,
            0.35F, 2.30F
    );
    private static final ShelfTransformBounds GLASS_COUNTER = new ShelfTransformBounds(
            -0.18F, 0.18F,
            -0.12F, 0.14F,
            -0.10F, 0.12F,
            -180.0F, 180.0F,
            0.30F, 1.80F
    );
    private static final ShelfTransformBounds MODULAR_WALL = new ShelfTransformBounds(
            -0.34F, 0.34F,
            -0.18F, 0.22F,
            -0.12F, 0.18F,
            -180.0F, 180.0F,
            0.25F, 1.80F
    );
    private static final ShelfTransformBounds SELLING_TABLE = new ShelfTransformBounds(
            -0.42F, 0.42F,
            -0.20F, 0.55F,
            -0.42F, 0.42F,
            -180.0F, 180.0F,
            0.40F, 3.80F
    );
    private static final ShelfTransformBounds INVISIBLE_DISPLAY = new ShelfTransformBounds(
            -0.42F, 0.42F,
            -1.20F, 0.80F,
            -0.42F, 0.42F,
            -180.0F, 180.0F,
            0.25F, 3.80F
    );

    public static ShelfDisplayType detectType(ShelfDisplayBlockEntity shelf) {
        if (shelf instanceof TallWallShelfBlockEntity) {
            return ShelfDisplayType.TALL_WALL;
        }
        if (shelf instanceof GlassCounterDisplayBlockEntity) {
            return ShelfDisplayType.GLASS_COUNTER;
        }
        if (shelf instanceof ModularWallDisplayBlockEntity) {
            return ShelfDisplayType.MODULAR_WALL;
        }
        if (shelf instanceof ShopSellingTableBlockEntity table) {
            if (isInvisibleDisplayState(table.getBlockState())) {
                return ShelfDisplayType.INVISIBLE_DISPLAY;
            }
            return ShelfDisplayType.SELLING_TABLE;
        }
        return ShelfDisplayType.UNKNOWN;
    }

    public static ShelfTransformBounds forType(ShelfDisplayType type) {
        if (type == null) {
            return TALL_WALL;
        }
        return switch (type) {
            case TALL_WALL -> TALL_WALL;
            case GLASS_COUNTER -> GLASS_COUNTER;
            case MODULAR_WALL -> MODULAR_WALL;
            case SELLING_TABLE -> SELLING_TABLE;
            case INVISIBLE_DISPLAY -> INVISIBLE_DISPLAY;
            case UNKNOWN -> TALL_WALL;
        };
    }

    public static ShelfTransformBounds forTypeId(String typeId) {
        return forType(ShelfDisplayType.fromId(typeId));
    }

    public static ShelfTransformBounds forShelf(ShelfDisplayBlockEntity shelf) {
        return forType(detectType(shelf));
    }

    private static boolean isInvisibleDisplayState(BlockState state) {
        return state != null
                && (state.is(ModBlocks.INVISIBLE_DISPLAY_SMALL.get())
                || state.is(ModBlocks.INVISIBLE_DISPLAY_MEDIUM.get())
                || state.is(ModBlocks.INVISIBLE_DISPLAY_LARGE.get())
                || state.is(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_SMALL.get())
                || state.is(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_MEDIUM.get())
                || state.is(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_LARGE.get()));
    }

    public ItemDisplayTransform clamp(ItemDisplayTransform transform) {
        if (transform == null) {
            transform = ItemDisplayTransform.DEFAULT;
        }
        return new ItemDisplayTransform(
                Mth.clamp(transform.offsetX(), minOffsetX, maxOffsetX),
                Mth.clamp(transform.offsetY(), minOffsetY, maxOffsetY),
                Mth.clamp(transform.offsetZ(), minOffsetZ, maxOffsetZ),
                Mth.clamp(transform.rotationX(), minRotation, maxRotation),
                Mth.clamp(transform.rotationY(), minRotation, maxRotation),
                Mth.clamp(transform.rotationZ(), minRotation, maxRotation),
                Mth.clamp(transform.scaleX(), minScale, maxScale),
                Mth.clamp(transform.scaleY(), minScale, maxScale),
                Mth.clamp(transform.scaleZ(), minScale, maxScale)
        );
    }
}
