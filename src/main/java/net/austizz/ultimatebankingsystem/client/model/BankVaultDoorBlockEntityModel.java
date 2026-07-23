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

public class BankVaultDoorBlockEntityModel {
	private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);
	private static final float DOOR_OPEN_DEGREES = 108.0F;
	private static final float DOOR_OVERSHOOT_DEGREES = 114.0F;
	private static final float WHEEL_SPIN_DEGREES = -430.0F;
	private static final float BOLT_RETRACTION_PIXELS = 2.4F;

	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "bank_vault_door"), "main");
	private final ModelPart bank_vault_door_block_entity_root;
	private final ModelPart static_bank_wall_and_opening;
	private final ModelPart round_vault_frame;
	private final ModelPart dark_vault_interior_visible_when_open;
	private final ModelPart static_right_hinge_mounts;
	private final ModelPart door_leaf_hinged_heavy_round_slab;
	private final ModelPart locking_wheel_spins_before_swing;
	private final ModelPart bolt_left_retracts;
	private final ModelPart bolt_right_retracts;
	private final ModelPart bolt_top_retracts;
	private final ModelPart bolt_bottom_retracts;

	public BankVaultDoorBlockEntityModel(ModelPart root) {
		this.bank_vault_door_block_entity_root = root.getChild("bank_vault_door_block_entity_root");
		this.static_bank_wall_and_opening = this.bank_vault_door_block_entity_root.getChild("static_bank_wall_and_opening");
		this.round_vault_frame = this.static_bank_wall_and_opening.getChild("round_vault_frame");
		this.dark_vault_interior_visible_when_open = this.static_bank_wall_and_opening.getChild("dark_vault_interior_visible_when_open");
		this.static_right_hinge_mounts = this.static_bank_wall_and_opening.getChild("static_right_hinge_mounts");
		this.door_leaf_hinged_heavy_round_slab = this.bank_vault_door_block_entity_root.getChild("door_leaf_hinged_heavy_round_slab");
		this.locking_wheel_spins_before_swing = this.door_leaf_hinged_heavy_round_slab.getChild("locking_wheel_spins_before_swing");
		this.bolt_left_retracts = this.door_leaf_hinged_heavy_round_slab.getChild("bolt_left_retracts");
		this.bolt_right_retracts = this.door_leaf_hinged_heavy_round_slab.getChild("bolt_right_retracts");
		this.bolt_top_retracts = this.door_leaf_hinged_heavy_round_slab.getChild("bolt_top_retracts");
		this.bolt_bottom_retracts = this.door_leaf_hinged_heavy_round_slab.getChild("bolt_bottom_retracts");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition bank_vault_door_block_entity_root = partdefinition.addOrReplaceChild("bank_vault_door_block_entity_root", CubeListBuilder.create().texOffs(0, 0).addBox(-32.0F, -0.5F, 5.5F, 64.0F, 1.5F, 4.5F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition static_bank_wall_and_opening = bank_vault_door_block_entity_root.addOrReplaceChild("static_bank_wall_and_opening", CubeListBuilder.create().texOffs(0, 0).addBox(31.5F, -26.0F, 1.5F, 2.5F, 50.0F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-34.0F, -26.0F, 1.5F, 2.5F, 50.0F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-34.0F, -28.0F, 1.5F, 68.0F, 2.5F, 6.5F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-34.0F, 23.8F, 1.5F, 68.0F, 2.2F, 6.5F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(29.55F, 18.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(29.55F, 13.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(29.55F, 8.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(29.55F, 3.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(29.55F, -1.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(29.55F, -6.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(29.55F, -11.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(29.55F, -16.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(29.55F, -21.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(25.55F, 18.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(25.55F, 13.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(25.55F, 8.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(25.55F, 3.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(25.55F, -1.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(25.55F, -6.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(25.55F, -11.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(25.55F, -16.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(25.55F, -21.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(13.55F, 18.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(13.55F, -21.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(9.55F, 18.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(9.55F, -21.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-10.45F, 18.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-10.45F, -21.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-14.45F, 18.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-14.45F, -21.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-26.45F, 18.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-26.45F, 13.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-26.45F, 8.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-26.45F, 3.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-26.45F, -1.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-26.45F, -6.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-26.45F, -11.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-26.45F, -16.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-26.45F, -21.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-30.45F, 18.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-30.45F, 13.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-30.45F, 8.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-30.45F, 3.55F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-30.45F, -1.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-30.45F, -6.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-30.45F, -11.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-30.45F, -16.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-30.45F, -21.45F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(28.55F, 21.05F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(28.55F, -23.95F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(21.55F, 21.05F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(21.55F, -23.95F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(14.55F, 21.05F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(14.55F, -23.95F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(7.55F, 21.05F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(7.55F, -23.95F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(0.55F, 21.05F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(0.55F, -23.95F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-6.45F, 21.05F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-6.45F, -23.95F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-13.45F, 21.05F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-13.45F, -23.95F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-20.45F, 21.05F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-20.45F, -23.95F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-27.45F, 21.05F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-27.45F, -23.95F, 2.0F, 0.9F, 0.9F, 0.9F, new CubeDeformation(0.08F))
		.texOffs(0, 0).addBox(-27.7F, -5.0F, -5.6F, 5.0F, 5.0F, 1.8F, new CubeDeformation(0.2F))
		.texOffs(0, 0).addBox(-26.4F, -1.15F, -6.2F, 2.4F, 0.65F, 0.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-27.033F, -1.95F, -6.2F, 3.6661F, 0.65F, 0.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-27.2F, -2.75F, -6.2F, 4.0F, 0.65F, 0.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-27.033F, -3.55F, -6.2F, 3.6661F, 0.65F, 0.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-26.4F, -4.35F, -6.2F, 2.4F, 0.65F, 0.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(20.25F, -26.0F, 2.8F, 13.75F, 50.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-34.0F, -26.0F, 2.8F, 13.75F, 50.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.25F, -26.0F, 2.8F, 40.5F, 7.75F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.25F, 18.25F, 2.8F, 40.5F, 5.75F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(23.82F, -26.0F, 2.15F, 0.36F, 50.0F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-24.18F, -26.0F, 2.15F, 0.36F, 50.0F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(11.84F, 18.25F, 2.15F, 0.32F, 5.75F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(11.84F, -26.0F, 2.15F, 0.32F, 7.75F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.16F, 18.25F, 2.15F, 0.32F, 5.75F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.16F, -26.0F, 2.15F, 0.32F, 7.75F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-12.16F, 18.25F, 2.15F, 0.32F, 5.75F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-12.16F, -26.0F, 2.15F, 0.32F, 7.75F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(20.25F, 11.84F, 2.15F, 13.75F, 0.32F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-34.0F, 11.84F, 2.15F, 13.75F, 0.32F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(20.25F, -1.16F, 2.15F, 13.75F, 0.32F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-34.0F, -1.16F, 2.15F, 13.75F, 0.32F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(20.25F, -14.16F, 2.15F, 13.75F, 0.32F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-34.0F, -14.16F, 2.15F, 13.75F, 0.32F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-19.5F, 17.85F, 0.9F, 39.0F, 1.75F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-19.5F, -19.6F, 0.9F, 39.0F, 1.75F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.0F, 18.13F, 2.35F, 20.9F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 18.13F, 2.35F, 20.9F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.0F, 17.08F, 2.35F, 20.9F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 17.08F, 2.35F, 20.9F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.8702F, 16.03F, 2.35F, 16.0298F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 16.03F, 2.35F, 16.0298F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(7.8209F, 14.98F, 2.35F, 13.0791F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 14.98F, 2.35F, 13.0791F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(9.7544F, 13.93F, 2.35F, 11.1456F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 13.93F, 2.35F, 11.1456F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(11.2415F, 12.88F, 2.35F, 9.6585F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 12.88F, 2.35F, 9.6585F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(12.4517F, 11.83F, 2.35F, 8.4483F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 11.83F, 2.35F, 8.4483F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(13.4636F, 10.78F, 2.35F, 7.4364F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 10.78F, 2.35F, 7.4364F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(14.3212F, 9.73F, 2.35F, 6.5788F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 9.73F, 2.35F, 6.5788F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(15.052F, 8.68F, 2.35F, 5.848F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 8.68F, 2.35F, 5.848F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(15.6744F, 7.63F, 2.35F, 5.2256F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 7.63F, 2.35F, 5.2256F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(16.2016F, 6.58F, 2.35F, 4.6984F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 6.58F, 2.35F, 4.6984F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(16.6428F, 5.53F, 2.35F, 4.2572F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 5.53F, 2.35F, 4.2572F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(17.005F, 4.48F, 2.35F, 3.895F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 4.48F, 2.35F, 3.895F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(17.2934F, 3.43F, 2.35F, 3.6066F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 3.43F, 2.35F, 3.6066F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(17.5118F, 2.38F, 2.35F, 3.3882F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 2.38F, 2.35F, 3.3882F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(17.6627F, 1.33F, 2.35F, 3.2373F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 1.33F, 2.35F, 3.2373F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(17.7482F, 0.28F, 2.35F, 3.1518F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 0.28F, 2.35F, 3.1518F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(17.769F, -0.77F, 2.35F, 3.131F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -0.77F, 2.35F, 3.131F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(17.7255F, -1.82F, 2.35F, 3.1745F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -1.82F, 2.35F, 3.1745F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(17.6171F, -2.87F, 2.35F, 3.2829F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -2.87F, 2.35F, 3.2829F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(17.4426F, -3.92F, 2.35F, 3.4574F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -3.92F, 2.35F, 3.4574F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(17.1999F, -4.97F, 2.35F, 3.7001F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -4.97F, 2.35F, 3.7001F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(16.886F, -6.02F, 2.35F, 4.014F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -6.02F, 2.35F, 4.014F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(16.4966F, -7.07F, 2.35F, 4.4034F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -7.07F, 2.35F, 4.4034F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(16.026F, -8.12F, 2.35F, 4.874F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -8.12F, 2.35F, 4.874F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(15.4666F, -9.17F, 2.35F, 5.4334F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -9.17F, 2.35F, 5.4334F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(14.8076F, -10.22F, 2.35F, 6.0924F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -10.22F, 2.35F, 6.0924F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(14.0347F, -11.27F, 2.35F, 6.8653F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -11.27F, 2.35F, 6.8653F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(13.1265F, -12.32F, 2.35F, 7.7735F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -12.32F, 2.35F, 7.7735F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(12.0509F, -13.37F, 2.35F, 8.8491F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -13.37F, 2.35F, 8.8491F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(10.7549F, -14.42F, 2.35F, 10.1451F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -14.42F, 2.35F, 10.1451F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(9.1382F, -15.47F, 2.35F, 11.7618F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -15.47F, 2.35F, 11.7618F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(6.9602F, -16.52F, 2.35F, 13.9398F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -16.52F, 2.35F, 13.9398F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.9932F, -17.57F, 2.35F, 17.9068F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -17.57F, 2.35F, 17.9068F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.0F, -18.62F, 2.35F, 20.9F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -18.62F, 2.35F, 20.9F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.0F, -19.67F, 2.35F, 20.9F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -19.67F, 2.35F, 20.9F, 1.17F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(15.2F, -18.55F, 2.3F, 5.7F, 4.9F, 3.5F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -18.55F, 2.3F, 5.7F, 4.9F, 3.5F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(15.2F, 13.65F, 2.3F, 5.7F, 4.9F, 3.5F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 13.65F, 2.3F, 5.7F, 4.9F, 3.5F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -24.0F, 0.0F));

		PartDefinition round_vault_frame = static_bank_wall_and_opening.addOrReplaceChild("round_vault_frame", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, 0.6F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -0.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.9F, -18.28F, 1.45F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -2.0F));

		PartDefinition recessed_frame_front_bevel_liner_95_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_95_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 6.2177F));

		PartDefinition recessed_frame_front_bevel_liner_94_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_94_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 6.1523F));

		PartDefinition recessed_frame_front_bevel_liner_93_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_93_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 6.0868F));

		PartDefinition recessed_frame_front_bevel_liner_92_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_92_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 6.0214F));

		PartDefinition recessed_frame_front_bevel_liner_91_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_91_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 5.9559F));

		PartDefinition recessed_frame_front_bevel_liner_90_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_90_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 5.8905F));

		PartDefinition recessed_frame_front_bevel_liner_89_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_89_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 5.825F));

		PartDefinition recessed_frame_front_bevel_liner_88_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_88_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 5.7596F));

		PartDefinition recessed_frame_front_bevel_liner_87_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_87_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 5.6941F));

		PartDefinition recessed_frame_front_bevel_liner_86_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_86_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 5.6287F));

		PartDefinition recessed_frame_front_bevel_liner_85_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_85_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 5.5632F));

		PartDefinition recessed_frame_front_bevel_liner_84_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_84_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 5.4978F));

		PartDefinition recessed_frame_front_bevel_liner_83_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_83_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 5.4323F));

		PartDefinition recessed_frame_front_bevel_liner_82_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_82_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 5.3669F));

		PartDefinition recessed_frame_front_bevel_liner_81_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_81_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 5.3014F));

		PartDefinition recessed_frame_front_bevel_liner_80_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_80_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.1F, -22.05F, -1.4F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 5.236F));

		PartDefinition recessed_frame_front_bevel_liner_79_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_79_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 5.1705F));

		PartDefinition recessed_frame_front_bevel_liner_78_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_78_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 5.1051F));

		PartDefinition recessed_frame_front_bevel_liner_77_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_77_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 5.0396F));

		PartDefinition recessed_frame_front_bevel_liner_76_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_76_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 4.9742F));

		PartDefinition recessed_frame_front_bevel_liner_75_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_75_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 4.9087F));

		PartDefinition recessed_frame_front_bevel_liner_74_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_74_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 4.8433F));

		PartDefinition recessed_frame_front_bevel_liner_73_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_73_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 4.7778F));

		PartDefinition recessed_frame_front_bevel_liner_72_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_72_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 4.7124F));

		PartDefinition recessed_frame_front_bevel_liner_71_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_71_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 4.6469F));

		PartDefinition recessed_frame_front_bevel_liner_70_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_70_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 4.5815F));

		PartDefinition recessed_frame_front_bevel_liner_69_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_69_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 4.516F));

		PartDefinition recessed_frame_front_bevel_liner_68_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_68_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 4.4506F));

		PartDefinition recessed_frame_front_bevel_liner_67_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_67_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 4.3851F));

		PartDefinition recessed_frame_front_bevel_liner_66_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_66_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 4.3197F));

		PartDefinition recessed_frame_front_bevel_liner_65_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_65_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 4.2542F));

		PartDefinition recessed_frame_front_bevel_liner_64_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_64_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.1F, -22.05F, -1.4F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 4.1888F));

		PartDefinition recessed_frame_front_bevel_liner_63_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_63_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 4.1233F));

		PartDefinition recessed_frame_front_bevel_liner_62_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_62_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 4.0579F));

		PartDefinition recessed_frame_front_bevel_liner_61_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_61_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.9924F));

		PartDefinition recessed_frame_front_bevel_liner_60_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_60_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.927F));

		PartDefinition recessed_frame_front_bevel_liner_59_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_59_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.8615F));

		PartDefinition recessed_frame_front_bevel_liner_58_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_58_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.7961F));

		PartDefinition recessed_frame_front_bevel_liner_57_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_57_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.7306F));

		PartDefinition recessed_frame_front_bevel_liner_56_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_56_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.6652F));

		PartDefinition recessed_frame_front_bevel_liner_55_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_55_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.5997F));

		PartDefinition recessed_frame_front_bevel_liner_54_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_54_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.5343F));

		PartDefinition recessed_frame_front_bevel_liner_53_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_53_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.4688F));

		PartDefinition recessed_frame_front_bevel_liner_52_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_52_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.4034F));

		PartDefinition recessed_frame_front_bevel_liner_51_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_51_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.3379F));

		PartDefinition recessed_frame_front_bevel_liner_50_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_50_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.2725F));

		PartDefinition recessed_frame_front_bevel_liner_49_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_49_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.207F));

		PartDefinition recessed_frame_front_bevel_liner_48_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_48_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.1F, -22.05F, -1.4F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.1416F));

		PartDefinition recessed_frame_front_bevel_liner_47_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_47_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.0761F));

		PartDefinition recessed_frame_front_bevel_liner_46_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_46_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 3.0107F));

		PartDefinition recessed_frame_front_bevel_liner_45_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_45_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 2.9452F));

		PartDefinition recessed_frame_front_bevel_liner_44_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_44_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 2.8798F));

		PartDefinition recessed_frame_front_bevel_liner_43_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_43_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 2.8143F));

		PartDefinition recessed_frame_front_bevel_liner_42_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_42_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 2.7489F));

		PartDefinition recessed_frame_front_bevel_liner_41_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_41_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 2.6834F));

		PartDefinition recessed_frame_front_bevel_liner_40_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_40_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 2.618F));

		PartDefinition recessed_frame_front_bevel_liner_39_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_39_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 2.5525F));

		PartDefinition recessed_frame_front_bevel_liner_38_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_38_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 2.4871F));

		PartDefinition recessed_frame_front_bevel_liner_37_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_37_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 2.4216F));

		PartDefinition recessed_frame_front_bevel_liner_36_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_36_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 2.3562F));

		PartDefinition recessed_frame_front_bevel_liner_35_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_35_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 2.2907F));

		PartDefinition recessed_frame_front_bevel_liner_34_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_34_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 2.2253F));

		PartDefinition recessed_frame_front_bevel_liner_33_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_33_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 2.1598F));

		PartDefinition recessed_frame_front_bevel_liner_32_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_32_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.1F, -22.05F, -1.4F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 2.0944F));

		PartDefinition recessed_frame_front_bevel_liner_31_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_31_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 2.0289F));

		PartDefinition recessed_frame_front_bevel_liner_30_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_30_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 1.9635F));

		PartDefinition recessed_frame_front_bevel_liner_29_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_29_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 1.898F));

		PartDefinition recessed_frame_front_bevel_liner_28_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_28_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 1.8326F));

		PartDefinition recessed_frame_front_bevel_liner_27_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_27_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 1.7671F));

		PartDefinition recessed_frame_front_bevel_liner_26_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_26_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 1.7017F));

		PartDefinition recessed_frame_front_bevel_liner_25_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_25_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 1.6362F));

		PartDefinition recessed_frame_front_bevel_liner_24_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_24_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 1.5708F));

		PartDefinition recessed_frame_front_bevel_liner_23_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_23_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 1.5053F));

		PartDefinition recessed_frame_front_bevel_liner_22_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_22_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 1.4399F));

		PartDefinition recessed_frame_front_bevel_liner_21_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_21_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 1.3744F));

		PartDefinition recessed_frame_front_bevel_liner_20_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_20_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 1.309F));

		PartDefinition recessed_frame_front_bevel_liner_19_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_19_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 1.2435F));

		PartDefinition recessed_frame_front_bevel_liner_18_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_18_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 1.1781F));

		PartDefinition recessed_frame_front_bevel_liner_17_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_17_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 1.1126F));

		PartDefinition recessed_frame_front_bevel_liner_16_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_16_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.1F, -22.05F, -1.4F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 1.0472F));

		PartDefinition recessed_frame_front_bevel_liner_15_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_15_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.9817F));

		PartDefinition recessed_frame_front_bevel_liner_14_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_14_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.9163F));

		PartDefinition recessed_frame_front_bevel_liner_13_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_13_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.8508F));

		PartDefinition recessed_frame_front_bevel_liner_12_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_12_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition recessed_frame_front_bevel_liner_11_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_11_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.7199F));

		PartDefinition recessed_frame_front_bevel_liner_10_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_10_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.6545F));

		PartDefinition recessed_frame_front_bevel_liner_9_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_9_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.589F));

		PartDefinition recessed_frame_front_bevel_liner_8_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_8_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.5236F));

		PartDefinition recessed_frame_front_bevel_liner_7_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_7_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.4581F));

		PartDefinition recessed_frame_front_bevel_liner_6_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_6_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition recessed_frame_front_bevel_liner_5_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_5_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.3272F));

		PartDefinition recessed_frame_front_bevel_liner_4_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_4_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.8F, -18.15F, -2.4F, 3.6F, 1.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.2618F));

		PartDefinition recessed_frame_front_bevel_liner_3_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_3_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.1963F));

		PartDefinition recessed_frame_front_bevel_liner_2_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_2_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.1309F));

		PartDefinition recessed_frame_front_bevel_liner_1_r1 = round_vault_frame.addOrReplaceChild("recessed_frame_front_bevel_liner_1_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -18.28F, -0.55F, 1.8F, 1.58F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.0654F));

		PartDefinition outer_static_round_frame_3480_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_3480_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 6.0737F));

		PartDefinition outer_static_round_frame_3360_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_3360_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 5.8643F));

		PartDefinition outer_static_round_frame_3240_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_3240_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 5.6549F));

		PartDefinition outer_static_round_frame_3120_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_3120_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 5.4454F));

		PartDefinition outer_static_round_frame_2880_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_2880_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 5.0265F));

		PartDefinition outer_static_round_frame_2760_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_2760_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 4.8171F));

		PartDefinition outer_static_round_frame_2640_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_2640_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 4.6077F));

		PartDefinition outer_static_round_frame_2520_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_2520_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 4.3982F));

		PartDefinition outer_static_round_frame_2280_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_2280_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 3.9794F));

		PartDefinition outer_static_round_frame_2160_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_2160_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 3.7699F));

		PartDefinition outer_static_round_frame_2040_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_2040_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 3.5605F));

		PartDefinition outer_static_round_frame_1920_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_1920_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 3.351F));

		PartDefinition outer_static_round_frame_1680_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_1680_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 2.9322F));

		PartDefinition outer_static_round_frame_1560_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_1560_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 2.7227F));

		PartDefinition outer_static_round_frame_1440_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_1440_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 2.5133F));

		PartDefinition outer_static_round_frame_1320_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_1320_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 2.3038F));

		PartDefinition outer_static_round_frame_1080_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_1080_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 1.885F));

		PartDefinition outer_static_round_frame_960_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_960_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 1.6755F));

		PartDefinition outer_static_round_frame_840_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_840_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 1.4661F));

		PartDefinition outer_static_round_frame_720_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_720_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 1.2566F));

		PartDefinition outer_static_round_frame_480_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_480_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 0.8378F));

		PartDefinition outer_static_round_frame_360_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_360_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 0.6283F));

		PartDefinition outer_static_round_frame_240_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_240_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 0.4189F));

		PartDefinition outer_static_round_frame_120_r1 = round_vault_frame.addOrReplaceChild("outer_static_round_frame_120_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -22.05F, -1.95F, 4.2F, 3.5F, 3.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.55F, 0.0F, 0.0F, 0.2094F));

		PartDefinition dark_vault_interior_visible_when_open = static_bank_wall_and_opening.addOrReplaceChild("dark_vault_interior_visible_when_open", CubeListBuilder.create().texOffs(0, 0).addBox(18.4F, -18.2F, -1.2F, 2.0F, 36.4F, 8.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.4F, -18.2F, -1.2F, 2.0F, 36.4F, 8.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-18.4F, 16.2F, -1.2F, 36.8F, 2.0F, 8.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-18.4F, -18.2F, -1.2F, 36.8F, 2.0F, 8.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.05F, -18.25F, 0.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 2.0F));

		PartDefinition recessed_frame_smooth_tunnel_liner_355_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_355_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 6.1959F));

		PartDefinition recessed_frame_smooth_tunnel_liner_350_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_350_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 6.1087F));

		PartDefinition recessed_frame_smooth_tunnel_liner_345_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_345_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 6.0214F));

		PartDefinition recessed_frame_smooth_tunnel_liner_340_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_340_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 5.9341F));

		PartDefinition recessed_frame_smooth_tunnel_liner_335_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_335_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 5.8469F));

		PartDefinition recessed_frame_smooth_tunnel_liner_330_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_330_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 5.7596F));

		PartDefinition recessed_frame_smooth_tunnel_liner_325_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_325_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 5.6723F));

		PartDefinition recessed_frame_smooth_tunnel_liner_320_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_320_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 5.5851F));

		PartDefinition recessed_frame_smooth_tunnel_liner_315_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_315_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 5.4978F));

		PartDefinition recessed_frame_smooth_tunnel_liner_310_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_310_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 5.4105F));

		PartDefinition recessed_frame_smooth_tunnel_liner_305_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_305_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 5.3233F));

		PartDefinition recessed_frame_smooth_tunnel_liner_300_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_300_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 5.236F));

		PartDefinition recessed_frame_smooth_tunnel_liner_295_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_295_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 5.1487F));

		PartDefinition recessed_frame_smooth_tunnel_liner_290_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_290_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 5.0615F));

		PartDefinition recessed_frame_smooth_tunnel_liner_285_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_285_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 4.9742F));

		PartDefinition recessed_frame_smooth_tunnel_liner_280_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_280_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 4.8869F));

		PartDefinition recessed_frame_smooth_tunnel_liner_275_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_275_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 4.7997F));

		PartDefinition recessed_frame_smooth_tunnel_liner_270_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_270_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 4.7124F));

		PartDefinition recessed_frame_smooth_tunnel_liner_265_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_265_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 4.6251F));

		PartDefinition recessed_frame_smooth_tunnel_liner_260_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_260_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 4.5379F));

		PartDefinition recessed_frame_smooth_tunnel_liner_255_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_255_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 4.4506F));

		PartDefinition recessed_frame_smooth_tunnel_liner_250_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_250_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 4.3633F));

		PartDefinition recessed_frame_smooth_tunnel_liner_245_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_245_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 4.2761F));

		PartDefinition recessed_frame_smooth_tunnel_liner_240_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_240_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 4.1888F));

		PartDefinition recessed_frame_smooth_tunnel_liner_235_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_235_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 4.1015F));

		PartDefinition recessed_frame_smooth_tunnel_liner_230_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_230_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 4.0143F));

		PartDefinition recessed_frame_smooth_tunnel_liner_225_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_225_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 3.927F));

		PartDefinition recessed_frame_smooth_tunnel_liner_220_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_220_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 3.8397F));

		PartDefinition recessed_frame_smooth_tunnel_liner_215_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_215_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 3.7525F));

		PartDefinition recessed_frame_smooth_tunnel_liner_210_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_210_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 3.6652F));

		PartDefinition recessed_frame_smooth_tunnel_liner_205_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_205_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 3.5779F));

		PartDefinition recessed_frame_smooth_tunnel_liner_200_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_200_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 3.4907F));

		PartDefinition recessed_frame_smooth_tunnel_liner_195_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_195_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 3.4034F));

		PartDefinition recessed_frame_smooth_tunnel_liner_190_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_190_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 3.3161F));

		PartDefinition recessed_frame_smooth_tunnel_liner_185_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_185_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 3.2289F));

		PartDefinition recessed_frame_smooth_tunnel_liner_180_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_180_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 3.1416F));

		PartDefinition recessed_frame_smooth_tunnel_liner_175_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_175_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 3.0543F));

		PartDefinition recessed_frame_smooth_tunnel_liner_170_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_170_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 2.9671F));

		PartDefinition recessed_frame_smooth_tunnel_liner_165_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_165_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 2.8798F));

		PartDefinition recessed_frame_smooth_tunnel_liner_160_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_160_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 2.7925F));

		PartDefinition recessed_frame_smooth_tunnel_liner_155_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_155_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 2.7053F));

		PartDefinition recessed_frame_smooth_tunnel_liner_150_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_150_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 2.618F));

		PartDefinition recessed_frame_smooth_tunnel_liner_145_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_145_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 2.5307F));

		PartDefinition recessed_frame_smooth_tunnel_liner_140_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_140_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 2.4435F));

		PartDefinition recessed_frame_smooth_tunnel_liner_135_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_135_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 2.3562F));

		PartDefinition recessed_frame_smooth_tunnel_liner_130_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_130_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 2.2689F));

		PartDefinition recessed_frame_smooth_tunnel_liner_125_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_125_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 2.1817F));

		PartDefinition recessed_frame_smooth_tunnel_liner_120_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_120_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 2.0944F));

		PartDefinition recessed_frame_smooth_tunnel_liner_115_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_115_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 2.0071F));

		PartDefinition recessed_frame_smooth_tunnel_liner_110_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_110_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 1.9199F));

		PartDefinition recessed_frame_smooth_tunnel_liner_105_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_105_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 1.8326F));

		PartDefinition recessed_frame_smooth_tunnel_liner_100_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_100_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 1.7453F));

		PartDefinition recessed_frame_smooth_tunnel_liner_95_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_95_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 1.6581F));

		PartDefinition recessed_frame_smooth_tunnel_liner_90_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_90_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 1.5708F));

		PartDefinition recessed_frame_smooth_tunnel_liner_85_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_85_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 1.4835F));

		PartDefinition recessed_frame_smooth_tunnel_liner_80_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_80_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 1.3963F));

		PartDefinition recessed_frame_smooth_tunnel_liner_75_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_75_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 1.309F));

		PartDefinition recessed_frame_smooth_tunnel_liner_70_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_70_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 1.2217F));

		PartDefinition recessed_frame_smooth_tunnel_liner_65_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_65_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 1.1345F));

		PartDefinition recessed_frame_smooth_tunnel_liner_60_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_60_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 1.0472F));

		PartDefinition recessed_frame_smooth_tunnel_liner_55_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_55_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.9599F));

		PartDefinition recessed_frame_smooth_tunnel_liner_50_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_50_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.8727F));

		PartDefinition recessed_frame_smooth_tunnel_liner_45_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_45_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition recessed_frame_smooth_tunnel_liner_40_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_40_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.6981F));

		PartDefinition recessed_frame_smooth_tunnel_liner_35_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_35_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.6109F));

		PartDefinition recessed_frame_smooth_tunnel_liner_30_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_30_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.5236F));

		PartDefinition recessed_frame_smooth_tunnel_liner_25_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_25_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.4363F));

		PartDefinition recessed_frame_smooth_tunnel_liner_20_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_20_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.3491F));

		PartDefinition recessed_frame_smooth_tunnel_liner_15_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_15_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.2618F));

		PartDefinition recessed_frame_smooth_tunnel_liner_10_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_10_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition recessed_frame_smooth_tunnel_liner_5_r1 = dark_vault_interior_visible_when_open.addOrReplaceChild("recessed_frame_smooth_tunnel_liner_5_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -18.25F, 2.15F, 2.1F, 1.4F, 7.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.0873F));

		PartDefinition static_right_hinge_mounts = static_bank_wall_and_opening.addOrReplaceChild("static_right_hinge_mounts", CubeListBuilder.create().texOffs(0, 0).addBox(-2.7F, -18.0F, -2.1F, 2.9F, 36.0F, 3.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-4.2F, -18.5F, -2.7F, 6.5F, 11.0F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-4.2F, 7.5F, -2.7F, 6.5F, 11.0F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-4.2F, 14.0F, -3.4F, 3.2F, 4.0F, 3.4F, new CubeDeformation(0.35F))
		.texOffs(0, 0).addBox(-4.2F, 10.0F, -3.4F, 3.2F, 4.0F, 3.4F, new CubeDeformation(0.35F))
		.texOffs(0, 0).addBox(-4.2F, -12.0F, -3.4F, 3.2F, 4.0F, 3.4F, new CubeDeformation(0.35F))
		.texOffs(0, 0).addBox(-4.2F, -16.0F, -3.4F, 3.2F, 4.0F, 3.4F, new CubeDeformation(0.35F)), PartPose.offset(-20.0F, 0.0F, -3.0F));

		PartDefinition door_leaf_hinged_heavy_round_slab = bank_vault_door_block_entity_root.addOrReplaceChild("door_leaf_hinged_heavy_round_slab", CubeListBuilder.create().texOffs(0, 0).addBox(14.6216F, 14.142F, -1.2F, 10.7569F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(10.9796F, 12.242F, -1.2F, 18.0408F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(8.7489F, 10.342F, -1.2F, 22.5022F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(7.1708F, 8.442F, -1.2F, 25.6583F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(6.0226F, 6.542F, -1.2F, 27.9548F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(5.2038F, 4.642F, -1.2F, 29.5924F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.6616F, 2.742F, -1.2F, 30.6769F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.3671F, 0.842F, -1.2F, 31.2658F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.3065F, -1.058F, -1.2F, 31.3871F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.4769F, -2.958F, -1.2F, 31.0463F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.8862F, -4.858F, -1.2F, 30.2276F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(5.5547F, -6.758F, -1.2F, 28.8907F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(6.5208F, -8.658F, -1.2F, 26.9583F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(7.8556F, -10.558F, -1.2F, 24.2889F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(9.7011F, -12.458F, -1.2F, 20.5978F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(12.4219F, -14.358F, -1.2F, 15.1562F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(18.748F, -16.258F, -1.2F, 2.504F, 1.558F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(17.85F, -18.65F, -1.95F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(18.45F, -11.4F, -2.6F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(18.9F, -8.7F, -2.8F, 2.2F, 1.0F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(19.3F, -15.2F, -2.85F, 1.4F, 11.0F, 1.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.4F, -18.0F, -3.0F, 4.2F, 36.0F, 1.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.2F, 6.2F, -4.0F, 3.8F, 9.6F, 2.8F, new CubeDeformation(0.45F))
		.texOffs(0, 0).addBox(-2.2F, -15.8F, -4.0F, 3.8F, 9.6F, 2.8F, new CubeDeformation(0.45F))
		.texOffs(0, 0).addBox(37.8F, 2.0F, -3.6F, 1.4F, 4.0F, 2.1F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(37.8F, -9.0F, -3.6F, 1.4F, 4.0F, 2.1F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(38.3F, -6.2F, -4.0F, 3.7F, 9.4F, 1.4F, new CubeDeformation(0.18F))
		.texOffs(0, 0).addBox(12.5144F, 13.78F, -1.32F, 14.9711F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(9.728F, 11.88F, -1.32F, 20.5439F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(7.8438F, 9.98F, -1.32F, 24.3124F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(6.4792F, 8.08F, -1.32F, 27.0417F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(5.4868F, 6.18F, -1.32F, 29.0264F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.7937F, 4.28F, -1.32F, 30.4127F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.3599F, 2.38F, -1.32F, 31.2802F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.1642F, 0.48F, -1.32F, 31.6716F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.1977F, -1.42F, -1.32F, 31.6046F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.4619F, -3.32F, -1.32F, 31.0762F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.969F, -5.22F, -1.32F, 30.062F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(5.7448F, -7.12F, -1.32F, 28.5105F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(6.8367F, -9.02F, -1.32F, 26.3265F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(8.3333F, -10.92F, -1.32F, 23.3333F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(10.4224F, -12.82F, -1.32F, 19.1552F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(13.6651F, -14.72F, -1.32F, 12.6698F, 0.382F, 2.44F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(12.7F, -16.7F, -1.35F, 14.6F, 0.84F, 2.47F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(12.7F, 15.86F, -1.35F, 14.6F, 0.84F, 2.47F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(3.75F, -13.5F, -3.25F, 0.9F, 27.0F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(19.45F, -12.5F, -3.5F, 1.1F, 25.0F, 0.95F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(6.5F, -0.55F, -3.52F, 27.0F, 1.1F, 0.97F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(15.7999F, 16.43F, 1.28F, 8.4002F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(12.7666F, 15.38F, 1.28F, 14.4668F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(10.7914F, 14.33F, 1.28F, 18.4173F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(9.2729F, 13.28F, 1.28F, 21.4542F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(8.036F, 12.23F, 1.28F, 23.928F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(7.0001F, 11.18F, 1.28F, 25.9999F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(6.12F, 10.13F, 1.28F, 27.76F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(5.3676F, 9.08F, 1.28F, 29.2648F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.7241F, 8.03F, 1.28F, 30.5518F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.1761F, 6.98F, 1.28F, 31.6477F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(3.7141F, 5.93F, 1.28F, 32.5718F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(3.3308F, 4.88F, 1.28F, 33.3384F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(3.0209F, 3.83F, 1.28F, 33.9581F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.7805F, 2.78F, 1.28F, 34.439F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.6067F, 1.73F, 1.28F, 34.7866F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.4975F, 0.68F, 1.28F, 35.0051F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.4516F, -0.37F, 1.28F, 35.0967F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.4687F, -1.42F, 1.28F, 35.0626F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.5488F, -2.47F, 1.28F, 34.9023F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.6929F, -3.52F, 1.28F, 34.6141F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.9026F, -4.57F, 1.28F, 34.1948F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(3.1803F, -5.62F, 1.28F, 33.6394F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(3.5295F, -6.67F, 1.28F, 32.9411F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(3.9547F, -7.72F, 1.28F, 32.0905F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.4624F, -8.77F, 1.28F, 31.0752F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(5.0608F, -9.82F, 1.28F, 29.8783F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(5.7615F, -10.87F, 1.28F, 28.477F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(6.5804F, -11.92F, 1.28F, 26.8393F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(7.5408F, -12.97F, 1.28F, 24.9185F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(8.6786F, -14.02F, 1.28F, 22.6428F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(10.0546F, -15.07F, 1.28F, 19.8908F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(11.7877F, -16.12F, 1.28F, 16.4247F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(14.1889F, -17.17F, 1.28F, 11.6222F, 1.22F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(16.5826F, 15.13F, -1.66F, 6.8347F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(11.6921F, 13.23F, -1.66F, 16.6157F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(9.0848F, 11.33F, -1.66F, 21.8305F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(7.2703F, 9.43F, -1.66F, 25.4593F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(5.9385F, 7.53F, -1.66F, 28.123F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.9604F, 5.63F, -1.66F, 30.0791F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.2701F, 3.73F, -1.66F, 31.4598F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(3.8305F, 1.83F, -1.66F, 32.339F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(3.6215F, -0.07F, -1.66F, 32.7569F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(3.6343F, -1.97F, -1.66F, 32.7314F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(3.8693F, -3.87F, -1.66F, 32.2613F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(4.3367F, -5.77F, -1.66F, 31.3267F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(5.058F, -7.67F, -1.66F, 29.8839F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(6.0729F, -9.57F, -1.66F, 27.8542F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(7.4522F, -11.47F, -1.66F, 25.0955F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(9.3366F, -13.37F, -1.66F, 21.3268F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(12.0785F, -15.27F, -1.66F, 15.843F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(17.8617F, -17.17F, -1.66F, 4.2766F, 1.82F, 0.38F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(3.0F, -15.2F, -1.62F, 1.35F, 30.4F, 3.48F, new CubeDeformation(0.0F)), PartPose.offset(-20.0F, -24.0F, -3.2F));

		PartDefinition raised_diagonal_locking_arm_2925_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("raised_diagonal_locking_arm_2925_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.525F, -16.7F, -0.575F, 1.05F, 10.2F, 1.15F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.575F, 0.0F, 0.0F, 5.1051F));

		PartDefinition raised_diagonal_locking_arm_2025_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("raised_diagonal_locking_arm_2025_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.525F, -16.7F, -0.575F, 1.05F, 10.2F, 1.15F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.575F, 0.0F, 0.0F, 3.5343F));

		PartDefinition raised_diagonal_locking_arm_1125_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("raised_diagonal_locking_arm_1125_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.525F, -16.7F, -0.575F, 1.05F, 10.2F, 1.15F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.575F, 0.0F, 0.0F, 1.9635F));

		PartDefinition raised_diagonal_locking_arm_225_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("raised_diagonal_locking_arm_225_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.525F, -16.7F, -0.575F, 1.05F, 10.2F, 1.15F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.575F, 0.0F, 0.0F, 0.3927F));

		PartDefinition heavy_radial_reinforcing_spoke_3150_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("heavy_radial_reinforcing_spoke_3150_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.7F, -15.2F, -0.7F, 1.4F, 11.0F, 1.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.55F, -11.4F, -0.45F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.15F, 0.0F, 0.0F, 5.4978F));

		PartDefinition heavy_radial_reinforcing_spoke_2700_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("heavy_radial_reinforcing_spoke_2700_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.7F, -15.2F, -0.7F, 1.4F, 11.0F, 1.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.1F, -8.7F, -0.65F, 2.2F, 1.0F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.55F, -11.4F, -0.45F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.15F, -18.65F, 0.2F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.15F, 0.0F, 0.0F, 4.7124F));

		PartDefinition heavy_radial_reinforcing_spoke_2250_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("heavy_radial_reinforcing_spoke_2250_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.7F, -15.2F, -0.7F, 1.4F, 11.0F, 1.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.55F, -11.4F, -0.45F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.15F, 0.0F, 0.0F, 3.927F));

		PartDefinition heavy_radial_reinforcing_spoke_1800_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("heavy_radial_reinforcing_spoke_1800_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.7F, -15.2F, -0.7F, 1.4F, 11.0F, 1.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.1F, -8.7F, -0.65F, 2.2F, 1.0F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.55F, -11.4F, -0.45F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.15F, -18.65F, 0.2F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.15F, 0.0F, 0.0F, 3.1416F));

		PartDefinition heavy_radial_reinforcing_spoke_1350_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("heavy_radial_reinforcing_spoke_1350_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.7F, -15.2F, -0.7F, 1.4F, 11.0F, 1.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.55F, -11.4F, -0.45F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.15F, 0.0F, 0.0F, 2.3562F));

		PartDefinition heavy_radial_reinforcing_spoke_900_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("heavy_radial_reinforcing_spoke_900_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.7F, -15.2F, -0.7F, 1.4F, 11.0F, 1.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.1F, -8.7F, -0.65F, 2.2F, 1.0F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.55F, -11.4F, -0.45F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.15F, -18.65F, 0.2F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.15F, 0.0F, 0.0F, 1.5708F));

		PartDefinition heavy_radial_reinforcing_spoke_450_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("heavy_radial_reinforcing_spoke_450_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.7F, -15.2F, -0.7F, 1.4F, 11.0F, 1.4F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.55F, -11.4F, -0.45F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.15F, 0.0F, 0.0F, 0.7854F));

		PartDefinition inner_locking_gear_notch_3300_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("inner_locking_gear_notch_3300_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.1F, -8.7F, -0.65F, 2.2F, 1.0F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.55F, -11.4F, -0.45F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.15F, -18.65F, 0.2F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.15F, 0.0F, 0.0F, 5.7596F));

		PartDefinition inner_locking_gear_notch_3000_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("inner_locking_gear_notch_3000_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.1F, -8.7F, -0.65F, 2.2F, 1.0F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.55F, -11.4F, -0.45F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.15F, -18.65F, 0.2F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.15F, 0.0F, 0.0F, 5.236F));

		PartDefinition inner_locking_gear_notch_2400_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("inner_locking_gear_notch_2400_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.1F, -8.7F, -0.65F, 2.2F, 1.0F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.55F, -11.4F, -0.45F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.15F, -18.65F, 0.2F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.15F, 0.0F, 0.0F, 4.1888F));

		PartDefinition inner_locking_gear_notch_2100_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("inner_locking_gear_notch_2100_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.1F, -8.7F, -0.65F, 2.2F, 1.0F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.55F, -11.4F, -0.45F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.15F, -18.65F, 0.2F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.15F, 0.0F, 0.0F, 3.6652F));

		PartDefinition inner_locking_gear_notch_1500_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("inner_locking_gear_notch_1500_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.1F, -8.7F, -0.65F, 2.2F, 1.0F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.55F, -11.4F, -0.45F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.15F, -18.65F, 0.2F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.15F, 0.0F, 0.0F, 2.618F));

		PartDefinition inner_locking_gear_notch_1200_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("inner_locking_gear_notch_1200_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.1F, -8.7F, -0.65F, 2.2F, 1.0F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.55F, -11.4F, -0.45F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.15F, -18.65F, 0.2F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.15F, 0.0F, 0.0F, 2.0944F));

		PartDefinition inner_locking_gear_notch_600_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("inner_locking_gear_notch_600_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.1F, -8.7F, -0.65F, 2.2F, 1.0F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.55F, -11.4F, -0.45F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.15F, -18.65F, 0.2F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.15F, 0.0F, 0.0F, 1.0472F));

		PartDefinition inner_locking_gear_notch_300_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("inner_locking_gear_notch_300_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.1F, -8.7F, -0.65F, 2.2F, 1.0F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.55F, -11.4F, -0.45F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.15F, -18.65F, 0.2F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -2.15F, 0.0F, 0.0F, 0.5236F));

		PartDefinition raised_inner_door_ring_3450_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("raised_inner_door_ring_3450_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.55F, -11.4F, -0.725F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -1.875F, 0.0F, 0.0F, 6.0214F));

		PartDefinition raised_inner_door_ring_2850_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("raised_inner_door_ring_2850_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.55F, -11.4F, -0.725F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -1.875F, 0.0F, 0.0F, 4.9742F));

		PartDefinition raised_inner_door_ring_2550_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("raised_inner_door_ring_2550_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.55F, -11.4F, -0.725F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -1.875F, 0.0F, 0.0F, 4.4506F));

		PartDefinition raised_inner_door_ring_1950_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("raised_inner_door_ring_1950_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.55F, -11.4F, -0.725F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -1.875F, 0.0F, 0.0F, 3.4034F));

		PartDefinition raised_inner_door_ring_1650_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("raised_inner_door_ring_1650_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.55F, -11.4F, -0.725F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -1.875F, 0.0F, 0.0F, 2.8798F));

		PartDefinition raised_inner_door_ring_1050_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("raised_inner_door_ring_1050_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.55F, -11.4F, -0.725F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -1.875F, 0.0F, 0.0F, 1.8326F));

		PartDefinition raised_inner_door_ring_750_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("raised_inner_door_ring_750_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.55F, -11.4F, -0.725F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -1.875F, 0.0F, 0.0F, 1.309F));

		PartDefinition raised_inner_door_ring_150_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("raised_inner_door_ring_150_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.55F, -11.4F, -0.725F, 3.1F, 1.2F, 1.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -1.875F, 0.0F, 0.0F, 0.2618F));

		PartDefinition deep_outer_door_rim_3500_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_3500_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 6.1087F));

		PartDefinition deep_outer_door_rim_3400_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_3400_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 5.9341F));

		PartDefinition deep_outer_door_rim_3200_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_3200_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 5.5851F));

		PartDefinition deep_outer_door_rim_3100_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_3100_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 5.4105F));

		PartDefinition deep_outer_door_rim_2900_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_2900_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 5.0615F));

		PartDefinition deep_outer_door_rim_2800_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_2800_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 4.8869F));

		PartDefinition deep_outer_door_rim_2600_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_2600_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 4.5379F));

		PartDefinition deep_outer_door_rim_2500_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_2500_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 4.3633F));

		PartDefinition deep_outer_door_rim_2300_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_2300_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 4.0143F));

		PartDefinition deep_outer_door_rim_2200_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_2200_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 3.8397F));

		PartDefinition deep_outer_door_rim_2000_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_2000_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 3.4907F));

		PartDefinition deep_outer_door_rim_1900_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_1900_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 3.3161F));

		PartDefinition deep_outer_door_rim_1700_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_1700_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 2.9671F));

		PartDefinition deep_outer_door_rim_1600_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_1600_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 2.7925F));

		PartDefinition deep_outer_door_rim_1400_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_1400_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 2.4435F));

		PartDefinition deep_outer_door_rim_1300_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_1300_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 2.2689F));

		PartDefinition deep_outer_door_rim_1100_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_1100_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 1.9199F));

		PartDefinition deep_outer_door_rim_1000_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_1000_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 1.7453F));

		PartDefinition deep_outer_door_rim_800_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_800_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 1.3963F));

		PartDefinition deep_outer_door_rim_700_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_700_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 1.2217F));

		PartDefinition deep_outer_door_rim_500_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_500_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 0.8727F));

		PartDefinition deep_outer_door_rim_400_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_400_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 0.6981F));

		PartDefinition deep_outer_door_rim_200_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_200_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 0.3491F));

		PartDefinition deep_outer_door_rim_100_r1 = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("deep_outer_door_rim_100_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.15F, -18.65F, -1.9F, 4.3F, 2.5F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.0F, 0.0F, -0.05F, 0.0F, 0.0F, 0.1745F));

		PartDefinition locking_wheel_spins_before_swing = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("locking_wheel_spins_before_swing", CubeListBuilder.create().texOffs(0, 0).addBox(-0.275F, -5.0F, -0.75F, 0.55F, 3.6F, 0.85F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.725F, -5.275F, -0.8F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.1325F, 0.912F, -1.0F, 2.265F, 0.738F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.6225F, 0.012F, -1.0F, 3.245F, 0.738F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.537F, -0.888F, -1.0F, 3.0741F, 0.738F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.6874F, -1.788F, -1.0F, 1.3748F, 0.738F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.55F, -6.75F, -1.2F, 1.1F, 1.1F, 1.4F, new CubeDeformation(0.18F)), PartPose.offset(20.0F, 0.0F, -3.0F));

		PartDefinition wheel_knob_270_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("wheel_knob_270_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.55F, -0.55F, -0.7F, 1.1F, 1.1F, 1.4F, new CubeDeformation(0.18F)), PartPose.offsetAndRotation(6.2F, 0.0F, -0.5F, 0.0F, 0.0F, 4.7124F));

		PartDefinition wheel_knob_180_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("wheel_knob_180_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.55F, -0.55F, -0.7F, 1.1F, 1.1F, 1.4F, new CubeDeformation(0.18F)), PartPose.offsetAndRotation(0.0F, 6.2F, -0.5F, 0.0F, 0.0F, 3.1416F));

		PartDefinition wheel_knob_90_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("wheel_knob_90_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.55F, -0.55F, -0.7F, 1.1F, 1.1F, 1.4F, new CubeDeformation(0.18F)), PartPose.offsetAndRotation(-6.2F, 0.0F, -0.5F, 0.0F, 0.0F, 1.5708F));

		PartDefinition locking_wheel_outer_ring_3400_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_3400_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 5.9341F));

		PartDefinition locking_wheel_outer_ring_3200_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_3200_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 5.5851F));

		PartDefinition locking_wheel_outer_ring_3000_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_3000_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 5.236F));

		PartDefinition locking_wheel_outer_ring_2800_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_2800_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 4.8869F));

		PartDefinition locking_wheel_outer_ring_2600_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_2600_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 4.5379F));

		PartDefinition locking_wheel_outer_ring_2400_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_2400_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 4.1888F));

		PartDefinition locking_wheel_outer_ring_2200_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_2200_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 3.8397F));

		PartDefinition locking_wheel_outer_ring_2000_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_2000_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 3.4907F));

		PartDefinition locking_wheel_outer_ring_1800_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_1800_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.275F, -5.0F, -0.4F, 0.55F, 3.6F, 0.85F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 3.1416F));

		PartDefinition locking_wheel_outer_ring_1600_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_1600_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 2.7925F));

		PartDefinition locking_wheel_outer_ring_1400_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_1400_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 2.4435F));

		PartDefinition locking_wheel_outer_ring_1200_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_1200_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 2.0944F));

		PartDefinition locking_wheel_outer_ring_1000_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_1000_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 1.7453F));

		PartDefinition locking_wheel_outer_ring_800_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_800_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 1.3963F));

		PartDefinition locking_wheel_outer_ring_600_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_600_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 1.0472F));

		PartDefinition locking_wheel_outer_ring_400_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_400_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 0.6981F));

		PartDefinition locking_wheel_outer_ring_200_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_outer_ring_200_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.725F, -5.275F, -0.45F, 1.45F, 0.55F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.35F, 0.0F, 0.0F, 0.3491F));

		PartDefinition locking_wheel_spoke_3150_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_spoke_3150_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.275F, -5.0F, -0.425F, 0.55F, 3.6F, 0.85F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.325F, 0.0F, 0.0F, 5.4978F));

		PartDefinition locking_wheel_spoke_2700_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_spoke_2700_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.275F, -5.0F, -0.425F, 0.55F, 3.6F, 0.85F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.325F, 0.0F, 0.0F, 4.7124F));

		PartDefinition locking_wheel_spoke_2250_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_spoke_2250_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.275F, -5.0F, -0.425F, 0.55F, 3.6F, 0.85F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.325F, 0.0F, 0.0F, 3.927F));

		PartDefinition locking_wheel_spoke_1350_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_spoke_1350_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.275F, -5.0F, -0.425F, 0.55F, 3.6F, 0.85F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.325F, 0.0F, 0.0F, 2.3562F));

		PartDefinition locking_wheel_spoke_900_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_spoke_900_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.275F, -5.0F, -0.425F, 0.55F, 3.6F, 0.85F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.325F, 0.0F, 0.0F, 1.5708F));

		PartDefinition locking_wheel_spoke_450_r1 = locking_wheel_spins_before_swing.addOrReplaceChild("locking_wheel_spoke_450_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.275F, -5.0F, -0.425F, 0.55F, 3.6F, 0.85F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.325F, 0.0F, 0.0F, 0.7854F));

		PartDefinition bolt_left_retracts = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("bolt_left_retracts", CubeListBuilder.create().texOffs(0, 0).addBox(5.1F, -0.9F, -0.5F, 13.4F, 1.8F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(17.4F, -2.2F, -0.9F, 2.6F, 4.4F, 1.9F, new CubeDeformation(0.0F)), PartPose.offset(20.0F, 0.0F, -2.8F));

		PartDefinition bolt_right_retracts = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("bolt_right_retracts", CubeListBuilder.create().texOffs(0, 0).addBox(-18.5F, -0.9F, -0.5F, 13.4F, 1.8F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.0F, -2.2F, -0.9F, 2.6F, 4.4F, 1.9F, new CubeDeformation(0.0F)), PartPose.offset(20.0F, 0.0F, -2.8F));

		PartDefinition bolt_top_retracts = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("bolt_top_retracts", CubeListBuilder.create().texOffs(0, 0).addBox(-0.8F, -15.0F, -0.5F, 1.6F, 9.5F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.4F, -16.8F, -0.9F, 4.8F, 3.3F, 1.9F, new CubeDeformation(0.0F)), PartPose.offset(20.0F, 0.0F, -2.8F));

		PartDefinition bolt_bottom_retracts = door_leaf_hinged_heavy_round_slab.addOrReplaceChild("bolt_bottom_retracts", CubeListBuilder.create().texOffs(0, 0).addBox(-0.8F, 5.5F, -0.5F, 1.6F, 9.5F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.4F, 13.5F, -0.9F, 4.8F, 3.3F, 1.9F, new CubeDeformation(0.0F)), PartPose.offset(20.0F, 0.0F, -2.8F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	public void applyAnimation(float progress) {
		float clamped = Mth.clamp(progress, 0.0F, 1.0F);
		float unlock = smooth(Mth.clamp(clamped / 0.18F, 0.0F, 1.0F));
		float swing = smooth(Mth.clamp((clamped - 0.18F) / 0.82F, 0.0F, 1.0F));
		float overshoot = Mth.sin(Mth.clamp((clamped - 0.78F) / 0.22F, 0.0F, 1.0F) * (float) Math.PI);

		door_leaf_hinged_heavy_round_slab.yRot = (DOOR_OPEN_DEGREES * swing + (DOOR_OVERSHOOT_DEGREES - DOOR_OPEN_DEGREES) * overshoot) * DEG_TO_RAD;
		locking_wheel_spins_before_swing.zRot = WHEEL_SPIN_DEGREES * unlock * DEG_TO_RAD;

		bolt_left_retracts.x = 20.0F - BOLT_RETRACTION_PIXELS * unlock;
		bolt_right_retracts.x = 20.0F + BOLT_RETRACTION_PIXELS * unlock;
		bolt_top_retracts.y = BOLT_RETRACTION_PIXELS * unlock;
		bolt_bottom_retracts.y = -BOLT_RETRACTION_PIXELS * unlock;
	}

	private static float smooth(float value) {
		float clamped = Mth.clamp(value, 0.0F, 1.0F);
		return clamped * clamped * (3.0F - 2.0F * clamped);
	}


	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		bank_vault_door_block_entity_root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}

	public void renderItemPreviewToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		boolean staticVisible = static_bank_wall_and_opening.visible;
		static_bank_wall_and_opening.visible = false;
		try {
			bank_vault_door_block_entity_root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		} finally {
			static_bank_wall_and_opening.visible = staticVisible;
		}
	}
}
