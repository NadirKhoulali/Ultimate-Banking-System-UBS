package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.block.entity.custom.ItemDisplayTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ShelfTransformPreviewClientState {
    private static final Map<PreviewKey, ItemDisplayTransform> PREVIEWS = new HashMap<>();

    private ShelfTransformPreviewClientState() {
    }

    public static void setPreview(String dimensionId, BlockPos blockPos, int slot, ItemDisplayTransform transform) {
        if (dimensionId == null || dimensionId.isBlank() || blockPos == null || slot < 0 || transform == null) {
            return;
        }
        PREVIEWS.put(new PreviewKey(normalizeDimensionId(dimensionId), blockPos.immutable(), slot), transform);
    }

    public static void clearPreview(String dimensionId, BlockPos blockPos, int slot) {
        if (dimensionId == null || dimensionId.isBlank() || blockPos == null || slot < 0) {
            return;
        }
        PREVIEWS.remove(new PreviewKey(normalizeDimensionId(dimensionId), blockPos.immutable(), slot));
    }

    public static void clearForDimension(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return;
        }
        String normalized = normalizeDimensionId(dimensionId);
        Iterator<PreviewKey> iterator = PREVIEWS.keySet().iterator();
        while (iterator.hasNext()) {
            PreviewKey key = iterator.next();
            if (key.dimensionId.equals(normalized)) {
                iterator.remove();
            }
        }
    }

    public static ItemDisplayTransform resolve(Level level,
                                               BlockPos blockPos,
                                               int slot,
                                               ItemDisplayTransform serverTransform) {
        if (level == null || blockPos == null || slot < 0) {
            return serverTransform == null ? ItemDisplayTransform.DEFAULT : serverTransform;
        }
        PreviewKey key = new PreviewKey(normalizeDimensionId(level.dimension().location().toString()), blockPos.immutable(), slot);
        ItemDisplayTransform preview = PREVIEWS.get(key);
        if (preview != null) {
            return preview;
        }
        return serverTransform == null ? ItemDisplayTransform.DEFAULT : serverTransform;
    }

    private static String normalizeDimensionId(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private record PreviewKey(String dimensionId, BlockPos blockPos, int slot) {
    }
}
