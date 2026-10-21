package com.oldrepublic.client.saber;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oldrepublic.client.fx.GlowRenderTypes;
import com.oldrepublic.client.fx.GlowRibbon;
import com.oldrepublic.client.fx.Vfx;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The saberstaff's whirl drawn as light (MILESTONE_4.md 5.6): both blades sweep a full turn round the
 * wielder at waist height in {@value #TICKS} ticks, fast at first and slowing, each leaving a fan of
 * light that fades back round behind it and in toward the wielder, with the smear of the white core just
 * behind the blade, so the whirl reads as a spinning disc, as the thrown saber's sweep does
 * ({@link ThrownSaberFx}). Sparks of blade light fly off the tips as they pass. The disc follows the
 * wielder and fades out over the second half of the turn; seen from the wielder's own eyes it is drawn
 * at half strength.
 */
final class StaffWhirlFx {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final int TICKS = 9;
	/** How far round each blade's fan reaches behind it (degrees), in steps of {@value #STEP}. */
	private static final float SWEEP = 150f;
	private static final float STEP = 7.5f;
	/** How far round the white core's smear reaches behind each blade (degrees). */
	private static final float CORE = 35f;
	private static final float ALPHA = 0.6f;
	/** The fan's inner edge, as a share of the whirl's radius. */
	private static final float INNER = 0.3f;

	private static final List<Whirl> WHIRLS = new ArrayList<>();

	/**
	 * One whirl: its wielder (followed while loaded), its feet where it began, the disc's height above the feet, its
	 * radius, the first blade's starting angle (degrees), the blade colour, the game time it began and how many of its
	 * {@value #TICKS} ticks throw sparks.
	 */
	private record Whirl(int entityId, Vec3 pos, float height, float radius, float start, int rgb, long born, int sparks) {
	}

	static void start(int entityId, Vec3 pos, float height, float radius, int rgb, long gameTime) {
		WHIRLS.add(new Whirl(entityId, pos, height, radius, RANDOM.nextFloat() * 360f, rgb, gameTime, Vfx.count(TICKS)));
	}

	/** Throws this tick's sparks off each blade tip and drops finished whirls. */
	static void tick(Minecraft mc) {
		if (mc.level == null) {
			clear();
			return;
		}

		long now = mc.level.getGameTime();
		WHIRLS.removeIf(whirl -> now - whirl.born() > TICKS);

		for (Whirl whirl : WHIRLS) {
			Vec3 centre = centre(mc, whirl, 1f);
			float lead = lead(whirl, now - whirl.born());

			for (int blade = 0; blade < 2; blade++) {
				if (RANDOM.nextInt(TICKS) >= whirl.sparks()) {
					continue;
				}

				float a = (lead + blade * 180f) * Mth.DEG_TO_RAD;
				Vec3 out = new Vec3(Mth.cos(a), 0, Mth.sin(a));
				Vec3 along = new Vec3(-Mth.sin(a), 0, Mth.cos(a));
				Vec3 at = centre.add(out.scale(whirl.radius() * (0.85 + RANDOM.nextDouble() * 0.15)));
				Vec3 velocity = along.scale(0.28 + RANDOM.nextDouble() * 0.1).add(out.scale(0.08)).add(0, 0.03 + RANDOM.nextDouble() * 0.04, 0);
				SaberSparks.streak(at, velocity, whirl.rgb(), 5 + RANDOM.nextInt(3));
			}
		}
	}

	static void clear() {
		WHIRLS.clear();
	}

	static void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null || WHIRLS.isEmpty()) {
			return;
		}

		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		// Drawn relative to the camera, so the floats stay small far from the world's origin.
		Vec3 cam = context.levelState().cameraRenderState.pos;
		Entity viewer = mc.getCameraEntity();
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowRenderTypes.glow(), (pose, buffer) -> {
			for (Whirl whirl : WHIRLS) {
				float age = now - whirl.born() + partial;

				if (age < 0f || age > TICKS) {
					continue;
				}

				float strength = ALPHA * (1f - smoothstep((age / TICKS - 0.45f) / 0.55f));

				if (firstPerson && viewer != null && viewer.getId() == whirl.entityId()) {
					strength *= 0.5f;
				}

				Vec3 centre = centre(mc, whirl, partial).subtract(cam);
				float lead = lead(whirl, age);
				// A fan never reaches back past where its blade began.
				float reach = Math.min(SWEEP, lead - whirl.start());

				for (int blade = 0; blade < 2; blade++) {
					fan(pose, buffer, centre, whirl, lead + blade * 180f, reach, strength);
				}
			}
		});
	}

	/** The disc's centre: at the wielder's waist while it is loaded, else above where the whirl began. */
	private static Vec3 centre(Minecraft mc, Whirl whirl, float partial) {
		Entity entity = mc.level.getEntity(whirl.entityId());
		Vec3 feet = entity != null ? entity.getPosition(partial) : whirl.pos();
		return feet.add(0, whirl.height(), 0);
	}

	/** The first blade's angle (degrees) {@code age} ticks into the whirl: a full turn, fast at first and slowing. */
	private static float lead(Whirl whirl, float age) {
		float u = Mth.clamp(age / TICKS, 0f, 1f);
		return whirl.start() + 360f * (1f - (1f - u) * (1f - u));
	}

	/**
	 * One blade's fan from the blade at {@code lead} back round {@code reach} degrees: the blade colour fading back round
	 * behind it and in toward the wielder, and over it, just behind the blade and out toward the tips, the smear of the
	 * blade's white core.
	 */
	private static void fan(PoseStack.Pose pose, VertexConsumer buffer, Vec3 centre, Whirl whirl, float lead, float reach, float strength) {
		float r = whirl.radius();
		band(pose, buffer, centre, lead, reach, r * INNER, r, whirl.rgb(), strength, 2f);
		band(pose, buffer, centre, lead, Math.min(CORE, reach), r * 0.55f, r, pale(whirl.rgb()), strength * 1.2f, 1.5f);
	}

	/** A band of the fan between two distances from the centre, fading back from the blade and in from the tips. */
	private static void band(PoseStack.Pose pose, VertexConsumer buffer, Vec3 centre, float lead, float reach, float inner, float outer, int rgb,
			float alpha, float falloff) {
		int steps = Math.max(1, Mth.ceil(reach / STEP));

		for (int i = 0; i < steps; i++) {
			float a0 = alpha * (float) Math.pow(1f - (float) i / steps, falloff);
			float a1 = alpha * (float) Math.pow(1f - (float) (i + 1) / steps, falloff);
			float d0 = lead - reach * i / steps;
			float d1 = lead - reach * (i + 1) / steps;
			vertex(pose, buffer, centre, d0, inner, rgb, a0 * 0.15f);
			vertex(pose, buffer, centre, d0, outer, rgb, a0);
			vertex(pose, buffer, centre, d1, outer, rgb, a1);
			vertex(pose, buffer, centre, d1, inner, rgb, a1 * 0.15f);
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 centre, float degrees, float distance, int rgb, float alpha) {
		float r = degrees * Mth.DEG_TO_RAD;
		buffer.addVertex(pose, (float) centre.x + Mth.cos(r) * distance, (float) centre.y, (float) centre.z + Mth.sin(r) * distance)
				.setColor(GlowRibbon.fade(0xFF000000 | rgb, alpha));
	}

	/** {@code rgb} taken a little under halfway to white. */
	private static int pale(int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return (r + (255 - r) * 9 / 20) << 16 | (g + (255 - g) * 9 / 20) << 8 | (b + (255 - b) * 9 / 20);
	}

	private static float smoothstep(float x) {
		float t = Mth.clamp(x, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	private StaffWhirlFx() {
	}
}
