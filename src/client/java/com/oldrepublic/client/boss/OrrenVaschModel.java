package com.oldrepublic.client.boss;

import com.oldrepublic.OldRepublicMod;

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

/**
 * Orren Vasch (spec 8.1): the vanilla humanoid in layered Archivist robes, with wide sleeves, a long
 * robe skirt, a cloak hung from the shoulders, a bandolier of empty holocron cradles across the chest,
 * a satchel at the right hip, and in phase 3 the stolen Sith holocron in his off-hand. The UV layout
 * (64x64) is the frozen W5 plan 4.10, painted by B in {@code tools/assets/gen_textures_boss.py}:
 * head 0,0, hat 32,0 (hair tie and short beard; the hood on the Unkept skin), body 16,16, arms 40,16
 * and legs 0,16 (left limbs mirrored), robe 16,32, sleeves 40,32, skirt 0,48, cloak 0,32,
 * bandolier 24,48, satchel 24,54, holocron 36,54.
 *
 * <p>Poses, from the synced action: reading at the lectern; the Hurl (off-hand raised as a chunk
 * tears loose); the Palm (hand drawn back); the Lash (blade low, dragged along the floor); the Lunge
 * (the renderer crouches him, blade drawn low); the Surge (holocron raised overhead, the cloak lifting
 * and streaming on the inrushing air); Spent (sagging, head down, arms hanging); the Grip (off-hand
 * reaching for the target, trembling while he channels); the transition leap (arms raised, the
 * crystal lifted); taking up the holocron (Unkept); bound at the wrists (custody). The kneel blends
 * over everything; the fall after the execution's stroke is the renderer's.
 */
