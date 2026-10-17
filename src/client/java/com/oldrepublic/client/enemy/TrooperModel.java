package com.oldrepublic.client.enemy;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.trooper.TrooperAction;
import com.oldrepublic.enemy.trooper.TrooperRole;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Trooper model (spec 7.1): the vanilla humanoid with plate armour built from the same textures as
 * the player's trooper armour, and an officer's uniform for the unarmoured officers.
 *
 * <ul>
 * <li>Republic: the hat layer is a rounded helmet shell under a low dome, with one wide horizontal
 * visor band across the face and round ear pieces.</li>
 * <li>Imperial: an angular helmet: a brow ridge over two narrow eye slits (painted), a vertical
 * nose ridge, angled cheek plates and a low crest.</li>
 * <li>Both: a cuirass, shoulder pauldrons and a belt. Role marks: the medic's kolto injector on the
 * hip (the teal shoulder band is painted), the grenadier's bandolier across the chest, the
 * sergeant's chevrons (painted on the helmet side).</li>
 * <li>Officers (either army): a peaked cap, a tunic with sleeves, no armour.</li>
 * </ul>
 *
 * <p>One layer per army ({@link #REPUBLIC}, {@link #IMPERIAL}); the officer parts are in both and
 * shown by role, so one renderer draws every role of its army. UV layout (64x64; the upper half is
 * the classic humanoid layout), mirrored in {@code tools/assets/gen_textures_trooper.py}:
 * cuirass 0,32; pauldrons 24,32; belt 0,48; injector 30,50; bandolier 52,41; Republic visor 44,32,
 * ears 52,35, dome 24,40; Imperial brow 24,40, nose 46,40, cheeks 24,43, crest 0,54; officer cap
 * 0,32, peak 36,32, tunic 0,44, sleeves 40,36 (an officer's texture never shows the armour parts).
 */
public class TrooperModel extends HumanoidModel<TrooperRenderState> {
	public static final ModelLayerLocation REPUBLIC = new ModelLayerLocation(OldRepublicMod.id("republic_trooper"), "main");
	public static final ModelLayerLocation IMPERIAL = new ModelLayerLocation(OldRepublicMod.id("sith_trooper"), "main");

	private final ModelPart cuirass;
	private final ModelPart belt;
	private final ModelPart injector;
	private final ModelPart bandolier;
	private final ModelPart rightPauldron;
	private final ModelPart leftPauldron;
	private final ModelPart cap;
	private final ModelPart capPeak;
	private final ModelPart tunic;
	private final ModelPart rightSleeve;
	private final ModelPart leftSleeve;
	/** The army's helmet parts on the head (dome, visor and ears; or brow, nose, cheeks and crest). */
	private final ModelPart[] helmet;

	public TrooperModel(ModelPart root) {
		super(root);
		this.cuirass = body.getChild("cuirass");
		this.belt = body.getChild("belt");
		this.injector = body.getChild("injector");
		this.bandolier = body.getChild("bandolier");
		this.tunic = body.getChild("tunic");
		this.rightPauldron = rightArm.getChild("pauldron");
		this.leftPauldron = leftArm.getChild("pauldron");
		this.rightSleeve = rightArm.getChild("sleeve");
		this.leftSleeve = leftArm.getChild("sleeve");
		this.cap = head.getChild("cap");
		this.capPeak = head.getChild("cap_peak");
		this.helmet = new ModelPart[] {optional(head, "dome"), optional(head, "visor"), optional(head, "ear_right"), optional(head, "ear_left"),
				optional(head, "brow"), optional(head, "nose"), optional(head, "cheek_right"), optional(head, "cheek_left"), optional(head, "crest")};
	}

	private static @Nullable ModelPart optional(ModelPart parent, String name) {
		return parent.hasChild(name) ? parent.getChild(name) : null;
	}

	public static LayerDefinition createRepublicLayer() {
		return createLayer(true);
	}

	public static LayerDefinition createImperialLayer() {
		return createLayer(false);
	}

	private static LayerDefinition createLayer(boolean republic) {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		PartDefinition body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm");
		PartDefinition leftArm = root.getChild("left_arm");

		// Plate armour, shared by both armies (the skins paint it in the army's colours).
		body.addOrReplaceChild("cuirass", CubeListBuilder.create().texOffs(0, 32)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		body.addOrReplaceChild("belt", CubeListBuilder.create().texOffs(0, 48)
				.addBox(-4f, 10f, -2f, 8f, 2f, 4f, new CubeDeformation(0.45f)), PartPose.ZERO);
		body.addOrReplaceChild("injector", CubeListBuilder.create().texOffs(30, 50)
				.addBox(4.2f, 9.5f, -1f, 1f, 3f, 2f), PartPose.ZERO);
		body.addOrReplaceChild("bandolier", CubeListBuilder.create().texOffs(52, 41)
				.addBox(-0.5f, -6.5f, -2.5f, 1f, 13f, 5f), PartPose.offsetAndRotation(0f, 6f, 0f, 0f, 0f, 0.62f));
		rightArm.addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(24, 32)
				.addBox(-3.5f, -2.6f, -2.5f, 5f, 3f, 5f, new CubeDeformation(0.1f)), PartPose.ZERO);
		leftArm.addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(24, 32).mirror()
				.addBox(-1.5f, -2.6f, -2.5f, 5f, 3f, 5f, new CubeDeformation(0.1f)), PartPose.ZERO);

		if (republic) {
			head.addOrReplaceChild("dome", CubeListBuilder.create().texOffs(24, 40)
					.addBox(-3.5f, -10.2f, -3.5f, 7f, 2f, 7f), PartPose.ZERO);
			head.addOrReplaceChild("visor", CubeListBuilder.create().texOffs(44, 32)
					.addBox(-4.5f, -5.6f, -4.9f, 9f, 2f, 1f), PartPose.ZERO);
			head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(52, 35)
					.addBox(-5.1f, -5.5f, -1.5f, 1f, 3f, 3f), PartPose.ZERO);
			head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(52, 35).mirror()
					.addBox(4.1f, -5.5f, -1.5f, 1f, 3f, 3f), PartPose.ZERO);
		} else {
			head.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(24, 40)
					.addBox(-4.5f, -6.3f, -5.1f, 9f, 1f, 2f), PartPose.ZERO);
			head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(46, 40)
					.addBox(-0.5f, -5.3f, -5.2f, 1f, 4f, 1f), PartPose.ZERO);
			head.addOrReplaceChild("cheek_right", CubeListBuilder.create().texOffs(24, 43)
					.addBox(-5.0f, -4.0f, -4.8f, 1f, 3f, 4f), PartPose.ZERO);
			head.addOrReplaceChild("cheek_left", CubeListBuilder.create().texOffs(24, 43).mirror()
					.addBox(4.0f, -4.0f, -4.8f, 1f, 3f, 4f), PartPose.ZERO);
			head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(0, 54)
					.addBox(-1f, -9.2f, -4f, 2f, 1f, 8f), PartPose.ZERO);
		}

		// The officer's uniform: a peaked cap, a tunic and sleeves (shown for officers only).
		head.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 32)
				.addBox(-4.5f, -10.5f, -4.5f, 9f, 3f, 9f), PartPose.ZERO);
		head.addOrReplaceChild("cap_peak", CubeListBuilder.create().texOffs(36, 32)
				.addBox(-4f, -0.5f, -3f, 8f, 1f, 3f), PartPose.offsetAndRotation(0f, -7.9f, -4.4f, 0.22f, 0f, 0f));
		body.addOrReplaceChild("tunic", CubeListBuilder.create().texOffs(0, 44)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.25f)), PartPose.ZERO);
		rightArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(40, 36)
				.addBox(-3f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.25f)), PartPose.ZERO);
		leftArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(40, 36).mirror()
				.addBox(-1f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.25f)), PartPose.ZERO);

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(TrooperRenderState state) {
		super.setupAnim(state);
		showParts(state.role);

		if (state.downed) {
			downedPose();
			return;
		}

		float ease = Mth.clamp(state.actionTicks / 4f, 0f, 1f);
		HumanoidArmParts arms = new HumanoidArmParts(this, state);

		switch (state.action) {
			case CHARGING -> {
				// The rifle is shouldered by the CROSSBOW_HOLD arm pose; a pistol is levelled along the look.
				if (!state.rifle && state.armed) {
					arms.main.xRot = Mth.lerp(ease, arms.main.xRot, head.xRot - Mth.HALF_PI);
					arms.main.yRot = Mth.lerp(ease, arms.main.yRot, head.yRot);
					arms.main.zRot = Mth.lerp(ease, arms.main.zRot, 0f);
				}
			}
			case VENTING -> {
				// Rifle lowered across the hips, muzzle down, while it vents steam.
				arms.main.xRot = Mth.lerp(ease, arms.main.xRot, -0.35f);
				arms.main.yRot = Mth.lerp(ease, arms.main.yRot, arms.inward(0.45f));
				arms.off.xRot = Mth.lerp(ease, arms.off.xRot, -0.5f);
				arms.off.yRot = Mth.lerp(ease, arms.off.yRot, -arms.inward(0.35f));
				head.xRot = Mth.lerp(ease, head.xRot, 0.35f);
			}
			case HEALING, KNEEL_TEND -> {
				// Knelt by the patient, both hands reaching forward and down.
				KneelPose.apply(this, ease);
				arms.main.xRot = Mth.lerp(ease, arms.main.xRot, -1.05f);
				arms.off.xRot = Mth.lerp(ease, arms.off.xRot, -0.9f);
				arms.main.zRot = Mth.lerp(ease, arms.main.zRot, 0f);
				arms.off.zRot = Mth.lerp(ease, arms.off.zRot, 0f);
				head.xRot = Mth.lerp(ease, head.xRot, 0.6f);
			}
			case RALLY_TELL -> {
				// The off-hand fist raised high.
				arms.off.xRot = Mth.lerp(ease, arms.off.xRot, -2.95f);
				arms.off.yRot = Mth.lerp(ease, arms.off.yRot, 0f);
				arms.off.zRot = Mth.lerp(ease, arms.off.zRot, arms.inward(0.18f));
				head.xRot = Mth.lerp(ease, head.xRot, -0.2f);
			}
			case FLARE_TELL -> {
				// The off arm straight up with the flare pistol (its ember glows at the hand).
				arms.off.xRot = Mth.lerp(ease, arms.off.xRot, -3.1f);
				arms.off.yRot = Mth.lerp(ease, arms.off.yRot, 0f);
				arms.off.zRot = Mth.lerp(ease, arms.off.zRot, arms.inward(0.08f));
				head.xRot = Mth.lerp(ease, head.xRot, -0.45f);
			}
			case GRENADE_TELL -> {
				// The throwing arm drawn back over the shoulder, the body twisted into the throw.
				arms.off.xRot = Mth.lerp(ease, arms.off.xRot, -3.45f);
				arms.off.yRot = Mth.lerp(ease, arms.off.yRot, 0f);
				arms.off.zRot = Mth.lerp(ease, arms.off.zRot, arms.inward(0.45f));
				body.yRot = Mth.lerp(ease, body.yRot, arms.inward(0.3f));
			}
			case SALUTE -> {
				// The main hand to the brow (the weapon is put away for it).
				arms.main.xRot = Mth.lerp(ease, arms.main.xRot, -2.25f);
				arms.main.yRot = Mth.lerp(ease, arms.main.yRot, arms.inward(0.65f));
				arms.main.zRot = Mth.lerp(ease, arms.main.zRot, -arms.inward(0.1f));
			}
			case STANDOFF -> {
				// Rifle held low across the body: watchful, not aiming.
				arms.main.xRot = Mth.lerp(ease, arms.main.xRot, -0.75f);
				arms.main.yRot = Mth.lerp(ease, arms.main.yRot, arms.inward(0.55f));
				arms.off.xRot = Mth.lerp(ease, arms.off.xRot, -0.85f);
				arms.off.yRot = Mth.lerp(ease, arms.off.yRot, -arms.inward(0.5f));
			}
			case BOUND -> {
				// Hands behind the back.
				arms.main.xRot = Mth.lerp(ease, arms.main.xRot, 0.45f);
				arms.off.xRot = Mth.lerp(ease, arms.off.xRot, 0.45f);
				arms.main.yRot = Mth.lerp(ease, arms.main.yRot, 0f);
				arms.off.yRot = Mth.lerp(ease, arms.off.yRot, 0f);
				arms.main.zRot = Mth.lerp(ease, arms.main.zRot, arms.inward(0.3f));
				arms.off.zRot = Mth.lerp(ease, arms.off.zRot, -arms.inward(0.3f));
				head.xRot = Mth.lerp(ease, head.xRot, 0.25f);
			}
			case IDLE -> {
			}
		}

		if (state.kneel > 0f && state.action != TrooperAction.HEALING && state.action != TrooperAction.KNEEL_TEND) {
			KneelPose.apply(this, state.kneel);
		}
	}

	/** Lying on its side (the renderer turns the body): limbs slack, one knee drawn up. */
	private void downedPose() {
		head.xRot = 0.15f;
		head.yRot = 0f;
		rightArm.xRot = -0.25f;
		rightArm.yRot = 0f;
		rightArm.zRot = 0.35f;
		leftArm.xRot = -0.6f;
		leftArm.yRot = 0f;
		leftArm.zRot = -0.15f;
		rightLeg.xRot = -0.5f;
		rightLeg.yRot = 0f;
		leftLeg.xRot = 0.1f;
		leftLeg.yRot = 0f;
		body.xRot = 0f;
		body.yRot = 0f;
	}

	private void showParts(TrooperRole role) {
		boolean officer = role == TrooperRole.OFFICER;
		boolean armoured = !officer;
		hat.visible = armoured;
		cuirass.visible = armoured;
		belt.visible = armoured;
		rightPauldron.visible = armoured;
		leftPauldron.visible = armoured;
		injector.visible = role == TrooperRole.MEDIC;
		bandolier.visible = role == TrooperRole.GRENADIER;
		cap.visible = officer;
		capPeak.visible = officer;
		tunic.visible = officer;
		rightSleeve.visible = officer;
		leftSleeve.visible = officer;

		for (ModelPart part : helmet) {
			if (part != null) {
				part.visible = armoured;
			}
		}
	}

	/**
	 * The main-hand and off-hand arms for the state's main arm. {@link #inward} gives the yaw or roll
	 * that turns the main arm toward the body's centre line (negative for the right arm, as in
	 * vanilla's crossbow hold); negate it for the off arm.
	 */
	private static final class HumanoidArmParts {
		final ModelPart main;
		final ModelPart off;
		private final float sign;

		HumanoidArmParts(TrooperModel model, TrooperRenderState state) {
			boolean right = state.mainArm != HumanoidArm.LEFT;
			this.main = right ? model.rightArm : model.leftArm;
			this.off = right ? model.leftArm : model.rightArm;
			this.sign = right ? -1f : 1f;
		}

		/** {@code amount} radians toward the centre line for the main arm. */
		float inward(float amount) {
			return sign * amount;
		}
	}
}
