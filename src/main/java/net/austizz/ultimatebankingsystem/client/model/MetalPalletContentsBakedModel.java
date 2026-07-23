package net.austizz.ultimatebankingsystem.client.model;

import com.mojang.math.Transformation;
import net.austizz.ultimatebankingsystem.block.entity.custom.MetalPalletModelData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps the metal pallet CENTER blockstate variant's baked model and appends
 * the pallet's cargo (bars / cash / money bundles) as static chunk-mesh quads,
 * driven by a {@link MetalPalletModelData.ContentsSnapshot} supplied through {@link ModelData} from
 * {@code MetalPalletBlockEntity#getModelData()}. This replaces the old
 * per-frame BlockEntityRenderer: contents change rarely, so the only cost is a
 * normal section rebuild on insert/remove and the per-frame cost is zero.
 *
 * <p>Behavior notes vs. the old BER:</p>
 * <ul>
 *   <li>Contents now render as far as chunks render (the old 96-block BER view
 *       distance cap is gone - an improvement).</li>
 *   <li>The block-breaking crumble overlay now also tints the content quads
 *       (accepted).</li>
 *   <li>All content quads live in the CENTER block's render section, so at
 *       extreme grazing angles a section-frustum cull could hide contents
 *       whose center block is off-screen - accepted cosmetic edge case.</li>
 * </ul>
 *
 * <p>Thread-safety: {@link #getQuads} runs on chunk-mesher worker threads. It
 * only performs pure reads of the immutable snapshot, the baked-model registry
 * (via {@link BlockRenderDispatcher#getBlockModel}) and the provided
 * {@link RandomSource}; there is no mutable static state.</p>
 */
public class MetalPalletContentsBakedModel extends BakedModelWrapper<BakedModel> {
    /**
     * The old path (BlockRenderDispatcher.renderSingleBlock) rendered content
     * models with a RandomSource seeded to 42L; keep that seed for parity.
     */
    private static final long CONTENT_QUAD_SEED = 42L;

    /**
     * Render types assumed while no snapshot is present yet (model data not
     * refreshed): the superset actually used by pallet contents - bars are
     * solid, cash/money stacks are registered as cutout in ClientModBusEvents.
     */
    private static final ChunkRenderTypeSet FALLBACK_CONTENT_TYPES =
            ChunkRenderTypeSet.of(RenderType.solid(), RenderType.cutout());

    public MetalPalletContentsBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state,
                                    @Nullable Direction side,
                                    RandomSource rand,
                                    ModelData data,
                                    @Nullable RenderType renderType) {
        List<BakedQuad> base = baseQuads(state, side, rand, data, renderType);
        if (side != null) {
            // Content quads are all emitted under side == null (see below);
            // directional passes only carry the wrapped pallet model.
            return base;
        }
        MetalPalletModelData.ContentsSnapshot snapshot = data.get(MetalPalletModelData.CONTENTS);
        if (snapshot == null || snapshot.entries().isEmpty()) {
            return base;
        }
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        List<BakedQuad> out = new ArrayList<>(base);
        for (MetalPalletModelData.ContentEntry entry : snapshot.entries()) {
            BakedModel contentModel = dispatcher.getBlockModel(entry.renderState());
            if (renderType != null) {
                rand.setSeed(CONTENT_QUAD_SEED);
                if (!contentModel.getRenderTypes(entry.renderState(), rand, ModelData.EMPTY).contains(renderType)) {
                    continue;
                }
            }
            // Pure translation: QuadTransformers.applying feeds the [0,1]
            // corner-space vertex positions straight through
            // Transformation.transformPosition, so a translation-only matrix
            // needs no blockCenterToCorner fixup, and normals are untouched
            // (identity normal matrix).
            IQuadTransformer transformer = QuadTransformers.applying(new Transformation(
                    new Vector3f(entry.xOff(), entry.yOff(), entry.zOff()), null, null, null));
            // CRITICAL: emit the content model's culled (directional) quads
            // under side == null in OUR output. The chunk mesher would
            // neighbor-cull directional quads against the blocks around the
            // pallet, but these faces float mid-pallet - bundle tops/sides
            // would vanish otherwise.
            appendContentQuads(out, contentModel, entry.renderState(), null, rand, renderType, transformer);
            for (Direction contentSide : Direction.values()) {
                appendContentQuads(out, contentModel, entry.renderState(), contentSide, rand, renderType, transformer);
            }
        }
        return out;
    }

    /**
     * The wrapped pallet model's quads, gated by the WRAPPED model's render
     * types. Our {@link #getRenderTypes} is a union, so the mesher also asks
     * for render types the base model does not use (e.g. cutout for money
     * bundles); without this gate the pallet would be tessellated once per
     * layer and z-fight with itself.
     */
    private List<BakedQuad> baseQuads(@Nullable BlockState state,
                                      @Nullable Direction side,
                                      RandomSource rand,
                                      ModelData data,
                                      @Nullable RenderType renderType) {
        if (renderType != null) {
            rand.setSeed(CONTENT_QUAD_SEED);
            if (!super.getRenderTypes(state, rand, data).contains(renderType)) {
                return List.of();
            }
        }
        // Fixed seed is safe: the pallet blockstate variant maps to a single
        // model, so the seed never selects between weighted variants.
        rand.setSeed(CONTENT_QUAD_SEED);
        return super.getQuads(state, side, rand, data, renderType);
    }

    private static void appendContentQuads(List<BakedQuad> out,
                                           BakedModel model,
                                           BlockState state,
                                           @Nullable Direction side,
                                           RandomSource rand,
                                           @Nullable RenderType renderType,
                                           IQuadTransformer transformer) {
        rand.setSeed(CONTENT_QUAD_SEED);
        for (BakedQuad quad : model.getQuads(state, side, rand, ModelData.EMPTY, renderType)) {
            out.add(withoutAmbientOcclusion(transformer.process(quad)));
        }
    }

    private static BakedQuad withoutAmbientOcclusion(BakedQuad quad) {
        if (!quad.hasAmbientOcclusion()) {
            return quad;
        }
        return new BakedQuad(
                quad.getVertices(),
                quad.getTintIndex(),
                quad.getDirection(),
                quad.getSprite(),
                quad.isShade(),
                false
        );
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        ChunkRenderTypeSet baseTypes = super.getRenderTypes(state, rand, data);
        MetalPalletModelData.ContentsSnapshot snapshot = data.get(MetalPalletModelData.CONTENTS);
        if (snapshot == null) {
            // No snapshot in the data yet: assume the full content superset so
            // nothing can be skipped by the mesher.
            return ChunkRenderTypeSet.union(baseTypes, FALLBACK_CONTENT_TYPES);
        }
        if (snapshot.entries().isEmpty()) {
            return baseTypes;
        }
        List<ChunkRenderTypeSet> sets = new ArrayList<>(snapshot.entries().size() + 1);
        sets.add(baseTypes);
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        for (MetalPalletModelData.ContentEntry entry : snapshot.entries()) {
            rand.setSeed(CONTENT_QUAD_SEED);
            sets.add(dispatcher.getBlockModel(entry.renderState())
                    .getRenderTypes(entry.renderState(), rand, ModelData.EMPTY));
        }
        return ChunkRenderTypeSet.union(sets);
    }
}
