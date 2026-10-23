package com.oldrepublic.client.travel;

import com.oldrepublic.travel.ShipRecord;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The Milestone 5 weapon and shield parts both ship models carry (spec MILESTONE_5.md 4.8, 5.2,
 * 5.3): twin cannons, the gunner turret (its ring turns and its guns lift where the gunner looks),
 * the missile rack and the shield emitter, each shown only with its upgrade. The cannons, the
 * turret and the rack hang at the class's own points ({@code ShipBody#cannons}, {@code #turret},
 * {@code #rack}, carried by the render state), so the drawn guns sit where the ship's numbers say.
 * Their boxes share one block of the texture, 132 × 12 pixels at the origin each model gives
 * ({@code weapon_parts} in {@code tools/assets/gen_textures_ship.py}).
 */
final class ShipWeaponParts {
	/** Ship space (blocks) to model units: an eighth of a block, the model's y running down. */
	private static final float UNITS = 8f;

	private final ModelPart cannonRight;
	private final ModelPart cannonLeft;
	private final ModelPart turret;
	private final ModelPart turretGuns;
	private final ModelPart rack;
	private final ModelPart emitter;

	ShipWeaponParts(ModelPart root) {
		this.cannonRight = root.getChild("cannon_right");
		this.cannonLeft = root.getChild("cannon_left");
		this.turret = root.getChild("turret");
		this.turretGuns = turret.getChild("turret_guns");
		this.rack = root.getChild("rack");
		this.emitter = root.getChild("emitter");
	}

	/** Adds the parts to a model's root, their boxes from the texture block at {@code u}, {@code v}, the emitter at {@code emitterAt}. */
	static void define(PartDefinition root, int u, int v, PartPose emitterAt) {
		// A cannon: a pod behind its barrel, the muzzle at the part's origin.
		root.addOrReplaceChild("cannon_right", CubeListBuilder.create()
				.texOffs(u, v).addBox(-1.5f, -1.5f, -12f, 3f, 3f, 9f)
				.texOffs(u + 24, v).addBox(-0.5f, -0.5f, -3f, 1f, 1f, 3f), PartPose.ZERO);
		root.addOrReplaceChild("cannon_left", CubeListBuilder.create()
				.texOffs(u, v).mirror().addBox(-1.5f, -1.5f, -12f, 3f, 3f, 9f)
				.texOffs(u + 24, v).mirror().addBox(-0.5f, -0.5f, -3f, 1f, 1f, 3f), PartPose.ZERO);

		// The turret: a ring and a head on the hull that turn, and twin guns that lift.
		PartDefinition turret = root.addOrReplaceChild("turret", CubeListBuilder.create()
				.texOffs(u + 32, v).addBox(-3f, -1f, -3f, 6f, 1f, 6f)
				.texOffs(u + 56, v).addBox(-2.5f, -3f, -2.5f, 5f, 2f, 5f), PartPose.ZERO);
		turret.addOrReplaceChild("turret_guns", CubeListBuilder.create()
				.texOffs(u + 76, v).addBox(-1.5f, -0.5f, 0f, 1f, 1f, 7f)
				.texOffs(u + 76, v).mirror().addBox(0.5f, -0.5f, 0f, 1f, 1f, 7f), PartPose.offset(0f, -2f, 1f));

		// The rack hangs from the hull down to its point; the emitter sits on the hull.
		root.addOrReplaceChild("rack", CubeListBuilder.create().texOffs(u + 92, v).addBox(-3f, -2f, -3f, 6f, 2f, 6f), PartPose.ZERO);
		root.addOrReplaceChild("emitter", CubeListBuilder.create().texOffs(u + 116, v).addBox(-2f, -1f, -2f, 4f, 1f, 4f), emitterAt);
	}

	/** Places, aims and shows the parts for this frame. */
	void setupAnim(CourierShuttleRenderState state) {
		boolean armed = (state.upgrades & ShipRecord.UPGRADE_CANNONS) != 0;
		place(cannonRight, state.cannonsAt[0]);
		place(cannonLeft, state.cannonsAt[1]);
		place(turret, state.turretAt);
		place(rack, state.rackAt);
		cannonRight.visible = armed;
		cannonLeft.visible = armed;
		turret.visible = armed;
		turret.yRot = state.turretYaw;
		turretGuns.xRot = state.turretPitch;
		rack.visible = (state.upgrades & ShipRecord.UPGRADE_MISSILES) != 0;
		emitter.visible = (state.upgrades & ShipRecord.UPGRADE_SHIELD) != 0;
	}

	/** Moves a part's origin to a point in ship space (right, up, forward). */
	private static void place(ModelPart part, double[] at) {
		part.setPos((float) at[0] * UNITS, (float) -at[1] * UNITS, (float) at[2] * UNITS);
	}
}
