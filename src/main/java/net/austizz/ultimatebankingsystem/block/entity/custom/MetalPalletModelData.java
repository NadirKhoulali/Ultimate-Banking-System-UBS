package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.List;

public final class MetalPalletModelData {
    public static final ModelProperty<ContentsSnapshot> CONTENTS = new ModelProperty<>();

    private MetalPalletModelData() {
    }

    public record ContentEntry(float xOff, float yOff, float zOff, BlockState renderState) {
    }

    public record ContentsSnapshot(List<ContentEntry> entries) {
        public ContentsSnapshot {
            entries = List.copyOf(entries);
        }
    }
}
