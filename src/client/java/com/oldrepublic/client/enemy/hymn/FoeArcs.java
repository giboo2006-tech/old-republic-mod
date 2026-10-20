package com.oldrepublic.client.enemy.hymn;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.client.fx.Vfx;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Short-lived lightning arcs drawn in the world as additive glow strips (W8's Force lightning: the
 * Hymnsworn storm-caller's bursts, Ulvaine's channel, the First Cantor's chain). Each arc is a
 * jagged polyline from the hand to the struck chest, largest in the middle, with a few short forks;
 * it re-forms every two ticks while it lasts (a crackle), or holds still with flashing effects off.
 * A pale violet-white core inside a wider, fainter glow. No screen flash.
 */
public final class FoeArcs {
	private static final int MAX_ARCS = 48;
	private static final int CORE_RGB = 0xF4F0FF;

	private static final class Arc {
		final Vec3 from;
		final Vec3 to;
		final int rgb;
		final float width;
		final int forks;
		final long until;
		List<Vec3[]> segments = List.of();

		Arc(Vec3 from, Vec3 to, int rgb, float width, int forks, long until) {
			this.from = from;
			this.to = to;
			this.rgb = rgb;
			this.width = width;
			this.forks = forks;
			this.until = until;
		}
	}

	private static final List<Arc> ARCS = new ArrayList<>();
	private static boolean initialised;
	private static long ticks;

	/** Hooks the level render, the client tick and the disconnect. Called once from {@code FoesM5Client}. */
	public static void init() {
		if (initialised) {
			return;
		}

		initialised = true;
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(FoeArcs::render);
		ClientTickEvents.END_CLIENT_TICK.register(FoeArcs::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ARCS.clear());
	}

	/**
	 * An arc of lightning from {@code from} to {@code to} for {@code life} ticks, glowing {@code rgb}
	 * (0xRRGGBB) around a pale core, {@code width} blocks across, with {@code forks} side branches.
	 */
	public static void arc(Vec3 from, Vec3 to, int rgb, int life, float width, int forks) {
		if (from.distanceToSqr(to) < 0.01 || !(Vfx.inRange(from) || Vfx.inRange(to))) {
			return;
		}

		if (ARCS.size() >= MAX_ARCS) {
			ARCS.remove(0);
		}

		Arc arc = new Arc(from, to, rgb, width, Math.max(0, forks), ticks + Math.max(1, life));
		build(arc);
		ARCS.add(arc);
	}

	/** Arcs showing now (the client tests read it). */
	public static int active() {
		return ARCS.size();
	}

	private static void tick(Minecraft mc) {
		ticks++;

		if (mc.level == null) {
			ARCS.clear();
			return;
		}

		for (Iterator<Arc> it = ARCS.iterator(); it.hasNext();) {
			Arc arc = it.next();

			if (ticks > arc.until) {
				it.remove();
			} else if (Vfx.flashing() && ticks % 2 == 0) {
				build(arc);
			}
		}
	}

	/** Lays out the arc's jagged path and its forks again. */
	private static void build(Arc arc) {
		Vec3 delta = arc.to.subtract(arc.from);
		double length = delta.length();
		int n = Math.max(6, Math.min(16, 6 + (int) (length / 1.2)));
		Vec3 dir = delta.scale(1 / length);
		Vec3 u = dir.cross(new Vec3(0, 1, 0));
		u = u.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : u.normalize();
		Vec3 v = u.cross(dir).normalize();
		double amp = Math.min(0.45, length * 0.07) + 0.03;
		Vec3[] points = new Vec3[n + 1];
		points[0] = arc.from;
		points[n] = arc.to;

		for (int i = 1; i < n; i++) {
			double t = (double) i / n;
			double envelope = Math.sin(Math.PI * t);
			points[i] = arc.from.add(delta.scale(t)).add(u.scale(FoeFx.jitter(amp) * envelope)).add(v.scale(FoeFx.jitter(amp) * envelope));
		}

		List<Vec3[]> segments = new ArrayList<>(n + arc.forks * 4);

		for (int i = 0; i < n; i++) {
			segments.add(new Vec3[] {points[i], points[i + 1]});
		}

		for (int f = 0; f < arc.forks; f++) {
			Vec3 start = points[1 + FoeFx.RANDOM.nextInt(Math.max(1, n - 1))];
			Vec3 side = dir.add(u.scale(FoeFx.jitter(0.9))).add(v.scale(FoeFx.jitter(0.9))).normalize();
			double forkLength = 0.4 + FoeFx.RANDOM.nextDouble() * 0.8;
			Vec3 prev = start;
			int pieces = 3;

			for (int k = 1; k <= pieces; k++) {
				Vec3 next = start.add(side.scale(forkLength * k / pieces)).add(FoeFx.jitterVec(0.05));
				segments.add(new Vec3[] {prev, next});
				prev = next;
			}
		}

		arc.segments = segments;
	}

	private static void render(LevelRenderContext context) {
		if (ARCS.isEmpty()) {
			return;
		}

		Vec3 cam = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		boolean flicker = Vfx.flashing();
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			for (Arc arc : ARCS) {
				float alpha = flicker ? 0.7f + FoeFx.RANDOM.nextFloat() * 0.3f : 0.85f;
				int glow = FoeGlow.argb(0.28f * alpha, arc.rgb);
				int core = FoeGlow.argb(0.9f * alpha, CORE_RGB);

				for (Vec3[] segment : arc.segments) {
					FoeGlow.strip(pose, buffer, segment[0], segment[1], arc.width, glow);
					FoeGlow.strip(pose, buffer, segment[0], segment[1], arc.width * 0.3f, core);
				}
			}
		});
		poseStack.popPose();
	}

	private FoeArcs() {
	}
}
