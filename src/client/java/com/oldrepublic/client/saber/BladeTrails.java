package com.oldrepublic.client.saber;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

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
 * The motion trail behind a moving blade: where each drawn blade was over the last {@value #TRAIL_MILLIS} ms, swept into a
 * fading ribbon of light, brighter toward the tip and the newer edge, and only where the blade moved fast (a swing, a
 * spin), so a blade held still or carried at a walk leaves nothing. The positions are the blade's real ones, read from the
 * pose it is drawn with ({@link SaberBladeRenderer}), so the trail follows every swing, stance and technique exactly.
 *
 * <p>A blade seen in the world (third person, other players, armour stands) is remembered in world space, so the camera
 * may move freely. A blade in the first-person hand is remembered relative to the view, so turning the head never smears
 * it; only the swing does. Nothing is recorded while a screen is open (the inventory's player preview draws the same
 * stack in another space).
 */
final class BladeTrails {
	private static final long TRAIL_MILLIS = 130L;
	private static final long TRAIL_NANOS = TRAIL_MILLIS * 1_000_000L;
	private static final long FORGET_NANOS = 1_000_000_000L;
	private static final int MAX_SAMPLES = 32;
	/** Tip speeds (blocks a second) where the trail starts to show and where it is at full strength. */
	private static final float SLOW = 2.5f;
	private static final float FAST = 9f;
	/** Steps from the hilt to the tip across the ribbon, for its fade toward the hilt. */
	private static final int ACROSS = 3;

	private record Key(ItemStack stack, int blade, boolean firstPerson) {
	}

	/** A blade's base and tip, in world space or (first person) view space. */
	private record Sample(long nanos, double bx, double by, double bz, double tx, double ty, double tz) {
	}

	private static final Map<Key, ArrayDeque<Sample>> HISTORIES = new HashMap<>();
	private static long lastPrune;

	/**
	 * Remembers where blade {@code blade} of {@code stack} is now ({@code base} and {@code tip} as the current pose maps them)
	 * and submits its trail, if it has one.
	 */
	static void recordAndDraw(SubmitNodeCollector collector, ItemStack stack, int blade, boolean firstPerson, Vector3fc base, Vector3fc tip, int rgb,
			float strength) {
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

		collector.submitCustomGeometry(poseStack, GlowRenderTypes.glow(), (pose, buffer) -> ribbon(pose, buffer, trail, alphas, offset, rgb, 0.7f));
		collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> ribbon(pose, buffer, trail, alphas, offset, rgb, 0.3f));
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
			float fresh = (float) Math.pow(1f - age, 1.6);
			alphas[i] = strength * fresh * fast * fast * (3f - 2f * fast);
		}

		return alphas;
	}

	/** The swept ribbon between consecutive blade positions, fading toward the hilt. */
	private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Sample[] trail, float[] alphas, Vec3 offset, int rgb, float alpha) {
		for (int i = 0; i + 1 < trail.length; i++) {
			if (alphas[i] <= 0.004f && alphas[i + 1] <= 0.004f) {
				continue;
			}

			for (int k = 0; k < ACROSS; k++) {
				float f0 = (float) k / ACROSS;
				float f1 = (float) (k + 1) / ACROSS;
				// Faint at the hilt, full toward the tip.
				float w0 = 0.15f + 0.85f * f0 * f0;
				float w1 = 0.15f + 0.85f * f1 * f1;
				vertex(pose, buffer, trail[i], f0, offset, rgb, alpha * alphas[i] * w0);
				vertex(pose, buffer, trail[i], f1, offset, rgb, alpha * alphas[i] * w1);
				vertex(pose, buffer, trail[i + 1], f1, offset, rgb, alpha * alphas[i + 1] * w1);
				vertex(pose, buffer, trail[i + 1], f0, offset, rgb, alpha * alphas[i + 1] * w0);
			}
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Sample s, float along, Vec3 offset, int rgb, float alpha) {
		double x = Mth.lerp(along, s.bx(), s.tx()) - offset.x;
		double y = Mth.lerp(along, s.by(), s.ty()) - offset.y;
		double z = Mth.lerp(along, s.bz(), s.tz()) - offset.z;
		buffer.addVertex(pose, (float) x, (float) y, (float) z).setColor(GlowRibbon.fade(0xFF000000 | rgb, alpha));
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
