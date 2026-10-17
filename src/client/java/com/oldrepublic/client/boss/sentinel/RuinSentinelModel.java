package com.oldrepublic.client.boss.sentinel;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.construct.SentinelVariant;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Ruin Sentinel model (spec 7.7): a humanoid frame of carved stone and bronze plates, 2.3 blocks tall
 * once {@link RuinSentinelRenderer} scales it. Beyond the vanilla humanoid it has a crest along the
 * helm, pauldrons, forearm gauntlets, greaves, a waist fauld, a back slab, the chest lumen, and two
 * chest plates hinged at the sides that part to expose the lumen (the exposed core, the lance tell).
 * The Watcher wears a carved tabard that swings with the forward leg; the Tomb sentinel has bronze
 * spikes along its pauldrons instead. The hat part is empty (the crest replaces it).
 *
 * <p>Both variants share this mesh and UV layout (64x64), painted by
 * {@code tools/assets/gen_textures_boss.py}: head 0,0; crest 32,0; legs 0,16; body 16,16; arms 40,16;
 * chest plates 0,32; spikes 12,32; pauldrons 24,32; lumen 48,32; fauld 0,41; gauntlets 32,41;
 * greaves 0,50; tabard 20,50; back slab 36,52 (left limbs and their plates mirrored). The lumen
 * texture uses the same layout.
 *
 * <p>Poses, from the synced action: the statue (head a little lowered, limbs straight, no sway),
 * the Watcher's bow, the wake (rising from the statue pose with a grinding tremor), the slam tell
 * (arms raised overhead, leaning back), the slam and exposed core (arms driven down, chest plates
 * parted), the lance tell (arms drawn back and out, chest thrust forward, plates half open), the
 * stagger and the crumbling slump on death.
 */
