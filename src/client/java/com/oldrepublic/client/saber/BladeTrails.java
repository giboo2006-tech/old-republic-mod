package com.oldrepublic.client.saber;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oldrepublic.client.fx.GlowRenderTypes;
import com.oldrepublic.client.fx.GlowRibbon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The motion trail behind a moving blade, like the smear of a fast blade on film: where each drawn blade was over the last
 * {@value #TRAIL_MILLIS} ms, swept into a ribbon of light that fades quickly behind the blade and toward the hilt, a glow of
 * the blade's colour with a paler smear of the core just behind the blade, and only where the blade moved fast (a swing, a
 * spin), so a blade held still or carried at a walk leaves nothing. Between two drawn frames the ribbon follows the arc the
 * blade turned through, so a slow frame rate still gives a curved sweep, not a flat sheet. The positions are the blade's
 * real ones, read from the pose it is drawn with ({@link SaberBladeRenderer}), so the trail follows every swing, stance and
 * technique exactly.
 *
 * <p>A blade seen in the world (third person, other players, armour stands) is remembered in world space, so the camera
 * may move freely. A blade in the first-person hand is remembered relative to the view, so turning the head never smears
 * it; only the swing does. Nothing is recorded while a screen is open (the inventory's player preview draws the same
 * stack in another space). A spinning, flying saber leaves no trail: its blades report where they are to
 * {@link ThrownSaberFx}, which draws their sweep ({@link #capture}).
 */
final class BladeTrails {
	private static final long TRAIL_MILLIS = 90L;
	private static final long TRAIL_NANOS = TRAIL_MILLIS * 1_000_000L;
	private static final long FORGET_NANOS = 1_000_000_000L;
	private static final int MAX_SAMPLES = 32;
	/** Tip speeds (blocks a second) where the trail starts to show and where it is at full strength. */
	private static final float SLOW = 2.5f;
	private static final float FAST = 9f;
	/** Steps from the hilt to the tip across the ribbon, for its fade toward the hilt. */
	private static final int ACROSS = 3;
	/** The most the blade turns (radians) within one step of the ribbon between two frames, and the most steps. */
	private static final float STEP_TURN = 0.1f;
	private static final int MAX_STEPS = 10;
	/** The glow's strength, and the core smear's: a paler colour, fading faster, just behind the blade. */
	private static final float GLOW = 0.75f;
	private static final float SMEAR = 0.3f;

	private record Key(ItemStack stack, int blade, boolean firstPerson) {
	}

	/** A blade's base and tip, in world space or (first person) view space. */
	private record Sample(long nanos, double bx, double by, double bz, double tx, double ty, double tz) {
	}

	private static final Map<Key, ArrayDeque<Sample>> HISTORIES = new HashMap<>();
	private static long lastPrune;
	/** Where blades report while {@link #capture} runs, or null. */
	private static @Nullable Sink sink;

	/** Told where each blade drawn under {@link #capture} is: its base and tip as the pose maps them. */
	@FunctionalInterface
	interface Sink {
		void blade(int blade, Vector3fc base, Vector3fc tip);
	}

	/** Runs {@code draw}, every blade it draws reporting to {@code to} instead of recording or drawing a trail. */
	static void capture(Sink to, Runnable draw) {
		Sink outer = sink;
		sink = to;

		try {
			draw.run();
		} finally {
			sink = outer;
		}
	}

	/**
	 * Remembers where blade {@code blade} of {@code stack} is now ({@code base} and {@code tip} as the current pose maps them)
	 * and submits its trail, if it has one.
	 */
	static void recordAndDraw(SubmitNodeCollector collector, ItemStack stack, int blade, boolean firstPerson, Vector3fc base, Vector3fc tip, int rgb,
			float strength) {
		if (sink != null) {
			sink.blade(blade, base, tip);
			return;
		}

		Minecraft mc = Minecraft.getInstance();

		if (mc.gui.screen() != null || mc.level == null) {
			return;
		}

		long now = System.nanoTime();
		prune(now);
		Key key = new Key(stack, blade, firstPerson);
		ArrayDeque<Sample> samples = HISTORIES.computeIfAbsent(key, k -> new ArrayDeque<>());
		Sample sample;

		if (firstPerson) {
			// The hand's pose maps into the camera-relative world, turned back by the view rotation the model-view applies.
			Matrix4f view = new Matrix4f(RenderSystem.getModelViewStack());
			Vector3f b = view.transformPosition(new Vector3f(base));
			Vector3f t = view.transformPosition(new Vector3f(tip));
			sample = new Sample(now, b.x, b.y, b.z, t.x, t.y, t.z);
		} else {
			Vec3 camera = mc.gameRenderer.mainCamera().position();
			sample = new Sample(now, camera.x + base.x(), camera.y + base.y(), camera.z + base.z(), camera.x + tip.x(), camera.y + tip.y(),
					camera.z + tip.z());
		}

		Sample last = samples.peekLast();

		if (last != null && now - last.nanos() < 2_000_000L) {
			// Drawn twice in a frame (a second pass): keep the first.
			return;
		}

		samples.addLast(sample);

		while (samples.size() > MAX_SAMPLES || now - samples.peekFirst().nanos() > TRAIL_NANOS) {
			samples.removeFirst();
		}

		if (samples.size() < 2) {
			return;
		}

		Sample[] trail = samples.toArray(new Sample[0]);
		PoseStack poseStack = new PoseStack();
		Vec3 offset;

		if (firstPerson) {
			poseStack.last().pose().set(new Matrix4f(RenderSystem.getModelViewStack()).invert());
			offset = Vec3.ZERO;
		} else {
			offset = mc.gameRenderer.mainCamera().position();
		}

		float[] alphas = alphas(trail, now, strength);
		boolean any = false;

		for (float a : alphas) {
			any |= a > 0.004f;
		}

		if (!any) {
			return;
		}

		int pale = SaberBladeRenderer.mix(0xFFFFFF, rgb, 0.45f);
		collector.submitCustomGeometry(poseStack, GlowRenderTypes.glow(), (pose, buffer) -> ribbon(pose, buffer, trail, alphas, offset, rgb, GLOW, 1));
		collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> ribbon(pose, buffer, trail, alphas, offset, pale, SMEAR, 3));
	}

	/** Each sample's alpha: fading with age, and only as strong as the tip was fast there. */
	private static float[] alphas(Sample[] trail, long now, float strength) {
		float[] alphas = new float[trail.length];

		for (int i = 0; i < trail.length; i++) {
			Sample s = trail[i];
			Sample previous = trail[Math.max(0, i - 1)];
			Sample next = trail[Math.min(trail.length - 1, i + 1)];
			double seconds = Math.max(1.0E-3, (next.nanos() - previous.nanos()) / 1.0E9);
			double dx = next.tx() - previous.tx();
			double dy = next.ty() - previous.ty();
			double dz = next.tz() - previous.tz();
			float speed = (float) (Math.sqrt(dx * dx + dy * dy + dz * dz) / seconds);
			float fast = Mth.clamp((speed - SLOW) / (FAST - SLOW), 0f, 1f);
			float age = Mth.clamp((now - s.nanos()) / (float) TRAIL_NANOS, 0f, 1f);
			float fresh = (1f - age) * (1f - age);
			alphas[i] = strength * fresh * fast * fast * (3f - 2f * fast);
		}

		return alphas;
	}

	/**
	 * The swept ribbon between consecutive blade positions, fading toward the hilt, each sample's alpha raised to {@code sharpen}
	 * (a higher power keeps it closer behind the blade). Each gap between two samples is split along the arc the blade turned
	 * through it: the base moved in a line, the blade turned about it.
	 */
	private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Sample[] trail, float[] alphas, Vec3 offset, int rgb, float alpha,
			int sharpen) {
		for (int i = 0; i + 1 < trail.length; i++) {
			float a0 = alpha * pow(alphas[i], sharpen);
			float a1 = alpha * pow(alphas[i + 1], sharpen);

			if (a0 <= 0.004f && a1 <= 0.004f) {
				continue;
			}

			Sample from = trail[i];
			Sample to = trail[i + 1];
			Vector3f baseFrom = new Vector3f((float) (from.bx() - offset.x), (float) (from.by() - offset.y), (float) (from.bz() - offset.z));
			Vector3f baseTo = new Vector3f((float) (to.bx() - offset.x), (float) (to.by() - offset.y), (float) (to.bz() - offset.z));
			Vector3f bladeFrom = new Vector3f((float) (from.tx() - offset.x), (float) (from.ty() - offset.y), (float) (from.tz() - offset.z)).sub(baseFrom);
			Vector3f bladeTo = new Vector3f((float) (to.tx() - offset.x), (float) (to.ty() - offset.y), (float) (to.tz() - offset.z)).sub(baseTo);
			float turn = bladeFrom.angle(bladeTo);
			int steps = Float.isFinite(turn) ? Mth.clamp(Mth.ceil(turn / STEP_TURN), 1, MAX_STEPS) : 1;
			Vector3f base0 = baseFrom;
			Vector3f tip0 = new Vector3f(baseFrom).add(bladeFrom);

			for (int j = 1; j <= steps; j++) {
				float u = (float) j / steps;
				Vector3f base1 = new Vector3f(baseFrom).lerp(baseTo, u);
				Vector3f tip1 = new Vector3f(base1).add(turned(bladeFrom, bladeTo, u));
				float c0 = Mth.lerp((float) (j - 1) / steps, a0, a1);
				float c1 = Mth.lerp(u, a0, a1);
				quads(pose, buffer, base0, tip0, base1, tip1, rgb, c0, c1);
				base0 = base1;
				tip0 = tip1;
			}
		}
	}

	/** The blade {@code u} of the way from {@code from} to {@code to}: turned along the arc between them, its length eased. */
	private static Vector3f turned(Vector3fc from, Vector3fc to, float u) {
		float lengthFrom = from.length();
		float lengthTo = to.length();

		if (lengthFrom < 1.0E-5f || lengthTo < 1.0E-5f) {
			return new Vector3f(from).lerp(to, u);
		}

		Vector3f a = new Vector3f(from).div(lengthFrom);
		Vector3f b = new Vector3f(to).div(lengthTo);
		float angle = (float) Math.acos(Mth.clamp(a.dot(b), -1f, 1f));
		float sin = Mth.sin(angle);
		Vector3f dir = sin < 1.0E-3f ? a.lerp(b, u) : a.mul(Mth.sin((1f - u) * angle) / sin).add(b.mul(Mth.sin(u * angle) / sin));
		return dir.normalize().mul(Mth.lerp(u, lengthFrom, lengthTo));
	}

	/** One step of the ribbon, from the blade at ({@code base0}, {@code tip0}) to ({@code base1}, {@code tip1}), split across it. */
	private static void quads(PoseStack.Pose pose, VertexConsumer buffer, Vector3fc base0, Vector3fc tip0, Vector3fc base1, Vector3fc tip1, int rgb,
			float alpha0, float alpha1) {
		for (int k = 0; k < ACROSS; k++) {
			float f0 = (float) k / ACROSS;
			float f1 = (float) (k + 1) / ACROSS;
			// Faint at the hilt, full toward the tip.
			float w0 = 0.15f + 0.85f * f0 * f0;
			float w1 = 0.15f + 0.85f * f1 * f1;
			vertex(pose, buffer, base0, tip0, f0, rgb, alpha0 * w0);
			vertex(pose, buffer, base0, tip0, f1, rgb, alpha0 * w1);
			vertex(pose, buffer, base1, tip1, f1, rgb, alpha1 * w1);
			vertex(pose, buffer, base1, tip1, f0, rgb, alpha1 * w0);
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vector3fc base, Vector3fc tip, float along, int rgb, float alpha) {
		buffer.addVertex(pose, Mth.lerp(along, base.x(), tip.x()), Mth.lerp(along, base.y(), tip.y()), Mth.lerp(along, base.z(), tip.z()))
				.setColor(GlowRibbon.fade(0xFF000000 | rgb, alpha));
	}

	private static float pow(float value, int power) {
		float result = value;

		for (int i = 1; i < power; i++) {
			result *= value;
		}

		return result;
	}

	/** Forgets blades not drawn for a second (put away, out of sight). */
	private static void prune(long now) {
		if (now - lastPrune < FORGET_NANOS / 4) {
			return;
		}

		lastPrune = now;

		for (Iterator<ArrayDeque<Sample>> it = HISTORIES.values().iterator(); it.hasNext();) {
			Sample last = it.next().peekLast();

			if (last == null || now - last.nanos() > FORGET_NANOS) {
				it.remove();
			}
		}
	}

	static void clear() {
		HISTORIES.clear();
	}

	private BladeTrails() {
	}
}
