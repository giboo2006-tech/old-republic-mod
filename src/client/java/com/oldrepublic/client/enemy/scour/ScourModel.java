package com.oldrepublic.client.enemy.scour;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.scour.SandScour;

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
 * The sand scour's model (spec MILESTONE_5.md 12.1): a low, armoured burrower of Ghessar's dunes, the
 * colour of the sand it hides in. A wedge-shaped head with a hinged lower jaw, a long body under
 * three ridged back plates, six short splayed legs, and a segmented tail; the matriarch (the
 * server's scale attribute, 1.6 times the size) ends hers in a heavy club.
 *
 * <p>Poses: the six-legged scuttle (alternate triplets); the bite (the jaw drops open with the attack
 * swing); the matriarch's sweep tell (the tail rises and coils up over her back, trembling).
 *
 * <p>UV layout (128x32), mirrored in {@code tools/assets/gen_textures_foes.py}: body 0,0; head 48,0;
 * jaw 76,0; back plate 100,0; legs 0,20; tail 8,20, 32,20 and 52,20; club 66,20.
 */
public class ScourModel extends EntityModel<ScourRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("sand_scour"), "main");
	private static final float EASE_TICKS = 6f;

	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart[] legs = new ModelPart[6];
	private final ModelPart tail1;
	private final ModelPart tail2;
	private final ModelPart tail3;
	private final ModelPart club;

	public ScourModel(ModelPart root) {
		super(root);
		ModelPart body = root.getChild("body");
		this.head = body.getChild("head");
		this.jaw = head.getChild("jaw");
		this.tail1 = body.getChild("tail1");
		this.tail2 = tail1.getChild("tail2");
		this.tail3 = tail2.getChild("tail3");
		this.club = tail3.getChild("club");

		for (int i = 0; i < legs.length; i++) {
			legs[i] = root.getChild("leg" + i);
		}
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-5f, -3f, -7f, 10f, 6f, 14f), PartPose.offset(0f, 16f, 0f));

		for (int i = 0; i < 3; i++) {
			body.addOrReplaceChild("plate" + i, CubeListBuilder.create().texOffs(100, 0)
					.addBox(-3f, -4.6f, -5.5f + i * 4.2f, 6f, 2f, 3f), PartPose.ZERO);
		}

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(48, 0)
				.addBox(-4f, -3f, -6f, 8f, 5f, 6f), PartPose.offset(0f, 0f, -7f));
		head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(76, 0)
				.addBox(-3.5f, 0f, -5f, 7f, 2f, 5f), PartPose.offset(0f, 2f, -0.5f));

		PartDefinition tail1 = body.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(8, 20)
				.addBox(-3f, -2f, 0f, 6f, 4f, 6f), PartPose.offsetAndRotation(0f, -0.5f, 6.5f, -0.12f, 0f, 0f));
		PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(32, 20)
				.addBox(-2f, -1.5f, 0f, 4f, 3f, 6f), PartPose.offsetAndRotation(0f, 0f, 5.5f, 0.1f, 0f, 0f));
		PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(52, 20)
				.addBox(-1f, -1f, 0f, 2f, 2f, 5f), PartPose.offsetAndRotation(0f, 0f, 5.5f, 0.1f, 0f, 0f));
		tail3.addOrReplaceChild("club", CubeListBuilder.create().texOffs(66, 20)
				.addBox(-2f, -2f, 0f, 4f, 4f, 3f), PartPose.offset(0f, 0f, 4.5f));

		// Three pairs of short legs splayed out from the body's sides.
		for (int i = 0; i < 6; i++) {
			float side = i % 2 == 0 ? -1f : 1f;
			float z = -4.5f + (i / 2) * 4.5f;
			CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 20);

			if (side > 0) {
				leg.mirror();
			}

			root.addOrReplaceChild("leg" + i, leg.addBox(-1f, 0f, -1f, 2f, 6f, 2f),
					PartPose.offsetAndRotation(side * 4.5f, 18f, z, 0f, 0f, -side * 0.6f));
		}

		return LayerDefinition.create(mesh, 128, 32);
	}

	@Override
	public void setupAnim(ScourRenderer.State state) {
		super.setupAnim(state);
		club.visible = state.matriarch;

		// The scuttle: alternate triplets of legs swing fore and aft and lift.
		float speed = Math.min(1f, state.walkAnimationSpeed * 1.6f);
		float pos = state.walkAnimationPos * 1.3f;

		for (int i = 0; i < legs.length; i++) {
			boolean first = (i == 0 || i == 3 || i == 4);
			float phase = first ? 0f : Mth.PI;
			float side = i % 2 == 0 ? -1f : 1f;
			legs[i].yRot = Mth.cos(pos + phase) * 0.5f * speed * side;
			// Lifting a foot splays the leg further out.
			legs[i].zRot = -side * (0.6f + Math.max(0f, Mth.sin(pos + phase)) * 0.3f * speed);
		}

		head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.6f;
		head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5f;
		// The bite: the jaw drops open with the swing, and snaps shut.
		jaw.xRot = Mth.sin(Mth.clamp(state.bite, 0f, 1f) * Mth.PI) * 0.75f + Mth.sin(state.ageInTicks * 0.15f) * 0.03f;
		// The tail sways while it moves and lies still while it waits.
		tail1.yRot = Mth.sin(state.ageInTicks * 0.12f) * 0.08f + Mth.cos(pos) * 0.15f * speed;
		tail2.yRot = tail1.yRot * 0.8f;

		if (state.action == SandScour.ACTION_SWEEP) {
			// The tail rises and coils up over her back, trembling with the strain.
			float ease = Mth.clamp(state.actionTicks / EASE_TICKS, 0f, 1f);
			float tremble = Mth.sin(state.ageInTicks * 1.7f) * 0.05f;
			tail1.xRot = Mth.lerp(ease, tail1.xRot, -0.95f + tremble);
			tail2.xRot = Mth.lerp(ease, tail2.xRot, -0.9f);
			tail3.xRot = Mth.lerp(ease, tail3.xRot, -0.8f - tremble);
			tail1.yRot = Mth.lerp(ease, tail1.yRot, 0.25f);
			head.xRot = Mth.lerp(ease, head.xRot, 0.2f);
		}
	}
}