public class RuinSentinelModel extends HumanoidModel<RuinSentinelRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("ruin_sentinel"), "main");

	/** Ticks the wake pose takes to rise out of the statue (the wake tell's default length). */
	private static final float WAKE_POSE_TICKS = 20f;
	/** Ticks to raise the arms at the start of the slam tell. */
	private static final float SLAM_RAISE_TICKS = 8f;
	/** Ticks of the downward blow at the start of the exposure. */
	private static final float SLAM_STRIKE_TICKS = 3f;
	/** Ticks to draw the arms back at the start of the lance tell. */
	private static final float LANCE_DRAW_TICKS = 6f;
	/** Ticks for the head to lower into, or rise out of, the bow. */
	static final float BOW_TICKS = 10f;
	/** How far the chest plates swing open, in radians. */
	private static final float PLATE_OPEN = 1.1f;
	/** The statue's lowered head, in radians. */
	private static final float STATUE_HEAD = 0.2f;

	private final ModelPart rightPlate;
	private final ModelPart leftPlate;
	private final ModelPart tabard;
	private final ModelPart rightSpikes;
	private final ModelPart leftSpikes;

	public RuinSentinelModel(ModelPart root) {
		super(root);
		this.rightPlate = body.getChild("right_plate");
		this.leftPlate = body.getChild("left_plate");
		this.tabard = body.getChild("tabard");
		this.rightSpikes = rightArm.getChild("spikes");
		this.leftSpikes = leftArm.getChild("spikes");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-4f, -8f, -4f, 8f, 8f, 8f), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(32, 0)
				.addBox(-1f, -11f, -5f, 2f, 3f, 10f), PartPose.ZERO);

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f), PartPose.ZERO);
		body.addOrReplaceChild("right_plate", CubeListBuilder.create().texOffs(0, 32)
				.addBox(0f, 1f, -1f, 4f, 6f, 1f), PartPose.offset(-4f, 0f, -2f));
		body.addOrReplaceChild("left_plate", CubeListBuilder.create().texOffs(0, 32).mirror()
				.addBox(-4f, 1f, -1f, 4f, 6f, 1f), PartPose.offset(4f, 0f, -2f));
		body.addOrReplaceChild("lumen", CubeListBuilder.create().texOffs(48, 32)
				.addBox(-2f, 2f, -2.5f, 4f, 4f, 1f), PartPose.ZERO);
		body.addOrReplaceChild("fauld", CubeListBuilder.create().texOffs(0, 41)
				.addBox(-5f, 10f, -3f, 10f, 3f, 6f), PartPose.ZERO);
		body.addOrReplaceChild("tabard", CubeListBuilder.create().texOffs(20, 50)
				.addBox(-3f, 0f, -1.5f, 6f, 9f, 1f), PartPose.offset(0f, 12f, -2f));
		body.addOrReplaceChild("back_slab", CubeListBuilder.create().texOffs(36, 52)
				.addBox(-3f, 1f, 2f, 6f, 10f, 2f), PartPose.ZERO);

		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16)
				.addBox(-3f, -2f, -2f, 4f, 12f, 4f), PartPose.offset(-5f, 2f, 0f));
		rightArm.addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(24, 32)
				.addBox(-4f, -3f, -3f, 6f, 3f, 6f), PartPose.ZERO);
		rightArm.addOrReplaceChild("spikes", CubeListBuilder.create()
				.texOffs(12, 32).addBox(-3f, -6f, -1f, 2f, 3f, 2f)
				.texOffs(12, 32).addBox(0f, -5f, -1f, 2f, 2f, 2f), PartPose.ZERO);
		rightArm.addOrReplaceChild("gauntlet", CubeListBuilder.create().texOffs(32, 41)
				.addBox(-3.5f, 4f, -2.5f, 5f, 6f, 5f), PartPose.ZERO);

		PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror()
				.addBox(-1f, -2f, -2f, 4f, 12f, 4f), PartPose.offset(5f, 2f, 0f));
		leftArm.addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(24, 32).mirror()
				.addBox(-2f, -3f, -3f, 6f, 3f, 6f), PartPose.ZERO);
		leftArm.addOrReplaceChild("spikes", CubeListBuilder.create().mirror()
				.texOffs(12, 32).addBox(1f, -6f, -1f, 2f, 3f, 2f)
				.texOffs(12, 32).addBox(-2f, -5f, -1f, 2f, 2f, 2f), PartPose.ZERO);
		leftArm.addOrReplaceChild("gauntlet", CubeListBuilder.create().texOffs(32, 41).mirror()
				.addBox(-1.5f, 4f, -2.5f, 5f, 6f, 5f), PartPose.ZERO);

		PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16)
				.addBox(-2f, 0f, -2f, 4f, 12f, 4f), PartPose.offset(-1.9f, 12f, 0f));
		rightLeg.addOrReplaceChild("greave", CubeListBuilder.create().texOffs(0, 50)
				.addBox(-2.5f, 6f, -2.5f, 5f, 6f, 5f), PartPose.ZERO);
		PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror()
				.addBox(-2f, 0f, -2f, 4f, 12f, 4f), PartPose.offset(1.9f, 12f, 0f));
		leftLeg.addOrReplaceChild("greave", CubeListBuilder.create().texOffs(0, 50).mirror()
				.addBox(-2.5f, 6f, -2.5f, 5f, 6f, 5f), PartPose.ZERO);

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(RuinSentinelRenderState state) {
		super.setupAnim(state);
		boolean tomb = state.variant == SentinelVariant.TOMB;
		tabard.visible = !tomb;
		rightSpikes.visible = tomb;
		leftSpikes.visible = tomb;

		// A heavy construct: short strides, stiff arms held a little away from the body, no idle sway.
		rightLeg.xRot *= 0.7f;
		leftLeg.xRot *= 0.7f;
		rightArm.xRot *= 0.5f;
		leftArm.xRot *= 0.5f;
		rightArm.zRot = 0.1f;
		leftArm.zRot = -0.1f;
		float plates = 0f;

		if (state.deathTime > 0f) {
			plates = slump(state);
		} else if (state.dormant) {
			statue(state);
		} else {
			plates = action(state);
		}

		rightPlate.yRot = plates * PLATE_OPEN;
		leftPlate.yRot = -plates * PLATE_OPEN;
		rightPlate.x -= plates * 0.6f;
		leftPlate.x += plates * 0.6f;
		// The tabard hangs in front of whichever leg is forward.
		tabard.xRot = Math.min(0f, Math.min(rightLeg.xRot, leftLeg.xRot));
	}

	/** The dormant statue, and the Watcher's bow. */
	private void statue(RuinSentinelRenderState state) {
		head.xRot = STATUE_HEAD + 0.45f * state.bow;
		head.yRot = 0f;
		body.xRot = 0.12f * state.bow;
		rightArm.xRot = -0.05f;
		leftArm.xRot = -0.05f;
		rightArm.yRot = 0f;
		leftArm.yRot = 0f;
		rightLeg.xRot = 0f;
		leftLeg.xRot = 0f;
	}

	/** The awake poses; returns how far the chest plates are open (0 to 1). */
	private float action(RuinSentinelRenderState state) {
		float age = state.actionAge;

		switch (state.action) {
			case WAKING -> {
				float p = ease(age / WAKE_POSE_TICKS);
				// Stone grinding loose: a fine tremor that fades as it rises.
				float tremor = (1f - p) * 0.04f * Mth.sin(age * 2.7f);
				head.xRot = Mth.lerp(p, STATUE_HEAD, head.xRot) + tremor;
				head.yRot *= p;
				float stretch = 0.3f * Mth.sin(p * Mth.PI);
				rightArm.zRot += stretch + tremor;
				leftArm.zRot -= stretch + tremor;
				rightArm.xRot = Mth.lerp(p, -0.05f, rightArm.xRot);
				leftArm.xRot = Mth.lerp(p, -0.05f, leftArm.xRot);
				return 0f;
			}
			case SLAM_TELL -> {
				float raise = ease(age / SLAM_RAISE_TICKS);
				float strain = age > 12f ? 0.03f * Mth.sin(age * 3.1f) : 0f;
				rightArm.xRot = Mth.lerp(raise, rightArm.xRot, -2.9f) + strain;
				leftArm.xRot = Mth.lerp(raise, leftArm.xRot, -2.9f) - strain;
				rightArm.zRot = Mth.lerp(raise, rightArm.zRot, -0.12f);
				leftArm.zRot = Mth.lerp(raise, leftArm.zRot, 0.12f);
				body.xRot = -0.15f * raise;
				head.xRot = Mth.lerp(raise, head.xRot, -0.2f);
				return 0f;
			}
			case EXPOSED -> {
				float strike = ease(age / SLAM_STRIKE_TICKS);
				float recover = ease((age - 6f) / 14f);
				float arms = age < SLAM_STRIKE_TICKS ? Mth.lerp(strike, -2.9f, -0.9f) : Mth.lerp(recover, -0.9f, -0.3f);
				rightArm.xRot = arms;
				leftArm.xRot = arms;
				rightArm.zRot = -0.05f;
				leftArm.zRot = 0.05f;
				body.xRot = 0.35f * (1f - 0.6f * recover);
				head.xRot = 0.25f;
				return ease(age / 4f);
			}
			case LANCE_TELL -> {
				float draw = ease(age / LANCE_DRAW_TICKS);
				rightArm.xRot = Mth.lerp(draw, rightArm.xRot, 0.45f);
				leftArm.xRot = Mth.lerp(draw, leftArm.xRot, 0.45f);
				rightArm.zRot = Mth.lerp(draw, rightArm.zRot, 0.5f);
				leftArm.zRot = Mth.lerp(draw, leftArm.zRot, -0.5f);
				body.xRot = -0.08f * draw;
				return 0.3f * draw;
			}
			case STAGGERED -> {
				float shake = 0.06f * Mth.sin(age * 1.9f);
				body.xRot = -0.3f + shake;
				head.xRot = -0.4f;
				head.zRot = 0.15f;
				rightArm.xRot = -0.3f;
				leftArm.xRot = -0.3f;
				rightArm.zRot = 0.7f + shake;
				leftArm.zRot = -0.7f - shake;
				return 0f;
			}
			default -> {
				return 0f;
			}
		}
	}

	/** Death: the head and shoulders slump, the arms hang and the chest plates fall loose. */
	private float slump(RuinSentinelRenderState state) {
		float d = ease(state.deathTime / 20f);
		head.xRot = Mth.lerp(d, head.xRot, 0.9f);
		body.xRot = 0.5f * d;
		rightArm.xRot = Mth.lerp(d, rightArm.xRot, -0.3f);
		leftArm.xRot = Mth.lerp(d, leftArm.xRot, -0.3f);
		rightArm.zRot = Mth.lerp(d, rightArm.zRot, 0.4f);
		leftArm.zRot = Mth.lerp(d, leftArm.zRot, -0.4f);
		rightLeg.xRot = -0.25f * d;
		leftLeg.xRot = 0.15f * d;
		return 0.8f * d;
	}

	/** Smoothstep of {@code t} clamped to 0..1. */
	private static float ease(float t) {
		float x = Mth.clamp(t, 0f, 1f);
		return x * x * (3f - 2f * x);
	}
}
