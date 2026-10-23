package com.oldrepublic.client.space;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The three starfighters' models (spec MILESTONE_5.md 5.5), one layer each:
 *
 * <ul>
 * <li><b>Hymn interceptor</b>: a slim black hull with a needle nose, a ringed engine, swept blade
 * wings ending in tall vertical blades, a dorsal fin and two gun rails under the wing roots;</li>
 * <li><b>kajidic raider</b>: a squat, boxy heavy fighter with stub wings, wingtip gun pods, twin
 * engines, a belly missile rack and a welded patch on its flank;</li>
 * <li><b>Hask fighter</b>: a lean hull with forward-swept wings ending in down-turned tips, a tall
 * dorsal fin, one engine and two cannons at the wing roots.</li>
 * </ul>
 *
 * <p>Model units are an eighth of a block (the renderer scales by 2); vanilla model space, y down:
 * +x is the fighter's right, −y up and +z the nose, centred on the middle of the entity's box.
 * Nothing moves inside the model: the renderer turns, pitches and banks the whole. The 128 × 64
 * textures' box layouts are mirrored in {@code tools/assets/gen_textures_space.py} (each part's
 * {@code texOffs} below); each has a {@code _glow} texture of the same layout for the exhausts.
 */
public class StarfighterModel extends EntityModel<StarfighterRenderState> {
	public static final ModelLayerLocation HYMN = new ModelLayerLocation(OldRepublicMod.id("hymn_interceptor"), "main");
	public static final ModelLayerLocation RAIDER = new ModelLayerLocation(OldRepublicMod.id("kajidic_raider"), "main");
	public static final ModelLayerLocation HASK = new ModelLayerLocation(OldRepublicMod.id("hask_fighter"), "main");
	public static final int TEX_W = 128;
	public static final int TEX_H = 64;

	public StarfighterModel(ModelPart root) {
		super(root);
	}

