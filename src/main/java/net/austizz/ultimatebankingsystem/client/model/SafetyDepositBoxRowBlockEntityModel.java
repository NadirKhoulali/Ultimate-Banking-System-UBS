package net.austizz.ultimatebankingsystem.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
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
import net.minecraft.util.Mth;

public class SafetyDepositBoxRowBlockEntityModel {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);
    private static final float DOOR_SWING_DEGREES = -92.0F;
    private static final float TRAY_SLIDE_PIXELS = -7.25F;
    private static final float ROW_TOP = -14.7F;
    private static final float ROW_PITCH = 3.55F;
    private static final float SMALL_DOOR_HEIGHT = 3.04F;
    private static final float DOOR_FRONT_Z = -8.25F;
    private static final float DOOR_THICKNESS = 0.8F;
    private static final float DOOR_HINGE_Z = DOOR_FRONT_Z + DOOR_THICKNESS;
    private static final ModuleType[] DEPOSIT_MODULES = {
            ModuleType.SMALL,
            ModuleType.MEDIUM,
            ModuleType.LARGE,
            ModuleType.EXTRA_LARGE
    };

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "safety_deposit_box_row"),
            "main"
    );

    private final ModelPart root;
    private final ModelPart[][] cavities = new ModelPart[ModuleType.values().length][SafetyDepositBoxRowBlockEntity.DOOR_COUNT];
    private final ModelPart[][] doors = new ModelPart[ModuleType.values().length][SafetyDepositBoxRowBlockEntity.DOOR_COUNT];
    private final ModelPart[][] trays = new ModelPart[ModuleType.values().length][SafetyDepositBoxRowBlockEntity.DOOR_COUNT];
    private final ModelPart[] covers = new ModelPart[SafetyDepositBoxRowBlockEntity.DOOR_COUNT];
    private final ModelPart[] separators = new ModelPart[SafetyDepositBoxRowBlockEntity.DOOR_COUNT - 1];

    public SafetyDepositBoxRowBlockEntityModel(ModelPart root) {
        this.root = root.getChild("root");
        for (ModuleType type : DEPOSIT_MODULES) {
            for (int start = 0; start < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; start++) {
                if (fits(type, start)) {
                    String prefix = partPrefix(type, start);
                    cavities[type.ordinal()][start] = this.root.getChild(prefix + "_cavity");
                    trays[type.ordinal()][start] = this.root.getChild(prefix + "_tray");
                    doors[type.ordinal()][start] = this.root.getChild(prefix + "_door");
                }
            }
        }
        for (int start = 0; start < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; start++) {
            covers[start] = this.root.getChild("cover_" + start);
        }
        for (int i = 0; i < separators.length; i++) {
            separators[i] = this.root.getChild("separator_" + i);
        }
        applyShellOnly();
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition root = meshDefinition.getRoot().addOrReplaceChild(
                "root",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F)
        );

        root.addOrReplaceChild("back_panel", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-8.0F, -16.0F, 6.75F, 16.0F, 16.0F, 1.25F, new CubeDeformation(0.0F))
                        .texOffs(0, 36).addBox(-7.15F, -15.15F, 5.95F, 14.3F, 14.1F, 0.78F, new CubeDeformation(0.0F))
                        .texOffs(92, 100).addBox(-6.9F, -14.85F, 5.55F, 13.8F, 13.5F, 0.22F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        root.addOrReplaceChild("left_trim", CubeListBuilder.create()
                        .texOffs(0, 54).addBox(-8.0F, -16.0F, -8.0F, 1.05F, 16.0F, 16.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 74).addBox(-7.68F, -15.5F, -8.55F, 0.35F, 15.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("right_trim", CubeListBuilder.create()
                        .texOffs(0, 54).addBox(6.95F, -16.0F, -8.0F, 1.05F, 16.0F, 16.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 74).addBox(7.33F, -15.5F, -8.55F, 0.35F, 15.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("top_trim", CubeListBuilder.create()
                        .texOffs(36, 54).addBox(-8.0F, -16.0F, -8.0F, 16.0F, 1.0F, 16.0F, new CubeDeformation(0.0F))
                        .texOffs(36, 62).addBox(-7.5F, -15.62F, -8.65F, 15.0F, 0.35F, 1.05F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("bottom_plinth", CubeListBuilder.create()
                        .texOffs(36, 66).addBox(-8.0F, -1.0F, -8.0F, 16.0F, 1.0F, 16.0F, new CubeDeformation(0.0F))
                        .texOffs(36, 74).addBox(-7.5F, -0.72F, -8.72F, 15.0F, 0.38F, 1.15F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        for (int i = 0; i < SafetyDepositBoxRowBlockEntity.DOOR_COUNT - 1; i++) {
            float y = ROW_TOP + (i + 1) * ROW_PITCH - 0.65F;
            root.addOrReplaceChild("separator_" + i, CubeListBuilder.create()
                            .texOffs(70, 0).addBox(-7.45F, y, -8.45F, 14.9F, 0.42F, 1.3F, new CubeDeformation(0.0F))
                            .texOffs(70, 12).addBox(-7.45F, y + 0.2F, -7.8F, 14.9F, 0.25F, 14.15F, new CubeDeformation(0.0F)),
                    PartPose.ZERO);
        }

        for (ModuleType type : DEPOSIT_MODULES) {
            for (int start = 0; start < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; start++) {
                if (fits(type, start)) {
                    addDepositModule(root, type, start);
                }
            }
        }
        for (int start = 0; start < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; start++) {
            addCover(root, start);
        }

        return LayerDefinition.create(meshDefinition, 128, 128);
    }

    public void applyShellOnly() {
        applyState(null, null);
    }

    public void applyState(ModuleType[] moduleTypes, float[] progress) {
        hideDynamicParts();
        applySeparatorVisibility(moduleTypes);
        for (int start = 0; start < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; start++) {
            ModuleType type = moduleTypes == null || start >= moduleTypes.length || moduleTypes[start] == null
                    ? ModuleType.EMPTY
                    : moduleTypes[start];
            if (type == ModuleType.COVER) {
                covers[start].visible = true;
                continue;
            }
            if (!type.assignable() || !fits(type, start)) {
                continue;
            }
            ModelPart cavity = cavities[type.ordinal()][start];
            ModelPart tray = trays[type.ordinal()][start];
            ModelPart door = doors[type.ordinal()][start];
            if (cavity == null || tray == null || door == null) {
                continue;
            }
            float doorProgress = progress == null || start >= progress.length ? 0.0F : progress[start];
            cavity.visible = true;
            tray.visible = true;
            door.visible = true;
            applyDoorMotion(door, tray, doorProgress);
        }
    }

    public void applyAnimation(float[] progress) {
        ModuleType[] modules = new ModuleType[SafetyDepositBoxRowBlockEntity.DOOR_COUNT];
        for (int i = 0; i < modules.length; i++) {
            modules[i] = ModuleType.SMALL;
        }
        applyState(modules, progress);
    }

    public void applyDoorState(int doorIndex, float progress) {
        ModuleType[] modules = new ModuleType[SafetyDepositBoxRowBlockEntity.DOOR_COUNT];
        float[] progresses = new float[SafetyDepositBoxRowBlockEntity.DOOR_COUNT];
        for (int i = 0; i < modules.length; i++) {
            modules[i] = ModuleType.SMALL;
        }
        if (doorIndex >= 0 && doorIndex < progresses.length) {
            progresses[doorIndex] = progress;
        }
        applyState(modules, progresses);
    }

    public void renderToBuffer(PoseStack poseStack,
                               VertexConsumer consumer,
                               int packedLight,
                               int packedOverlay,
                               int color) {
        root.render(poseStack, consumer, packedLight, packedOverlay, color);
    }

    private void hideDynamicParts() {
        for (ModuleType type : DEPOSIT_MODULES) {
            for (int start = 0; start < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; start++) {
                hide(cavities[type.ordinal()][start]);
                hide(trays[type.ordinal()][start]);
                hide(doors[type.ordinal()][start]);
            }
        }
        for (ModelPart cover : covers) {
            hide(cover);
        }
    }

    private void applySeparatorVisibility(ModuleType[] moduleTypes) {
        for (ModelPart separator : separators) {
            separator.visible = true;
        }
        if (moduleTypes == null) {
            return;
        }
        for (int start = 0; start < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; start++) {
            ModuleType type = start >= moduleTypes.length || moduleTypes[start] == null ? ModuleType.EMPTY : moduleTypes[start];
            if (!type.occupiesRows() || type.rowSpan() <= 1) {
                continue;
            }
            int end = Math.min(SafetyDepositBoxRowBlockEntity.DOOR_COUNT, start + type.rowSpan());
            for (int row = start + 1; row < end; row++) {
                separators[row - 1].visible = false;
            }
        }
    }

    private static void addDepositModule(PartDefinition root, ModuleType type, int start) {
        String prefix = partPrefix(type, start);
        float yTop = rowTop(start);
        float height = moduleHeight(type);
        float yCenter = yTop + height * 0.5F;
        float innerHeight = Math.max(1.6F, height - 0.34F);
        float trayHeight = Math.max(2.04F, height - 0.98F);
        float cavityY = yTop + 0.18F;
        float cavityZ = -7.05F;
        float cavityWidth = 14.1F;
        float cavityDepth = 13.05F;
        float cavityWall = 0.34F;

        root.addOrReplaceChild(prefix + "_cavity", CubeListBuilder.create()
                        .texOffs(0, 102).addBox(-7.05F, cavityY, cavityZ, cavityWall, innerHeight, cavityDepth, new CubeDeformation(0.0F))
                        .texOffs(0, 102).addBox(6.71F, cavityY, cavityZ, cavityWall, innerHeight, cavityDepth, new CubeDeformation(0.0F))
                        .texOffs(64, 102).addBox(-7.05F, cavityY, cavityZ, cavityWidth, cavityWall, cavityDepth, new CubeDeformation(0.0F))
                        .texOffs(64, 102).addBox(-7.05F, cavityY + innerHeight - cavityWall, cavityZ, cavityWidth, cavityWall, cavityDepth, new CubeDeformation(0.0F))
                        .texOffs(92, 100).addBox(-7.05F, cavityY, cavityZ + cavityDepth - 0.34F, cavityWidth, innerHeight, 0.34F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        float halfTrayHeight = trayHeight * 0.5F;
        root.addOrReplaceChild(prefix + "_tray", CubeListBuilder.create()
                        .texOffs(76, 40).addBox(-6.45F, halfTrayHeight - 0.34F, -6.45F, 12.9F, 0.34F, 10.85F, new CubeDeformation(0.0F))
                        .texOffs(76, 54).addBox(-6.85F, -halfTrayHeight, -6.8F, 0.42F, trayHeight, 11.25F, new CubeDeformation(0.0F))
                        .texOffs(76, 54).addBox(6.43F, -halfTrayHeight, -6.8F, 0.42F, trayHeight, 11.25F, new CubeDeformation(0.0F))
                        .texOffs(104, 54).addBox(-7.0F, halfTrayHeight - 0.86F, -7.2F, 14.0F, 0.86F, 0.48F, new CubeDeformation(0.0F))
                        .texOffs(104, 68).addBox(-6.45F, -halfTrayHeight, -6.78F, 12.9F, 0.28F, 0.34F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, yCenter, 0.0F));

        PartDefinition door = root.addOrReplaceChild(prefix + "_door", CubeListBuilder.create()
                        .texOffs(0, 84).addBox(-14.45F, -height * 0.5F, -0.8F, 14.45F, height, 0.8F, new CubeDeformation(0.0F))
                        .texOffs(38, 84).addBox(-14.0F, -height * 0.5F + 0.44F, -1.27F, 13.35F, Math.max(1.0F, height - 0.88F), 0.36F, new CubeDeformation(0.0F))
                        .texOffs(70, 0).addBox(-14.21F, -height * 0.5F + 0.22F, -1.54F, 0.38F, Math.max(0.8F, height - 0.44F), 0.38F, new CubeDeformation(0.0F))
                        .texOffs(70, 4).addBox(-0.62F, -height * 0.5F + 0.22F, -1.54F, 0.38F, Math.max(0.8F, height - 0.44F), 0.38F, new CubeDeformation(0.0F))
                        .texOffs(70, 8).addBox(-14.03F, -height * 0.5F + 0.2F, -1.56F, 13.62F, 0.34F, 0.38F, new CubeDeformation(0.0F))
                        .texOffs(70, 12).addBox(-14.03F, height * 0.5F - 0.54F, -1.56F, 13.62F, 0.34F, 0.38F, new CubeDeformation(0.0F))
                        .texOffs(78, 82).addBox(-13.59F, -0.42F, -1.6F, 2.65F, 0.72F, 0.36F, new CubeDeformation(0.0F))
                        .texOffs(88, 82).addBox(-3.8F, -0.58F, -1.67F, 0.62F, 0.62F, 0.42F, new CubeDeformation(0.0F))
                        .texOffs(96, 82).addBox(-2.37F, -0.58F, -1.67F, 0.62F, 0.62F, 0.42F, new CubeDeformation(0.0F))
                        .texOffs(104, 82).addBox(-1.33F, -0.2F, -1.55F, 0.72F, 0.4F, 0.42F, new CubeDeformation(0.0F)),
                PartPose.offset(7.22F, yCenter, DOOR_HINGE_Z));

        door.addOrReplaceChild(prefix + "_lock_plate", CubeListBuilder.create()
                        .texOffs(98, 82).addBox(-4.5F, -1.0F, -1.5F, 2.9F, 1.36F, 0.24F, new CubeDeformation(0.0F))
                        .texOffs(108, 82).addBox(-0.34F, -height * 0.5F + 0.18F, -1.5F, 0.34F, 0.86F, 0.42F, new CubeDeformation(0.0F))
                        .texOffs(108, 86).addBox(-0.34F, height * 0.5F - 1.04F, -1.5F, 0.34F, 0.86F, 0.42F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
    }

    private static void addCover(PartDefinition root, int start) {
        float yTop = rowTop(start);
        root.addOrReplaceChild("cover_" + start, CubeListBuilder.create()
                        .texOffs(0, 84).addBox(-7.22F, yTop + 0.02F, -8.25F, 14.45F, 3.04F, 0.8F, new CubeDeformation(0.0F))
                        .texOffs(38, 84).addBox(-6.75F, yTop + 0.46F, -8.72F, 13.35F, 2.16F, 0.36F, new CubeDeformation(0.0F))
                        .texOffs(70, 8).addBox(-6.25F, yTop + 1.36F, -8.98F, 12.5F, 0.25F, 0.32F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
    }

    private static void applyDoorMotion(ModelPart door, ModelPart tray, float progress) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        float latchDelay = Mth.clamp((clamped - 0.10F) / 0.90F, 0.0F, 1.0F);
        float swing = smooth(latchDelay);
        float slide = smooth(Mth.clamp((clamped - 0.35F) / 0.65F, 0.0F, 1.0F));
        door.yRot = DOOR_SWING_DEGREES * swing * DEG_TO_RAD;
        tray.z = TRAY_SLIDE_PIXELS * slide;
    }

    private static boolean fits(ModuleType type, int start) {
        return type != null
                && type.occupiesRows()
                && start >= 0
                && start + type.rowSpan() <= SafetyDepositBoxRowBlockEntity.DOOR_COUNT;
    }

    private static float rowTop(int start) {
        return ROW_TOP + start * ROW_PITCH;
    }

    private static float moduleHeight(ModuleType type) {
        return SMALL_DOOR_HEIGHT + (Math.max(1, type.rowSpan()) - 1) * ROW_PITCH;
    }

    private static String partPrefix(ModuleType type, int start) {
        return type.serializedName() + "_" + start;
    }

    private static void hide(ModelPart part) {
        if (part != null) {
            part.visible = false;
            part.yRot = 0.0F;
        }
    }

    private static float smooth(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
