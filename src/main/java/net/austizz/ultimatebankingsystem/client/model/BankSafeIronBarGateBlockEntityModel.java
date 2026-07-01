package net.austizz.ultimatebankingsystem.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class BankSafeIronBarGateBlockEntityModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "bank_safe_iron_bar_gate"),
            "main"
    );

    private static final float[] LEFT_RETRACTIONS = {-2.2F, -5.4F, -8.6F, -11.8F, -15.0F, -18.2F, -21.4F};
    private static final float[] RIGHT_RETRACTIONS = {21.4F, 18.2F, 15.0F, 11.8F, 8.6F, 5.4F, 2.2F};

    private static final float[][] STATIC_FRAME_BOXES = {
            {1.0F, 1.0F, -30.5F, -24.15F, -2.05F, 61.0F, 0.6F, 0.7F},
            {1.0F, 1.0F, -30.5F, 22.35F, -2.05F, 61.0F, 0.6F, 0.7F},
            {1.0F, 1.0F, -32.7F, -23.2F, -2.25F, 4.4F, 45.2F, 0.6F},
            {1.0F, 1.0F, 28.3F, -23.2F, -2.25F, 4.4F, 45.2F, 0.6F},
            {4.0F, 4.0F, -34.0F, -28.0F, -1.5F, 68.0F, 2.9F, 3.6F},
            {4.0F, 4.0F, -34.0F, 23.1F, -1.5F, 68.0F, 2.9F, 3.6F},
            {4.0F, 4.0F, -34.0F, -28.0F, -1.5F, 2.9F, 54.0F, 3.6F},
            {4.0F, 4.0F, 31.1F, -28.0F, -1.5F, 2.9F, 54.0F, 3.6F},
            {4.0F, 4.0F, -31.0F, -24.8F, -1.2F, 62.0F, 1.6F, 3.8F},
            {4.0F, 4.0F, -31.0F, 21.7F, -1.2F, 62.0F, 1.6F, 3.8F},
            {1.0F, 1.0F, -34.0F, -25.1F, 1.9F, 6.2F, 48.2F, 0.8F},
            {1.0F, 1.0F, 27.8F, -25.1F, 1.9F, 6.2F, 48.2F, 0.8F},
            {1.0F, 1.0F, -33.2F, -27.55F, -2.05F, 66.4F, 0.6F, 0.5F},
            {1.0F, 1.0F, -33.2F, 24.7F, -2.05F, 66.4F, 0.55F, 0.5F},
            {1.0F, 1.0F, -33.55F, -27.3F, -2.1F, 0.6F, 52.6F, 0.55F},
            {1.0F, 1.0F, 32.95F, -27.3F, -2.1F, 0.6F, 52.6F, 0.55F},
            {1.0F, 1.0F, -29.8F, -29.0F, -0.7F, 1.4F, 0.9F, 1.4F},
            {1.0F, 1.0F, -24.4F, -29.0F, -0.7F, 1.4F, 0.9F, 1.4F},
            {1.0F, 1.0F, -19.0F, -29.0F, -0.7F, 1.4F, 0.9F, 1.4F},
            {1.0F, 1.0F, -13.6F, -29.0F, -0.7F, 1.4F, 0.9F, 1.4F},
            {1.0F, 1.0F, -8.2F, -29.0F, -0.7F, 1.4F, 0.9F, 1.4F},
            {1.0F, 1.0F, -2.8F, -29.0F, -0.7F, 1.4F, 0.9F, 1.4F},
            {1.0F, 1.0F, 1.4F, -29.0F, -0.7F, 1.4F, 0.9F, 1.4F},
            {1.0F, 1.0F, 6.8F, -29.0F, -0.7F, 1.4F, 0.9F, 1.4F},
            {1.0F, 1.0F, 12.2F, -29.0F, -0.7F, 1.4F, 0.9F, 1.4F},
            {1.0F, 1.0F, 17.6F, -29.0F, -0.7F, 1.4F, 0.9F, 1.4F},
            {1.0F, 1.0F, 23.0F, -29.0F, -0.7F, 1.4F, 0.9F, 1.4F},
            {1.0F, 1.0F, 28.4F, -29.0F, -0.7F, 1.4F, 0.9F, 1.4F},
            {1.0F, 1.0F, -33.2F, -27.6F, -2.85F, 66.4F, 0.65F, 0.5F},
            {1.0F, 1.0F, -33.2F, 24.55F, -2.85F, 66.4F, 0.65F, 0.5F},
            {1.0F, 1.0F, -33.55F, -27.3F, -2.9F, 0.65F, 52.6F, 0.55F},
            {1.0F, 1.0F, 32.9F, -27.3F, -2.9F, 0.65F, 52.6F, 0.55F}
    };

    private static final float[][] LEFT_RETRACTING_BAR_00_BOXES = {
            {3.0F, 3.0F, -29.55F, -24.7F, -2.2F, 1.1F, 47.3F, 2.55F},
            {1.0F, 1.0F, -29.35F, -24.2F, -2.75F, 0.35F, 46.4F, 0.5F},
            {2.0F, 2.0F, -28.75F, -24.2F, -1.8F, 0.3F, 46.4F, 2.25F},
            {2.0F, 2.0F, -30.25F, -23.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {2.0F, 2.0F, -30.25F, -5.0F, -2.15F, 2.5F, 1.75F, 2.35F},
            {2.0F, 2.0F, -30.25F, 8.8F, -2.1F, 2.5F, 1.3F, 2.25F},
            {2.0F, 2.0F, -30.25F, 20.6F, -2.1F, 2.5F, 1.75F, 2.3F}
    };

    private static final float[][] LEFT_RETRACTING_BAR_01_BOXES = {
            {3.0F, 3.0F, -25.05F, -24.7F, -2.2F, 1.1F, 47.3F, 2.55F},
            {1.0F, 1.0F, -24.85F, -24.2F, -2.75F, 0.35F, 46.4F, 0.5F},
            {2.0F, 2.0F, -24.25F, -24.2F, -1.8F, 0.3F, 46.4F, 2.25F},
            {2.0F, 2.0F, -25.75F, -23.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {2.0F, 2.0F, -25.75F, -5.0F, -2.15F, 2.5F, 1.75F, 2.35F},
            {2.0F, 2.0F, -25.75F, 8.8F, -2.1F, 2.5F, 1.3F, 2.25F},
            {2.0F, 2.0F, -25.75F, 20.6F, -2.1F, 2.5F, 1.75F, 2.3F}
    };

    private static final float[][] LEFT_RETRACTING_BAR_02_BOXES = {
            {3.0F, 3.0F, -20.55F, -24.7F, -2.2F, 1.1F, 47.3F, 2.55F},
            {1.0F, 1.0F, -20.35F, -24.2F, -2.75F, 0.35F, 46.4F, 0.5F},
            {2.0F, 2.0F, -19.75F, -24.2F, -1.8F, 0.3F, 46.4F, 2.25F},
            {2.0F, 2.0F, -21.25F, -23.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {2.0F, 2.0F, -21.25F, -5.0F, -2.15F, 2.5F, 1.75F, 2.35F},
            {2.0F, 2.0F, -21.25F, 8.8F, -2.1F, 2.5F, 1.3F, 2.25F},
            {2.0F, 2.0F, -21.25F, 20.6F, -2.1F, 2.5F, 1.75F, 2.3F}
    };

    private static final float[][] LEFT_RETRACTING_BAR_03_BOXES = {
            {3.0F, 3.0F, -16.05F, -24.7F, -2.2F, 1.1F, 47.3F, 2.55F},
            {1.0F, 1.0F, -15.85F, -24.2F, -2.75F, 0.35F, 46.4F, 0.5F},
            {2.0F, 2.0F, -15.25F, -24.2F, -1.8F, 0.3F, 46.4F, 2.25F},
            {2.0F, 2.0F, -16.75F, -23.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {2.0F, 2.0F, -16.75F, -5.0F, -2.15F, 2.5F, 1.75F, 2.35F},
            {2.0F, 2.0F, -16.75F, 8.8F, -2.1F, 2.5F, 1.3F, 2.25F},
            {2.0F, 2.0F, -16.75F, 20.6F, -2.1F, 2.5F, 1.75F, 2.3F}
    };

    private static final float[][] LEFT_RETRACTING_BAR_04_BOXES = {
            {3.0F, 3.0F, -11.55F, -24.7F, -2.2F, 1.1F, 47.3F, 2.55F},
            {1.0F, 1.0F, -11.35F, -24.2F, -2.75F, 0.35F, 46.4F, 0.5F},
            {2.0F, 2.0F, -10.75F, -24.2F, -1.8F, 0.3F, 46.4F, 2.25F},
            {2.0F, 2.0F, -12.25F, -23.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {2.0F, 2.0F, -12.25F, -5.0F, -2.15F, 2.5F, 1.75F, 2.35F},
            {2.0F, 2.0F, -12.25F, 8.8F, -2.1F, 2.5F, 1.3F, 2.25F},
            {2.0F, 2.0F, -12.25F, 20.6F, -2.1F, 2.5F, 1.75F, 2.3F}
    };

    private static final float[][] LEFT_RETRACTING_BAR_05_BOXES = {
            {3.0F, 3.0F, -7.05F, -24.7F, -2.2F, 1.1F, 47.3F, 2.55F},
            {1.0F, 1.0F, -6.85F, -24.2F, -2.75F, 0.35F, 46.4F, 0.5F},
            {2.0F, 2.0F, -6.25F, -24.2F, -1.8F, 0.3F, 46.4F, 2.25F},
            {2.0F, 2.0F, -7.75F, -23.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {2.0F, 2.0F, -7.75F, -5.0F, -2.15F, 2.5F, 1.75F, 2.35F},
            {2.0F, 2.0F, -7.75F, 8.8F, -2.1F, 2.5F, 1.3F, 2.25F},
            {2.0F, 2.0F, -7.75F, 20.6F, -2.1F, 2.5F, 1.75F, 2.3F}
    };

    private static final float[][] LEFT_RETRACTING_BAR_06_BOXES = {
            {3.0F, 3.0F, -2.55F, -24.7F, -2.2F, 1.1F, 47.3F, 2.55F},
            {1.0F, 1.0F, -2.35F, -24.2F, -2.75F, 0.35F, 46.4F, 0.5F},
            {2.0F, 2.0F, -1.75F, -24.2F, -1.8F, 0.3F, 46.4F, 2.25F},
            {2.0F, 2.0F, -3.25F, -23.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {2.0F, 2.0F, -3.25F, -5.0F, -2.15F, 2.5F, 1.75F, 2.35F},
            {2.0F, 2.0F, -3.25F, 8.8F, -2.1F, 2.5F, 1.3F, 2.25F},
            {2.0F, 2.0F, -3.25F, 20.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {1.0F, 1.0F, -2.95F, -1.8F, -3.05F, 2.1F, 4.0F, 0.7F}
    };

    private static final float[][] RIGHT_RETRACTING_BAR_00_BOXES = {
            {3.0F, 3.0F, 1.45F, -24.7F, -2.2F, 1.1F, 47.3F, 2.55F},
            {1.0F, 1.0F, 1.65F, -24.2F, -2.75F, 0.35F, 46.4F, 0.5F},
            {2.0F, 2.0F, 2.25F, -24.2F, -1.8F, 0.3F, 46.4F, 2.25F},
            {2.0F, 2.0F, 0.75F, -23.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {2.0F, 2.0F, 0.75F, -5.0F, -2.15F, 2.5F, 1.75F, 2.35F},
            {2.0F, 2.0F, 0.75F, 8.8F, -2.1F, 2.5F, 1.3F, 2.25F},
            {2.0F, 2.0F, 0.75F, 20.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {1.0F, 1.0F, 0.85F, -6.2F, -3.1F, 2.1F, 4.0F, 0.7F}
    };

    private static final float[][] RIGHT_RETRACTING_BAR_01_BOXES = {
            {3.0F, 3.0F, 5.95F, -24.7F, -2.2F, 1.1F, 47.3F, 2.55F},
            {1.0F, 1.0F, 6.15F, -24.2F, -2.75F, 0.35F, 46.4F, 0.5F},
            {2.0F, 2.0F, 6.75F, -24.2F, -1.8F, 0.3F, 46.4F, 2.25F},
            {2.0F, 2.0F, 5.25F, -23.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {2.0F, 2.0F, 5.25F, -5.0F, -2.15F, 2.5F, 1.75F, 2.35F},
            {2.0F, 2.0F, 5.25F, 8.8F, -2.1F, 2.5F, 1.3F, 2.25F},
            {2.0F, 2.0F, 5.25F, 20.6F, -2.1F, 2.5F, 1.75F, 2.3F}
    };

    private static final float[][] RIGHT_RETRACTING_BAR_02_BOXES = {
            {3.0F, 3.0F, 10.45F, -24.7F, -2.2F, 1.1F, 47.3F, 2.55F},
            {1.0F, 1.0F, 10.65F, -24.2F, -2.75F, 0.35F, 46.4F, 0.5F},
            {2.0F, 2.0F, 11.25F, -24.2F, -1.8F, 0.3F, 46.4F, 2.25F},
            {2.0F, 2.0F, 9.75F, -23.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {2.0F, 2.0F, 9.75F, -5.0F, -2.15F, 2.5F, 1.75F, 2.35F},
            {2.0F, 2.0F, 9.75F, 8.8F, -2.1F, 2.5F, 1.3F, 2.25F},
            {2.0F, 2.0F, 9.75F, 20.6F, -2.1F, 2.5F, 1.75F, 2.3F}
    };

    private static final float[][] RIGHT_RETRACTING_BAR_03_BOXES = {
            {3.0F, 3.0F, 14.95F, -24.7F, -2.2F, 1.1F, 47.3F, 2.55F},
            {1.0F, 1.0F, 15.15F, -24.2F, -2.75F, 0.35F, 46.4F, 0.5F},
            {2.0F, 2.0F, 15.75F, -24.2F, -1.8F, 0.3F, 46.4F, 2.25F},
            {2.0F, 2.0F, 14.25F, -23.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {2.0F, 2.0F, 14.25F, -5.0F, -2.15F, 2.5F, 1.75F, 2.35F},
            {2.0F, 2.0F, 14.25F, 8.8F, -2.1F, 2.5F, 1.3F, 2.25F},
            {2.0F, 2.0F, 14.25F, 20.6F, -2.1F, 2.5F, 1.75F, 2.3F}
    };

    private static final float[][] RIGHT_RETRACTING_BAR_04_BOXES = {
            {3.0F, 3.0F, 19.45F, -24.7F, -2.2F, 1.1F, 47.3F, 2.55F},
            {1.0F, 1.0F, 19.65F, -24.2F, -2.75F, 0.35F, 46.4F, 0.5F},
            {2.0F, 2.0F, 20.25F, -24.2F, -1.8F, 0.3F, 46.4F, 2.25F},
            {2.0F, 2.0F, 18.75F, -23.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {2.0F, 2.0F, 18.75F, -5.0F, -2.15F, 2.5F, 1.75F, 2.35F},
            {2.0F, 2.0F, 18.75F, 8.8F, -2.1F, 2.5F, 1.3F, 2.25F},
            {2.0F, 2.0F, 18.75F, 20.6F, -2.1F, 2.5F, 1.75F, 2.3F}
    };

    private static final float[][] RIGHT_RETRACTING_BAR_05_BOXES = {
            {3.0F, 3.0F, 23.95F, -24.7F, -2.2F, 1.1F, 47.3F, 2.55F},
            {1.0F, 1.0F, 24.15F, -24.2F, -2.75F, 0.35F, 46.4F, 0.5F},
            {2.0F, 2.0F, 24.75F, -24.2F, -1.8F, 0.3F, 46.4F, 2.25F},
            {2.0F, 2.0F, 23.25F, -23.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {2.0F, 2.0F, 23.25F, -5.0F, -2.15F, 2.5F, 1.75F, 2.35F},
            {2.0F, 2.0F, 23.25F, 8.8F, -2.1F, 2.5F, 1.3F, 2.25F},
            {2.0F, 2.0F, 23.25F, 20.6F, -2.1F, 2.5F, 1.75F, 2.3F}
    };

    private static final float[][] RIGHT_RETRACTING_BAR_06_BOXES = {
            {3.0F, 3.0F, 28.45F, -24.7F, -2.2F, 1.1F, 47.3F, 2.55F},
            {1.0F, 1.0F, 28.65F, -24.2F, -2.75F, 0.35F, 46.4F, 0.5F},
            {2.0F, 2.0F, 29.25F, -24.2F, -1.8F, 0.3F, 46.4F, 2.25F},
            {2.0F, 2.0F, 27.75F, -23.6F, -2.1F, 2.5F, 1.75F, 2.3F},
            {2.0F, 2.0F, 27.75F, -5.0F, -2.15F, 2.5F, 1.75F, 2.35F},
            {2.0F, 2.0F, 27.75F, 8.8F, -2.1F, 2.5F, 1.3F, 2.25F},
            {2.0F, 2.0F, 27.75F, 20.6F, -2.1F, 2.5F, 1.75F, 2.3F}
    };

    private final ModelPart root;
    private final ModelPart[] leftBars = new ModelPart[7];
    private final ModelPart[] rightBars = new ModelPart[7];

    public BankSafeIronBarGateBlockEntityModel(ModelPart root) {
        this.root = root.getChild("root");
        for (int i = 0; i < 7; i++) {
            leftBars[i] = this.root.getChild("left_retracting_bar_0" + i);
            rightBars[i] = this.root.getChild("right_retracting_bar_0" + i);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition root = meshDefinition.getRoot().addOrReplaceChild(
                "root",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F)
        );
        addBoxes(root, "static_frame", STATIC_FRAME_BOXES);
        addBoxes(root, "left_retracting_bar_00", LEFT_RETRACTING_BAR_00_BOXES);
        addBoxes(root, "left_retracting_bar_01", LEFT_RETRACTING_BAR_01_BOXES);
        addBoxes(root, "left_retracting_bar_02", LEFT_RETRACTING_BAR_02_BOXES);
        addBoxes(root, "left_retracting_bar_03", LEFT_RETRACTING_BAR_03_BOXES);
        addBoxes(root, "left_retracting_bar_04", LEFT_RETRACTING_BAR_04_BOXES);
        addBoxes(root, "left_retracting_bar_05", LEFT_RETRACTING_BAR_05_BOXES);
        addBoxes(root, "left_retracting_bar_06", LEFT_RETRACTING_BAR_06_BOXES);
        addBoxes(root, "right_retracting_bar_00", RIGHT_RETRACTING_BAR_00_BOXES);
        addBoxes(root, "right_retracting_bar_01", RIGHT_RETRACTING_BAR_01_BOXES);
        addBoxes(root, "right_retracting_bar_02", RIGHT_RETRACTING_BAR_02_BOXES);
        addBoxes(root, "right_retracting_bar_03", RIGHT_RETRACTING_BAR_03_BOXES);
        addBoxes(root, "right_retracting_bar_04", RIGHT_RETRACTING_BAR_04_BOXES);
        addBoxes(root, "right_retracting_bar_05", RIGHT_RETRACTING_BAR_05_BOXES);
        addBoxes(root, "right_retracting_bar_06", RIGHT_RETRACTING_BAR_06_BOXES);
        return LayerDefinition.create(meshDefinition, 128, 128);
    }

    public void applyAnimation(float progress) {
        float motion = openingMotion(progress);
        for (int i = 0; i < 7; i++) {
            leftBars[i].x = LEFT_RETRACTIONS[i] * motion;
            rightBars[i].x = RIGHT_RETRACTIONS[i] * motion;
        }
    }

    public void renderToBuffer(PoseStack poseStack,
                               VertexConsumer vertexConsumer,
                               int packedLight,
                               int packedOverlay,
                               int color) {
        root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }

    private static void addBoxes(PartDefinition parent, String name, float[][] boxes) {
        CubeListBuilder builder = CubeListBuilder.create();
        for (float[] box : boxes) {
            addBox(builder, (int) box[0], (int) box[1], box[2], box[3], box[4], box[5], box[6], box[7]);
        }
        parent.addOrReplaceChild(name, builder, PartPose.ZERO);
    }

    private static void addBox(CubeListBuilder builder,
                               int u,
                               int v,
                               float x,
                               float y,
                               float z,
                               float width,
                               float height,
                               float depth) {
        builder.texOffs(u, v).addBox(x, y, z, width, height, depth, new CubeDeformation(0.0F));
    }

    private static float openingMotion(float progress) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        float delayed = Mth.clamp((clamped - 0.07F) / 0.93F, 0.0F, 1.0F);
        return delayed * delayed * (3.0F - 2.0F * delayed);
    }
}
