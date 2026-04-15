package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class BankTellerRenderer extends HumanoidMobRenderer<BankTellerEntity, PlayerModel<BankTellerEntity>> {

    private static final ResourceLocation MALE_TEXTURE =
            new ResourceLocation(UltimateBankingSystem.MODID, "textures/entity/bank_teller_male.png");
    private static final ResourceLocation FEMALE_TEXTURE =
            new ResourceLocation(UltimateBankingSystem.MODID, "textures/entity/bank_teller_female.png");

    private final PlayerModel<BankTellerEntity> wideModel;
    private final PlayerModel<BankTellerEntity> slimModel;

    public BankTellerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = this.model;
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public ResourceLocation getTextureLocation(BankTellerEntity entity) {
        return entity.getVariant() == BankTellerEntity.VARIANT_FEMALE ? FEMALE_TEXTURE : MALE_TEXTURE;
    }

    @Override
    public void render(BankTellerEntity entity,
                       float entityYaw,
                       float partialTicks,
                       PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer,
                       int packedLight) {
        this.model = entity.getVariant() == BankTellerEntity.VARIANT_FEMALE ? this.slimModel : this.wideModel;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected void scale(BankTellerEntity livingEntity, PoseStack poseStack, float partialTickTime) {
        super.scale(livingEntity, poseStack, partialTickTime);
        poseStack.scale(0.98F, 0.98F, 0.98F);
    }
}
