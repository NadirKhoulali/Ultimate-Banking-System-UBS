package net.austizz.ultimatebankingsystem.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity.ModuleType;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

public final class SafetyDepositBoxTrayModel {
    private static final ModuleType[] DISPLAY_TYPES = {
            ModuleType.SMALL,
            ModuleType.MEDIUM,
            ModuleType.LARGE,
            ModuleType.EXTRA_LARGE
    };

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "safety_deposit_box_tray"),
            "main"
    );

    private final ModelPart root;
    private final ModelPart[] trays = new ModelPart[ModuleType.values().length];

    public SafetyDepositBoxTrayModel(ModelPart bakedRoot) {
        this.root = bakedRoot.getChild("root");
        for (ModuleType type : DISPLAY_TYPES) {
            trays[type.ordinal()] = root.getChild(partName(type));
        }
        setModuleType(ModuleType.SMALL);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild(
                "root",
                CubeListBuilder.create(),
                PartPose.ZERO
        );
        for (ModuleType type : DISPLAY_TYPES) {
            addTray(root, partName(type), type, PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    static void addTray(PartDefinition parent, String name, ModuleType type, PartPose pose) {
        float trayHeight = trayHeight(type);
        float halfTrayHeight = trayHeight * 0.5F;
        parent.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(76, 40).addBox(-6.45F, halfTrayHeight - 0.34F, -6.45F,
                                12.9F, 0.34F, 10.85F, new CubeDeformation(0.0F))
                        .texOffs(76, 54).addBox(-6.85F, -halfTrayHeight, -6.8F,
                                0.42F, trayHeight, 11.25F, new CubeDeformation(0.0F))
                        .texOffs(76, 54).addBox(6.43F, -halfTrayHeight, -6.8F,
                                0.42F, trayHeight, 11.25F, new CubeDeformation(0.0F))
                        .texOffs(104, 54).addBox(-7.0F, halfTrayHeight - 0.86F, -7.2F,
                                14.0F, 0.86F, 0.48F, new CubeDeformation(0.0F))
                        .texOffs(104, 68).addBox(-6.45F, -halfTrayHeight, -6.78F,
                                12.9F, 0.28F, 0.34F, new CubeDeformation(0.0F)),
                pose);
    }

    public void setModuleType(ModuleType requested) {
        ModuleType selected = requested != null && requested.assignable()
                ? requested : ModuleType.SMALL;
        for (ModuleType type : DISPLAY_TYPES) {
            trays[type.ordinal()].visible = type == selected;
        }
    }

    public void renderToBuffer(PoseStack poseStack,
                               VertexConsumer consumer,
                               int packedLight,
                               int packedOverlay,
                               int color) {
        root.render(poseStack, consumer, packedLight, packedOverlay, color);
    }

    public static float trayHeight(ModuleType type) {
        ModuleType safe = type != null && type.assignable() ? type : ModuleType.SMALL;
        float moduleHeight = 3.04F + (Math.max(1, safe.rowSpan()) - 1) * 3.55F;
        return Math.max(2.04F, moduleHeight - 0.98F);
    }

    private static String partName(ModuleType type) {
        return type.serializedName() + "_tray";
    }
}