public class OrrenVaschModel extends HumanoidModel<OrrenVaschRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("orren_vasch"), "main");

	/** The cloak's resting tilt away from the back, so it clears the robe and skirt. */
	private static final float CLOAK_REST = 0.06f;
	/** How far the cloak lifts at the height of the Surge gather. */
	private static final float CLOAK_SURGE = 1.15f;
	/** Ticks most poses take to blend in after their action starts. */
	private static final float BLEND_TICKS = 6f;

	private final ModelPart cloak;
	private final ModelPart holocron;

	public OrrenVaschModel(ModelPart root) {
		super(root);
		this.cloak = body.getChild("cloak");
		this.holocron = leftArm.getChild("holocron");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm");
		PartDefinition leftArm = root.getChild("left_arm");

		body.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(16, 32)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 48)
				.addBox(-4f, 12f, -2f, 8f, 8f, 4f, new CubeDeformation(0.4f)), PartPose.ZERO);
		// Hung from the shoulders behind the back; it lifts and streams in the Surge gather.
		body.addOrReplaceChild("cloak", CubeListBuilder.create().texOffs(0, 32)
				.addBox(-4f, 0f, 0f, 8f, 16f, 0f), PartPose.offsetAndRotation(0f, 0.3f, 2.45f, CLOAK_REST, 0f, 0f));
		// The strap of empty holocron cradles, from the left shoulder down to the right hip.
		body.addOrReplaceChild("bandolier", CubeListBuilder.create().texOffs(24, 48)
				.addBox(-4.5f, -0.5f, -2.5f, 9f, 1f, 5f), PartPose.offsetAndRotation(0f, 6f, 0f, 0f, 0f, 0.75f));
		// The satchel at the front of the right hip, clear of the hanging sleeve.
		body.addOrReplaceChild("satchel", CubeListBuilder.create().texOffs(24, 54)
				.addBox(-2f, 0f, -1f, 4f, 4f, 2f), PartPose.offsetAndRotation(-2.6f, 10f, -3.3f, 0f, 0.15f, 0f));
		rightArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(40, 32)
				.addBox(-3f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		leftArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(40, 32).mirror()
				.addBox(-1f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		// Cupped under the palm of the off-hand; shown in phase 3 only.
		leftArm.addOrReplaceChild("holocron", CubeListBuilder.create().texOffs(36, 54)
				.addBox(-1f, 9f, -1f, 2f, 2f, 2f), PartPose.offset(1f, 1.6f, -1.2f));

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(OrrenVaschRenderState state) {
		super.setupAnim(state);
		holocron.visible = state.holocron;
		float time = state.ageInTicks;
		float age = state.actionAge;
		float blend = Mth.clamp(age / BLEND_TICKS, 0f, 1f);

		// The cloak sways a little at rest and lifts with his stride.
		cloak.xRot = CLOAK_REST + 0.45f * Mth.clamp(state.walkAnimationSpeed, 0f, 1f) + 0.02f * Mth.sin(time * 0.09f);

		if (state.fall > 0f) {
			fallen();
			return;
		}

		switch (state.action) {
			case READING -> {
				// Head bowed over the book on the lectern, both hands on its pages.
				head.xRot = Mth.lerp(blend, head.xRot, 0.55f);
				rightArm.xRot = Mth.lerp(blend, rightArm.xRot, -0.6f);
				rightArm.yRot = Mth.lerp(blend, rightArm.yRot, -0.2f);
				leftArm.xRot = Mth.lerp(blend, leftArm.xRot, -0.7f);
				leftArm.yRot = Mth.lerp(blend, leftArm.yRot, 0.25f);
			}
			case HURL -> {
				// The off-hand rises as the chunk tears loose; he looks up after it.
				leftArm.xRot = Mth.lerp(blend, leftArm.xRot, -2.7f);
				leftArm.yRot = Mth.lerp(blend, leftArm.yRot, 0.1f);
				leftArm.zRot = Mth.lerp(blend, leftArm.zRot, -0.25f);
				head.xRot = Mth.lerp(blend, head.xRot, -0.25f);
			}
			case PALM -> {
				// The hand drawn back, palm open.
				leftArm.xRot = Mth.lerp(blend, leftArm.xRot, 0.75f);
				leftArm.yRot = Mth.lerp(blend, leftArm.yRot, 0f);
				leftArm.zRot = Mth.lerp(blend, leftArm.zRot, -0.3f);
			}
			case LASH -> {
				// The blade low and out to the side, its tip dragged along the floor.
				rightArm.xRot = Mth.lerp(blend, rightArm.xRot, 0.35f);
				rightArm.yRot = Mth.lerp(blend, rightArm.yRot, 0f);
				rightArm.zRot = Mth.lerp(blend, rightArm.zRot, 0.45f);
				leftArm.zRot = Mth.lerp(blend, leftArm.zRot, -0.2f);
			}
			case LUNGE -> {
				// Crouched (the renderer sets the vanilla crouch), the blade drawn low and back.
				rightArm.xRot = Mth.lerp(blend, rightArm.xRot, 0.55f);
				rightArm.zRot = Mth.lerp(blend, rightArm.zRot, 0.25f);
				leftArm.xRot = Mth.lerp(blend, leftArm.xRot, -0.5f);
			}
			case SURGE -> surge(age, time, state.flashing);
			case SPENT -> {
				// Sagging after the Surge: head down, shoulders slumped, arms hanging.
				head.xRot = Mth.lerp(blend, head.xRot, 0.6f);
				body.xRot = Mth.lerp(blend, body.xRot, 0.25f);
				rightArm.xRot = Mth.lerp(blend, rightArm.xRot, 0.3f);
				rightArm.zRot = Mth.lerp(blend, rightArm.zRot, 0.06f);
				leftArm.xRot = Mth.lerp(blend, leftArm.xRot, 0.25f);
				leftArm.zRot = Mth.lerp(blend, leftArm.zRot, -0.06f);
			}
			case GRIP -> {
				// The off-hand reaches for the target along his look, trembling once he channels.
				float tremble = age > 10f ? 0.04f * Mth.sin(time * 2.3f) : 0f;
				leftArm.xRot = Mth.lerp(blend, leftArm.xRot, head.xRot - Mth.HALF_PI + tremble);
				leftArm.yRot = Mth.lerp(blend, leftArm.yRot, head.yRot + 0.1f);
				leftArm.zRot = Mth.lerp(blend, leftArm.zRot, 0f);
			}
			case LEAP -> {
				// The transition leap: both arms raised, the singing crystal lifted in the off-hand.
				rightArm.xRot = Mth.lerp(blend, rightArm.xRot, -2.1f);
				rightArm.zRot = Mth.lerp(blend, rightArm.zRot, 0.3f);
				leftArm.xRot = Mth.lerp(blend, leftArm.xRot, -3.0f);
				leftArm.zRot = Mth.lerp(blend, leftArm.zRot, -0.1f);
				head.xRot = Mth.lerp(blend, head.xRot, -0.35f);
			}
			case UNKEPT -> {
				// Taking up the holocron: held before his chest, his eyes on it.
				leftArm.xRot = Mth.lerp(blend, leftArm.xRot, -1.35f);
				leftArm.yRot = Mth.lerp(blend, leftArm.yRot, 0.35f);
				head.xRot = Mth.lerp(blend, head.xRot, 0.35f);
			}
			case CUFFED -> {
				// Wrists bound together in front of him, head a little lowered.
				rightArm.xRot = Mth.lerp(blend, rightArm.xRot, -0.55f);
				rightArm.yRot = Mth.lerp(blend, rightArm.yRot, -0.4f);
				rightArm.zRot = 0f;
				leftArm.xRot = Mth.lerp(blend, leftArm.xRot, -0.55f);
				leftArm.yRot = Mth.lerp(blend, leftArm.yRot, 0.4f);
				leftArm.zRot = 0f;
				head.xRot = Mth.lerp(blend, head.xRot, 0.3f);
			}
			default -> {
			}
		}

		kneel(state.kneel);
	}

	/**
	 * The Surge gather: the holocron raised overhead, the blade held out low, and the cloak lifting
	 * and streaming out on the air rushing in toward him; with flashing effects on its edge ripples.
	 */
	private void surge(float age, float time, boolean flashing) {
		float raise = Mth.clamp(age / 8f, 0f, 1f);
		leftArm.xRot = Mth.lerp(raise, leftArm.xRot, -2.9f);
		leftArm.yRot = Mth.lerp(raise, leftArm.yRot, 0f);
		leftArm.zRot = Mth.lerp(raise, leftArm.zRot, -0.1f);
		rightArm.xRot = Mth.lerp(raise, rightArm.xRot, 0.2f);
		rightArm.zRot = Mth.lerp(raise, rightArm.zRot, 0.5f);
		head.xRot = Mth.lerp(raise, head.xRot, -0.3f);
		// The cloak keeps lifting through the whole 30-tick gather.
		float lift = Mth.clamp(age / 24f, 0f, 1f);
		float ripple = flashing ? 0.12f * Mth.sin(time * 1.7f) : 0.05f * Mth.sin(time * 0.6f);
		cloak.xRot = CLOAK_REST + CLOAK_SURGE * lift + ripple * lift;
		cloak.zRot = 0.04f * Mth.sin(time * 1.1f) * lift;
	}

	/** Lying face down where the stroke left him (the renderer tips the body): arms at his sides, head turned. */
	private void fallen() {
		head.xRot = 0f;
		head.yRot = 0.7f;
		body.xRot = 0f;
		rightArm.xRot = 0.1f;
		rightArm.yRot = 0f;
		rightArm.zRot = 0.15f;
		leftArm.xRot = -0.2f;
		leftArm.yRot = 0f;
		leftArm.zRot = -0.25f;
		rightLeg.xRot = 0f;
		leftLeg.xRot = 0.08f;
		cloak.xRot = CLOAK_REST;
	}

	/**
	 * Down on one knee, head bowed, arms hanging, blended by {@code amount} (0 to 1). The renderer
	 * lowers the body by {@link OrrenVaschRenderer#KNEEL_DROP}.
	 */
	private void kneel(float amount) {
		if (amount <= 0f) {
			return;
		}

		rightLeg.xRot = Mth.lerp(amount, rightLeg.xRot, -1.45f);
		rightLeg.yRot = Mth.lerp(amount, rightLeg.yRot, 0.12f);
		leftLeg.xRot = Mth.lerp(amount, leftLeg.xRot, 1.3f);
		leftLeg.yRot = Mth.lerp(amount, leftLeg.yRot, -0.08f);
		head.xRot = Mth.lerp(amount, head.xRot, 0.45f);
		body.xRot = Mth.lerp(amount, body.xRot, 0.12f);
		rightArm.xRot = Mth.lerp(amount, rightArm.xRot, 0.15f);
		rightArm.yRot = Mth.lerp(amount, rightArm.yRot, 0f);
		rightArm.zRot = Mth.lerp(amount, rightArm.zRot, 0.1f);
		leftArm.xRot = Mth.lerp(amount, leftArm.xRot, 0.15f);
		leftArm.yRot = Mth.lerp(amount, leftArm.yRot, 0f);
		leftArm.zRot = Mth.lerp(amount, leftArm.zRot, -0.1f);
		cloak.xRot = Mth.lerp(amount, cloak.xRot, 0.3f);
	}
}
