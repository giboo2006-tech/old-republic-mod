package com.oldrepublic.client.travel;

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
 * The interceptor (spec MILESTONE_5.md 4.9): a narrow wedge of a hull with a short nose, a bubble
 * canopy over the pilot that lifts open on the ground, swept delta wings, twin engine pods along
 * the tail, a raked dorsal fin and three landing struts. With its upgrades: wing-root cannons, the
 * aft gunner's turret behind the canopy, the missile rack under the nose and the shield emitter on
 * it ({@link ShipWeaponParts}). An original design.
 *
 * <p>Model units are an eighth of a block (the renderer scales by 2), as for the courier; vanilla
 * model space, y down: +x is the ship's right, −y up and +z the nose. The model spans about
 * 5.5 × 2.5 × 6.4 blocks with its struts' feet at y 0. The 256 × 128 texture's box layout is
 * mirrored in {@code tools/assets/gen_textures_ship.py} ({@code INTERCEPTOR_PARTS}).
 */
public class InterceptorModel extends EntityModel<CourierShuttleRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("interceptor"), "main");
	public static final int TEX_W = 256;
	public static final int TEX_H = 128;
	/** The canopy's lift when open (on the ground) and the struts' fold when retracted, in radians. */
	private static final float CANOPY_OPEN = 0.6f;
	private static final float STRUT_FOLD = -1.45f;

	private final ModelPart canopy;
	private final ModelPart[] struts;
	private final ShipWeaponParts weapons;

	public InterceptorModel(ModelPart root) {
		super(root);
		this.canopy = root.getChild("canopy");
		this.struts = new ModelPart[] {root.getChild("strut_nose"), root.getChild("strut_left"), root.getChild("strut_right")};
		this.weapons = new ShipWeaponParts(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// The hull, the nose and its sensor tip.
		root.addOrReplaceChild("hull", CubeListBuilder.create().texOffs(0, 0).addBox(-7f, -14f, -20f, 14f, 9f, 34f), PartPose.ZERO);
		root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(96, 0).addBox(-5f, -12f, 14f, 10f, 7f, 10f), PartPose.ZERO);
		root.addOrReplaceChild("nose_tip", CubeListBuilder.create().texOffs(136, 0).addBox(-3f, -10f, 24f, 6f, 4f, 5f), PartPose.ZERO);

		// The bubble canopy, hinged at its rear edge so its front lifts open.
		root.addOrReplaceChild("canopy", CubeListBuilder.create().texOffs(96, 18).addBox(-4f, -6f, 0f, 8f, 6f, 12f), PartPose.offset(0f, -14f, -2f));

		// Swept delta wings: back at the tips, a slight droop.
		root.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(0, 44).addBox(0f, -1f, -10f, 14f, 2f, 16f),
				PartPose.offsetAndRotation(7f, -8f, -2f, 0f, 0.38f, 0.05f));
		root.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(0, 44).mirror().addBox(-14f, -1f, -10f, 14f, 2f, 16f),
				PartPose.offsetAndRotation(-7f, -8f, -2f, 0f, -0.38f, -0.05f));

		// Twin engine pods along the tail, their nozzles' rear faces carrying the engine glow.
		root.addOrReplaceChild("nacelle_right", CubeListBuilder.create()
				.texOffs(60, 44).addBox(-2.5f, -2.5f, -7f, 5f, 5f, 14f)
				.texOffs(98, 44).addBox(-2f, -2f, -9f, 4f, 4f, 2f), PartPose.offset(6.8f, -7.2f, -13f));
		root.addOrReplaceChild("nacelle_left", CubeListBuilder.create()
				.texOffs(60, 44).mirror().addBox(-2.5f, -2.5f, -7f, 5f, 5f, 14f)
				.texOffs(98, 44).mirror().addBox(-2f, -2f, -9f, 4f, 4f, 2f), PartPose.offset(-6.8f, -7.2f, -13f));

		// The raked dorsal fin behind the turret.
		root.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(110, 44).addBox(-1f, -6f, -8f, 2f, 6f, 8f),
				PartPose.offsetAndRotation(0f, -14f, -14f, -0.25f, 0f, 0f));

		// Landing struts: one under the nose, two under the engine pods.
		strut(root, "strut_nose", 0f, 22f);
		strut(root, "strut_left", -6f, -8f);
		strut(root, "strut_right", 6f, -8f);

		// The cannons, the turret, the rack (at the class's points, set in setupAnim) and the emitter on the nose.
		ShipWeaponParts.define(root, 0, 64, PartPose.offset(0f, -12f, 19f));
		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}

	/** A landing strut: a leg and a foot, hinged at the hull's belly. */
	private static void strut(PartDefinition root, String name, float x, float z) {
		root.addOrReplaceChild(name, CubeListBuilder.create()
				.texOffs(158, 0).addBox(-1f, 0f, -1f, 2f, 4f, 2f)
				.texOffs(166, 0).addBox(-2f, 4f, -2f, 4f, 1f, 4f), PartPose.offset(x, -5f, z));
	}

	@Override
	public void setupAnim(CourierShuttleRenderState state) {
		super.setupAnim(state);

		// Struts fold back into the hull; hidden once stowed.
		float fold = (1f - state.struts) * STRUT_FOLD;
		boolean out = state.struts > 0.02f;

		for (ModelPart strut : struts) {
			strut.xRot = fold;
			strut.visible = out;
		}

		// The canopy is the courier's ramp: open on the ground, shut at the spool, open again at the settle.
		float open = Mth.clamp(state.ramp, 0f, 1f);
		canopy.xRot = CANOPY_OPEN * open * open * (3f - 2f * open);
		weapons.setupAnim(state);
	}
}
