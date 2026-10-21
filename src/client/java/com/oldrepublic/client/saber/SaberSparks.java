package com.oldrepublic.client.saber;

import java.util.HashMap;
import java.util.Map;

import com.oldrepublic.client.force.energy.Anchor;
import com.oldrepublic.client.force.energy.EnergyRender;
import com.oldrepublic.client.force.energy.Flare;
import com.oldrepublic.client.force.energy.Glint;
import com.oldrepublic.client.force.energy.Palette;
import com.oldrepublic.client.force.energy.Spark;
import com.oldrepublic.client.fx.GlowRibbon;
import com.oldrepublic.client.fx.Vfx;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The sparks and contact light of a saber clash or deflection, also used by other client features (the Engineering
 * Bench's welding arm, the stun grenade, an electrostaff blocking a saber, a Legendary craft), and the saber's other
 * touches of light: the glint of an attack tell and the streaks of a lunge or a staff whirl. They are drawn in code as
 * light ({@link EnergyRender}), not as square particles. A spark is a short streak along its flight, white-hot at first,
 * then cooling to the given colour as it falls, and it bounces off the ground. The contact flash is a burst of light that
 * dies away over a few ticks, or a soft glow when flashing effects are off. Callers check {@link Vfx#inRange} and scale
 * counts with {@link Vfx#count} themselves (an attack tell is never thinned).
 */
public final class SaberSparks {
	private static final RandomSource RANDOM = RandomSource.create();
	/** The owner key of every spark and flash ({@link EnergyRender#owner}); none is ever taken away early. */
	private static final long OWNER = EnergyRender.owner(0x5A8E, 0);
	/** Fall a tick, and the share of speed kept a tick. */
	private static final float GRAVITY = 0.018f;
	private static final float DRAG = 0.88f;
	/** A spark's life in ticks: at least this, plus up to {@link #LIFE_SPREAD} more. */
	private static final int LIFE = 7;
	private static final int LIFE_SPREAD = 6;
	/** The glow's radius (blocks) of a white-hot spark and of a cooled one; the core is a sixth of it. */
	private static final float HOT_RADIUS = 0.06f;
	private static final float COOL_RADIUS = 0.045f;
	/** How much wider than the callers' cone the sparks fly, so a clash sprays rather than streams. */
	private static final double FAN = 1.6;
	/** A glint's light (blocks): wider than a spark's, its points a little thicker. */
	private static final float GLINT_RADIUS = 0.12f;
	/** A streak's width against a spark's, and the share of its speed it keeps a tick. */
	private static final float STREAK_SIZE = 1.5f;
	private static final float STREAK_DRAG = 0.72f;
	/** White-hot, cooled and glint light by colour, made once. */
	private static final Map<Integer, Palette[]> PALETTES = new HashMap<>();

	/**
	 * {@code count} sparks thrown out around {@code dir} (the wider {@code spread}, the wider the fan), flying about
	 * {@code speed} blocks a tick, cooling toward {@code color}.
	 */
	public static void sparks(Vec3 pos, Vec3 dir, double spread, int count, int color, double speed) {
		Vec3 axis = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : dir.normalize();
		Palette[] light = palettes(color);

		for (int i = 0; i < count; i++) {
			Vec3 r = new Vec3(RANDOM.nextDouble() * 2 - 1, RANDOM.nextDouble() * 2 - 1, RANDOM.nextDouble() * 2 - 1);
			Vec3 v = axis.add(r.scale(spread * FAN)).normalize().scale(speed * (0.4 + RANDOM.nextDouble() * 1.2));
			float size = 0.7f + RANDOM.nextFloat() * 0.6f;
			EnergyRender.add(new Spark(OWNER, pos, v, LIFE + RANDOM.nextInt(LIFE_SPREAD + 1), size, GRAVITY, DRAG, light[0], light[1]));
		}
	}

	/** A burst of {@code color} light that dies away in a few ticks, or a soft glow that never flashes with flashing effects off. */
	public static void flash(Vec3 pos, int color) {
		Flare flare = Flare.on(OWNER, Anchor.fixed(pos), palettes(color)[0]).core(0.05f);

		if (Vfx.flashing()) {
			flare.burst(4).radius(0.7f, 0.35f);
		} else {
			flare.life(6, 2f, 4f).radius(0.45f).strength(0.35f);
		}

		EnergyRender.add(flare);
	}

	/**
	 * A soft glow of {@code color} that fades over a few ticks and never flashes; spawned every few ticks it reads as steady
	 * light (a Legendary craft's glow, MILESTONE_5.md 15.3).
	 */
	public static void glow(Vec3 pos, int color) {
		EnergyRender.add(Flare.on(OWNER, Anchor.fixed(pos), palettes(color)[1]).life(5, 1.5f, 3.5f).radius(0.4f).strength(0.35f));
	}

	/**
	 * A glint of {@code color} light at {@code at} (following it, so it stays on a moving blade tip): a pinpoint that opens
	 * into a four-pointed star, its points {@code size} blocks long, and closes again over {@code life} ticks.
	 */
	public static void glint(Anchor at, int color, int life, float size) {
		EnergyRender.add(new Glint(OWNER, at, palettes(color)[2], life, size, 1f));
	}

	/**
	 * A streak of {@code color} light thrown along {@code velocity} (blocks a tick) that slows without falling, cooling from
	 * white-hot as it fades over {@code life} ticks: the blade light left along a lunge or round a staff whirl.
	 */
	public static void streak(Vec3 pos, Vec3 velocity, int color, int life) {
		Palette[] light = palettes(color);
		EnergyRender.add(new Spark(OWNER, pos, velocity, life, STREAK_SIZE, 0f, STREAK_DRAG, light[0], light[1]));
	}

	/** The white-hot light (first), the cooled light (second) and a glint's light (third) of {@code color}. */
	private static Palette[] palettes(int color) {
		return PALETTES.computeIfAbsent(color & 0xFFFFFF,
				rgb -> new Palette[] {palette(rgb, 0.8f, HOT_RADIUS), palette(rgb, 0.35f, COOL_RADIUS), palette(rgb, 0.7f, GLINT_RADIUS)});
	}

	/**
	 * Light of {@code rgb}: a core {@code white} of the way to white, inside a glow of the colour {@code radius} blocks
	 * wide; the flare (the flash's light) is the core fading into the colour.
	 */
	private static Palette palette(int rgb, float white, float radius) {
		int core = SaberBladeRenderer.mix(rgb, 0xFFFFFF, white);
		int pale = SaberBladeRenderer.mix(rgb, 0xFFFFFF, white * 0.6f);
		GlowRibbon.Profile glow = new GlowRibbon.Profile(new float[] {0f, 0.3f, 1f}, new int[] {argb(0.7f, pale), argb(0.28f, rgb), argb(0f, rgb)});
		GlowRibbon.Profile hot = new GlowRibbon.Profile(new float[] {0f, 0.5f, 1f}, new int[] {argb(1f, core), argb(0.9f, pale), argb(0f, rgb)});
		GlowRibbon.Profile flare = new GlowRibbon.Profile(new float[] {0f, 0.15f, 0.45f, 1f},
				new int[] {argb(0.85f, core), argb(0.5f, pale), argb(0.14f, rgb), argb(0f, rgb)});
		return new Palette(glow, radius, hot, radius / 6f, flare);
	}

	private static int argb(float alpha, int rgb) {
		return Mth.clamp(Math.round(alpha * 255f), 0, 255) << 24 | rgb & 0xFFFFFF;
	}

	private SaberSparks() {
	}
}
