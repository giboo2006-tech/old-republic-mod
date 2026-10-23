package com.oldrepublic.client.travel;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.travel.ShipRecord;

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
 * The courier shuttle (spec MILESTONE_4.md 4.1): a blunt nose with a raised canopy, a dorsal spine
 * and swept tail fin, swept-down wings, twin engine nacelles, three landing struts, a tail ramp and,
 * on a hulk, a tarp and two timber jacks. Upgrades show: a belly cargo pod, spine tanks and nose
 * plating; from Milestone 5 (4.8, 5.2) the twin cannons beside the nose, the dorsal gunner turret
 * (it turns and lifts its guns where the gunner looks), the missile rack under the nose and the
 * shield emitter on the spine. The weapons hang at the class's points ({@code ShipBody#cannons},
 * {@code #turret}, {@code #rack}), so the drawn guns and the ship's own numbers agree.
 *
 * <p>Model units are an eighth of a block (the renderer scales by 2); vanilla model space, y down:
 * +x is the ship's right, −y up and +z the nose. The model spans about 9 × 4 × 11 blocks with its
 * struts' feet at y 0. The 512 × 256 texture's box layout is mirrored in
 * {@code tools/assets/gen_textures_ship.py} (each part's {@code texOffs} below).
 */
public class CourierShuttleModel extends EntityModel<CourierShuttleRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("courier_shuttle"), "main");
	public static final int TEX_W = 512;
	public static final int TEX_H = 256;
	/** Ramp angle when lowered (its tail edge on the ground) and strut fold when retracted, in radians. */
	private static final float RAMP_OPEN = 0.34f;
	private static final float STRUT_FOLD = -1.45f;

	private final ModelPart tarp;
	private final ModelPart jackLeft;
	private final ModelPart jackRight;
	private final ModelPart[] struts;
	private final ModelPart ramp;
	private final ModelPart cargo;
	private final ModelPart tanks;
	private final ModelPart plating;
	private final ShipWeaponParts weapons;

	public CourierShuttleModel(ModelPart root) {
		super(root);
		this.tarp = root.getChild("tarp");
		this.jackLeft = root.getChild("jack_left");
		this.jackRight = root.getChild("jack_right");
		this.struts = new ModelPart[] {root.getChild("strut_nose"), root.getChild("strut_left"), root.getChild("strut_right")};
		this.ramp = root.getChild("ramp");
		this.cargo = root.getChild("cargo");
		this.tanks = root.getChild("tanks");
		this.plating = root.getChild("plating");
		this.weapons = new ShipWeaponParts(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// Fuselage, nose, canopy, spine and tail fin.
		root.addOrReplaceChild("hull", CubeListBuilder.create().texOffs(0, 0).addBox(-14f, -24f, -36f, 28f, 18f, 64f), PartPose.ZERO);
		root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(186, 48).addBox(-11f, -21f, 28f, 22f, 14f, 12f), PartPose.ZERO);
		root.addOrReplaceChild("nose_tip", CubeListBuilder.create().texOffs(330, 48).addBox(-7f, -17f, 40f, 14f, 8f, 4f), PartPose.ZERO);
		root.addOrReplaceChild("canopy", CubeListBuilder.create().texOffs(256, 48).addBox(-9f, -29f, 14f, 18f, 5f, 18f), PartPose.ZERO);
		root.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(186, 0).addBox(-6f, -27f, -30f, 12f, 3f, 44f), PartPose.ZERO);
		root.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(448, 28).addBox(-1.5f, -6f, -14f, 3f, 6f, 14f),
				PartPose.offsetAndRotation(0f, -27f, -22f, -0.12f, 0f, 0f));

		// Swept-down wings: swept back first, then drooped at the tips.
		root.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(386, 0).addBox(0f, -2f, -14f, 22f, 3f, 24f),
				PartPose.offsetAndRotation(14f, -12f, -4f, 0f, 0.3f, 0.28f));
		root.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(386, 0).mirror().addBox(-22f, -2f, -14f, 22f, 3f, 24f),
				PartPose.offsetAndRotation(-14f, -12f, -4f, 0f, -0.3f, -0.28f));

		// Twin nacelles with their nozzles (the nozzles' rear faces carry the engine glow).
		root.addOrReplaceChild("nacelle_right", CubeListBuilder.create()
				.texOffs(300, 0).addBox(-5f, -5f, -16f, 10f, 10f, 32f)
				.texOffs(368, 48).addBox(-4f, -4f, -18f, 8f, 8f, 2f), PartPose.offset(21f, -15f, -26f));
		root.addOrReplaceChild("nacelle_left", CubeListBuilder.create()
				.texOffs(300, 0).mirror().addBox(-5f, -5f, -16f, 10f, 10f, 32f)
				.texOffs(368, 48).mirror().addBox(-4f, -4f, -18f, 8f, 8f, 2f), PartPose.offset(-21f, -15f, -26f));

		// The tail ramp, hinged at its front edge under the hull.
		root.addOrReplaceChild("ramp", CubeListBuilder.create().texOffs(386, 28).addBox(-6f, 0f, -18f, 12f, 1f, 18f), PartPose.offset(0f, -6f, -18f));

		// Landing struts: a leg and a foot each, hinged at the hull.
		root.addOrReplaceChild("strut_nose", CubeListBuilder.create()
				.texOffs(404, 84).addBox(-1f, 0f, -1f, 2f, 6f, 2f)
				.texOffs(414, 84).addBox(-2.5f, 6f, -2.5f, 5f, 1f, 5f), PartPose.offset(0f, -7f, 32f));
		root.addOrReplaceChild("strut_left", CubeListBuilder.create()
				.texOffs(436, 84).addBox(-1f, 0f, -1f, 2f, 5f, 2f)
				.texOffs(446, 84).addBox(-3f, 5f, -3f, 6f, 1f, 6f), PartPose.offset(-11f, -6f, -10f));
		root.addOrReplaceChild("strut_right", CubeListBuilder.create()
				.texOffs(436, 84).addBox(-1f, 0f, -1f, 2f, 5f, 2f)
				.texOffs(446, 84).addBox(-3f, 5f, -3f, 6f, 1f, 6f), PartPose.offset(11f, -6f, -10f));

		// A hulk's tarp and the timber jacks under its wings.
		root.addOrReplaceChild("tarp", CubeListBuilder.create().texOffs(0, 84).addBox(-17f, -31f, -34f, 34f, 26f, 68f), PartPose.offset(0f, 0f, 0f));
		root.addOrReplaceChild("jack_left", CubeListBuilder.create().texOffs(390, 84).addBox(-1.5f, 0f, -1.5f, 3f, 10f, 3f), PartPose.offset(-25f, -10f, -6f));
		root.addOrReplaceChild("jack_right", CubeListBuilder.create().texOffs(390, 84).addBox(-1.5f, 0f, -1.5f, 3f, 10f, 3f), PartPose.offset(25f, -10f, -6f));

		// Upgrades: the belly cargo pod, the spine tanks and the nose plating.
		root.addOrReplaceChild("cargo", CubeListBuilder.create().texOffs(274, 84).addBox(-8f, 0f, -10f, 16f, 3f, 20f), PartPose.offset(0f, -6f, -2f));
		root.addOrReplaceChild("tanks", CubeListBuilder.create()
				.texOffs(348, 84).addBox(-11f, -3f, -8f, 4f, 3f, 16f)
				.texOffs(348, 84).mirror().addBox(7f, -3f, -8f, 4f, 3f, 16f), PartPose.offset(0f, -24f, -14f));
		root.addOrReplaceChild("plating", CubeListBuilder.create().texOffs(206, 84).addBox(-11.5f, -20.5f, 28f, 23f, 12f, 10f), PartPose.ZERO);

		// Milestone 5: the cannons, the turret, the rack (at the class's points, set in setupAnim) and the emitter on the spine.
		ShipWeaponParts.define(root, 210, 112, PartPose.offset(0f, -27f, -14f));
		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}

	@Override
	public void setupAnim(CourierShuttleRenderState state) {
		super.setupAnim(state);

		// The hulk standing up: the tarp is pulled back toward the tail and bunches; the jacks sink into the wings.
		float stand = state.standUp;
		tarp.visible = stand < 1f;
		tarp.z = -46f * ease(stand);
		tarp.zScale = 1f - 0.8f * stand;
		tarp.yScale = 1f - 0.25f * stand;
		jackLeft.visible = stand < 1f;
		jackRight.visible = stand < 1f;
		jackLeft.yScale = 1f - stand;
		jackRight.yScale = 1f - stand;

		// Struts fold back into the hull; hidden once stowed.
		float fold = (1f - state.struts) * STRUT_FOLD;
		boolean out = state.struts > 0.02f;

		for (ModelPart strut : struts) {
			strut.xRot = fold;
			strut.visible = out;
		}

		ramp.xRot = RAMP_OPEN * ease(state.ramp);
		cargo.visible = (state.upgrades & ShipRecord.UPGRADE_CARGO) != 0;
		tanks.visible = (state.upgrades & ShipRecord.UPGRADE_TANK) != 0;
		plating.visible = (state.upgrades & ShipRecord.UPGRADE_PLATING) != 0;
		weapons.setupAnim(state);
	}

	private static float ease(float t) {
		float u = Mth.clamp(t, 0f, 1f);
		return u * u * (3f - 2f * u);
	}
}
