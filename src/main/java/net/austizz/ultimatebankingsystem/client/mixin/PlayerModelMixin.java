package net.austizz.ultimatebankingsystem.client.mixin;

import net.austizz.ultimatebankingsystem.client.DallasMaskAnimationClientState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin extends HumanoidModel<LivingEntity> {
    protected PlayerModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL")
    )
    private void ultimatebankingsystem$animateDallasMask(
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo callbackInfo
    ) {
        DallasMaskAnimationClientState.AnimationSample sample =
                DallasMaskAnimationClientState.sample(entity.getUUID());
        if (sample == null || sample.reachAmount() <= 0.0F) {
            return;
        }

        float reach = sample.reachAmount();
        rightArm.xRot = Mth.lerp(reach, rightArm.xRot, -1.48F);
        leftArm.xRot = Mth.lerp(reach, leftArm.xRot, -1.48F);
        rightArm.yRot = Mth.lerp(reach, rightArm.yRot, -0.28F);
        leftArm.yRot = Mth.lerp(reach, leftArm.yRot, 0.28F);
        rightArm.zRot = Mth.lerp(reach, rightArm.zRot, 0.10F);
        leftArm.zRot = Mth.lerp(reach, leftArm.zRot, -0.10F);

        PlayerModel<?> playerModel = (PlayerModel<?>) (Object) this;
        playerModel.rightSleeve.copyFrom(rightArm);
        playerModel.leftSleeve.copyFrom(leftArm);
    }
}