	/** The Hymn interceptor: black and violet, blade-winged. */
	public static LayerDefinition createHymnLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2f, -2f, -11f, 4f, 4f, 20f), PartPose.ZERO);
		root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(48, 0).addBox(-1f, -1f, 9f, 2f, 2f, 5f), PartPose.ZERO);
		root.addOrReplaceChild("canopy", CubeListBuilder.create().texOffs(48, 8).addBox(-1.5f, -4f, 0f, 3f, 2f, 6f), PartPose.ZERO);
		root.addOrReplaceChild("engine", CubeListBuilder.create().texOffs(66, 0).addBox(-3f, -3f, -15f, 6f, 6f, 4f), PartPose.ZERO);
		root.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(66, 10).addBox(-0.5f, -6f, -10f, 1f, 4f, 6f), PartPose.ZERO);
		root.addOrReplaceChild("wing_right", CubeListBuilder.create()
				.texOffs(0, 24).addBox(0f, -0.5f, -4f, 10f, 1f, 6f)
				.texOffs(32, 24).addBox(9.5f, -3f, -5f, 1f, 4f, 8f),
				PartPose.offsetAndRotation(2f, 0f, -2f, 0f, 0.35f, 0.15f));
		root.addOrReplaceChild("wing_left", CubeListBuilder.create().mirror()
				.texOffs(0, 24).addBox(-10f, -0.5f, -4f, 10f, 1f, 6f)
				.texOffs(32, 24).addBox(-10.5f, -3f, -5f, 1f, 4f, 8f),
				PartPose.offsetAndRotation(-2f, 0f, -2f, 0f, -0.35f, -0.15f));
		root.addOrReplaceChild("gun_right", CubeListBuilder.create().texOffs(80, 10).addBox(3f, 0.5f, -3f, 1f, 1f, 12f), PartPose.ZERO);
		root.addOrReplaceChild("gun_left", CubeListBuilder.create().mirror().texOffs(80, 10).addBox(-4f, 0.5f, -3f, 1f, 1f, 12f), PartPose.ZERO);
		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}

	/** The kajidic raider: squat, patched, heavy. */
	public static LayerDefinition createRaiderLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5f, -4f, -10f, 10f, 7f, 18f), PartPose.ZERO);
		root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(56, 0).addBox(-3.5f, -2.5f, 8f, 7f, 5f, 4f), PartPose.ZERO);
		root.addOrReplaceChild("canopy", CubeListBuilder.create().texOffs(56, 9).addBox(-2.5f, -6f, 1f, 5f, 2f, 6f), PartPose.ZERO);
		root.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(0, 25).addBox(5f, -1f, -6f, 6f, 2f, 10f), PartPose.ZERO);
		root.addOrReplaceChild("wing_left", CubeListBuilder.create().mirror().texOffs(0, 25).addBox(-11f, -1f, -6f, 6f, 2f, 10f), PartPose.ZERO);
		root.addOrReplaceChild("pod_right", CubeListBuilder.create().texOffs(32, 25).addBox(11f, -2f, -4f, 3f, 3f, 12f), PartPose.ZERO);
		root.addOrReplaceChild("pod_left", CubeListBuilder.create().mirror().texOffs(32, 25).addBox(-14f, -2f, -4f, 3f, 3f, 12f), PartPose.ZERO);
		root.addOrReplaceChild("engine_right", CubeListBuilder.create().texOffs(78, 0).addBox(1f, -3f, -14f, 4f, 4f, 5f), PartPose.ZERO);
		root.addOrReplaceChild("engine_left", CubeListBuilder.create().mirror().texOffs(78, 0).addBox(-5f, -3f, -14f, 4f, 4f, 5f), PartPose.ZERO);
		root.addOrReplaceChild("rack", CubeListBuilder.create().texOffs(62, 25).addBox(-3f, 3f, -4f, 6f, 2f, 8f), PartPose.ZERO);
		root.addOrReplaceChild("patch", CubeListBuilder.create().texOffs(78, 9).addBox(5f, -4f, -8f, 1f, 4f, 7f), PartPose.ZERO);
		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}

	/** The Hask fighter: forward-swept, in clan colours. */
	public static LayerDefinition createHaskLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5f, -2.5f, -11f, 5f, 5f, 20f), PartPose.ZERO);
		root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(50, 0).addBox(-1.5f, -1.5f, 9f, 3f, 3f, 4f), PartPose.ZERO);
		root.addOrReplaceChild("canopy", CubeListBuilder.create().texOffs(50, 7).addBox(-2f, -4.5f, 0f, 4f, 2f, 6f), PartPose.ZERO);
		root.addOrReplaceChild("engine", CubeListBuilder.create().texOffs(70, 11).addBox(-2f, -2f, -14f, 4f, 4f, 3f), PartPose.ZERO);
		root.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(106, 0).addBox(-0.5f, -7f, -11f, 1f, 5f, 6f), PartPose.ZERO);
		root.addOrReplaceChild("wing_right", CubeListBuilder.create()
				.texOffs(0, 25).addBox(0f, -0.5f, -3f, 10f, 1f, 6f)
				.texOffs(32, 25).addBox(9f, -0.5f, -3f, 1f, 4f, 5f),
				PartPose.offsetAndRotation(2.5f, 1f, -4f, 0f, -0.45f, -0.06f));
		root.addOrReplaceChild("wing_left", CubeListBuilder.create().mirror()
				.texOffs(0, 25).addBox(-10f, -0.5f, -3f, 10f, 1f, 6f)
				.texOffs(32, 25).addBox(-10f, -0.5f, -3f, 1f, 4f, 5f),
				PartPose.offsetAndRotation(-2.5f, 1f, -4f, 0f, 0.45f, 0.06f));
		root.addOrReplaceChild("gun_right", CubeListBuilder.create().texOffs(84, 0).addBox(3f, 0f, -2f, 1f, 1f, 10f), PartPose.ZERO);
		root.addOrReplaceChild("gun_left", CubeListBuilder.create().mirror().texOffs(84, 0).addBox(-4f, 0f, -2f, 1f, 1f, 10f), PartPose.ZERO);
		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}
}
