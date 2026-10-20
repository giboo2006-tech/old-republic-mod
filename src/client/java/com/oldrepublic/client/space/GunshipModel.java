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
import net.minecraft.util.Mth;

/**
 * The blockade gunship (spec MILESTONE_5.md 5.5): a slab-sided hull 24 blocks long with a blunt
 * prow, a keel and a dorsal spine, a bank of engines across the stern, a raised bridge aft, four
 * twin-barrelled turrets on its back (where {@code BlockadeGunship.TURRETS} fire from) and the
 * launch bay's doors in its belly. The shield generator stands on the spine at its seat
 * ({@code BlockadeGunship.GENERATOR_SEAT}) as a part of this model, shown while it stands; its own
 * entity draws nothing, so the two never drift apart.
 *
 * <p>The hull is built in two halves split at the middle, so that after the end blast it breaks in
 * two (15.3). Model units are a quarter of a block (the renderer scales by 4); vanilla model space,
 * y down: +x is the ship's right, −y up, +z the prow, the hull's bottom at y 0. The 256 × 256
 * texture's box layout is mirrored in {@code tools/assets/gen_textures_space.py}; the
 * {@code _glow} texture lights the engines and the running lights, the {@code _core} texture the
 * generator's core.
 */
public class GunshipModel extends EntityModel<GunshipRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("blockade_gunship"), "main");
	public static final int TEX_W = 256;
	public static final int TEX_H = 256;
	/** The bay doors' angle when open (radians). */
	private static final float DOOR_OPEN = 1.2f;

	private final ModelPart fore;
	private final ModelPart aft;
	private final ModelPart doorRight;
	private final ModelPart doorLeft;
	private final ModelPart generator;

	public GunshipModel(ModelPart root) {
		super(root);
		this.fore = root.getChild("fore");
		this.aft = root.getChild("aft");
		this.doorRight = fore.getChild("door_right");
		this.doorLeft = fore.getChild("door_left");
		this.generator = root.getChild("generator");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// The fore half: hull, prow, keel, spine, two turrets and the bay doors. Pivot at the break, mid-height.
		PartDefinition fore = root.addOrReplaceChild("fore", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-16f, -11f, 0f, 32f, 22f, 40f)
				.texOffs(144, 0).addBox(-12f, -8f, 40f, 24f, 16f, 8f)
				.texOffs(0, 62).addBox(-8f, 11f, 0f, 16f, 2f, 36f)
				.texOffs(104, 62).addBox(-3f, -15f, 0f, 6f, 4f, 36f),
				PartPose.offset(0f, -13f, 0f));
		fore.addOrReplaceChild("turret_fore_right", CubeListBuilder.create()
				.texOffs(208, 0).addBox(10.8f, -14f, 30f, 4f, 3f, 4f)
				.texOffs(208, 8).addBox(12f, -16f, 33f, 2f, 2f, 7f), PartPose.ZERO);
		fore.addOrReplaceChild("turret_fore_left", CubeListBuilder.create().mirror()
				.texOffs(208, 0).addBox(-14.8f, -14f, 30f, 4f, 3f, 4f)
				.texOffs(208, 8).addBox(-14f, -16f, 33f, 2f, 2f, 7f), PartPose.ZERO);
		// The doors hang on hinges at their outer edges and swing down from the middle.
		fore.addOrReplaceChild("door_right", CubeListBuilder.create().texOffs(144, 24).addBox(-6f, 0f, 0f, 6f, 1f, 16f),
				PartPose.offset(6f, 13f, 4f));
		fore.addOrReplaceChild("door_left", CubeListBuilder.create().mirror().texOffs(144, 24).addBox(0f, 0f, 0f, 6f, 1f, 16f),
				PartPose.offset(-6f, 13f, 4f));

		// The aft half: hull, engine bank, keel, spine, the bridge and two turrets.
		PartDefinition aft = root.addOrReplaceChild("aft", CubeListBuilder.create()
				.texOffs(0, 102).addBox(-16f, -11f, -40f, 32f, 22f, 40f)
				.texOffs(144, 126).addBox(-14f, -9f, -48f, 28f, 18f, 8f)
				.texOffs(0, 164).addBox(-8f, 11f, -36f, 16f, 2f, 36f)
				.texOffs(104, 164).addBox(-3f, -15f, -36f, 6f, 4f, 36f)
				.texOffs(188, 62).addBox(-6f, -16f, -34f, 12f, 5f, 14f),
				PartPose.offset(0f, -13f, 0f));
		aft.addOrReplaceChild("turret_aft_right", CubeListBuilder.create()
				.texOffs(208, 0).addBox(10.8f, -14f, -32f, 4f, 3f, 4f)
				.texOffs(208, 8).addBox(12f, -16f, -29f, 2f, 2f, 7f), PartPose.ZERO);
		aft.addOrReplaceChild("turret_aft_left", CubeListBuilder.create().mirror()
				.texOffs(208, 0).addBox(-14.8f, -14f, -32f, 4f, 3f, 4f)
				.texOffs(208, 8).addBox(-14f, -16f, -29f, 2f, 2f, 7f), PartPose.ZERO);

		// The shield generator on its seat (0, 7, -2 blocks): base, column, core, cap and emitter.
		root.addOrReplaceChild("generator", CubeListBuilder.create()
				.texOffs(188, 81).addBox(-5f, -2f, -5f, 10f, 2f, 10f)
				.texOffs(228, 81).addBox(-2f, -6f, -2f, 4f, 4f, 4f)
				.texOffs(188, 93).addBox(-3f, -8f, -3f, 6f, 2f, 6f)
				.texOffs(212, 93).addBox(-3.5f, -10f, -3.5f, 7f, 2f, 7f)
				.texOffs(240, 93).addBox(-0.5f, -11f, -0.5f, 1f, 1f, 1f),
				PartPose.offset(0f, -28f, -8f));
		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}

	@Override
	public void setupAnim(GunshipRenderState state) {
		super.setupAnim(state);
		doorRight.zRot = -DOOR_OPEN * state.bay;
		doorLeft.zRot = DOOR_OPEN * state.bay;
		generator.visible = state.shielded && state.breakUp <= 0f;

		// Broken in two: each half sags away from the break, drifting and rolling apart.
		float t = state.breakUp;

		if (t > 0f) {
			float ease = Mth.sin(t * Mth.HALF_PI);
			fore.z += 8f * ease;
			fore.y += 6f * ease;
			fore.xRot = -0.3f * ease;
			fore.zRot = 0.15f * ease;
			aft.z -= 8f * ease;
			aft.y += 4f * ease;
			aft.xRot = 0.25f * ease;
			aft.zRot = -0.1f * ease;
		}
	}
}
